import httpx
from app.core.config import service_settings
def get_embedding(text: str):
    """ 将用户输入向量化 """
    url = f"http://{service_settings.EMBED_MODEL_HOST}:{service_settings.EMBED_MODEL_PORT}/api/embed"

    payload = {
        "model": service_settings.EMBED_MODEL_NAME,
        "input": text
    }
    try:
        with httpx.Client(timeout=60.0, proxy=None, trust_env=False) as client:
            resp = client.post(url, json=payload)
            resp.raise_for_status()
            data = resp.json()
            return data.get("embeddings")
    except Exception as e:
        return f"向量化失败：{str(e)}"
    
result = get_embedding("你好")
print(result)

# uv run python -m test.ollamaAPI.embed