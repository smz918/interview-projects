import os
import sys
import tempfile
import unittest
import io
import zipfile
from pathlib import Path
from unittest.mock import patch

temp=tempfile.TemporaryDirectory()
os.environ['DATABASE_URL']='sqlite:///'+str(Path(temp.name)/'test.db')
os.environ['PASSWORD_VAULT_KEY_FILE']=str(Path(temp.name)/'vault.key')
sys.path.insert(0,str(Path(__file__).parent/'api'))
import main
import management
from fastapi.testclient import TestClient
from sqlmodel import Session,select


class ManagementTest(unittest.TestCase):
    @classmethod
    def tearDownClass(cls):
        cls.client.close();main.engine.dispose();temp.cleanup()
    @classmethod
    def setUpClass(cls):
        main.startup();main.app.dependency_overrides[management.central_admin]=lambda:'fixture-admin'
        cls.client=TestClient(main.app)
    def test_account_lifecycle(self):
        c=self.client;body={'phone':'13900009876','password':'fixture-password','device_id':'fixture-one','referrer':'测试推荐人'}
        self.assertEqual(c.post('/api/v1/auth/register',json=body).json()['status'],'pending')
        self.assertEqual(c.post('/api/v1/auth/register',json=body).status_code,409)
        self.assertEqual(c.post('/api/v1/auth/login',json=body).status_code,403)
        users=c.get('/api/v1/management/users').json();u=next(u for u in users if u['phone']==body['phone'])
        self.assertNotIn('password_hash',u);self.assertEqual(u['source'],'抖音')
        self.assertNotIn('password_encrypted',u)
        self.assertEqual(u['password'],body['password']);self.assertEqual(u['referrer'],body['referrer'])
        self.assertTrue(u['created_at']);self.assertEqual(u['last_login'],'');self.assertEqual(u['last_used'],'')
        self.assertEqual(c.get('/api/v1/management/users').headers['cache-control'],'no-store')
        def current():return next(row for row in c.get('/api/v1/management/users').json() if row['id']==u['id'])
        def status(value,expires=''):
            return c.put('/api/v1/management/users/'+str(u['id']),json={'status':value,'note':'fixture','expires_at':expires})
        self.assertEqual(status('active').status_code,200)
        token=c.post('/api/v1/auth/login',json=body).json()['token'];headers={'Authorization':'Bearer '+token}
        logged=current();self.assertTrue(logged['last_login']);self.assertEqual(logged['referrer'],body['referrer'])
        self.assertEqual(c.post('/api/v1/device/fixture-one/heartbeat',json={},headers=headers).json()['status'],'active')
        self.assertEqual(current()['last_used'],'')
        c.post('/api/v1/device/fixture-one/heartbeat',json={'running_task':'test-task'},headers=headers)
        used=current();self.assertTrue(used['last_used']);self.assertEqual(used['last_login'],logged['last_login'])
        c.put('/api/v1/management/users/'+str(u['id']),json={'status':'active','referrer':'新推荐人'})
        self.assertEqual(current()['referrer'],'新推荐人')
        self.assertEqual(c.post('/api/v1/device/wrong/heartbeat',json={},headers=headers).status_code,401)
        self.assertEqual(main.authorize_ai('fixture-one','Bearer '+token,''),'fixture-one')
        status('frozen')
        self.assertEqual(c.post('/api/v1/device/fixture-one/heartbeat',json={},headers=headers).status_code,401)
        with self.assertRaises(main.HTTPException):main.authorize_ai('fixture-one','Bearer '+token,'')
        self.assertEqual(c.post('/api/v1/auth/login',json=body).status_code,403)
        status('active','2020-01-01T00:00:00Z')
        self.assertEqual(c.post('/api/v1/auth/login',json=body).status_code,403)
        status('active')
        token=c.post('/api/v1/auth/login',json=body).json()['token'];headers={'Authorization':'Bearer '+token}
        c.post('/api/v1/auth/logout',headers=headers)
        self.assertEqual(c.post('/api/v1/device/fixture-one/heartbeat',json={},headers=headers).status_code,401)
        self.assertEqual(c.post('/api/v1/auth/login',json=body).status_code,200)
        self.assertEqual(c.post('/api/v1/management/users/'+str(u['id'])+'/password',json={'password':'changed-password'}).status_code,200)
        self.assertEqual(c.post('/api/v1/auth/login',json=body).status_code,401)
        body['password']='changed-password';self.assertEqual(c.post('/api/v1/auth/login',json=body).status_code,200)
        self.assertEqual(current()['password'],'changed-password')
        with Session(main.engine) as s:
            stored=s.exec(select(main.User).where(main.User.phone==body['phone'])).one()
            self.assertTrue(stored.password_hash.startswith('pbkdf2$'))
            self.assertNotIn(body['password'],stored.password_encrypted)
        self.assertGreater(len(c.get('/api/v1/management/logs').json()),4)
    def test_admin_boundary(self):
        with self.assertRaises(main.HTTPException):management.central_admin('')
        class Response:
            def __enter__(self):return self
            def __exit__(self,*a):pass
            def read(self):return b'{"is_admin":false}'
        with patch('management.urllib.request.urlopen',return_value=Response()):
            with self.assertRaises(main.HTTPException):management.central_admin('Bearer user-token')
        main.app.dependency_overrides.clear()
        try:self.assertEqual(self.client.get('/api/v1/management/users').status_code,401)
        finally:main.app.dependency_overrides[management.central_admin]=lambda:'fixture-admin'
    def test_admin_created_password(self):
        c=self.client
        self.assertEqual(c.post('/api/v1/management/users',json={'phone':'13900009874','password':'created-pass','referrer':'管理员推荐'}).status_code,200)
        u=next(u for u in c.get('/api/v1/management/users').json() if u['phone']=='13900009874')
        self.assertEqual(u['password'],'created-pass');self.assertEqual(u['referrer'],'管理员推荐')
    def test_task_log_upload_list_download_and_delete(self):
        c=self.client;phone='13900009873';task='1791842400000'
        body={'phone':phone,'password':'fixture-password','device_id':'log-device'}
        self.assertEqual(c.post('/api/v1/auth/register',json=body).status_code,200)
        user=next(u for u in c.get('/api/v1/management/users').json() if u['phone']==phone)
        c.put('/api/v1/management/users/'+str(user['id']),json={'status':'active','note':'','expires_at':''})
        token=c.post('/api/v1/auth/login',json=body).json()['token'];headers={'Authorization':'Bearer '+token}
        with patch.dict(os.environ,{'TASK_LOG_DIR':str(Path(temp.name)/'task-logs')}):
            uploaded=c.post('/api/v1/tasks/'+task+'/logs',json={'logs':'task started\ntask finished'},headers=headers)
            self.assertEqual(uploaded.status_code,200)
            self.assertEqual(c.post('/api/v1/tasks/'+task+'/logs',json={'logs':'x'},headers={'Authorization':'Bearer invalid'}).status_code,401)
            listed=c.get('/api/v1/management/users/'+str(user['id'])+'/tasks').json()
            self.assertEqual([x['task_id'] for x in listed],[task])
            downloaded=c.get('/api/v1/management/users/'+str(user['id'])+'/tasks/'+task+'/logs')
            self.assertEqual(downloaded.status_code,200);self.assertEqual(downloaded.text,'task started\ntask finished')
            archive=io.BytesIO()
            with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:z.writestr('logs.txt','task started');z.writestr('xml/1791842400000_1.xml','<hierarchy/>')
            diagnostic=c.post('/api/v1/tasks/'+task+'/diagnostics',content=archive.getvalue(),headers={**headers,'Content-Type':'application/zip'})
            self.assertEqual(diagnostic.status_code,200,diagnostic.text)
            self.assertEqual(c.post('/api/v1/tasks/'+task+'/diagnostics',content=b'not a zip',headers={**headers,'Content-Type':'application/zip'}).status_code,400)
            diagnostics=c.get('/api/v1/management/users/'+str(user['id'])+'/diagnostics').json()
            self.assertEqual([x['task_id'] for x in diagnostics],[task])
            downloaded_zip=c.get('/api/v1/management/users/'+str(user['id'])+'/tasks/'+task+'/diagnostics')
            self.assertEqual(downloaded_zip.status_code,200);self.assertEqual(downloaded_zip.content,archive.getvalue())
            self.assertEqual(c.post('/api/v1/tasks/'+task+'/delete',headers=headers).status_code,200)
            self.assertEqual(c.get('/api/v1/management/users/'+str(user['id'])+'/tasks').json(),[])
            self.assertEqual(c.get('/api/v1/management/users/'+str(user['id'])+'/diagnostics').json(),[])
    def test_legacy_password_upgrade(self):
        with Session(main.engine) as s:
            s.add(main.User(phone='13900009875',password_hash=main.hashlib.sha256(b'legacy-pass').hexdigest()));s.commit()
        response=self.client.post('/api/v1/auth/login',json={'phone':'13900009875','password':'legacy-pass','device_id':'legacy-device'})
        self.assertEqual(response.status_code,200)
        with Session(main.engine) as s:
            u=s.exec(select(main.User).where(main.User.phone=='13900009875')).one()
            self.assertTrue(u.password_hash.startswith('pbkdf2$'));self.assertEqual(u.password_encrypted,'')

if __name__=='__main__':unittest.main()
