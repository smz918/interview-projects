from http import HTTPStatus
from dashscope.audio.asr import Recognition
import dashscope
dashscope.api_key = "YOUR_API_KEY_HERE"

def test_file_asr(audio_path: str):
    recognition = Recognition(
        model='paraformer-realtime-v2',  # ✅ 推荐模型
        format='mp3',                     # 支持 pcm/wav/mp3/opus 等
        sample_rate=22050,                # 麦克风标准采样率
        language_hints=['zh', 'yue'],     # 普通话+粤语，覆盖家庭场景
        disfluency_removal_enabled=True,  # 过滤"嗯/啊"等语气词
        callback=None
    )
    
    result = recognition.call(audio_path)  # 🔄 阻塞直到识别完成
    
    if result.status_code == HTTPStatus.OK:
        text = result.get_sentence()
        print(f"✅ 识别结果: {text}")
        return text
    else:
        print(f"❌ 错误: {result.message}")
        return None

if __name__ == '__main__':
    test_file_asr('voice_output_1773643222.mp3')