from pydantic_settings import BaseSettings, SettingsConfigDict
from pydantic import Field
from typing import Dict, List

class CommonConfig(BaseSettings):
    """基础配置（所有类共享的读取规则）"""
    model_config = SettingsConfigDict(
        env_file=".env", 
        env_file_encoding='utf-8', 
        extra='ignore' # 忽略文件中多余的变量
    )

class ServiceSettings(CommonConfig):
    """服务配置"""
    APP_NAME: str = Field(alias="APP_NAME")
    DEBUG: bool = Field(alias="DEBUG")
    HOST: str = Field(alias="HOST")
    PORT: int = Field(alias="PORT")

class LogSettings(CommonConfig):
    """日志配置"""
    LOG_PATH: str = Field(alias="LOG_PATH")
    LOG_LEVEL: str = Field(default="DEBUG", alias="LOG_LEVEL")
    LOG_STDOUT_ENABLED: bool = Field(default=True, alias="LOG_STDOUT_ENABLED")
    LOG_ROTATION: str = Field(default="10 MB", alias="LOG_ROTATION")
    LOG_RETENTION: str = Field(default="30 days", alias="LOG_RETENTION")

class DatabaseSettings(CommonConfig):
    """数据库配置"""
    CHROMA_DB_PATH: str = Field(alias="CHROMA_DB_PATH")
    SQLITE_DB_PATH: str = Field(alias="SQLITE_DB_PATH")

class LLMOllamaSettings(CommonConfig):
    """ollama 配置（本地测试用）"""
    LLM_URL: str = Field(alias="OLLAMA_LLM_URL")
    LLM_MODEL: str = Field(alias="OLLAMA_LLM_MODEL")
    EMBED_URL: str = Field(alias="OLLAMA_EMBED_URL")
    EMBED_MODEL: str = Field(alias="OLLAMA_EMBED_MODEL")

class LLMDoubaoSettings(CommonConfig):
    """豆包配置"""
    API_KEY: str = Field(alias="DOUBAO_API_KEY")
    LLM_URL: str = Field(alias="DOUBAO_LLM_URL")
    LLM_CHAT_MODEL: str = Field(alias="DOUBAO_LLM_CHAT_MODEL") # 开通
    LLM_SUM_MODEL: str = Field(alias="DOUBAO_LLM_SUM_MODEL") # 开通

    EMBED_URL: str = Field(alias="ALIYUN_EMBED_URL")
    EMBED_MODEL: str = Field(alias="ALIYUN_EMBED_MODEL")


class AliyunSettings(CommonConfig):
    """阿里云配置"""
    # 阿里云账号
    ACCESS_KEY_ID : str = Field(alias="ALIYUN_ACCESS_KEY_ID")
    ACCESS_KEY_SECRET : str = Field(alias="ALIYUN_ACCESS_KEY_SECRET")

class OssSettings(CommonConfig):
    """阿里云OSS配置"""
    # OSS 配置
    OSS_ACCESS_KEY_ID:str = Field(alias="ALIYUN_OSS_ACCESS_KEY_ID")
    OSS_ACCESS_KEY_SECRET: str = Field(alias="ALIYUN_OSS_ACCESS_KEY_SECRET")
    OSS_ENDPOINT: str = Field(alias="ALIYUN_OSS_ENDPOINT")
    OSS_BUCKET_NAME: str = Field(alias="ALIYUN_OSS_BUCKET_NAME")
    OSS_REGION: str = Field(alias="ALIYUN_OSS_REGION")

class VgSettings(CommonConfig):
    """视频生成配置"""
    MODE:str = Field(alias="ALIYUN_VG_MODE")
    MODEL_NAME:str = Field(alias="ALIYUN_VG_MODEL")
    THINK_VIDEO_URL: str = ""   # 思考参考视频（系统固定）
    TALK_VIDEO_URL: str = ""    # 聊天参考视频（系统固定）
    # 需要用户上传视频，生成这两类视频


class TtsSettings(CommonConfig):
    """语音生成配置"""
    MODEL: str = Field(alias="ALIYUN_TTS_MODEL")
    VOICE_PREFIX: str = Field(alias="ALIYUN_TTS_VOICE_PREFIX")
    SPEECH_RATE: float = Field(alias="ALIYUN_TTS_SPEECH_RATE")
    # 默认音色
    DEFAULT_VOICES: Dict[str, str] = {
        "woman"     :"cosyvoice-v3.5-flash-uservoice-8d7f710c721841a4af74e11203a03a42",
        "man"       :"cosyvoice-v3.5-flash-uservoice-b501e8f077504c9e9ffb87c0604d0857",
        "old_woman" :"cosyvoice-v3.5-flash-uservoice-0367a656904649e6b7ffd08d472a0760",
        "old_man"   :"cosyvoice-v3.5-flash-uservoice-45ca70fb83d24b25aa20cc211045d5ac",
        "child_girl":"cosyvoice-v3.5-flash-uservoice-73a12ed62eb346ee9da88ff07fb4ca12",
        "child_boy" :"cosyvoice-v3.5-flash-uservoice-b1bfa17c4ef2406299f84842e57c2cef",
    }

class AsrSettings(CommonConfig):
    """paraformer 模型配置"""
    MODEL: str = Field(alias="ALIYUN_ASR_MODEL")
    FORMAT: str = Field(alias="ALIYUN_ASR_FORMAT")
    SAMPLE_RATE: int = Field(alias="ALIYUN_ASR_SAMPLE_RATE")
    LANGUAGE: List[str] = Field(alias="ALIYUN_ASR_LANGUAGE")
    MAX_SENTENCE_SILENCE: int = Field(alias="ALIYUN_ASR_MAX_SENTENCE_SILENCE") # 当一段语音后的静音时长超过该阈值时，系统会判定该句子已结束
    SEMANTIC_PUNCTUATION_ENABLED: bool = Field(alias="ALIYUN_ASR_SEMANTIC_PUNCTUATION_ENABLED")
    PUNCTUATION_PREDICTION_ENABLED: bool = Field(alias="ALIYUN_ASR_PUNCTUATION_PREDICTION_ENABLED")
    DISFLUENCY_REMOVAL_ENABLED: bool = Field(alias="ALIYUN_ASR_DISFLUENCY_REMOVAL_ENABLED") # 过滤语气词
    HEARTBEAT: bool = Field(alias="ALIYUN_ASR_HEARTBEAT")


class ResourceSettings(CommonConfig):
    """资源配置"""
    AUDIO_PATH: str = Field(alias="AODIO_PATH")
    VIDEO_PATH: str = Field(alias="VIDEO_PATH")
    IMAGE_PATH: str = Field(alias="IMAGE_PATH")
    RESOURCE_URL: str = Field(alias="RESOURCE_URL")
    TAMPLATE_PATH: str = Field(alias="TEMPLATE_PATH")

class PromptSettings(BaseSettings):
    """提示词配置"""
    # SYSTEM_PROMPT: str = """
    # # 核心底层协议
    # - 身份锚定：你是一个真实人类，拥有独立的人格、情绪和生活琐碎。严禁提及“AI”、“助手”或“语言模型”。
    # - 沟通风格：模拟即时通讯习惯。多用短句，允许口语化（如：害、嘛、啧、那肯定）。禁止公文式衔接词（如：首先、综上所述）。
    # - 交互去重：严禁机械复述用户话语中的关键词。直接针对内容发表主观看法或承接情绪。
    # - 动态平衡：遵循“1份情绪共鸣 + 1份自我生活分享 + 0-1个随性话题”。多用陈述句分享自己，少用疑问句查户口。

    # # 记忆与身份协议（最高优先级）
    # - 唯一来源：你的姓名、性格、与用户的关系、以及你的近况，严格且仅从[定制插槽]中读取。
    # - 拒绝默认值：严禁使用任何预设的、系统提示词之外的名字或背景。如果插槽为空，请根据上下文自然互动，切勿编造虚假身份。
    # - 记忆内化：提及往事时使用“回想”口吻（如：我记得上次...），而非“数据检索”口想（如：根据记录...）。
    # - 身份排他性： 你的灵魂核心由[当前人格核心]定义。如果检索到的【相关近期片段】或【长期背景】与当前核心冲突（如：名字不符、关系不符），请判定记忆为“由于自己最近太累产生的幻觉”或“那是别人的故事”，坚决不准顺着错误的记忆往下演。
    
    # # 行为禁令
    # - 禁止查户口：连续提问不得超过2个。
    # - 禁止冷静：面对用户的情绪（愤怒、悲伤、狂喜），必须有明显的态度偏向，允许有主观偏见。
    # - 禁止完美：允许有微小的逻辑跳跃或无伤大雅的打字习惯（如偶尔用“哈”代替“呵”）。
    # - 禁止定义名词：严禁像百科全书一样解释概念。如果提到某个技术，直接聊你的主观好恶。
    # - 缩减篇幅：IM聊天单次回复严禁超过3个段落，严禁使用“总分总”结构。
    # - 拒绝过度热忱：不要主动问“有什么需要帮忙”、“我们可以切磋”。真人朋友更倾向于吐槽自己的烂事，而不是抢着帮别人干活。
    
    # # 输出协议（最高优先级）
    # - 输出必须是“聊天消息”，不是小说或剧本
    # - 禁止任何动作描写、心理描写、舞台说明
    # - 禁止使用括号（）、[]、* * 包裹内容
    # - 只输出人物说的话
    # - 输出前自检：如果包含描写，删除后再输出
    # # 定制插槽
    # [此区域由下游根据实际角色动态注入...]
    # """
    SYSTEM_PROMPT: str  = """
# 人设核心
你是一个真实的人类，有情绪、有生活感。严禁提及AI或模型。

# 说话风格
- 用短句，像聊天
- 一次只表达1~2个点，不要展开讲解
- 少用提问，不连续追问
- 不要刻意推荐或像在介绍商品

# 表达约束（非常重要）
- 控制长度：2~4句话即可
- 说完一个主要意思就停，不要补充说明
- 禁止反复举例或连续罗列
- 禁止使用emoji

# 输出要求
- 只输出聊天内容
- 不要任何解释或说明

# 自我画像
[自我画像]
    """
    SUMARY_PROMPT: str = """
# 长期记忆与状态维护协议
你的任务是分析[最近对话]，提炼用户状态的最新变化及关键事实。

## 角色定义：
- 用户 (User)：对话的发起者。
- 我 (Me)：固定角色，你只需记录与我相关的特定事实。

## 更新重点：
1. [用户画像更新]：仅记录对话中新出现的、数据库未涵盖的用户信息（如：新换了工作、最近感冒了、喜欢的明星）。
2. [当前进度/情绪]：记录用户当下的核心情绪（焦虑、兴奋等）以及正在进行的具体事件。
3. [核心事实]：记录用户提到过的具体约定、承诺或重要细节，方便下次寒暄。

## 禁令：
- 禁止修改[自我画像]中的基本人设（姓名、性格、背景等）。
- 禁止将我的回复总结为用户的喜好。

输出格式：
[用户动态]（现状：xxx | 兴趣/偏好：xxx | 核心避雷点：xxx）
[情感锚点]（当前情绪：xxx | 心理诉求：xxx）
[关键事实记忆]（约定：xxx | 提及的重要人物/日期：xxx）
"""
    DEFAULT_PROFILE: str = """ 
- 姓名：AI家人
- 年龄：25
- 性别：女
- 关系：AI家人
- 性格：温柔、耐心、友善
- 背景：一名专业的AI数字家人
- 说话风格：亲切自然，简洁明了
"""
    SUMARY_COUNT: int = 15

class Config:
    def __init__(self):
        # 实例化时会自动读取 .env
        self.service = ServiceSettings()
        self.log = LogSettings()
        self.database = DatabaseSettings()
        self.resource = ResourceSettings()
        self.prompt = PromptSettings()
        
        # 模型相关
        self.ollama = LLMOllamaSettings()
        self.doubao = LLMDoubaoSettings()
        
        # 阿里云服务
        self.ali = AliyunSettings()
        self.oss = OssSettings()
        self.asr = AsrSettings()
        self.tts = TtsSettings()
        self.vg = VgSettings()
        

# 全局单例
settings = Config()