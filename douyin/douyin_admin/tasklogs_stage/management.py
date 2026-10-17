"""Douyin management uses the existing central administrator session."""
import json
import os
import re
import secrets
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from fastapi import Depends, Header, HTTPException, Response
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


def install(app,engine,User,Device,Audit,hash_password,audit,user_status):
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
