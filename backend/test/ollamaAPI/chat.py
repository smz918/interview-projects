import httpx
from typing import List
from app.core.config import service_settings,prompt_settings


def llm_service(prompt: str) -> str:
    url = "http://{service_settings.LLM_MODEL_HOST}:{service_settings.LLM_MODEL_PORT}/api/generate"
    
    payload = {
        "model": service_settings.LLM_MODEL_NAME,
        "prompt": prompt,
        "stream": False  # 必须添加，否则返回的是流式文本，resp.json() 会报错
    }

    try:
        with httpx.Client(timeout=60.0,
                          proxy=None,
                          trust_env=False) as client:
            resp = client.post(url, json=payload)
            resp.raise_for_status()
            data = resp.json()
            return data.get("response", "")
    except httpx.HTTPStatusError as e:
        return f"接口响应错误: {e.response.status_code}"
    except Exception as e:
        return f"连接失败: {str(e)}"
    
# response = llm_service("你好")
# print(response)


def refine_persona(latest_long: str,chats: List[str]) -> str:
    """ 生成聊天总结——记忆持久化 """

    content = f"""
    [存量档案]\n{latest_long}
    [最新记忆]\n{chats}
    """
    url = f"http://{service_settings.LLM_MODEL_HOST}:{service_settings.LLM_MODEL_PORT}/api/chat"
    payload = {
        "model": service_settings.LLM_MODEL_NAME,
        "messages": [
            {
                "role": "system",
                "content": prompt_settings.SUMARY_PROMPT,
            },{
                "role": "user", 
                "content": content
            }
        ],
        "stream": False
    }
    
    try:
        with httpx.AsyncClient(timeout=60.0, proxy=None, trust_env=False) as client:
            resp = client.post(url, json=payload)
            resp.raise_for_status()
            data = resp.json()
            return data.get("response")
            
    except Exception as e:
        return f"连接失败: {str(e)}"
    
result = refine_persona("",[])
print(result)