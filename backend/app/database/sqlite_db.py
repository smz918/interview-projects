import os
import datetime
from loguru import logger
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from app.core.config import settings
from app.database.sqlite_models.models import Base, ClonedPersona, ClonedVideos, ClonedAudios, ClonedTask

class RelationalDB:

    def __init__(self, path: str):
        # 数据库 URL
        database_url = f"sqlite:///{os.path.abspath(path)}"
        # 创建数据库引擎
        self.engine = create_engine(
            database_url, 
            connect_args={"check_same_thread": False}
        )
        # 确保表已创建
        Base.metadata.create_all(bind=self.engine)
        # 创建会话工厂
        self.SessionLocal = sessionmaker(
            autocommit=False, 
            autoflush=False, 
            bind=self.engine
        )
    
    def save_task(self, task_id: str, user_id: str, status: str) -> None:
        """ 保存或更新用户克隆任务（音色和视频关系使用了逻辑关联） """
        logger.debug(f"保存用户克隆任务：{user_id} - {task_id} - {status}")
        with self.SessionLocal() as session:
            try:
                record = session.query(ClonedTask).filter(
                    ClonedTask.task_id == task_id
                ).first()
                if record:
                    record.status = status
                else:
                    new_task = ClonedTask(
                        task_id=task_id,
                        user_id=user_id,
                        status=status
                    )
                    session.add(new_task)
                session.commit()
            except Exception as e:
                session.rollback()
                raise e
    
    def save_video(
            self, 
            user_id: str, 
            video_id:str,  
            video_type: str,
            video_path: str, 
            parent_task_id: str
        ) -> None:
        """ 保存或更新生成的视频文件与用户关系 """
        logger.debug(f"保存用户视频文件关系：{user_id} - {video_id} - {video_path}")
        with self.SessionLocal() as session:
            try:
                record = session.query(ClonedVideos).filter(
                    ClonedVideos.user_id == user_id,
                    ClonedVideos.video_type == video_type,
                    ClonedVideos.parent_task_id == parent_task_id
                ).first()
                if record:  # 如果已存在，则更新
                    record.video_id = video_id
                    record.video_path = video_path
                    record.created_at = datetime.datetime.now()
                else:  # 否则插入
                    new_video = ClonedVideos(
                        user_id=user_id, 
                        video_id=video_id,
                        video_type=video_type, 
                        video_path=video_path,
                        parent_task_id=parent_task_id
                    )
                    session.add(new_video)
                session.commit()
            except Exception as e:
                session.rollback()
                raise e

    def save_audio(self, user_id: str, audio_id: str ,parent_task_id: str) -> None:
        """ 保存或更新生成音频ID与用户关系，如已存在则更新，不存在则插入 """
        with self.SessionLocal() as session:
            try:
                record = session.query(ClonedAudios).filter(
                    ClonedAudios.user_id == user_id,
                    ClonedAudios.parent_task_id == parent_task_id
                ).first()
                if record:
                    record.audio_id = audio_id
                    record.updated_at = datetime.datetime.now()
                else:
                    new_record = ClonedAudios(
                        user_id=user_id, 
                        audio_id=audio_id,
                        parent_task_id=parent_task_id
                    )
                    session.add(new_record)
                session.commit()
            except Exception as e:
                session.rollback()
                raise e

    def save_persona(self, user_id:str, task_id: str, persona: dict) -> None:
        """ 
        保存或更新用户数字人画像信息 
        persona = {
            "name": "张三",
            "age": 25,
            "gender": "男",
            "relation": "朋友",
            "personality": "开朗乐观",
            "background": "程序员",
            "chat_style": "幽默风趣"
        }
        """
        with self.SessionLocal() as session:
            try: 
                record = session.query(ClonedPersona).filter(
                    ClonedPersona.user_id == user_id,
                    ClonedPersona.parent_task_id == task_id
                ).first()
                if record:
                    record.name = persona.get("name")
                    record.age = persona.get("age")
                    record.gender = persona.get("gender")
                    record.relation = persona.get("relation")
                    record.personality = persona.get("personality")
                    record.background = persona.get("background")
                    record.chat_style = persona.get("chat_style")
                else:
                    new_record = ClonedPersona(
                        user_id=user_id,
                        parent_task_id=task_id,
                        name=persona.get("name"),
                        age=persona.get("age"),
                        gender=persona.get("gender"),
                        relation=persona.get("relation"),
                        personality=persona.get("personality"),
                        background=persona.get("background"),
                        chat_style=persona.get("chat_style")
                    )
                    session.add(new_record)
                session.commit()
            except Exception as e:
                session.rollback()
                raise e

    def get_latest_clone_id(self, user_id: str) -> str:
        """ 通过用户ID查询最新的克隆任务ID """
        with self.SessionLocal() as session:
            record = session.query(ClonedTask).filter(
                ClonedTask.user_id == user_id
            ).order_by(ClonedTask.created_at.desc()).first()
            return record.task_id if record else ""     

    
    def get_clone_id(self, user_id: str, task_id: str) -> dict:
        """ 通过用户ID和任务ID查询完整的任务上下文 """
        with self.SessionLocal() as session:
            task = session.query(ClonedTask).filter(
                ClonedTask.user_id == user_id,
                ClonedTask.task_id == task_id
            ).first()

            if not task:
                return {
                    "user_id": user_id,
                    "task_id": task_id,
                    "status": None,
                    "voice_task_id": "", 
                    "think_video_id": "", 
                    "speak_video_id": "",
                    "think_url": "",
                    "speak_url": ""
                }

            # 获取最新的音频 ID
            audio_id = task.audios[-1].audio_id if task.audios else ""
            # 分别获取 think 和 speak 类型的最新视频 ID 和 路径
            # 这里的路径 (video_path) 是数据库里存的本地相对路径
            think_v = [v for v in task.videos if v.video_type == 'think']
            speak_v = [v for v in task.videos if v.video_type == 'speak']
            latest_think = think_v[-1] if think_v else None
            latest_speak = speak_v[-1] if speak_v else None
            return {
                "user_id": user_id,
                "task_id": task_id,
                "status": task.status,  # 必须返回当前数据库中的状态
                "voice_task_id": audio_id,
                "think_video_id": latest_think.video_id if latest_think else "",
                "speak_video_id": latest_speak.video_id if latest_speak else "",
                "think_url": latest_think.video_path if latest_think else "",
                "speak_url": latest_speak.video_path if latest_speak else "",
                "persona": task.persona.to_dict() if task.persona else {}
            }

    def get_clone_list(self, user_id: str) -> list:
        """ 获取用户所有克隆任务列表 """
        with self.SessionLocal() as session:
            records = session.query(ClonedTask).filter(
                ClonedTask.user_id == user_id
            ).order_by(ClonedTask.created_at.desc()).all()
            results = []
            for record in records:
                # 获取视频合成结果
                think_url = ""
                speak_url = ""
                if record.videos:
                    # 提取思考视频（取最新的一条）
                    tv = [v.video_path for v in record.videos if v.video_type == 'think']
                    think_url = tv[-1] if tv else ""
                    # 提取说话视频（取最新的一条）
                    sv = [v.video_path for v in record.videos if v.video_type == 'speak']
                    speak_url = sv[-1] if sv else ""
                
                # 获取人设名称
                persona_name = record.persona.name if record.persona else "未命名"
                results.append({
                    "task_id": record.task_id,
                    "status": record.status, # (SUCCESS/RUNNING/FAILED)
                    "think_url": think_url,
                    "speak_url": speak_url,
                    "persona_name": persona_name,
                    "created_at": record.created_at.strftime("%Y-%m-%d %H:%M:%S") if record.created_at else ""
                })
            return results


    def delete_clone_task(self, user_id: str, task_id: str) -> bool:
        """ 删除用户克隆任务 """
        with self.SessionLocal() as session:
            task = session.query(ClonedTask).filter(
                ClonedTask.user_id==user_id, 
                ClonedTask.task_id==task_id
            ).first()
            if task:
                session.delete(task) # 数据库会自动清理所有关联的 Persona, Videos, Audios
                session.commit()
                return True
            return False

    def get_persona(self, user_id: str, task_id: str) -> object:
        """ 获取用户信息 """
        with self.SessionLocal() as session:
            personal = session.query(ClonedPersona).filter(
                ClonedPersona.user_id == user_id,
                ClonedPersona.parent_task_id == task_id
            ).first()
            return personal if personal else None

    def get_audio_id(self, user_id: str, task_id: str) -> str:
        """ 获取用户生成的最新音色id """
        with self.SessionLocal() as session:
            audio = session.query(ClonedAudios).filter(
                ClonedAudios.user_id == user_id,
                ClonedAudios.parent_task_id == task_id
            ).order_by(ClonedAudios.created_at.desc()).first()
            return audio.audio_id if audio else None

    def get_video_id(self, user_id: str, video_type: str, task_id: str) -> str:
        """ 获取用户生成的最新视频文件任务id """
        with self.SessionLocal() as session:
            video = session.query(ClonedVideos).filter(
                ClonedVideos.user_id == user_id, 
                ClonedVideos.video_type == video_type,
                ClonedVideos.parent_task_id == task_id
            ).order_by(ClonedVideos.created_at.desc()).first()
            return video.video_id if video else None

    def get_video_path(self, user_id: str, video_type: str, task_id:str) -> str:
        """ 获取用户生成最新视频文件路径 """
        with self.SessionLocal() as session:
            video = session.query(ClonedVideos).filter(
                ClonedVideos.user_id == user_id, 
                ClonedVideos.video_type == video_type,
                ClonedVideos.parent_task_id == task_id
            ).order_by(ClonedVideos.created_at.desc()).first()
            return video.video_path if video else None

# 全局单例实例化
relational_db = RelationalDB(settings.database.SQLITE_DB_PATH)