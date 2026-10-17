import asyncio
import json
import httpx
from loguru import logger
from app.services.llm.base import BaseLLM
from typing import AsyncGenerator, List
from app.core.config import settings
from openai import AsyncOpenAI

class DoubaoLLM(BaseLLM):
    def __init__(self):
        self.api_key = settings.doubao.API_KEY
        self.llm_url = settings.doubao.LLM_URL
        self.llm_model = settings.doubao.LLM_CHAT_MODEL

        self.embed_api_key = settings.ali.ACCESS_KEY_SECRET
        self.embed_url = settings.doubao.EMBED_URL
        self.embed_model = settings.doubao.EMBED_MODEL

        # LLM 请求头
        self.byte_headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json"
        }

        # 创建 OpenAI 客户端
        self.embed_client = AsyncOpenAI(
            api_key=self.embed_api_key,
            base_url=self.embed_url,
            # timeout 防止向量化挂起
            timeout=10.0 
        )
        
        # 针对 httpx 流式请求
        self.http_client = httpx.AsyncClient(
            timeout=60.0,
            limits=httpx.Limits(max_connections=100, max_keepalive_connections=20),
            trust_env=False
        )

    async def embedding(self,chat: str):
        """ 将用户输入向量化 """
        try: # 使用异步方法调用
            reps = await self.embed_client.embeddings.create(
                model=self.embed_model,
                input=chat,
                dimensions=512,
            )
            return reps.data[0].embedding
        except Exception as e:
            logger.error(f"Embedding 请求失败: {e}")
            return None


    async def chat(self, latest_long: str,chats: List[str]) -> str:
        """ 记忆持久化————滚动式更新记忆档案 """
        content = f"""
        [记忆档案]\n{latest_long}
        [最新对话]\n{chats}
        """
        payload = {
            "model": self.llm_model,
            "messages": [
                {
                    "role": "system",
                    "content": settings.prompt.SUMARY_PROMPT,
                },{
                    "role": "user", 
                    "content": content
                }
            ],"stream": False
        }
        try:
            resp = await self.http_client.post(self.llm_url, json=payload, headers=self.byte_headers)
            resp.raise_for_status()
            data = resp.json()
            return data["choices"][0]["message"]["content"]
        except Exception as e:
                logger.error(f"Chat 请求失败: {e}")
                return "记忆提炼失败"

    async def stream(self, system_prompt: str, chat: str, memory: str) -> AsyncGenerator[str, None]:
        """LLM 流式输出 token"""
        payload = {
            "model": self.llm_model,
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "system", "content": f"[记忆]\n{memory}"},
                {"role": "user", "content": chat},
            ],
            "stream": True
        }

        # 获取响应
        async with self.http_client.stream("POST", self.llm_url, json=payload, headers=self.byte_headers) as resp:
            async for line in resp.aiter_lines():
                if not line.startswith("data: "):
                    continue
                
                data_str = line[6:]
                if data_str.strip() == "[DONE]":
                    break
                    
                try:
                    data = json.loads(data_str)
                    delta = data["choices"][0].get("delta", {})
                    if "content" in delta:
                        yield delta["content"]
                except Exception:
                    continue
    async def close(self):
        """ 优雅退出，关闭连接池 """
        await asyncio.gather(
            self.http_client.aclose(),
            self.embed_client.close()
        )


    # async def embedding(self,chat: str):
    #     """ 将用户输入向量化 """
    #     client = OpenAI(
    #         api_key=self.embed_api_key, 
    #         base_url= self.embed_url
    #     )
    #     # 发送请求
    #     reps = client.embeddings.create(
    #         model=self.embed_model,
    #         input= chat,
    #         dimensions=512,
    #     )
    #     return reps.data[0].embedding


    # async def chat(self, latest_long: str,chats: List[str]) -> str:
    #     """ 记忆持久化————滚动式更新记忆档案 """
    #     content = f"""
    #     [记忆档案]\n{latest_long}
    #     [最新对话]\n{chats}
    #     """
    #     payload = {
    #         "model": self.llm_model,
    #         "messages": [
    #             {
    #                 "role": "system",
    #                 "content": settings.prompt.SUMARY_PROMPT,
    #             },{
    #                 "role": "user", 
    #                 "content": content
    #             }
    #         ],"stream": False
    #     }
    #     async with httpx.AsyncClient(timeout=60.0, proxy=None, trust_env=False) as client:
    #         resp = await client.post(self.llm_url, json=payload, headers=self.byte_headers)
    #         resp.raise_for_status()
    #         data = resp.json()
    #         return data["choices"][0]["message"]["content"]


    # async def stream(self, system_prompt: str, chat: str, memory: str) -> AsyncGenerator[str, None]:
    #     """LLM 流式输出 token"""
    #     payload = {
    #         "model": self.llm_model,
    #         "messages": [
    #             {"role": "system", "content": system_prompt},
    #             {"role": "system", "content": f"[记忆]\n{memory}"},
    #             {"role": "user", "content": chat},
    #         ],
    #         "stream": True
    #     }
    #     # 发送请求
    #     async with httpx.AsyncClient(timeout=60.0, proxy=None, trust_env=False) as client:
    #         # 获取响应
    #         async with client.stream("POST", self.llm_url, json=payload, headers=self.byte_headers) as resp:
    #             async for line in resp.aiter_lines():
    #                 if not line.startswith("data: "):
    #                     continue
                    
    #                 data_str = line[6:]
    #                 if data_str.strip() == "[DONE]":
    #                     break
                        
    #                 try:
    #                     data = json.loads(data_str)
    #                     delta = data["choices"][0].get("delta", {})
    #                     if "content" in delta:
    #                         yield delta["content"]
    #                 except Exception:
    #                     continue