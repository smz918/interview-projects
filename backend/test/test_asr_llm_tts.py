import asyncio
import websockets
import json

WS_URL = "ws://127.0.0.1:8888/llm/voice"

async def run():
    async with websockets.connect(WS_URL) as ws:
        # 发送用户ID
        await ws.send(json.dumps({
            "user_id": "test_user"
        }))

        # 模拟发送音频流（用本地 wav 文件）
        with open("voice_output_1773643222.wav", "rb") as f:
            while chunk := f.read(3200):  # 20ms 音频
                await ws.send(chunk)
                await asyncio.sleep(0.02)

        # 接收返回
        while True:
            msg = await ws.recv()

            if isinstance(msg, bytes):
                print("🔊 收到音频:", len(msg))
                # 可以写入文件测试
                with open("out.pcm", "ab") as f:
                    f.write(msg)

            else:
                data = json.loads(msg)
                print("💬", data)

asyncio.run(run())