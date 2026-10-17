import http.client
import json
from app.core.config import llm_doubao_settings
conn = http.client.HTTPSConnection("ark.cn-beijing.volces.com")
payload = json.dumps({
   "model": "ep-20260318135312-pk5l2",
   "input": [
        {
            "type":"text",
            "text":"天很蓝，海很深"
        },
        {    
            "type":"image_url",
            "image_url":{
                "url":"https://ark-project.tos-cn-beijing.volces.com/images/view.jpeg"
            }
        }
   ]
})
headers = {
   'Authorization': f'Bearer {llm_doubao_settings.API_KEY}',
   'Content-Type': 'application/json'
}
conn.request("POST", "/api/v3/embeddings/multimodal", payload, headers)
res = conn.getresponse()
data = res.read()
print(data.decode("utf-8"))

# uv run python -m test.byteAPI.embed