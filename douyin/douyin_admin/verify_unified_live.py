"""Live authorization smoke: isolated temporary Douyin account, no social actions."""
import hashlib
import json
import secrets
import sqlite3
import urllib.error
import urllib.request
from datetime import datetime,timedelta

base='http://127.0.0.1:9000'
token=secrets.token_urlsafe(32)
phone='199'+''.join(str(secrets.randbelow(10)) for _ in range(8))
password=secrets.token_urlsafe(18)
device='unified-regression-'+secrets.token_hex(8)

def request(path,method='GET',data=None,auth='',expected=200):
    headers={'Content-Type':'application/json'}
    if auth:headers['Authorization']='Bearer '+auth
    req=urllib.request.Request(base+path,data=None if data is None else json.dumps(data).encode(),headers=headers,method=method)
    try:
        with urllib.request.urlopen(req,timeout=12) as r:status=r.status;raw=r.read()
    except urllib.error.HTTPError as e:status=e.code;raw=e.read()
    if status!=expected:raise AssertionError(path+' HTTP '+str(status)+' expected '+str(expected))
    return json.loads(raw)

def xhs_fingerprint():
    with sqlite3.connect('/opt/518-auth/auth.db') as c:
        return hashlib.sha256(repr(c.execute('select * from users order by id').fetchall()).encode()).hexdigest()

before=xhs_fingerprint();created=False
try:
    with sqlite3.connect('/opt/518-auth/auth.db') as c:
        admin=c.execute('select id from users where is_admin=1 and is_active=1 limit 1').fetchone()
        if not admin:raise RuntimeError('No existing active administrator')
        c.execute('insert into sessions(token,user_id,device_id,device_name,app_version,expires_at) values(?,?,?,?,?,?)',
            (token,admin[0],'unified-regression','deployment-test','test',(datetime.utcnow()+timedelta(minutes=10)).strftime('%Y-%m-%d %H:%M:%S')))
    assert request('/health')['ok']
    assert request('/douyin/health')['ok']
    request('/api/auth/users',auth=token)
    request('/douyin/api/v1/management/users',expected=401)
    body={'phone':phone,'password':password,'device_id':device,'referrer':'部署回归推荐人'}
    result=request('/douyin/api/v1/auth/register','POST',body);created=True;assert result['status']=='pending'
    request('/douyin/api/v1/auth/login','POST',body,expected=403)
    users=request('/douyin/api/v1/management/users',auth=token)
    user=next(u for u in users if u['phone']==phone);assert user['source']=='抖音'
    assert 'password_hash' not in user
    assert 'password_encrypted' not in user
    assert user['password']==password and user['referrer']==body['referrer']
    assert user['created_at'] and not user['last_login'] and not user['last_used']
    route='/douyin/api/v1/management/users/'+str(user['id'])
    request(route,'PUT',{'status':'active','note':'部署回归临时账号','expires_at':''},token)
    login=request('/douyin/api/v1/auth/login','POST',body);session=login['token']
    heartbeat='/douyin/api/v1/device/'+device+'/heartbeat'
    assert request(heartbeat,'POST',{'app_version':'1.5.1-test'},session)['status']=='active'
    latest=next(u for u in request('/douyin/api/v1/management/users',auth=token) if u['phone']==phone)
    assert latest['last_login'] and not latest['last_used']
    request(heartbeat,'POST',{'app_version':'1.5.1-test','running_task':'fixture-task'},session)
    used=next(u for u in request('/douyin/api/v1/management/users',auth=token) if u['phone']==phone)
    assert used['last_used'] and used['last_login']==latest['last_login']
    request(route,'PUT',{'status':'active','referrer':'修改推荐人'},token)
    assert next(u for u in request('/douyin/api/v1/management/users',auth=token) if u['phone']==phone)['referrer']=='修改推荐人'
    request('/douyin/api/v1/management/users',auth=session,expected=401)
    request(route,'PUT',{'status':'frozen','note':'部署回归临时账号','expires_at':''},token)
    request(heartbeat,'POST',{},session,401)
    new_password=secrets.token_urlsafe(18)
    request(route+'/password','POST',{'password':new_password},token)
    latest=next(u for u in request('/douyin/api/v1/management/users',auth=token) if u['phone']==phone)
    assert latest['password']==new_password
    with sqlite3.connect('/opt/douyin_admin/data/app.db') as c:
        ciphertext=c.execute('select password_encrypted from user where phone=?',(phone,)).fetchone()[0]
        assert ciphertext and new_password not in ciphertext
    request('/douyin/api/v1/ai/judge','POST',{'comment':'fixture'},session,403)
    body['password']=new_password
    request('/douyin/api/v1/auth/login','POST',body,expected=403)
    request(route,'PUT',{'status':'active','note':'部署回归临时账号','expires_at':''},token)
    body['password']=new_password
    session=request('/douyin/api/v1/auth/login','POST',body)['token']
    request('/douyin/api/v1/auth/logout','POST',{},session)
    request(heartbeat,'POST',{},session,401)
    assert before==xhs_fingerprint(),'Existing XHS users changed'
    print('PASS: XHS unchanged; register/activate/password display+encryption/referrer/login+use times/freeze/AI denial/reset/restore/logout')
finally:
    with sqlite3.connect('/opt/518-auth/auth.db') as c:c.execute('delete from sessions where token=?',(token,))
    if created:
        with sqlite3.connect('/opt/douyin_admin/data/app.db') as c:
            c.execute('delete from device where device_id=?',(device,))
            c.execute('delete from audit where phone=?',(phone,))
            c.execute('delete from user where phone=?',(phone,))
    print('Temporary test account/session removed; existing accounts untouched')
