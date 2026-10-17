"""Guarded incremental deployment; run from the uploaded staging directory."""
import hashlib
import json
import shutil
import subprocess
import time
import urllib.request
from pathlib import Path

stage=Path(__file__).resolve().parent
live=Path('/opt/douyin_admin')
expected={
    'main.py':'58c0b80eab35145082b03d4426090c2e8af18e4a38b087b8628bab4b3d4cdb93',
    'management.py':'3e1361a098d2a2e8264883395c1cb24be47e009ad7e67aa360523c766c93d6d1',
    'admin-douyin.js':'ec8104fef332f6d84b7325aaa9f39a60bcd823fc4ce6a2016642313870d62beb',
}
for name,digest in expected.items():
    assert hashlib.sha256((live/'api'/name).read_bytes()).hexdigest()==digest,'Live source changed: '+name

def run(args,cwd=None):subprocess.run(args,cwd=str(cwd) if cwd else None,check=True)
def output(args):return subprocess.check_output(args,universal_newlines=True)
def containers():return dict(line.split() for line in output(['docker','ps','--format','{{.Names}} {{.ID}}']).splitlines())
def health():
    for attempt in range(30):
        try:
            with urllib.request.urlopen('http://127.0.0.1:9000/douyin/health',timeout=2) as r:
                if r.status==200:return
        except Exception:time.sleep(1)
    raise RuntimeError('Douyin health failed')

before=containers();pid=output(['systemctl','show','518-central-auth','-p','MainPID'])
old_image=output(['docker','inspect','douyin_admin-api-1','--format','{{.Image}}']).strip()
backup=Path('/opt/518-deploy-backups')/('douyin-151-'+time.strftime('%Y%m%d-%H%M%S'))
backup.mkdir(mode=0o700)
names=('main.py','management.py','admin-douyin.js','password_vault.py','requirements.txt','Dockerfile')
existing=[]
for name in names:
    if (live/'api'/name).exists():shutil.copy2(live/'api'/name,backup/name);existing.append(name)
run(['sqlite3',str(live/'data/app.db'),".backup '"+str(backup/'app.db')+"'"])
key=live/'data/password-vault.key'
if key.exists():shutil.copy2(key,backup/'password-vault.key')
(backup/'rollback.json').write_text(json.dumps({'image':old_image,'files':existing,'containers':before,'central':pid}))
print('BACKUP',backup,flush=True)

# Build before changing the running service, entirely from staged wheels.
run(['docker','tag',old_image,'douyin-auth-base:pre-151'])
(stage/'Dockerfile.offline').write_text('FROM douyin-auth-base:pre-151\nWORKDIR /app\nCOPY deploy-wheels /tmp/151-wheels\nRUN pip install --no-index --find-links=/tmp/151-wheels cryptography==46.0.3\nCOPY main.py management.py password_vault.py admin-douyin.js requirements.txt ./\n')
run(['docker','build','--pull=false','--network=none','-f','Dockerfile.offline','-t','douyin_admin-api:151','.'],stage)
changed=False
try:
    for name in names:shutil.copy2(stage/name,live/'api'/name)
    changed=True
    run(['docker','tag','douyin_admin-api:151','douyin_admin-api:latest'])
    run(['docker','compose','up','-d','--no-build','--no-deps','api'],live);health()
    run(['python3',str(stage/'verify_unified_live.py')])
    after=containers()
    assert all(after.get(n)==i for n,i in before.items() if n!='douyin_admin-api-1'),'Unrelated container changed'
    assert output(['systemctl','show','518-central-auth','-p','MainPID'])==pid,'Central process changed'
    # Keep the new encryption key backed up separately; never print its contents.
    if key.exists():shutil.copy2(key,backup/'password-vault.key.after')
    print('DEPLOY_151_OK; XHS PID and unrelated container IDs unchanged; backup='+str(backup),flush=True)
except Exception:
    if changed:
        for name in existing:shutil.copy2(backup/name,live/'api'/name)
        run(['docker','tag',old_image,'douyin_admin-api:latest'])
        run(['docker','compose','up','-d','--no-build','--force-recreate','--no-deps','api'],live)
        health()
        print('ROLLED_BACK: code/image only; preserved database and key',flush=True)
    raise
