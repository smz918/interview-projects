"""Douyin management uses the existing central administrator session."""
import json
import io
import os
import re
import secrets
import urllib.request
import zipfile
import xml.etree.ElementTree as ET
from datetime import datetime, timedelta, timezone
from pathlib import Path
from fastapi import Depends, Header, HTTPException, Request, Response
from fastapi.responses import FileResponse
from password_vault import encrypt_password, reveal_password
from pydantic import BaseModel, Field
from sqlmodel import Session, select


def central_admin(authorization: str=Header(default='')):
    if not authorization.startswith('Bearer ') or len(authorization)>512:raise HTTPException(401,'请先登录管理后台')
    request=urllib.request.Request(os.getenv('CENTRAL_ADMIN_URL','http://host.docker.internal:9000/api/auth/verify'),
        data=b'{}',headers={'Content-Type':'application/json','Authorization':authorization},method='POST')
    try:
        with urllib.request.urlopen(request,timeout=5) as response:data=json.load(response)
    except Exception:raise HTTPException(401,'管理员登录已失效，请重新登录')
    if data.get('is_admin') is not True:raise HTTPException(403,'需要管理员权限')
    return data.get('username','admin')


class ManagedUser(BaseModel):
    phone: str
    password: str=Field(min_length=6,max_length=128)
    note: str=Field(default='',max_length=500)
    referrer: str=Field(default='',max_length=100)

class UpdateUser(BaseModel):
    status: str
    note: str=Field(default='',max_length=500)
    expires_at: str=''
    referrer: str | None=Field(default=None,max_length=100)

class ResetPassword(BaseModel):
    password: str=Field(min_length=6,max_length=128)


def install(app,engine,User,Device,Audit,RemoteTask,RemoteLead,hash_password,audit,user_status):
    def recent_comment_time():
        """Use a synthetic timestamp from the latest 30 days for customer output."""
        seconds = secrets.randbelow(30 * 24 * 60 * 60 + 1)
        return (datetime.now(timezone.utc) - timedelta(seconds=seconds)).isoformat()

    def xlsx_rows(raw: bytes):
        try:
            archive=zipfile.ZipFile(io.BytesIO(raw))
            names=set(archive.namelist())
            if 'xl/worksheets/sheet1.xml' not in names or len(raw)>25*1024*1024: raise ValueError()
            ns={'m':'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
            shared=[]
            if 'xl/sharedStrings.xml' in names:
                root=ET.fromstring(archive.read('xl/sharedStrings.xml'))
                shared=[''.join(t.text or '' for t in si.findall('.//m:t',ns)) for si in root.findall('m:si',ns)]
            root=ET.fromstring(archive.read('xl/worksheets/sheet1.xml'))
            parsed=[]
            for row in root.findall('.//m:row',ns):
                values={}
                for cell in row.findall('m:c',ns):
                    ref=cell.attrib.get('r',''); col=re.match(r'[A-Z]+',ref)
                    if not col: continue
                    letters=col.group(0); index=0
                    for ch in letters: index=index*26+ord(ch)-64
                    typ=cell.attrib.get('t',''); value=''
                    if typ=='inlineStr': value=''.join(t.text or '' for t in cell.findall('.//m:t',ns))
                    else:
                        node=cell.find('m:v',ns); value=node.text if node is not None else ''
                        if typ=='s' and value.isdigit() and int(value)<len(shared): value=shared[int(value)]
                    values[index]=value
                if values: parsed.append([values.get(i,'') for i in range(1,max(values)+1)])
            if not parsed: raise ValueError()
            headers=parsed[0]; lookup={'评论内容':'comment','用户主页链接':'profile_url','评论时间':'comment_time','IP属地':'ip_location'}
            indexes={lookup[h]:i for i,h in enumerate(headers) if h in lookup}
            if len(indexes)<4: raise ValueError()
            return [{key:(row[i] if i<len(row) else '') for key,i in indexes.items()} for row in parsed[1:]]
        except Exception as exc:
            raise HTTPException(400,'XLSX 文件格式无效或缺少指定表头') from exc
    @app.get('/api/v1/management/users')
    def users(response:Response,admin=Depends(central_admin)):
        response.headers['Cache-Control']='no-store'
        with Session(engine) as session:
            rows=session.exec(select(User).order_by(User.id.desc())).all()
            devices=session.exec(select(Device)).all()
            return [{'id':u.id,'phone':u.phone,'source':'抖音','status':user_status(u),'note':u.note,
                'expires_at':u.expires_at,'created_at':u.created_at,'password':reveal_password(u.password_encrypted),
                'referrer':u.referrer,'last_login':u.last_login,'last_used':u.last_used,
                'devices':[{'device_id':d.device_id,'status':d.status,'last_seen':d.last_seen,'app_version':d.app_version} for d in devices if d.user_id==u.phone]} for u in rows]

    @app.post('/api/v1/management/users')
    def create(payload:ManagedUser,admin=Depends(central_admin)):
        if not re.fullmatch(r'1[3-9]\d{9}',payload.phone):raise HTTPException(400,'请输入11位手机号')
        with Session(engine) as session:
            if session.exec(select(User).where(User.phone==payload.phone)).first():raise HTTPException(409,'手机号已存在')
            user=User(phone=payload.phone,password_hash=hash_password(payload.password),password_encrypted=encrypt_password(payload.password),referrer=payload.referrer.strip(),status='active',note=payload.note,created_at=datetime.now(timezone.utc).isoformat())
            session.add(user);audit(session,payload.phone,'管理员创建','管理员：'+admin);session.commit()
            return {'ok':True}

    @app.put('/api/v1/management/users/{user_id}')
    def update(user_id:int,payload:UpdateUser,admin=Depends(central_admin)):
        if payload.status not in ('active','frozen','pending'):raise HTTPException(400,'授权状态无效')
        expires=''
        if payload.expires_at:
            try:
                dt=datetime.fromisoformat(payload.expires_at.replace('Z','+00:00'))
                if dt.tzinfo is None:raise ValueError()
                expires=dt.astimezone(timezone.utc).isoformat()
            except ValueError:raise HTTPException(400,'到期时间需要包含时区')
        with Session(engine) as session:
            user=session.get(User,user_id)
            if not user:raise HTTPException(404,'抖音用户不存在')
            user.status=payload.status;user.note=payload.note;user.expires_at=expires;session.add(user)
            if payload.referrer is not None:user.referrer=payload.referrer.strip()
            if payload.status!='active':
                for device in session.exec(select(Device).where(Device.user_id==user.phone)).all():
                    device.token_hash=secrets.token_hex(32);session.add(device)
            audit(session,user.phone,'授权更新','管理员：'+admin+'；状态：'+payload.status+'；到期：'+(expires or '长期'))
            session.commit();return {'ok':True,'status':user_status(user)}

    @app.post('/api/v1/management/users/{user_id}/password')
    def reset(user_id:int,payload:ResetPassword,admin=Depends(central_admin)):
        with Session(engine) as session:
            user=session.get(User,user_id)
            if not user:raise HTTPException(404,'抖音用户不存在')
            user.password_hash=hash_password(payload.password);user.password_encrypted=encrypt_password(payload.password);session.add(user)
            for device in session.exec(select(Device).where(Device.user_id==user.phone)).all():
                device.token_hash=secrets.token_hex(32);session.add(device)
            audit(session,user.phone,'密码重置','管理员：'+admin+'；旧登录已失效');session.commit()
        return {'ok':True}

    @app.get('/api/v1/management/logs')
    def logs(phone:str='',admin=Depends(central_admin)):
        with Session(engine) as session:
            query=select(Audit)
            if phone:query=query.where(Audit.phone==phone)
            return session.exec(query.order_by(Audit.id.desc()).limit(200)).all()

    @app.get('/api/v1/management/remote-tasks')
    def remote_tasks(status: str = '', admin=Depends(central_admin)):
        with Session(engine) as session:
            query = select(RemoteTask).order_by(RemoteTask.id.desc()).limit(500)
            if status: query = query.where(RemoteTask.status == status)
            tasks = session.exec(query).all()
            return {'tasks': [{'task_id': t.task_id, 'user_id': t.user_id, 'task_name': t.task_name, 'keyword': t.keyword,
                'sort': t.sort, 'content_form': t.content_form, 'published': t.published, 'scope': t.scope,
                'duration': t.duration, 'status': t.status, 'claimed_by': t.claimed_by, 'error': t.error,
                'result_count': t.result_count, 'created_at': t.created_at, 'updated_at': t.updated_at} for t in tasks]}

    @app.delete('/api/v1/management/remote-tasks/{task_id}')
    def delete_remote_task(task_id: str, admin=Depends(central_admin)):
        """Delete a server-acquisition task and all results submitted for it."""
        if not re.fullmatch(r'[a-f0-9]{24}', task_id):
            raise HTTPException(400, '任务编号无效')
        with Session(engine) as session:
            task = session.exec(select(RemoteTask).where(RemoteTask.task_id == task_id)).first()
            if not task:
                raise HTTPException(404, '任务不存在')
            for row in session.exec(select(RemoteLead).where(RemoteLead.task_id == task_id)).all():
                session.delete(row)
            audit(session, task.user_id, '删除服务器获客任务', '任务：' + task_id + '；管理员：' + admin)
            session.delete(task)
            session.commit()
        return {'ok': True, 'task_id': task_id}

    @app.post('/api/v1/management/remote-tasks/{task_id}/claim')
    def claim_remote_task(task_id: str, admin=Depends(central_admin)):
        with Session(engine) as session:
            task = session.exec(select(RemoteTask).where(RemoteTask.task_id == task_id)).first()
            if not task: raise HTTPException(404, '任务不存在')
            if task.status not in ('pending', 'processing'): raise HTTPException(409, '任务已经完成或失败')
            task.status='processing'; task.claimed_by=admin; task.updated_at=datetime.now(timezone.utc).isoformat(); session.add(task); session.commit()
            return {'ok': True, 'task_id': task_id, 'status': task.status, 'claimed_by': admin}

    @app.post('/api/v1/management/remote-tasks/{task_id}/status')
    def set_remote_task_status(task_id: str, payload: dict, admin=Depends(central_admin)):
        status = str(payload.get('status','')).strip()
        if status not in ('pending','processing','failed'): raise HTTPException(400, '状态无效')
        with Session(engine) as session:
            task = session.exec(select(RemoteTask).where(RemoteTask.task_id == task_id)).first()
            if not task: raise HTTPException(404, '任务不存在')
            task.status=status; task.error=str(payload.get('error',''))[:500]; task.updated_at=datetime.now(timezone.utc).isoformat(); session.add(task); session.commit()
            return {'ok': True, 'task_id': task_id, 'status': status}

    @app.post('/api/v1/management/remote-tasks/{task_id}/results')
    def upload_remote_results(task_id: str, payload: dict, admin=Depends(central_admin)):
        rows = payload.get('rows', [])
        if not isinstance(rows, list) or len(rows) > 10000: raise HTTPException(400, '结果格式或数量无效')
        def value(row, *names):
            for name in names:
                v = row.get(name, '') if isinstance(row, dict) else ''
                if v is not None and str(v).strip(): return str(v).strip()
            return ''
        normalized=[]; seen=set()
        for row in rows:
            item={'comment':value(row,'comment','评论内容'), 'profile_url':value(row,'profile_url','用户主页链接'),
                'comment_time':recent_comment_time(), 'ip_location':value(row,'ip_location','IP属地')}
            if any(len(v)>2000 for v in item.values()): raise HTTPException(400, '单条结果字段过长')
            key=tuple(item.values())
            if key == ('','','','') or key in seen: continue
            seen.add(key); normalized.append(item)
        with Session(engine) as session:
            task=session.exec(select(RemoteTask).where(RemoteTask.task_id==task_id)).first()
            if not task: raise HTTPException(404,'任务不存在')
            for old in session.exec(select(RemoteLead).where(RemoteLead.task_id==task_id)).all(): session.delete(old)
            for item in normalized: session.add(RemoteLead(task_id=task_id, **item))
            task.status='completed'; task.error=''; task.result_count=len(normalized); task.updated_at=datetime.now(timezone.utc).isoformat(); session.add(task)
            audit(session,task.user_id,'人工获客结果上传','任务：'+task_id+'；结果：'+str(len(normalized))); session.commit()
        return {'ok':True,'task_id':task_id,'status':'completed','result_count':len(normalized)}

    @app.post('/api/v1/management/remote-tasks/{task_id}/results-xlsx')
    async def upload_remote_xlsx(task_id: str, request: Request, admin=Depends(central_admin)):
        raw=await request.body()
        rows=xlsx_rows(raw)
        # Reuse the same normalization and ownership-independent completion path.
        with Session(engine) as session:
            task=session.exec(select(RemoteTask).where(RemoteTask.task_id==task_id)).first()
            if not task: raise HTTPException(404,'任务不存在')
            for old in session.exec(select(RemoteLead).where(RemoteLead.task_id==task_id)).all(): session.delete(old)
            seen=set(); normalized=[]
            for row in rows:
                item={k:str(row.get(k,'')).strip()[:2000] for k in ('comment','profile_url','ip_location')}
                item['comment_time']=recent_comment_time()
                key=tuple(item.values())
                if key==('','','','') or key in seen: continue
                seen.add(key); normalized.append(item)
            for item in normalized: session.add(RemoteLead(task_id=task_id, **item))
            task.status='completed'; task.error=''; task.result_count=len(normalized); task.updated_at=datetime.now(timezone.utc).isoformat(); session.add(task)
            audit(session,task.user_id,'上传人工获客 XLSX 结果','任务：'+task_id+'；结果：'+str(len(normalized))); session.commit()
        return {'ok':True,'task_id':task_id,'status':'completed','result_count':len(normalized)}

    @app.get('/api/v1/management/remote-tasks/{task_id}/results')
    def remote_results(task_id: str, admin=Depends(central_admin)):
        with Session(engine) as session:
            task=session.exec(select(RemoteTask).where(RemoteTask.task_id==task_id)).first()
            if not task: raise HTTPException(404,'任务不存在')
            return {'task_id':task_id,'rows':[{'comment':r.comment,'profile_url':r.profile_url,'comment_time':r.comment_time,'ip_location':r.ip_location} for r in session.exec(select(RemoteLead).where(RemoteLead.task_id==task_id).order_by(RemoteLead.id)).all()]}

    @app.get('/api/v1/management/users/{user_id}/tasks')
    def task_logs(user_id:int,admin=Depends(central_admin)):
        with Session(engine) as session:
            user=session.get(User,user_id)
            if not user:raise HTTPException(404,'抖音用户不存在')
            folder=Path(os.getenv('TASK_LOG_DIR','/data/task-logs'))/user.phone
            if not folder.is_dir():return []
            return [{'task_id':p.stem,'size':p.stat().st_size,'updated_at':datetime.fromtimestamp(p.stat().st_mtime,timezone.utc).isoformat()} for p in sorted(folder.glob('*.log'),key=lambda x:x.stat().st_mtime,reverse=True)[:500]]

    @app.get('/api/v1/management/users/{user_id}/tasks/{task_id}/logs')
    def download_task_log(user_id:int,task_id:str,admin=Depends(central_admin)):
        if not re.fullmatch(r'[0-9]{10,20}',task_id):raise HTTPException(400,'任务编号无效')
        with Session(engine) as session:
            user=session.get(User,user_id)
            if not user:raise HTTPException(404,'抖音用户不存在')
            path=Path(os.getenv('TASK_LOG_DIR','/data/task-logs'))/user.phone/(task_id+'.log')
            if not path.is_file():raise HTTPException(404,'任务日志不存在')
            return FileResponse(path,media_type='text/plain; charset=utf-8',filename=user.phone+'_'+task_id+'.log')

    @app.get('/api/v1/management/users/{user_id}/diagnostics')
    def task_diagnostics(user_id:int,admin=Depends(central_admin)):
        with Session(engine) as session:
            user=session.get(User,user_id)
            if not user:raise HTTPException(404,'抖音用户不存在')
            folder=Path(os.getenv('TASK_LOG_DIR','/data/task-logs'))/user.phone
            if not folder.is_dir():return []
            return [{'task_id':p.stem,'size':p.stat().st_size,'updated_at':datetime.fromtimestamp(p.stat().st_mtime,timezone.utc).isoformat()} for p in sorted(folder.glob('*.zip'),key=lambda x:x.stat().st_mtime,reverse=True)[:500]]

    @app.get('/api/v1/management/users/{user_id}/tasks/{task_id}/diagnostics')
    def download_task_diagnostics(user_id:int,task_id:str,admin=Depends(central_admin)):
        if not re.fullmatch(r'[0-9]{10,20}',task_id):raise HTTPException(400,'任务编号无效')
        with Session(engine) as session:
            user=session.get(User,user_id)
            if not user:raise HTTPException(404,'抖音用户不存在')
            path=Path(os.getenv('TASK_LOG_DIR','/data/task-logs'))/user.phone/(task_id+'.zip')
            if not path.is_file():raise HTTPException(404,'诊断包不存在')
            return FileResponse(path,media_type='application/zip',filename=user.phone+'_'+task_id+'_诊断.zip')
