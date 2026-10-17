from typing import AsyncGenerator

class BaseLLM:
    """ LLM 基类 """
    async def embedding(self, text: str) -> list:
        raise NotImplementedError

    async def chat(self, messages: list) -> str:
        raise NotImplementedError

    async def stream(self, system_prompt: str, chat: str, memory: str) -> AsyncGenerator[str, None]:
        raise NotImplementedError
