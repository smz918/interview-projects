"""Run on server after uploading to a unique staging directory.
Only Douyin service is recreated; central service is not restarted.
"""
import hashlib
import json
import shutil
import sqlite3
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

stage=Path(__file__).resolve().parent
live=Path('/opt/douyin_admin')
central=Path('/opt/518-auth/central_auth')
nginx=Path('/www/server/panel/vhost/nginx/518-auth-9000.conf')
backup=Path('/opt/518-deploy-backups')/('douyin-unified-'+time.strftime('%Y%m%d-%H%M%S'))
backup.mkdir(mode=0o700)
manifest=json.loads((stage/'manifest.json').read_text())
for target,digest in manifest.items():
    if hashlib.sha256(Path(target).read_bytes()).hexdigest()!=digest:raise RuntimeError('Source changed, refusing overwrite: '+target)

def run(args,cwd=None):
    subprocess.run(args,cwd=cwd,check=True)

def health():
    for _ in range(20):
        try:
            with urllib.request.urlopen('http://127.0.0.1:18081/health',timeout=2) as r:
                if r.status==200:return
        except Exception:time.sleep(1)
    raise RuntimeError('Douyin health failed')

paths=[live/'api/main.py',live/'api/Dockerfile',live/'docker-compose.yml',central/'admin.html',nginx]
for i,p in enumerate(paths):shutil.copy2(p,backup/str(i))
for src,name in [(live/'data/app.db','douyin.db'),(Path('/opt/518-auth/auth.db'),'central.db')]:
    run(['sqlite3',str(src),".backup '"+str(backup/name)+"'"])
old_image=subprocess.check_output(['docker','inspect','douyin_admin-api-1','--format','{{.Image}}'],universal_newlines=True).strip()
(backup/'old-image.txt').write_text(old_image)
(backup/'paths.json').write_text(json.dumps([str(p) for p in paths]))
print('BACKUP',backup,flush=True)
try:
    for name in ('main.py','management.py','admin-douyin.js','Dockerfile'):
        shutil.copy2(stage/name,live/'api'/name)
    compose=(live/'docker-compose.yml').read_text()
    if 'host.docker.internal:host-gateway' not in compose:
        compose=compose.replace('    restart: unless-stopped','    restart: unless-stopped\n    extra_hosts:\n      - "host.docker.internal:host-gateway"',1)
    (live/'docker-compose.yml').write_text(compose)
    # Dependencies are unchanged: layer code over the verified installed image,
    # avoiding a registry/network pull on this production host.
    run(['docker','tag',old_image,'douyin-auth-base:pre-unified'])
    (live/'api/Dockerfile.deploy').write_text('FROM douyin-auth-base:pre-unified\nWORKDIR /app\nCOPY main.py management.py admin-douyin.js ./\n')
    run(['docker','build','--pull=false','--network=none','-f','Dockerfile.deploy','-t','douyin_admin-api:latest','.'],str(live/'api'))
    run(['docker','compose','up','-d','--no-build','--no-deps','api'],str(live));health()
    config=nginx.read_text()
    if 'location /douyin/' not in config:
        config=config.replace('    location / {','    location /douyin/ {\n        proxy_pass http://127.0.0.1:18081/;\n        proxy_set_header Host $host;\n        proxy_set_header X-Real-IP $remote_addr;\n        proxy_read_timeout 60s;\n    }\n\n    location / {',1)
    nginx.write_text(config)
    run(['/www/server/nginx/sbin/nginx','-t'])
    run(['/www/server/nginx/sbin/nginx','-s','reload'])
    html=(central/'admin.html').read_text()
    if '/douyin/admin-douyin.js' not in html:
        html=html.replace('</body>','<script src="/douyin/admin-douyin.js?v=1.5.0"></script>\n</body>')
    (central/'admin.html').write_text(html)
    # nginx reload is asynchronous; old workers can briefly serve the prior routes.
    for attempt in range(20):
        try:
            with urllib.request.urlopen('http://127.0.0.1:9000/douyin/health',timeout=3) as r:assert r.status==200
            break
        except Exception:
            if attempt==19:raise
            time.sleep(.5)
    with urllib.request.urlopen('http://127.0.0.1:9000/health',timeout=10) as r:assert r.status==200
    print('DEPLOY_OK',backup,flush=True)
except Exception:
    for i,p in enumerate(paths):shutil.copy2(backup/str(i),p)
    # Roll back code/image. Additive DB columns can remain; do not discard new writes.
    run(['docker','tag',old_image,'douyin_admin-api:latest'])
    run(['docker','compose','up','-d','--no-build','--force-recreate','--no-deps','api'],str(live))
    run(['/www/server/nginx/sbin/nginx','-t']);run(['/www/server/nginx/sbin/nginx','-s','reload'])
    raise
