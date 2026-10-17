from dashscope.audio.tts_v2 import VoiceEnrollmentService
from loguru import logger
from app.core.config import settings
import dashscope
def delete_voice() -> None:
    service = VoiceEnrollmentService()
    dashscope.api_key = settings.ali.ACCESS_KEY_SECRET
    # 按前缀筛选，或设为None查询所有
    voices = service.list_voices(prefix='uservoice', page_index=0, page_size=10)

    ids = [voice['voice_id'] for voice in voices]
    # for vid in ids:
    #     if vid == 'cosyvoice-v3.5-flash-uservoice-6ee3327cc8e94f0a921ef940d65e4622':
    #         continue
    #     service.delete_voice(vid)
    print(ids)

    print(f"Request ID: {service.get_last_request_id()}")
    # print(f"Found voices: {voices}")

# def delete_voice(voice_id: str) -> None:
#     service = VoiceEnrollmentService()
#     dashscope.api_key = settings.ali.ACCESS_KEY_SECRET
#     service.delete_voice(voice_id=voice_id)
#     print(f"Request ID: {service.get_last_request_id()}")

# delete_voice("cosyvoice-v3.5-flash-uservoice-df1137c1e1cf43e1a6c1af4dc1dda414")
delete_voice()

# def query_voice(voice_id: str) -> dict:
#     """ 查询声纹克隆状态  """
#     logger.debug(f"声纹状态查看: {voice_id}")
#     service = VoiceEnrollmentService()
#     # 轮询查询声纹状态
#     voice_info = service.query_voice(voice_id=voice_id)
#     status = voice_info.get("status")
#     logger.debug(f"声纹状态: {voice_info}")
#     if status == "OK":
#         return "SUCCESS"
#     elif status == "UNDEPLOYED":
#         return "FAILED"
#     return "RUNNING"
