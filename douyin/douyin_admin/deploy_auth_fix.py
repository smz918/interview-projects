"""Deploy only the reviewed login change to the existing isolated service."""
import os
import sys
import time
from pathlib import Path
import paramiko

c = paramiko.SSHClient()
c.load_system_host_keys()
c.connect("47.120.41.123", username="root", password=os.environ["LEAD_SERVER_PASSWORD"], timeout=15)
def run(cmd):
    _, out, err = c.exec_command(cmd, timeout=60)
    text = out.read().decode()
    error = err.read().decode()
    print(text[-5000:])
    if out.channel.recv_exit_status():
        raise RuntimeError(error[-2000:])

try:
    run("cd /opt/douyin_admin && docker compose ps && sha256sum api/main.py")
    if "--deploy" in sys.argv:
        s = c.open_sftp()
        path = "/opt/douyin_admin/api/main.py"
        original = s.open(path).read().decode()
        anchor = '                device.user_id, device.status, device.token_hash = req.phone, "active", hashlib.sha256(token.encode()).hexdigest()'
        replacement = '''                if device.status != "active":
                    raise HTTPException(status_code=403, detail="设备已停用，请联系管理员")
                if device.user_id != req.phone:
                    raise HTTPException(status_code=403, detail="设备已绑定其他账号")
                device.token_hash = hashlib.sha256(token.encode()).hexdigest()'''
        if anchor not in original:
            if replacement not in original:
                raise RuntimeError("Server source differs: no exact patch target")
        else:
            assert original.count(anchor) == 1
            with s.open(path + ".backup-" + str(int(time.time())), "w") as out:
                out.write(original)
            with s.open(path + ".staged", "w") as out:
                out.write(original.replace(anchor, replacement))
            s.posix_rename(path + ".staged", path)
        s.put(str(Path(__file__).with_name("verify_authorization.py")), "/opt/douyin_admin/verify_authorization.py")
        run("cd /opt/douyin_admin && docker compose up -d --build api")
        run("cd /opt/douyin_admin && docker compose cp verify_authorization.py api:/tmp/verify_authorization.py && docker compose exec -T api python /tmp/verify_authorization.py")
    run("curl --fail --max-time 10 http://127.0.0.1:18081/health")
finally:
    c.close()
