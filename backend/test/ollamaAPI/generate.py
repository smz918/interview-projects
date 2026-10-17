import httpx
from typing import List
from app.core.config import prompt_settings,service_settings
def summarize_chats(user_id: str, chats: List[str]) -> str:
    """ 生成聊天总结 """
    prompt = f"{prompt_settings.SUMARY_PROMPT}\n{chats}"
    url = f"http://{service_settings.LLM_MODEL_HOST}:{service_settings.LLM_MODEL_PORT}/api/generate"
    payload = {
        "model": service_settings.LLM_MODEL_NAME,
        "prompt": prompt,
        "stream": False
    }
    
    try:
        with httpx.Client(timeout=60.0, proxy=None, trust_env=False) as client:
            resp = client.post(url, json=payload)
            resp.raise_for_status()
            data = resp.json()
            return data.get("response")
            
    except Exception as e:
        return f"连接失败: {str(e)}"

prompt = summarize_chats("1", ["User: 你好，你可以叫小明吗Assistant: 当然可以，小明在线，有什么想聊的？",
                                "User: 我最近快开学了，有点焦虑Assistant: 哎，快开学了啊，我能理解这种感觉。紧张点也没关系，毕竟又是一个新学期，新鲜事儿多着呢！你可以试试制定个计划，把心情写下来，或者找朋友聊聊，分分心。", 
                                "User: 是的，我得好好训练一下代码能力了Assistant: 嗯，学习新东西总是会有点压力，别太往心里去。可以试试找个编程社区或者朋友一起学习，互相帮助进度会快不少哦！加油！"])
print(prompt)