import requests


def upload_file(signed_url, file_path):
    try:
        # 打开文件
        with open(file_path, 'rb') as file:
            # 发送PUT请求上传文件
            response = requests.put(signed_url, data=file)
     
        print(f"返回上传状态码：{response.status_code}")
        if response.status_code == 200:
            print("使用网络库上传成功")
        print(response.text)
 
    except Exception as e:
        print(f"发生错误：{e}")
    
upload_file(
    signed_url ="https://ahdysj.oss-cn-beijing.aliyuncs.com/uploads/image/1775181178_1775181178313064822.jpeg?x-oss-signature-version=OSS4-HMAC-SHA256&x-oss-date=20260403T015258Z&x-oss-expires=599&x-oss-credential=YOUR_OSS_ACCESS_KEY_ID%2F20260403%2Fcn-beijing%2Foss%2Faliyun_v4_request&x-oss-signature=babf4a71d9b883f0a14953cab1395b79faeb9c337424b71f7b3df03711665593",
    file_path = "./test/test.jpeg"
)