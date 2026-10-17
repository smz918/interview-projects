import datetime
from sqlalchemy import Column, ForeignKey, String, Integer, DateTime, Text
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import relationship

Base = declarative_base()


class ClonedTask(Base):
    """ 克隆数字人任务 """
    __tablename__ = "cloned_tasks"
    task_id = Column(String, primary_key=True) # 总任务ID 也是 clone ID
    user_id = Column(String, index=True)
    status = Column(String, default="RUNNING") # RUNNING, SUCCESS, FAILED
    created_at = Column(DateTime, default=datetime.datetime.now)
    updated_at = Column(DateTime, default=datetime.datetime.now, onupdate=datetime.datetime.now)

    persona = relationship("ClonedPersona", back_populates="task", cascade="all, delete-orphan", uselist=False)
    videos = relationship("ClonedVideos", back_populates="task", cascade="all, delete-orphan")
    audios = relationship("ClonedAudios", back_populates="task", cascade="all, delete-orphan")


class ClonedPersona(Base):
    """ 存储数字人的性格设定 """
    __tablename__ = "cloned_personas"
    
    id = Column(Integer, primary_key=True, autoincrement=True)
    user_id = Column(String, index=True, nullable=False)
    parent_task_id = Column(String, ForeignKey("cloned_tasks.task_id", ondelete="CASCADE"), index=True, unique=True)
    # 人设细节
    name = Column(String, comment="数字人姓名")
    age = Column(Integer)
    gender = Column(String)
    relation = Column(String, comment="与用户关系")
    personality = Column(Text, comment="性格描述")
    background = Column(Text, comment="背景设定")
    chat_style = Column(Text, comment="说话风格")
    created_at = Column(DateTime, default=datetime.datetime.now)
    updated_at = Column(DateTime, default=datetime.datetime.now, onupdate=datetime.datetime.now)
    task = relationship("ClonedTask", back_populates="persona")


class ClonedVideos(Base):
    """ 存储数字人生成的视频 """
    __tablename__ = "cloned_videos"
    id = Column(Integer, primary_key=True, autoincrement=True)
    user_id = Column(String, index=True, nullable=False)
    video_id = Column(String, nullable=False)
    video_type = Column(String)  # 'think' 或 'speak'
    video_path = Column(String, nullable=False)
    parent_task_id = Column(String, ForeignKey("cloned_tasks.task_id", ondelete="CASCADE"), index=True)
    created_at = Column(DateTime, default=datetime.datetime.now)
    updated_at = Column(DateTime, default=datetime.datetime.now, onupdate=datetime.datetime.now)
    task = relationship("ClonedTask", back_populates="videos")


class ClonedAudios(Base):
    """ 存储数字人生成的音色 """
    __tablename__ = "cloned_audios"
    id = Column(Integer, primary_key=True, autoincrement=True)
    user_id = Column(String, index=True, nullable=False)
    audio_id = Column(String, nullable=False)
    parent_task_id = Column(String, ForeignKey("cloned_tasks.task_id", ondelete="CASCADE"), index=True)
    created_at = Column(DateTime, default=datetime.datetime.now)
    updated_at = Column(DateTime, default=datetime.datetime.now, onupdate=datetime.datetime.now)
    task = relationship("ClonedTask", back_populates="audios")