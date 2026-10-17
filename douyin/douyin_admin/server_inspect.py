"""Read-only inventory of the existing services; never print environment secrets."""
import getpass
import paramiko

client = paramiko.SSHClient()
client.load_system_host_keys()
client.set_missing_host_key_policy(paramiko.RejectPolicy())
client.connect('47.120.41.123', username='root', password=getpass.getpass('SSH password: '), timeout=15)
try:
    commands = [
        "ss -ltnp | head -35",
        "docker ps --format '{{.Names}} {{.Image}} {{.Ports}}'",
        "ls -la /opt; ps -eo pid,comm,args | grep -E 'uvicorn|gunicorn|node|nginx|9000' | grep -v grep",
        "curl -sS --max-time 10 -I http://127.0.0.1:9000/",
    ]
    for command in commands:
        _, out, err = client.exec_command(command, timeout=25)
        print(out.read().decode('utf-8', errors='replace'))
        print(err.read().decode('utf-8', errors='replace'))
finally:
    client.close()
