"""Configure the connected test device without embedding credentials in the APK."""
import re
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path

def adb(*args, data=None):
    return subprocess.run(["adb", *args], input=data, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True).stdout

adb("shell", "am", "force-stop", "dy.lead.client")
raw = adb("shell", "run-as", "dy.lead.client", "cat", "shared_prefs/workspace.xml")
root = ET.fromstring(raw)
source = Path(r"C:\Users\xl__p\.codex\attachments\2fbddda3-2532-47f9-8dcc-7772419ef7cb\pasted-text.txt").read_text(encoding="utf-8")
key = re.search(r"ark-[A-Za-z0-9-]+", source).group(0)
config = {"ai_key": key, "ai_model": "doubao-seed-2-0-mini-260428", "keyword": "黄金回收", "limit": "1", "perVideo": "3", "sort": "最多点赞", "published": "不限", "mode": "按视频数量停止", "identity": "我是回收黄金的人", "target": "想出售黄金的人"}
for name, value in config.items():
    for node in root.findall(f"./*[@name='{name}']"):
        root.remove(node)
    ET.SubElement(root, "string", {"name": name}).text = value
payload = ET.tostring(root, encoding="utf-8", xml_declaration=True)
adb("shell", "run-as", "dy.lead.client", "sh", "-c", "'cat > shared_prefs/workspace.xml'", data=payload)
enabled = adb("shell", "settings", "get", "secure", "enabled_accessibility_services").decode().strip()
services = [x for x in enabled.split(":") if x and x != "null"]
own = "dy.lead.client/.AutomationService"
services = [x for x in services if not x.startswith("dy.lead.client/")]
adb("shell", "settings", "put", "secure", "enabled_accessibility_services", ":".join(services))
adb("shell", "am", "start", "-n", "dy.lead.client/.AutomationActivity")
services.append(own)
adb("shell", "settings", "put", "secure", "enabled_accessibility_services", ":".join(services))
adb("shell", "settings", "put", "secure", "accessibility_enabled", "1")
print("Configured test: 1 video, 3 comments, most likes; AI key present; preserved other accessibility services")
