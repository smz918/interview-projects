from dashscope.audio.tts_v2 import ResultCallback
from loguru import logger
import asyncio

class TtsCallback(ResultCallback):
    """ TTS回调类 """
    def __init__(self, queue: asyncio.Queue, loop: asyncio.AbstractEventLoop):
        self.queue = queue  # 用于存储数据
        self.loop = loop    # 获取当前运行循环

    def _push_data(self, data) -> None:
        """ 推数据到队列 """
        self.loop.call_soon_threadsafe(
            self.queue.put_nowait, data
        )

    def on_open(self) -> None:
        """ 当和服务端建立连接完成后，该方法立刻被回调。 """
        logger.debug("TTS 会话已启动")
        self._push_data({"tts_event": "opened"})

    def on_close(self) -> None:
        """ 当服务已经关闭连接后被回调。 """
        logger.debug("TTS 会话已关闭")
        self._push_data({"tts_event": "closed"})

    def on_complete(self) -> None:
        """ 当所有合成数据全部返回（语音合成完成）后被回调。"""
        logger.debug("TTS 语音合成完成")
        self._push_data({"tts_event": "completed"})

    def on_error(self, message) -> None:
        """ 发生异常时该方法被回调。 """
        # logger.error(f"TTS 发生异常: {message}")
        if isinstance(message, dict):
            message = message.get("header")
            logger.error(message.get("error_message"))

    def on_event( self, message: str) -> None:
        """ 
        当服务有回复时会被回调。 
        message为JSON字符串，解析可获取Task ID（task_id参数）、
        本次请求中计费的有效字符数（characters参数）等信息。
        """
        pass
        # logger.debug(f"TTS 回复: {message}")

    def on_data(self, data: bytes) -> None:
        """ 当服务器有合成音频返回时被回调。 """
        """ 当服务器有音频返回时，直接推送到 WebSocket """
        if data:
            self.loop.call_soon_threadsafe(self.queue.put_nowait, data)

