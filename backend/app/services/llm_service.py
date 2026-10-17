import time
from typing import List, AsyncGenerator
from loguru import logger

from app.utils.time_logger import time_logger

class LlmService:
    def __init__(self, provider: str):
        self.provider = provider
        self._client = self.get_llm_client()
        self.strong_punctuations = {"。", "！", "？", "!", "?", "\n"}
        self.weak_punctuations = {"，", ",", "；", ";", "：", ":", " "}

    def get_llm_client(self):
        """获取 LLM 客户端"""
        if self.provider == "ollama":
            from app.services.llm.ollama import OllamaLLM
            return OllamaLLM()
        else:
            from app.services.llm.doubao import DoubaoLLM
            return DoubaoLLM()

    @time_logger
    async def chat_embedding(self, chat: str):
        """使用 LLM API 向量化用户输入"""
        try:
            return await self._client.embedding(chat)
        except Exception as e:
            logger.error(f"向量化失败: {str(e)}")
            return f"向量化失败：{str(e)}"
    

    async def refine_persona(self, latest_long: str, chats: List[str]) -> str:
        """使用 LLM 更新记忆档案"""
        try:
            return await self._client.chat(latest_long, chats)
        except Exception as e:
            logger.error(f"更新记忆失败: {str(e)}")
            return f"更新记忆失败: {str(e)}"
    
    async def llm_stream(self,system_prompt: str, chat: str, memory: str) -> AsyncGenerator[str, None]:
        """LLM 流式输出 token"""
        try:
            async for token in self._client.stream(system_prompt, chat, memory):
                yield token
        except Exception as e:
            logger.error(f"输出连接失败: {str(e)}")
            yield f"输出连接失败: {str(e)}"

    async def sentence_buffer(self, token_stream, min_length=10):
        buffer = []
        current_len = 0
        is_first_sentence = True # 新增首句标识
        
        async for token in token_stream:
            buffer.append(token)
            current_len += len(token)
            
            should_yield = False
            # 1. 强标点 (。！？) 永远直接断
            if any(p in token for p in self.strong_punctuations):
                should_yield = True
            # 2. 首句优化：只要有弱标点 (，) 且长度 > 3 就断，不等待 min_length
            elif is_first_sentence and any(p in token for p in self.weak_punctuations) and current_len > 3:
                should_yield = True
            # 3. 常规逻辑
            elif current_len >= min_length and any(p in token for p in self.weak_punctuations):
                should_yield = True
            elif current_len >= 35:
                should_yield = True
                
            if should_yield:
                full_sent = "".join(buffer).strip()
                if full_sent:
                    yield full_sent
                    is_first_sentence = False # 发送后就不是首句了
                buffer = []
                current_len = 0
        if buffer:
            final_sent = "".join(buffer).strip()
            if final_sent:
                yield final_sent

    # async def sentence_buffer(self, token_stream: AsyncGenerator[str, None], min_length: int = 10) -> AsyncGenerator[str, None]:
    #     """
    #     优化后的缓冲区：使用列表合并字符串，提升内存效率
    #     """
    #     buffer = []
    #     current_len = 0
        
    #     async for token in token_stream:
    #         buffer.append(token)
    #         current_len += len(token)
            
    #         # 逻辑：遇到强标点立即断句；遇到弱标点且长度达标断句；超长断句
    #         should_yield = False
    #         if any(p in token for p in self.strong_punctuations):
    #             should_yield = True
    #         elif current_len >= min_length and any(p in token for p in self.weak_punctuations):
    #             should_yield = True
    #         elif current_len >= 35: # 适当放宽安全阈值
    #             should_yield = True
                
    #         if should_yield:
    #             full_sent = "".join(buffer).strip()
    #             if full_sent:
    #                 yield full_sent
    #             buffer = []
    #             current_len = 0

    #     if buffer:
    #         final_sent = "".join(buffer).strip()
    #         if final_sent:
    #             yield final_sent

llm_service = LlmService(provider="doubao")