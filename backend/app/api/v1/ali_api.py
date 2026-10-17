import os
import uuid
import asyncio
from loguru import logger
from app.core.config import settings
from app.database import relational_db
from fastapi import APIRouter, File, Form, HTTPException, UploadFile
from fastapi.concurrency import run_in_threadpool
from app.services import ali_service,oss_service
from app.database.sqlite_schema.request import \
     UploadRequest, CloneIDRequest, CloneStatusRequest, CloneDefaultVoiceRequest,\
       CloneCreateRequest, CloneListRequest, CloneUpdateRequest, CloneDeleteRequest

router = APIRouter()


@router.post("/get_upload_url")
async def get_oss_upload_url(request: UploadRequest):
    """ 获取直传 OSS 的预签名 URL """
    file_type = request.file_type
    file_name = request.file_name
    content_type = request.content_type
    try:
        logger.debug(f"生成上传链接: {file_type}, {file_name}, {content_type}")
        
        if file_type not in ["image", "audio"]:
            raise ValueError("file_type 必须是 image/audio")
        if not file_name:
            raise ValueError("file_name 不能为空")
        result = oss_service.generate_upload_presigned_url(
            file_type=file_type, 
            original_filename=file_name,
            content_type=content_type,
            expire_seconds=600
        )
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"生成上传链接失败: {str(e)}")


@router.post("/upload_file")
async def upload_file(
    file_type: str = Form(...),
    file: UploadFile = File(...),
    file_name: str | None = Form(None),
    content_type: str | None = Form(None),
):
    """Receive multipart uploads from App clients and forward them to OSS."""
    try:
        if file_type not in ["image", "audio"]:
            raise HTTPException(status_code=400, detail="file_type must be image/audio")

        original_filename = file_name or file.filename or f"{file_type}_{uuid.uuid4().hex}.bin"
        real_content_type = content_type or file.content_type or "application/octet-stream"
        data = await file.read()
        if not data:
            raise HTTPException(status_code=400, detail="empty file")

        result = await run_in_threadpool(
            oss_service.upload_file_bytes,
            file_type,
            original_filename,
            data,
            real_content_type,
        )
        return {"status": "SUCCESS", "data": result}
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Upload file failed: {e}")
        raise HTTPException(status_code=500, detail=f"upload failed: {str(e)}")


# default_voices = {
#     "woman":     "cosyvoice-v3.5-flash-uservoice-8d7f710c721841a4af74e11203a03a42",
#     "man":       "cosyvoice-v3.5-flash-uservoice-b501e8f077504c9e9ffb87c0604d0857",
#     "old_woman": "cosyvoice-v3.5-flash-uservoice-0367a656904649e6b7ffd08d472a0760",
#     "old_man":   "cosyvoice-v3.5-flash-uservoice-45ca70fb83d24b25aa20cc211045d5ac",
#     "child_girl":"cosyvoice-v3.5-flash-uservoice-73a12ed62eb346ee9da88ff07fb4ca12",
#     "child_boy": "cosyvoice-v3.5-flash-uservoice-b1bfa17c4ef2406299f84842e57c2cef",
# }

@router.post("/create_clone_task")
async def create_clone_task(request: CloneCreateRequest):
    """ 提交克隆任务 """
    user_id = request.user_id
    audio_url = request.audio_url
    audio_role = request.audio_role
    image_url = request.image_url

    think_url = f"{settings.resource.TAMPLATE_PATH}/thinking.mp4"
    speak_url = f"{settings.resource.TAMPLATE_PATH}/speaking.mp4"
    default_voices = settings.tts.DEFAULT_VOICES    # 默认音色
    clone_id = str(uuid.uuid4())                    # 生成本次克隆的总 ID
    try:
        audio_id = None
        tasks = [] # 根据上传是否上传音频选择是否扩充并发任务

        if not audio_url and audio_role: 
            # 用户选择默认音色模板
            if audio_role in default_voices:
                audio_id = default_voices[audio_role]
            else:
                raise HTTPException(status_code=400, detail="无效的角色类型")
        
        elif audio_url: 
            # 使用用户上传的音频，则创建音色
            audio_task = ali_service.create_voice(audio_url)
            tasks.append(audio_task)
        
        else:
            raise HTTPException(status_code=400, detail="必须提供音频 URL 或指定角色")
    
        # 创建视频任务生成
        think_task = ali_service.create_video(image_url, think_url)
        speak_task = ali_service.create_video(image_url, speak_url)
        tasks.extend([think_task, speak_task])
        results = await asyncio.gather(*tasks)

        if audio_id: # 使用预设角色的情况
            think_id, speak_id = results
        else:       # 创建了新音频任务的情况
            audio_id, think_id, speak_id = results

        # 存入数据库，全部关联到 clone_id
        relational_db.save_task(clone_id, user_id, "RUNNING") # 存主表
        relational_db.save_audio(user_id, audio_id, clone_id)
        relational_db.save_video(user_id, think_id,"think" , "", clone_id)
        relational_db.save_video(user_id, speak_id,"speak" , "", clone_id)
        return {"status": "SUCCESS", "data": {"clone_id": clone_id}}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"任务创建失败: {str(e)}")


# @router.post("/clone_default_vioce")
# async def clone_defuault_vioce(request: CloneDefaultVoiceRequest):
#     """ 提交克隆任务 """
#     audio_url = request.audio_url
#     try:
#        audio_id = await ali_service.create_voice(audio_url)
#        return {"status": "SUCCESS", "data": audio_id}
#     except Exception as e:
#         raise HTTPException(status_code=500, detail=f"创建音色失败: {str(e)}")
    


@router.post("/get_latest_clone_id")
async def get_latest_clone_id(request: CloneIDRequest):
    """ 获取最新一次克隆的 ID """
    user_id = request.user_id
    try:
        clone_id = relational_db.get_latest_clone_id(user_id)
        if clone_id:
            return {"status": "SUCCESS", "data": clone_id}
        else:
            return {"status": "NO_TASK", "data": "未找到相关任务记录"}
    except Exception as e:
        logger.error(f"获取最新一次克隆的 ID 失败: {str(e)}")
        raise HTTPException(status_code=500, detail="获取最新一次克隆的 ID 失败")


@router.post("/clone_status")
async def get_clone_status(request: CloneStatusRequest):
    """ 获取克隆状态，并下载视频文件暴露给客户端使用 """
    user_id = request.user_id
    task_id = request.task_id
    try:
        results = None
        task_result = relational_db.get_clone_id(user_id, task_id)
        if not task_result:
            return {"status": "NO_TASK", "data": "未找到相关任务记录"}
        voice_id = task_result.get("voice_task_id")
        think_id = task_result.get("think_video_id")
        speak_id = task_result.get("speak_video_id")

        base_path = settings.resource.VIDEO_PATH
        # 统一存储路径
        think_local = os.path.join(base_path,f"{user_id}/{task_id}/{user_id}_think.mp4")
        speak_local = os.path.join(base_path,f"{user_id}/{task_id}/{user_id}_speak.mp4")

        def is_locally_ready(u_id:str, t_id:str, v_type:str)-> bool:
            """判断是否真正克隆成功（生成后将数据写入数据库并下载到服务器）"""
            path = relational_db.get_video_path(u_id, v_type, t_id)
            logger.debug(f"{u_id} {t_id} {v_type} {path}")
            return path and os.path.exists(path)
        
        if is_locally_ready(user_id,task_id,"think") and is_locally_ready(user_id,task_id,"speak"):
            relational_db.save_task(task_id, user_id, "SUCCESS")
            return {
                "status": "SUCCESS",
                "data": {
                    "think_video_url": think_local,
                    "speak_video_url": speak_local,
                    }
                }
        
        # 查询音色和视频任务生成状态
        voice_task = run_in_threadpool(ali_service.query_voice, voice_id) if voice_id else None
        think_task = ali_service.query_video(think_id) if think_id else None
        speak_task = ali_service.query_video(speak_id) if speak_id else None
        results = await asyncio.gather(
            voice_task if voice_task else asyncio.sleep(0, result="FAILED"),
            think_task if think_task else asyncio.sleep(0, result={"status": "FAILED", "data": None}),
            speak_task if speak_task else asyncio.sleep(0, result={"status": "FAILED", "data": None}),
        )
        voice_res, think_res, speak_res = results
        v_status = voice_res
        t_status = think_res.get("status")
        s_status = speak_res.get("status")
        current_statuses = [v_status, s_status, t_status]
        
        async def download_and_update(remote_url: str, save_path: str, v_id: str, u_id:str, t_id: str, v_type:str) -> bool:
            if not remote_url: return False
            record = relational_db.get_video_path(user_id, v_type, task_id)
            if record and os.path.exists(save_path):
                return True
            logger.debug(f"下载文件: {remote_url}, {save_path}")
            try:
                logger.debug(f"开始下载视频: {remote_url}")
                await ali_service.download_file(remote_url, save_path)
                relational_db.save_video(
                    user_id=u_id, 
                    video_id=v_id,
                    video_type=v_type, 
                    video_path=save_path,
                    parent_task_id=t_id
                )
                return True
            except Exception as e:
                logger.error(f"下载或保存失败: {e}")
                return False

        if t_status == "SUCCESS":
            await download_and_update(think_res.get("data"), think_local, think_id, user_id, task_id,"think")
        if s_status == "SUCCESS":
            await download_and_update(speak_res.get("data"), speak_local, speak_id, user_id, task_id,"speak")

        # 最终状态校验
        success_set = {"SUCCESS", "UNKNOWN"}
        if all(s in success_set for s in current_statuses):
            if is_locally_ready(user_id,task_id,"think") and is_locally_ready(user_id,task_id,"speak"):
                relational_db.save_task(task_id, user_id, "SUCCESS")
                return {
                    "status": "SUCCESS",
                    "data": {
                        "think_video_url": think_local,
                        "speak_video_url": speak_local,
                    }
                }
            else:
                logger.debug(f"生成结果{results}")
                return {"status": "RUNNING", "data": "视频下载中"}
        if v_status == "FAILED" or s_status == "FAILED":
            logger.debug(f"生成结果{results}")
            relational_db.save_task(task_id, user_id, "FAILED")
            return {"status": "FAILED", "data": "生成视频失败"}
        return {"status": "RUNNING", "data": "生成视频中"}

    except Exception as e:
        logger.error(f"Status Error: {e}")
        raise HTTPException(status_code=500, detail="Internal Server Error")
    
@router.post("/list_clone_task")
async def list_clone_task(request: CloneListRequest):
    """ 获取克隆任务列表 """
    user_id = request.user_id
    try:
        tasks = relational_db.get_clone_list(user_id)
        return {"status": "SUCCESS", "data": {"tasks": tasks}}
    except Exception as e:
        logger.error(f"Get clone list failed: {str(e)}")
        raise HTTPException(status_code=500, detail="获取列表失败")

@router.post("/delete_clone_task")
async def delete_clone_task(request: CloneDeleteRequest):
    """ 删除克隆任务——删除声纹和视频资源和数据记录 """
    user_id = request.user_id
    task_id = request.task_id
    try:
        # 删除声纹
        audio_id = relational_db.get_audio_id(user_id, task_id)
        if audio_id:  
            try: # 删除声纹，但是声纹ID不是默认的角色声纹
                ali_service.delete_voice(audio_id)
            except Exception as e:
                logger.warning(f"云端声纹删除失败或不存在 (ID: {audio_id}): {str(e)}")

        # 删除视频
        think_path = relational_db.get_video_path(user_id,"think",task_id)
        if think_path: 
            try:
                await ali_service.delete_video(think_path)
            except Exception as e:
                logger.warning(f"云端Think视频删除失败: {str(e)}")
        speak_path = relational_db.get_video_path(user_id,"speak",task_id)
        if speak_path: 
            try:
                await ali_service.delete_video(speak_path)
            except Exception as e:
                logger.warning(f"云端Speak视频删除失败: {str(e)}")
                
        # 删除数据库记录
        result = relational_db.delete_clone_task(user_id, task_id)
        if result: return {"status": "SUCCESS", "data": "删除成功"}
        else:
            raise HTTPException(status_code=404, detail="未找到该任务，无需删除")
    except Exception as e:
        logger.error(f"删除克隆任务失败: {str(e)}")
        raise HTTPException(status_code=500, detail="删除失败")

@router.post("/update_clone_task")
async def update_clone_task(request: CloneUpdateRequest):
    """ 
    更新克隆任务（更新声纹和个性设定）
    例：:
    {
        "user_id": "123",
        "task_id": "456",
        "audio_url": "https://example.com/audio.mp3",
        "persona": {
            "name": "张三",
            "age": 18,
            "gender": "男",
            "relation":"同学",
            "personality": "乐观开朗",
            "background": "学生",
            "chat_style": "风趣幽默"
        }
    }
    """
    user_id = request.user_id
    task_id = request.task_id
    new_audio_url = request.audio_url
    persona = request.persona
    try:
        logger.debug(f"更新克隆任务: {request.json()}")
        if new_audio_url:
            # 创建声纹
            new_audio_id = await ali_service.create_voice(new_audio_url)
            # 删除旧声纹
            old_audio_id = relational_db.get_audio_id(user_id, task_id)
            logger.debug(f"旧声纹ID: {old_audio_id}")
            if old_audio_id: 
                try:
                    await ali_service.delete_voice(old_audio_id)
                except Exception:
                    logger.warning(f"正常状态，忽略删除失败: {old_audio_id}")
            relational_db.save_audio(user_id, new_audio_id, task_id)

        if persona:
            persona_dict = request.persona.model_dump()
            relational_db.save_persona(user_id, task_id, persona_dict)
        return {"status": "SUCCESS", "data": "更新成功"}
    except Exception as e:
        logger.error(f"更新克隆任务失败: {str(e)}")
        raise HTTPException(status_code=500, detail="更新失败")
    

