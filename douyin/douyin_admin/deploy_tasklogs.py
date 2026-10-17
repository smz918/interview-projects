"""Narrow rollback-safe deployment for task-log API endpoints only."""
import hashlib
import json
import shutil
import subprocess
import time
import urllib.error
import urllib.request
from pathlib import Path

stage = Path(__file__).resolve().parent
live = Path('/opt/douyin_admin')
backup = Path('/opt/518-deploy-backups') / ('douyin-tasklogs-' + time.strftime('%Y%m%d-%H%M%S'))
backup.mkdir(mode=0o700, parents=True)
names = ('main.py', 'management.py', 'admin-douyin.js')


def run(args, cwd=None):
    subprocess.run(args, cwd=str(cwd) if cwd else None, check=True)


def output(args):
    return subprocess.check_output(args, universal_newlines=True).strip()


def health():
    for _ in range(30):
        try:
            with urllib.request.urlopen('http://127.0.0.1:18081/health', timeout=2) as r:
                if r.status == 200:
                    return
        except Exception:
            time.sleep(1)
    raise RuntimeError('Douyin API health check failed')


manifest = json.loads((stage / 'manifest.json').read_text())
for name, digest in manifest.items():
    if hashlib.sha256((stage / name).read_bytes()).hexdigest() != digest:
        raise RuntimeError('Staged source hash mismatch: ' + name)

container_before = output(['docker', 'ps', '--format', '{{.Names}} {{.ID}}'])
central_before = output(['systemctl', 'show', '518-central-auth', '-p', 'MainPID'])
old_image = output(['docker', 'inspect', 'douyin_admin-api-1', '--format', '{{.Image}}'])
for name in names:
    shutil.copy2(live / 'api' / name, backup / name)
run(['sqlite3', str(live / 'data/app.db'), ".backup '" + str(backup / 'app.db') + "'"])
(backup / 'old-image.txt').write_text(old_image)
(backup / 'container-before.txt').write_text(container_before)
(backup / 'central-before.txt').write_text(central_before)
print('BACKUP=' + str(backup), flush=True)

try:
    for name in names:
        shutil.copy2(stage / name, live / 'api' / name)
    run(['docker', 'tag', old_image, 'douyin-tasklogs-base:rollback'])
    (live / 'api/Dockerfile.tasklogs').write_text(
        'FROM douyin-tasklogs-base:rollback\nWORKDIR /app\nCOPY main.py management.py admin-douyin.js ./\n'
    )
    run(['docker', 'build', '--pull=false', '--network=none', '-f', 'Dockerfile.tasklogs', '-t', 'douyin_admin-api:tasklogs', '.'], live / 'api')
    run(['docker', 'tag', 'douyin_admin-api:tasklogs', 'douyin_admin-api:latest'])
    run(['docker', 'compose', 'up', '-d', '--no-build', '--no-deps', 'api'], live)
    health()
    req = urllib.request.Request('http://127.0.0.1:18081/api/v1/tasks/1791842400000/logs', data=b'{}', headers={'Content-Type': 'application/json'}, method='POST')
    with urllib.request.urlopen(req, timeout=5) as r:
        raise RuntimeError('Unauthenticated upload unexpectedly accepted: ' + str(r.status))
    req = urllib.request.Request('http://127.0.0.1:18081/api/v1/tasks/1791842400000/diagnostics', data=b'PK'+bytes((3, 4)), headers={'Content-Type': 'application/zip'}, method='POST')
    with urllib.request.urlopen(req, timeout=5) as r:
        raise RuntimeError('Unauthenticated diagnostics upload unexpectedly accepted: ' + str(r.status))
except urllib.error.HTTPError as e:
    if e.code not in (400, 401):
        raise
except Exception:
    for name in names:
        shutil.copy2(backup / name, live / 'api' / name)
    run(['docker', 'tag', old_image, 'douyin_admin-api:latest'])
    run(['docker', 'compose', 'up', '-d', '--no-build', '--force-recreate', '--no-deps', 'api'], live)
    health()
    print('ROLLED_BACK=' + str(backup), flush=True)
    raise

container_after = output(['docker', 'ps', '--format', '{{.Names}} {{.ID}}'])
central_after = output(['systemctl', 'show', '518-central-auth', '-p', 'MainPID'])
before = dict(line.split() for line in container_before.splitlines())
after = dict(line.split() for line in container_after.splitlines())
if central_after != central_before or any(after.get(name) != cid for name, cid in before.items() if name != 'douyin_admin-api-1'):
    raise RuntimeError('Unrelated service identity changed; inspect immediately')
print('DEPLOY_OK=' + str(backup), flush=True)
