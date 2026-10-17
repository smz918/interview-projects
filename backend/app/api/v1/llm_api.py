import json
import uuid
import asyncio
from loguru import logger
from app.utils.tools import queue_consumer
from app.services import asr_manager, tts_manager
from fastapi import APIRouter, WebSocket, WebSocketDisconnect

router = APIRouter()

@router.websocket("/voice")
async def voice_ws(ws: WebSocket):
    """对话入口"""
    await ws.accept()
    # 保存会话信息
    session_data = {
        "id":None,          # 会话ID
        "user_id":None,     # 用户ID
        "clone_id":None,    # 数字人ID
        "ws": ws,           # WebSocket 对象
        "ready": asyncio.Event(),   # 创建一个事件对象，用于同步会话状态
        "current_task":None,        # 当前任务对象
        "active": True              # 会话是否激活
    }
    queue = asyncio.Queue()         
    loop = asyncio.get_running_loop()
    # 初始化 ASR 管理器，识别结果通过 queue 传递给消费者任务
    asr_mgr = asr_manager.AsrManager(queue, loop) 
    # 初始化 TTS 管理器，合成结果通过 WebSocket 发送
    tts_mgr = tts_manager.TtsManager(ws, loop)
    # 启动 ASR 
    asr_mgr.start()
    # 消费者任务
    consumer_task = None
    try:
        while True:
            msg = await ws.receive()
            if "text" in msg: # 获取 JSON 命令
                data = json.loads(msg["text"])
                logger.debug(f"收到数据 {data}")
                msg_type = data.get("type")
                # 获取不同的指令情况
                if msg_type == "start":
                    # 告知前端 Session 已建立
                    session_data["user_id"] = data.get("user_id")
                    session_data["clone_id"] = data.get("clone_id")
                    session_data["id"] = str(uuid.uuid4())
                    session_data["ready"].set()
                    if consumer_task is None:# 消费者任务开启
                        consumer_task = asyncio.create_task(
                            queue_consumer(queue, session_data, tts_mgr)
                        )
                    await ws.send_json({
                        "type": "session_ready", 
                        "session_id": session_data["id"]
                    })
                    
                elif msg_type == "interrupt":
                    if session_data["current_task"] and not session_data["current_task"].done():
                        session_data["current_task"].cancel()
                    await tts_mgr.stop() 
                    await ws.send_json({"type": "stop_playback"})

            elif "bytes" in msg:
                # 未初始化则忽略音频
                if session_data["id"]:
                    asr_mgr.send(msg["bytes"])

    except WebSocketDisconnect:
        logger.info("前端 WebSocket 连接已断开")
    except RuntimeError as e:
        # WebSocket 已断开时仍尝试 receive/send 会抛 RuntimeError
        if "disconnect message" in str(e):
            logger.info("WebSocket 已断开（正常关闭）")
        else:
            logger.error(f"WebSocket RuntimeError: {e}")
    except Exception as e:
        logger.error(f"WebSocket 运行异常: {e}")
    finally:
        session_data["active"] = False
        session_data["ready"].set()
        await asr_mgr.stop()
        await tts_mgr.stop()
        if consumer_task:
            consumer_task.cancel()
        logger.info("会话资源已清理")

