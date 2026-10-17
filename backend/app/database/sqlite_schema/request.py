from typing import Optional
from pydantic import BaseModel, Field

class UploadRequest(BaseModel):
    """ 预签名上传请求参数 """
    file_type: str = Field(..., description="文件类型 (例如: audio, image, video)")
    file_name: str = Field(..., description="文件原始名称 (带后缀)")
    content_type: Optional[str] = Field(
        "application/octet-stream", 
        description="MIME 类型，默认为二进制流"
    )

class CloneCreateRequest(BaseModel):
    """ 发起克隆任务请求参数 """
    user_id: str = Field(..., description="用户 ID")
    image_url: str = Field(..., description="用于克隆形象的图片文件 URL")
    audio_url: str = Field(None, description="用于克隆音色的音频文件 URL")
    audio_role: str = Field(None, description="选择音色角色（老人男，老人女...）")

class CloneDefaultVoiceRequest(BaseModel):
    """ 获取默认音色请求参数 """
    audio_url: str = Field(..., description="音频url")

class PersonaData(BaseModel):
    """ 具体的数字人性格设定数据 """
    name: Optional[str] = Field(None, description="数字人姓名")
    age: Optional[int] = Field(None, description="年龄")
    gender: Optional[str] = Field(None, description="性别")
    relation: Optional[str] = Field(None, description="与用户关系")
    personality: Optional[str] = Field(None, description="性格描述")
    background: Optional[str] = Field(None, description="背景设定")
    chat_style: Optional[str] = Field(None, description="说话风格")

class PersonaRequest(BaseModel):
    """ 保存/更新人设请求参数 """
    user_id: str = Field(..., description="用户ID")
    task_id: str = Field(..., description="关联的任务ID/Clone ID")
    persona: PersonaData = Field(..., description="具体的人设细节")

class CloneStatusRequest(BaseModel):
    """ 查询克隆任务状态请求参数 """
    user_id: str = Field(..., description="用户 ID")
    task_id: str = Field(..., description="任务 ID (即 clone_id)")

class CloneIDRequest(BaseModel):
    """ 获取最新任务 ID 请求参数 """
    user_id: str = Field(..., description="用户 ID")

class CloneListRequest(BaseModel):
    """ 获取克隆任务列表请求参数 """
    user_id: str = Field(..., description="用户 ID")

class CloneUpdateRequest(BaseModel):
    """ 更新克隆任务请求参数 """
    user_id: str = Field(..., description="用户 ID")
    task_id: str = Field(..., description="任务 ID (即 clone_id)")
    audio_url: Optional[str] = Field(None, description="用于克隆音色的音频文件 URL")
    persona: Optional[PersonaData] = Field(None, description="具体的人设细节")

class CloneDeleteRequest(BaseModel):
    """ 删除克隆任务请求参数 """
    user_id: str = Field(..., description="用户 ID")
    task_id: str = Field(..., description="任务 ID (即 clone_id)")
