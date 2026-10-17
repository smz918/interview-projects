import os
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.api.v1 import ali_api
from app.api.v1 import llm_api
from app.core.logger import logger_init
from app.core.config import settings
from fastapi.staticfiles import StaticFiles

# 初始化 Fast API
app = FastAPI(title=settings.service.APP_NAME)
# 日志初始化
logger_init()
# # 跨域
# app.add_middleware(
#     CORSMiddleware,
#     allow_origins=["*"],  # 测试阶段用 "*"
#     allow_credentials=True,
#     allow_methods=["*"],
#     allow_headers=["*"],
# )
# 路由定义
app.include_router(llm_api.router,prefix="/llm",tags=["llm_service"])
app.include_router(ali_api.router,prefix="/ali",tags=["ali_service"])
# 静态文件
# app.mount("/resources", StaticFiles(directory="resources"), name="resources")
@app.on_event("startup")
async def create_resource_dirs():
    """确保资源目录存在"""
    for path in [settings.resource.AUDIO_PATH, 
                 settings.resource.VIDEO_PATH, 
                 settings.resource.IMAGE_PATH]:
        os.makedirs(path, exist_ok=True)  # exist_ok=True 表示存在则不报错