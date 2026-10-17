import sys
from loguru import logger
from app.core.config import settings

def logger_init():
    """配置全局日志"""
    # 移除 Loguru 默认的控制台输出
    logger.remove()
    
    if settings.log.LOG_STDOUT_ENABLED:
        # 配置控制台输出 (DEBUG 级别)
        logger.add(
            sys.stdout, 
            colorize=True, 
            format="<green>{time:YYYY-MM-DD HH:mm:ss}</green> | <level>{level: <8}</level> | <cyan>{name}</cyan>:<cyan>{function}</cyan>:<cyan>{line}</cyan> - <level>{message}</level>",
            level=settings.log.LOG_LEVEL
        )

    # 配置写入文件 (INFO 级别)
    logger.add(
        settings.log.LOG_PATH,
        rotation=settings.log.LOG_ROTATION,
        retention=settings.log.LOG_RETENTION,
        compression="zip",
        encoding="utf-8",
        enqueue=True,  # 异步写入，不阻塞 FastAPI 协程
        level="DEBUG"
    )
    return logger