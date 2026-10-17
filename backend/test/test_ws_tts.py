import asyncio
import websockets
import json
from pydub import AudioSegment
from pydub.playback import play
from pydub.utils import which
from io import BytesIO
AudioSegment.converter = r"D:\APP\ffmpeg-master-latest-win64-gpl-shared\bin\ffmpeg.exe"
AudioSegment.ffprobe = r"D:\APP\ffmpeg-master-latest-win64-gpl-shared\bin\ffprobe.exe"
WS_URL = "ws://127.0.0.1:8888/llm_service/chat/ws/chat"  # 替换为你的 FastAPI WebSocket 地址
VOICE_OUTPUT_FILE = "output.mp3"
VOICE_ID = "cosyvoice-v3.5-plus-myvoice-7704df33978047248f062ab965465ceb"

async def test_ws():
    async with websockets.connect(WS_URL) as ws:
        # 发送测试请求
        message = {
            "user_id": "1",
            "chat": "你好，请介绍一下你自己。",
            "personal": """    
            姓名：小明
            年龄：23
            关系：同学
            性别：男
            性格：情绪细腻、偶尔吐槽
            背景：安徽农业大学计算机专业
            兴趣：动漫、猫
            说话风格：温和、短句聊天"""
        }
        await ws.send(json.dumps(message))

        print("💬 发送消息成功，等待 LLM 流式响应...")
        buffer = BytesIO()

        while True:
            try:
                msg = await ws.recv()
                # 判断是音频还是文本
                if isinstance(msg, bytes):
                    # 收到音频片段，播放
                    buffer.write(msg)
                    buffer.seek(0)
                    audio_segment = AudioSegment.from_file(buffer, format="mp3")
                    play(audio_segment)
                    buffer = BytesIO()  # 清空缓存
                else:
                    data = json.loads(msg)
                    if data["type"] == "text":
                        print("LLM 文本:", data["data"])
                    elif data["type"] == "error":
                        print("错误:", data["data"])
                        break
            except websockets.ConnectionClosed:
                print("WebSocket 已关闭")
                break
            except Exception as e:
                print("处理异常:", e)
                break

asyncio.run(test_ws())