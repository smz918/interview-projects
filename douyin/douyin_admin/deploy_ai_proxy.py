"""Deploy the AI proxy without placing either secret in the repository."""
import os
from pathlib import Path
import paramiko

HOST = "47.120.41.123"
ROOT = "/opt/douyin_admin"
password = os.environ["LEAD_SERVER_PASSWORD"]
ark_key = os.environ["ARK_API_KEY"]
proxy_token = os.environ["AI_PROXY_TOKEN"]

client = paramiko.SSHClient()
client.load_system_host_keys()
client.set_missing_host_key_policy(paramiko.RejectPolicy())
client.connect(HOST, username="root", password=password, timeout=15)

def run(command):
    _, stdout, stderr = client.exec_command(command, timeout=120)
    code = stdout.channel.recv_exit_status()
    out = stdout.read().decode("utf-8", "replace").strip()
    err = stderr.read().decode("utf-8", "replace").strip()
    if code:
        raise RuntimeError(f"remote command failed ({code}): {err[-500:]}")
    return out

try:
    sftp = client.open_sftp()
    for local, remote in ((Path("api") / "main.py", f"{ROOT}/api/main.py"), (Path("docker-compose.yml"), f"{ROOT}/docker-compose.yml")):
        staged = remote + ".staged"
        sftp.put(str(Path(__file__).parent / local), staged)
        sftp.posix_rename(staged, remote)

    env_path = f"{ROOT}/.env"
    try:
        existing = sftp.open(env_path).read().decode("utf-8")
    except IOError:
        existing = ""
    values = {
        "ARK_API_KEY": ark_key,
        "ARK_MODEL": "doubao-seed-2-0-mini-260428",
        "AI_PROXY_TOKEN": proxy_token,
    }
    lines = [line for line in existing.splitlines() if not any(line.startswith(name + "=") for name in values)]
    lines.extend(f"{name}={value}" for name, value in values.items())
    staged = env_path + ".staged"
    with sftp.open(staged, "w") as out:
        out.write("\n".join(lines) + "\n")
    sftp.chmod(staged, 0o600)
    sftp.posix_rename(staged, env_path)
    sftp.close()

    print(run(f"cd {ROOT} && docker compose up -d --build api"))
    print(run(f"cd {ROOT} && docker compose ps"))
    try:
        print(run(f"curl --fail --max-time 10 http://127.0.0.1:18081/health"))
    except RuntimeError:
        print(run(f"cd {ROOT} && docker compose logs --tail=80 api"))
        raise
finally:
    client.close()
