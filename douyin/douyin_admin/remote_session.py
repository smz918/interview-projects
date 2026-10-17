"""Interactive maintenance channel. Password is hidden and never persisted."""
import getpass
import json
import sys
import time
from pathlib import Path
import paramiko

sys.stdout.reconfigure(encoding='utf-8')
client=paramiko.SSHClient()
client.load_system_host_keys()
client.set_missing_host_key_policy(paramiko.RejectPolicy())
client.connect('47.120.41.123',username='root',password=getpass.getpass('SSH password: '),timeout=15)
client.get_transport().set_keepalive(20)
print('CONNECTED',flush=True)
try:
    for line in sys.stdin:
        try:
            req=json.loads(line)
            if req.get('exit'):break
            if 'cmd' in req:
                _,out,err=client.exec_command(req['cmd'],timeout=req.get('timeout',60))
                channel=out.channel
                while not channel.exit_status_ready() or channel.recv_ready() or channel.recv_stderr_ready():
                    if channel.recv_ready():print(channel.recv(32768).decode('utf-8',errors='replace'),end='',flush=True)
                    if channel.recv_stderr_ready():print(channel.recv_stderr(32768).decode('utf-8',errors='replace'),end='',flush=True)
                    time.sleep(.1)
                print('EXIT',out.channel.recv_exit_status(),flush=True)
            elif 'get' in req:
                with client.open_sftp() as sftp:
                    for remote,local in req['get']:
                        Path(local).parent.mkdir(parents=True,exist_ok=True)
                        sftp.get(remote,local)
                print('DOWNLOADED',len(req['get']),flush=True)
            elif 'put' in req:
                with client.open_sftp() as sftp:
                    for local,remote in req['put']:sftp.put(local,remote)
                print('UPLOADED',len(req['put']),flush=True)
        except Exception as exc: print(type(exc).__name__,str(exc),flush=True)
finally:client.close()
