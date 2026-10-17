import os
import time
import requests
import oss2
from dotenv import load_dotenv
from pathlib import Path

# 加载配置
load_dotenv()

# ========== 配置区域 ==========
API_KEY = os.getenv("DASHSCOPE_API_KEY")
# OSS 配置
OSS_AK = os.getenv("OSS_ACCESS_KEY_ID")
OSS_SK = os.getenv("OSS_ACCESS_KEY_SECRET")
OSS_ENDPOINT = os.getenv("OSS_ENDPOINT")
OSS_BUCKET = os.getenv("OSS_BUCKET_NAME")

# 本地文件路径
IMAGE_PATH = "test_video.jpeg"
VIDEO_PATH = "test_video.mp4"

# 测试参数
MODE = "wan-std"
MODEL_NAME = "wan2.2-animate-move" 

if not all([API_KEY, OSS_AK, OSS_SK, OSS_ENDPOINT, OSS_BUCKET]):
    raise ValueError("❌ 环境变量不完整，请检查 .env 文件")
# ============================

def upload_to_oss(file_path):
    """上传文件到阿里云 OSS 并返回签名 URL"""
    file_name = Path(file_path).name
    # 加上时间戳防止同名覆盖
    oss_obj_name = f"test_animate/{int(time.time())}_{file_name}"
    
    auth = oss2.Auth(OSS_AK, OSS_SK)
    bucket = oss2.Bucket(auth, OSS_ENDPOINT, OSS_BUCKET)
    
    print(f"📤 正在上传到 OSS: {file_name}...")
    try:
        # 上传文件
        bucket.put_object_from_file(oss_obj_name, file_path)
        
        # 生成签名 URL (有效期 2 小时)，确保 API 能抓取
        # 如果你的 Bucket 是公共读，可以直接拼接 URL，但签名 URL 更安全
        signed_url = bucket.sign_url('GET', oss_obj_name, 7200)
        print(f"✅ 上传成功，生成签名链接")
        return signed_url
    except Exception as e:
        raise RuntimeError(f"❌ OSS 上传失败: {e}")

def create_animate_task(image_url, video_url):
    """创建 Wan Animate Move 任务"""
    url = "https://dashscope.aliyuncs.com/api/v1/services/aigc/image2video/video-synthesis"
    headers = {
        "Authorization": f"Bearer {API_KEY}",
        "Content-Type": "application/json",
        "X-DashScope-Async": "enable"
    }
    payload = {
        "model": MODEL_NAME,
        "input": {
            "image_url": image_url,
            "video_url": video_url
        },
        "parameters": {
            "mode": MODE
        }
    }
    
    resp = requests.post(url, headers=headers, json=payload, timeout=30)
    if resp.status_code != 200:
        print(f"❌ API 报错详情: {resp.text}")
    resp.raise_for_status()
    
    task_id = resp.json().get("output", {}).get("task_id")
    print(f"🎬 任务创建成功，task_id: {task_id}")
    return task_id

def query_task_status(task_id, timeout=600, interval=15):
    """轮询任务状态"""
    url = f"https://dashscope.aliyuncs.com/api/v1/tasks/{task_id}"
    headers = {"Authorization": f"Bearer {API_KEY}"}
    
    start_time = time.time()
    while time.time() - start_time < timeout:
        try:
            resp = requests.get(url, headers=headers, timeout=30)
            resp.raise_for_status()
            data = resp.json()
            status = data.get("output", {}).get("task_status", "UNKNOWN")
            
            elapsed = time.time() - start_time
            print(f"⏳ [{elapsed:.1f}s] 状态: {status}")
            
            if status == "SUCCEEDED":
                return data["output"]["results"]["video_url"]
            elif status == "FAILED":
                print(f"❌ 任务失败: {data.get('output', {}).get('message', '未知错误')}")
                return None
            
            time.sleep(interval)
        except Exception as e:
            print(f"⚠️ 查询异常: {e}")
            time.sleep(interval)
    return None

def main():
    total_start = time.time()
    print(f"🚀 OSS 模式启动 ")
    print("=" * 60)
    
    # 1. 检查本地文件
    if not os.path.exists(IMAGE_PATH) or not os.path.exists(VIDEO_PATH):
        print("❌ 本地找不到 test.jpeg 或 test.mp4")
        return

    # 2. 上传 OSS
    try:
        img_url = upload_to_oss(IMAGE_PATH)
        vid_url = upload_to_oss(VIDEO_PATH)
    except Exception as e:
        print(e)
        return

    # 3. 创建任务
    try:
        task_id = create_animate_task(img_url, vid_url)
    except Exception as e:
        print(f"❌ 创建任务失败: {e}")
        return

    # 4. 等待结果
    result_url = query_task_status(task_id)
    
    if result_url:
        print(f"\n✨ 生成成功！结果地址:\n{result_url}")
        # 下载结果
        resp = requests.get(result_url)
        save_name = f"oss_output_{int(time.time())}.mp4"
        with open(save_name, "wb") as f:
            f.write(resp.content)
        print(f"💾 已保存至本地: {save_name}")
    
    print(f"\n📊 总耗时: {time.time() - total_start:.1f}秒")

if __name__ == "__main__":
    main()