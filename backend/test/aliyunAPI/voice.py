import os
import time
import requests
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

def upload_to_oss(file_path):
    """上传文件到阿里云 OSS 并返回签名 URL"""
    file_name = Path(file_path).name
    print(f"📤 正在上传到 OSS: {file_name}...")
    # 加上时间戳防止同名覆盖
    oss_obj_name = f"test_voice/{int(time.time())}_{file_name}"
    
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
    


TARGET_MODEL = "cosyvoice-v3.5-plus" 
VOICE_PREFIX = "myvoice" # 仅允许数字和小写字母，小于十个字符

def create_voice_task(text, voice_url):
    dashscope.api_key = API_KEY
    service = VoiceEnrollmentService()
    try:
        voice_id = service.create_voice(
            target_model=TARGET_MODEL,
            prefix=VOICE_PREFIX,
            url=voice_url,
        )
        print(f"Voice enrollment submitted successfully. Request ID: {service.get_last_request_id()}")
        print(f"Generated Voice ID: {voice_id}")
    except Exception as e:
        print(f"Error during voice creation: {e}")
        raise e
    max_attempts = 30
    poll_interval = 10 # 秒
    for attempt in range(max_attempts):
        try:
            voice_info = service.query_voice(voice_id=voice_id)
            status = voice_info.get("status")
            print(f"Attempt {attempt + 1}/{max_attempts}: Voice status is '{status}'")
            
            if status == "OK":
                print("Voice is ready for synthesis.")
                break
            elif status == "UNDEPLOYED":
                print(f"Voice processing failed with status: {status}. Please check audio quality or contact support.")
                raise RuntimeError(f"Voice processing failed with status: {status}")
            # 对于 "DEPLOYING" 等中间状态，继续等待
            time.sleep(poll_interval)
        except Exception as e:
            print(f"Error during status polling: {e}")
            time.sleep(poll_interval)
    else:
        print("Polling timed out. The voice is not ready after several attempts.")
        raise RuntimeError("Polling timed out. The voice is not ready after several attempts.")
    
    try:
        synthesizer = SpeechSynthesizer(model=TARGET_MODEL, voice=voice_id)
        text_to_synthesize = text
        
        # call()方法返回二进制音频数据
        audio_data = synthesizer.call(text_to_synthesize)
        print(f"Speech synthesis successful. Request ID: {synthesizer.get_last_request_id()}")

        # 保存音频文件
        output_file = "my_custom_voice_output.mp3"
        with open(output_file, "wb") as f:
            f.write(audio_data)
        print(f"Audio saved to {output_file}")

    except Exception as e:
        print(f"Error during speech synthesis: {e}")


def main():
    text = "恭喜，已成功复刻并合成了属于自己的声音！"
    VOICE_FILE_PATH = "test_voice.m4a"
    if not os.path.exists(VOICE_FILE_PATH) or not os.path.exists(VOICE_FILE_PATH):
        print("❌ 本地找不到 test_voice.m4a")
        return
    voice_url = upload_to_oss(VOICE_FILE_PATH)
    
    create_voice_task(text, voice_url)

if __name__ == "__main__":
    main()