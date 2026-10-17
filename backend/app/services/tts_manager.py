import asyncio
from fastapi import WebSocket
from dashscope.audio.tts_v2 import AudioFormat, SpeechSynthesizer
from app.services import tts_callback
from app.core.config import settings
from loguru import logger

class TtsManager:
    def __init__(self, ws:WebSocket, loop: asyncio.AbstractEventLoop):
        self.queue = asyncio.Queue()
        self.loop = loop
        self.ws = ws
        self.tts = None
        self._running = False
        self._worker_task = None

    async def _audio_sender_worker(self) -> None:
        """ 将队列中的音频数据通过 websocket 发送给客户端 """
        logger.info("音频发送 Worker 已启动")
        try:
            logger.debug("TTS 开始音频发送任务")
            while self._running:
                data = await self.queue.get()
                if data is None: 
                    logger.debug("收到结束信号，退出发送任务")
                    break
                if isinstance(data, bytes):
                    # logger.debug(f"TTS 发送音频帧: {len(data)}")
                    await self.ws.send_bytes(data)
                if isinstance(data, dict):
                    logger.debug(f"TTS 事件: {data}")
        except Exception as e:
            logger.error(f"音频发送异常: {e} | 数据为: {data}")
        finally:
            logger.debug("音频发送 Worker 已关闭")
            self.queue.task_done() 

    # def _clear_queue(self) -> None:
    #     """ 清空队列中的数据 """    
    #     while not self.queue.empty():
    #         try:
    #             self.queue.get_nowait()
    #             self.queue.task_done()
    #         except asyncio.QueueEmpty:
    #             break
            
    def start(self, voice_id: str) -> None:
        """ 创建 TTS 服务接口：文字转语音，由接口接收发送 """
        if self._running and self.tts:
            return
        if self.tts:
            try: # 清除旧的实例
                self.tts.streaming_complete()
            except:
                pass
            self.tts = None
        # 创建 TTS 回调实例
        callback = tts_callback.TtsCallback(self.queue, self.loop)
        try: # 创建 TTS 实例
            self.tts = SpeechSynthesizer(
                model=settings.tts.MODEL,               # 模型
                voice=voice_id,                         # 声纹 ID
                callback=callback,                      # 回调实例
                speech_rate = settings.tts.SPEECH_RATE, # 语速
                format=AudioFormat.PCM_16000HZ_MONO_16BIT,  # 音频格式和采样率
            )
            
            self._running = True
            if self._worker_task is None or self._worker_task.done():
                self._worker_task = asyncio.create_task(self._audio_sender_worker())
            logger.debug("TTS 管理器已就绪")
        except Exception as e:
            self._running = False
            logger.error(f"TTS 启动失败: {e}")
            raise

    def send(self,voice_id: str, text: str) -> None:
        """ 发送 LLM 输出的文本作为 TTS 输入 """
        if not text.strip(): return
        if not self._running or self.tts is None:
            self.start(voice_id)
        try:
            self.tts.streaming_call(text)
        except Exception as e:
            # logger.warning(f"TTS 发送音频帧时出错: {e}")
            self._running = False
            try:
                self.start(voice_id)
                self.tts.streaming_call(text)
            except Exception as e:
                logger.error(f"停止 TTS 时出错: {e}")

    async def stop(self) -> None:
        """ 停止 TTS 服务 """
        if self.tts:
            try:
                # self.tts.streaming_complete()
                self.tts.streaming_cancel()
                self._running = False
                await self.queue.put(None)
                if self._worker_task:
                    await self._worker_task
                logger.info("TTS 服务已安全停止")
            except Exception as e:
                logger.error(f"停止 TTS 时出错: {e}")
            finally:
                self.tts = None
                self._worker_task = None

    def finalize(self) -> None:
        """ 提交本轮合成任务，强制 SDK 输出所有音频 """
        if self.tts and self._running:
            try:
                logger.debug("发送 TTS 结束信号 (streaming_complete)")
                self.tts.streaming_complete() 
            except Exception as e:
                logger.error(f"TTS finalize 失败: {e}")