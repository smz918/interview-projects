"""Build the existing no-login debug client with its server-side proxy credential.

The Ark API key remains on the server. Password and proxy credential are never printed.
"""
import getpass
import os
from pathlib import Path
import subprocess
import paramiko


def main():
    client = paramiko.SSHClient()
    client.load_system_host_keys()
    client.set_missing_host_key_policy(paramiko.RejectPolicy())
    client.connect("47.120.41.123", username="root",
                   password=getpass.getpass("Server password: "), timeout=15)
    try:
        with client.open_sftp() as sftp:
            with sftp.open("/opt/douyin_admin/.env") as stream:
                config = stream.read().decode("utf-8")
        token = next((line.split("=", 1)[1].strip().strip("\"'")
                      for line in config.splitlines()
                      if line.startswith("AI_PROXY_TOKEN=")), "")
        if not token:
            raise RuntimeError("Server AI proxy credential is missing; refusing disconnected build")
    finally:
        client.close()
    env = dict(os.environ, AI_PROXY_TOKEN=token)
    gradle = Path(os.environ["USERPROFILE"]) / ".gradle/wrapper/dists/gradle-9.3.1-bin/23ovyewtku6u96viwx3xl3oks/gradle-9.3.1/bin/gradle.bat"
    print("Server AI proxy credential loaded; building without printing secrets.", flush=True)
    raise SystemExit(subprocess.call([str(gradle), "--console=plain", "-q", "-I",
                                     "presentation-tests.init.gradle", ":app:assembleDebug"],
                                    cwd=Path(__file__).resolve().parent, env=env))


if __name__ == "__main__":
    main()
