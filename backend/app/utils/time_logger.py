import inspect
import time
import asyncio
import functools
from loguru import logger

def time_logger(func):
    """
    通用耗时监控装饰器：自动识别同步/异步函数
    """
    @functools.wraps(func)
    async def async_wrapper(*args, **kwargs):
        start_time = time.perf_counter()
        try:
            return await func(*args, **kwargs)
        finally:
            end_time = time.perf_counter()
            duration = (end_time - start_time) * 1000
            logger.debug(f"[{func.__name__}] 异步耗时: {duration:.2f}ms")

    @functools.wraps(func)
    def sync_wrapper(*args, **kwargs):
        start_time = time.perf_counter()
        try:
            return func(*args, **kwargs)
        finally:
            end_time = time.perf_counter()
            duration = (end_time - start_time) * 1000
            logger.debug(f"[{func.__name__}] 同步耗时: {duration:.2f}ms")

    # 根据原函数是否是异步函数，返回对应的包装器
    if inspect.iscoroutinefunction(func):
        return async_wrapper
    return sync_wrapper