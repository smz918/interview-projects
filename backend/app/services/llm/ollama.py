import json
import httpx
from app.services.llm.base import BaseLLM
from typing import AsyncGenerator, List
from app.core.config import settings

class OllamaLLM(BaseLLM):
    def __init__(self):
        self.llm_url = f"http://{settings.ollama.LLM_URL}/api/chat"
        self.embed_url = f"http://{settings.ollama.EMBED_URL}/api/embed"
        self.llm_model = settings.ollama.LLM_MODEL
        self.embed_model = settings.ollama.EMBED_MODEL
    
    async def embedding(self, chat: str):
        """ 将用户输入向量化 """
        payload = {
            "model": self.embed_model,
            "input": chat
        }
        async with httpx.AsyncClient(timeout=60.0, proxy=None, trust_env=False) as client:
            resp = await client.post(self.embed_url, json=payload)
            resp.raise_for_status()
            data = resp.json()
            return data.get("embeddings")[0]

    async def chat(self, latest_long: str,chats: List[str]) -> str:
        """ 记忆持久化————滚动式更新记忆档案 """
        content = f"""
        [记忆档案]\n{latest_long}
        [最新对话]\n{chats}
        """
        # print(content)
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
        async with httpx.AsyncClient(timeout=60.0, proxy=None, trust_env=False) as client:
            resp = await client.post(self.llm_url, json=payload)
            resp.raise_for_status()
            data = resp.json()
            message = data.get("message", {})
            return message.get("content")

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
        # 发送请求
        async with httpx.AsyncClient(timeout=60.0, proxy=None, trust_env=False) as client:
            # 获取响应
            async with client.stream("POST", self.llm_url, json=payload) as resp:
                async for line in resp.aiter_lines():
                    if not line.strip():
                        continue
                    try: # 忽略无效数据
                        data = json.loads(line)
                        token = data.get("message", {}).get("content", "")
                        if token:
                            yield token
                    except Exception:
                        continue
        
    
