from dashscope.audio.asr import RecognitionCallback, RecognitionResult
from loguru import logger
import asyncio

class AsrCallback(RecognitionCallback):
    """ ASR回调类 """
    def __init__(self, queue: asyncio.Queue, loop: asyncio.AbstractEventLoop):
        self.queue = queue
        self.loop = loop
    
    def _push_data(self, data) -> None:
        """ 推送数据到队列 """
        self.loop.call_soon_threadsafe(
            self.queue.put_nowait,
            data
        )

    def on_open(self) -> None:
        """ 当和服务端建立连接完成后，该方法立刻被回调。"""
        logger.debug("ASR 会话已启动")
        self._push_data({"asr_event": "opened"})

    
    def on_close(self) -> None:
        """ 当服务已经关闭连接后进行回调。 """
        logger.debug("ASR 会话已关闭")
        self._push_data({"asr_event": "closed"})

    def on_complete(self) -> None:
        """ 当所有识别结果全部返回后进行回调。 """
        logger.info("ASR 识别完成")
        self._push_data({"asr_event": "completed"})

    def on_error(self, result: RecognitionResult) -> None:
        """ 发生异常时该方法被回调。 """
        logger.error(f"ASR 错误: {result}")
        # 解析错误码，区分可恢复/不可恢复错误
        if isinstance(result, dict):
            code = result.get("code", "")
            if code in ["NO_VALID_AUDIO_ERROR", "SessionExpired"]:
                self._push_data({"asr_event": "error_unrecoverable","code": code})

    def on_event(self, result: RecognitionResult) -> None:
        """ 当服务有回复时会被回调。 """
        sentence = result.get_sentence()
        if not sentence:
            return
        text = sentence.get("text", "")
        if text and RecognitionResult.is_sentence_end(sentence):
            logger.debug(f"asr 识别: {text}")
            self._push_data(text)
