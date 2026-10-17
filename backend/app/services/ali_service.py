import os
import uuid
import httpx
import dashscope
from loguru import logger
from app.core.config import settings
from dashscope.audio.tts_v2 import VoiceEnrollmentService

# 加载配置
dashscope.api_key = settings.ali.ACCESS_KEY_SECRET

async def create_video(image_url: str, video_url: str) -> str:
    """ 使用 httpx 异步发送请求，创建视频合成任务 """
    url = "https://dashscope.aliyuncs.com/api/v1/services/aigc/image2video/video-synthesis"
    headers = {
        "Authorization": f"Bearer {dashscope.api_key}",
        "Content-Type": "application/json",
        "X-DashScope-Async": "enable"
    }
    payload = {
        "model": settings.vg.MODEL_NAME,
        "input": {
            "image_url": image_url,
            "video_url": video_url
        },
        "parameters": {
            "mode": settings.vg.MODE,
        }
    }
    async with httpx.AsyncClient() as client:
        resp = await client.post(url, headers=headers, json=payload, timeout=30.0)
        resp.raise_for_status()
        return resp.json()["output"]["task_id"]


async def query_video(task_id: str) -> dict:
    """ 查询视频合成状态 """
    url = f"https://dashscope.aliyuncs.com/api/v1/tasks/{task_id}"
    headers = {"Authorization": f"Bearer {dashscope.api_key}"}

    async with httpx.AsyncClient() as client:
        resp = await client.get(url, headers=headers)
        resp.raise_for_status()
        data = resp.json()["output"]
        status = data["task_status"]
        
        if status == "SUCCEEDED":
            return {
                "status": "SUCCESS", 
                "data": data["results"]["video_url"]
            }
        elif status == "UNKNOWN": # 说明已经完成生成并且链接过期
            return {"status": "UNKNOWN","data": None}
        elif status == "FAILED":
            logger.error(f"视频合成状态: {data}")
            return {"status": "FAILED","data": None}
        logger.debug(f"视频合成状态: {data}")
        return {"status": "RUNNING", "data": None}

async def download_file(url: str, dest_path: str):
    """支持原子性写入的异步下载"""
    # 确保目标目录存在
    os.makedirs(os.path.dirname(dest_path), exist_ok=True)
    
    # 定义临时路径（建议加随机后缀防止并发冲突）
    temp_path = f"{dest_path}.{uuid.uuid4().hex}.tmp"
    
    try:
        async with httpx.AsyncClient(timeout=60.0) as client:
            async with client.stream("GET", url) as response:
                response.raise_for_status()
                # 写入临时文件
                with open(temp_path, "wb") as f:
                    async for chunk in response.aiter_bytes():
                        f.write(chunk)
        
        # 只有下载成功完成，才执行替换操作
        os.replace(temp_path, dest_path)
        return dest_path

    except Exception as e:
        # 发生异常时，清理掉不完整的临时文件
        if os.path.exists(temp_path):
            os.remove(temp_path)
        logger.error(f"文件下载失败: {e}")
        raise e 

def delete_video(video_path: str) -> None:
    """ 删除视频
    video_path 示例: resources/video/user_001/task_id/user_001_video.mp4
    """
    try:
        if not video_path:
            logger.warning("删除视频失败：文件路径为空")
            return
        # 获取绝对路径，防止路径注入安全风险
        abs_path = os.path.abspath(video_path)
        # 检查文件是否存在
        if os.path.exists(abs_path):
            # 获取文件夹路径 (task_id 这一层)
            task_dir = os.path.dirname(abs_path)
            # 删除文件
            os.remove(abs_path)
            logger.info(f"已删除视频文件: {abs_path}")
            # 如果该目录下没有其他文件了，把任务文件夹也删掉
            if os.path.exists(task_dir) and not os.listdir(task_dir):
                os.rmdir(task_dir)
                logger.info(f"已清理空目录: {task_dir}")
        else:
            logger.warning(f"视频文件不存在，无需删除: {abs_path}")
    except Exception as e:
        logger.error(f"物理删除文件失败: {str(e)}")


async def create_voice(voice_url: str) -> str:
    """" 克隆声纹 """
    service = VoiceEnrollmentService()
    # 创建声纹
    voice_id = service.create_voice(
        target_model=settings.tts.MODEL,
        prefix=settings.tts.VOICE_PREFIX,
        url=voice_url,
    )
    return voice_id

def query_voice(voice_id: str) -> dict:
    """ 查询声纹克隆状态  """
    service = VoiceEnrollmentService()
    # 轮询查询声纹状态
    voice_info = service.query_voice(voice_id=voice_id)
    status = voice_info.get("status")
    logger.debug(f"声纹状态: {voice_info}")
    if status == "OK":
        return "SUCCESS"
    elif status == "UNDEPLOYED":
        return "FAILED"
   
    return "RUNNING"

def delete_voice(voice_id: str) -> None:
    """ 删除声纹 """
    default_voices = settings.tts.DEFAULT_VOICES
    if voice_id in default_voices.values():  # 默认声纹不能删除
        logger.warning(f"删除声纹失败：默认声纹不能删除: {voice_id}")
        return
    service = VoiceEnrollmentService()
    service.delete_voice(voice_id=voice_id)
