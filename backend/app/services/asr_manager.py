import asyncio
from loguru import logger
from dashscope.audio.asr import Recognition
from app.services import asr_callback
from app.core.config import settings

class AsrManager:
    """ 
    ASR 服务管理器 
    将原始音频数据发送到 ASR 服务进行处理：介质 audio_queue
    识别结果通过 ASR 回调函数返回：介质 text_queue
    """
    def __init__(self, queue: asyncio.Queue, loop: asyncio.AbstractEventLoop):
        self.audio_queue = asyncio.Queue()  # 内部队列，用于缓冲待发送的原始音频流
        self.text_queue = queue             # 外部传入的队列，用于存放 ASR 识别结果
        self.loop = loop
        self.asr = None
        self._running = False
        self._worker_task = None

    async def _audio_sender_worker(self):
        """ 从内部音频队列取出数据并喂给 ASR 接口 """
        logger.info("ASR 音频处理 Worker 已启动")
        try:
            while self._running: 
                # 等待音频数据
                audio_data = await self.audio_queue.get()
                if audio_data is None:
                    break
                
                if self.asr:
                    try: # 尝试发送音频数据到 asr 服务
                        self.asr.send_audio_frame(audio_data)
                    except Exception as e:
                        logger.error(f"ASR 发送音频帧失败: {e}")
                # 等待音频帧处理完成
                self.audio_queue.task_done()
        except Exception as e:
            logger.error(f"ASR Worker 异常: {e}")
        finally:
            logger.debug("ASR 音频处理 Worker 已关闭")

    def start(self) -> None:
        """ 启动 ASR 服务及内部处理线程 """
        if self._running and self.asr:
            return 
        
        if self.asr:
            try: # 清理旧实例状态
                self.asr.stop()
            except:
                pass
            self.asr = None
        # ASR 回调实例化，最终将识别结果发送到 text_queue
        callback = asr_callback.AsrCallback(self.text_queue, self.loop)
        try:
            # 实例化 ASR 服务
            self.asr = Recognition(
                model=settings.asr.MODEL,
                format=settings.asr.FORMAT,
                sample_rate=settings.asr.SAMPLE_RATE,
                language_hint=settings.asr.LANGUAGE,
                semantic_punctuation_enabled=settings.asr.SEMANTIC_PUNCTUATION_ENABLED, # 开启VAD（语音活动检测）断句，关闭语义断句。
                max_sentence_silence=settings.asr.MAX_SENTENCE_SILENCE, # VAD 断句的静音时长阈值
                punctuation_prediction_enabled=settings.asr.PUNCTUATION_PREDICTION_ENABLED, # 自动添加标点
                disfluency_removal_enabled=settings.asr.DISFLUENCY_REMOVAL_ENABLED, # 过滤语气词
                heartbeat=settings.asr.HEARTBEAT,   # 保持长连接
                callback=callback
            )
            self.asr.start()
            self._running = True
            # 开启异步任务处理音频流
            if self._worker_task is None or self._worker_task.done():
                self._worker_task = asyncio.create_task(self._audio_sender_worker())
            logger.info("ASR 服务及 Worker 已成功启动")
        except Exception as e:
            self._running = False
            logger.error(f"ASR 启动失败: {e}")
            raise

    def send(self, audio: bytes) -> None:
        """ 快速将音频数据放入内部队列，不阻塞主循环 """
        if not self._running or self.asr is None:
            self.start()
        try: # 非阻塞放入队列
            self.audio_queue.put_nowait(audio)
        except Exception as e:
            logger.error(f"ASR 音频数据放入队列失败: {e}")

    async def stop(self) -> None:
        """ 安全关闭 ASR 服务 """
        self._running = False
        if self.asr:
            try:
                # 放入结束信号
                await self.audio_queue.put(None)
                if self._worker_task:
                    await self._worker_task
                
                self.asr.stop()
                logger.info("ASR 服务已安全停止")
            except Exception as e:
                logger.error(f"停止 ASR 时出错: {e}")
            finally:
                self.asr = None