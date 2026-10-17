import asyncio
import datetime
from loguru import logger
from app.core.config import settings
from app.services import tts_manager, llm_service
from app.database import relational_db, vector_db
from app.utils.time_logger import time_logger

async def queue_consumer(
    queue: asyncio.Queue, 
    session_data: dict, 
    tts_mgr: tts_manager.TtsManager
):
    """ 消费者 —— 将asr识别的结果转入到llm后又向tts推送 """
    await session_data["ready"].wait() 
    try:
        while session_data["active"]:
            # 获取 ASR 识别出的数据
            # logger.debug(f"Session {session_data}: 获取 ASR 识别结果")  
            data = await queue.get()
            try:
                # 基础过滤逻辑
                text = filter_text(data)
                if not text:
                    continue
                # 确保 user_id 已绑定
                if not session_data["user_id"]:
                    logger.debug(f"等待 user_id {session_data["user_id"]}绑定...")
                    await session_data["ready"].wait()
                # 如果当前有用户正在说话的任务，直接取消它
                if session_data["current_task"] and not session_data["current_task"].done():
                    logger.info("检测到新输入，取消当前播放任务")
                    session_data["current_task"].cancel()
                    try:
                        await session_data["current_task"]
                    except asyncio.CancelledError:
                        pass
                    # 通知前端：立刻掐断音频播放
                    await tts_mgr.stop()
                    await session_data["ws"].send_json({"type": "stop_playback"})
                # 开启新的对话任务链
                if session_data["active"]:
                    session_data["current_task"] = asyncio.create_task(
                        handle_llm_tts(text, session_data, tts_mgr)
                    )
            except Exception as e:
                logger.error(f"处理 ASR 队列数据异常: {e}")
            finally:
                queue.task_done()
    except asyncio.CancelledError:
        logger.info("消费者任务被取消")


def filter_text(data):
    """ 过滤无效字符 """
    if isinstance(data, dict) and "asr_event" in data:
        return ""
    text = str(data).strip()
    if not text or all(c in "!?.,;:() ”“\"'#*-_" for c in text):
        return ""
    return text

# DEFAULT_PROFILE = (
#     "- 姓名：AI家人\n"
#     "- 年龄：25\n"
#     "- 性别：女\n"
#     "- 关系：AI家人\n"
#     "- 性格：温柔、耐心、友善\n"
#     "- 背景：一名专业的AI数字家人\n"
#     "- 说话风格：亲切自然，简洁明了"
# )

@time_logger
async def get_user_info(user_id: str,clone_id: str) -> dict:
    """ 获取用户的数字人信息 """
    voice_id = relational_db.get_audio_id(user_id, clone_id)
    persona = relational_db.get_persona(user_id, clone_id)
    if voice_id and persona:
        profile = (
            f"- 姓名：{persona.name}\n"
            f"- 年龄：{persona.age}\n"
            f"- 性别：{persona.gender}\n"
            f"- 关系：{persona.relation}\n"
            f"- 性格：{persona.personality}\n"
            f"- 背景：{persona.background}\n"
            f"- 说话风格：{persona.chat_style}"
        )
        return {"voice_id": voice_id,"profile": profile}
    logger.warning(f"用户 {user_id} / clone {clone_id} / 语音 ID: {voice_id} 未找到人设配置，使用默认人设")
    return {"voice_id": voice_id, "profile": settings.prompt.DEFAULT_PROFILE}

async def handle_llm_tts(text: str, session_data: dict, tts_mgr: tts_manager.TtsManager):
    """ 处理 LLM 输出并推送给前端 """
    current_task = asyncio.current_task()
    user_id = session_data["user_id"]
    clone_id = session_data["clone_id"]

    # # logger.debug(f"user_id: {user_id} - clone_id: {clone_id} - 向量化并转语音中...")
    # user_info = await get_user_info(user_id, clone_id)
    # # 获取对话向量
    # embedding = await llm_service.chat_embedding(text)

    user_info, embedding = await asyncio.gather(
        get_user_info(user_id, clone_id),
        llm_service.chat_embedding(text),
    )
    # 获取上下文记忆
    context_memory = vector_db.query_combined_memory(user_id, clone_id, embedding)
    # 同时注入系统提示词
    now = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    persona_content = f"{user_info['profile']}\n[当前时间]：{now}"
    system_prompt = settings.prompt.SYSTEM_PROMPT.replace(
        "[自我画像]",persona_content
    )
    # logger.debug(f"system_prompt: {system_prompt}")
    # logger.debug(f"context_memory: {context_memory}")
    
    # 记录完整回复    
    full_response = ""
    # LLM 流式输出
    token_stream = llm_service.llm_stream(system_prompt, text, context_memory)
    sentence_stream = llm_service.sentence_buffer(token_stream)
    try:
        async for sentence in sentence_stream:
            if not session_data["active"]:
                return
            if current_task.cancelled() or not session_data["active"]:
                logger.debug("handle_llm_tts 检测到取消信号，终止输出")
                return
            tts_mgr.send(user_info["voice_id"], sentence)
            full_response += sentence

        tts_mgr.finalize()
        if full_response:
            logger.debug(f"LLM 输出: {full_response}")
            # 保存这段对话为短期记忆
            vector_db.save_short_memory(user_id, clone_id, text, full_response, embedding)
            # 检查是否需要更新长期记忆
            asyncio.create_task(memory_update(user_id, clone_id))
    except Exception as e:
        logger.error(f"处理 LLM/TTS 时发生错误: {e}")
        # await tts_mgr.stop()

async def memory_update(user_id: str, clone_id: str) -> None:
    """ 检查是否需要更新长期记忆 """
    # 获取当前用户的最新一次提炼的长期记忆
    latest_long = vector_db.get_latest_long_memory(user_id, clone_id)
    # 获取当前用户的短期记忆数量
    count = vector_db.get_count(user_id, clone_id, "short")
    if count >= settings.prompt.SUMARY_COUNT:
        # 获取短期记忆
        ids, docs = vector_db.get_short_memories(user_id, clone_id, settings.prompt.SUMARY_COUNT)
        # 提炼长期记忆
        summary = await llm_service.refine_persona(latest_long, docs)

        logger.info(f"summary:{summary}")
        embedding = await llm_service.chat_embedding(summary)
        vector_db.save_long_memory(user_id, clone_id, summary, embedding)
        vector_db.delete_short_memories(ids)
