import os
import time
import oss2
from dotenv import load_dotenv
from pathlib import Path
import dashscope
from dashscope.audio.tts_v2 import VoiceEnrollmentService, SpeechSynthesizer
# 加载配置
load_dotenv()

# ========== 配置区域 ==========
API_KEY = os.getenv("DASHSCOPE_API_KEY")
# OSS 配置
OSS_AK = os.getenv("OSS_ACCESS_KEY_ID")
OSS_SK = os.getenv("OSS_ACCESS_KEY_SECRET")
OSS_ENDPOINT = os.getenv("OSS_ENDPOINT")
OSS_BUCKET = os.getenv("OSS_BUCKET_NAME")

VOICE_FILE_PATH = "test_voice.m4a"
TARGET_MODEL = "cosyvoice-v3.5-plus" 
VOICE_PREFIX = "myvoice"

def upload_to_oss(file_path, ak, sk, endpoint, bucket_name):
    """上传资源文件到阿里云 OSS"""
    file_name = Path(file_path).name
    # 添加时间戳防止同名覆盖
    oss_obj_name = f"test_voice/{int(time.time())}_{file_name}"
    # 创建 OSS 实例
    auth = oss2.Auth(ak, sk)
    bucket = oss2.Bucket(auth, endpoint, bucket_name)
    try:
        # 上传文件
        bucket.put_object_from_file(oss_obj_name, file_path)
        # 生成签名 URL 
        signed_url = bucket.sign_url('GET', oss_obj_name, 7200)
    except Exception as e:
        raise RuntimeError(f"OSS 上传失败: {e}")
    return signed_url

def clone_voice(api_key, model, prefix, voice_url):
    """"克隆声纹"""
    dashscope.api_key = api_key
    service = VoiceEnrollmentService()
    # 创建声纹
    voice_id = service.create_voice(
        target_model=model,
        prefix=prefix,
        url=voice_url,
    )

    # 轮询查询声纹状态
    for _ in range(30):

        voice_info = service.query_voice(voice_id=voice_id)
        status = voice_info.get("status")

        if status == "OK":
            return voice_id

        if status == "UNDEPLOYED":
            raise RuntimeError("voice clone failed")

        time.sleep(10)

    raise RuntimeError("voice clone timeout")


def synthesize_speech(api_key, model, voice_id, text):
    """合成语音"""
    dashscope.api_key = api_key
    # 创建合成器
    synthesizer = SpeechSynthesizer(
        model=model,
        voice=voice_id
    )
    # 调用合成
    audio = synthesizer.call(text)

    output_file = f"voice_output_{int(time.time())}.mp3"
    with open(output_file, "wb") as f:
        f.write(audio)
    print(f"音频文件已保存 {output_file}")
    return audio

def realtime_speech(api_key, model, voice_id, text):
    """实时语音"""
    pass



if __name__ == "__main__":
    voice_id = "cosyvoice-v3.5-plus-myvoice-7704df33978047248f062ab965465ceb"
    start_time = time.time()
    synthesize_speech(api_key=API_KEY, model=TARGET_MODEL, voice_id=voice_id, text="你好")
    end_time = time.time()
    print(f"合成完成，耗时: {end_time - start_time:.2f}s")