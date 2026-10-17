import time
import mimetypes
from pathlib import Path
import alibabacloud_oss_v2 as oss
from app.core.config import settings
from datetime import timedelta
from loguru import logger

credentials = oss.credentials.StaticCredentialsProvider(
    access_key_id=settings.oss.OSS_ACCESS_KEY_ID,
    access_key_secret=settings.oss.OSS_ACCESS_KEY_SECRET,
)
cfg = oss.config.load_default()
cfg.endpoint = settings.oss.OSS_ENDPOINT
cfg.credentials_provider = credentials
cfg.region = settings.oss.OSS_REGION
client = oss.Client(cfg)

# async def upload_to_oss(file_path: str, folder: str) -> str:
#     """
#     上传资源文件到阿里云 OSS（后端测试）
#     """
#     file_name = Path(file_path).name
#     oss_obj_name = f"{folder}/{int(time.time())}_{file_name}"
#     try:
#         # 上传文件
#         client.put_object_from_file(
#             oss.PutObjectRequest(
#                 bucket=settings.oss.OSS_BUCKET_NAME, 
#                 key=oss_obj_name, 
#             ),
#             file_path
#         )
#         # 生成签名 URL
#         signed = client.presign(
#             oss.GetObjectRequest(
#                 bucket=settings.oss.OSS_BUCKET_NAME, 
#                 key=oss_obj_name,
#             ),
#             expires=timedelta(seconds=3600)
#         )
#         return signed.url
#     except Exception as e:
#         raise RuntimeError(f"OSS 上传失败: {e}")
    

def generate_upload_presigned_url(
    file_type: str, 
    original_filename: str,
    content_type: str = None,
    expire_seconds: int = 600  # 10 分钟，上传操作不需要太长
) -> dict:
    """
    生成用于前端直传 OSS 的 PUT URL
    生成用于公开访问 OSS 的 GET URL 
    """
    logger.debug(f"content_type: {content_type}")
    ext = Path(original_filename).suffix or ".bin"
    # 生成 OSS Key
    timestamp = f"{int(time.time())}_{time.time_ns()}"
    oss_key = f"uploads/{file_type}/{timestamp}{ext}"
    
    # 获取文件类型
    if not content_type:
        content_type, _ = mimetypes.guess_type(original_filename)
    content_type = content_type or "application/octet-stream"
    try:
        # 生成 PutObject 的预签名 URL（关键：用 PutObjectRequest）
        presigned_url = client.presign(
            oss.PutObjectRequest(
                bucket=settings.oss.OSS_BUCKET_NAME,
                key=oss_key,
                content_type=content_type
            ),
            expires=timedelta(seconds=expire_seconds),
        )
        
        # 生成上传完成后的公开访问 URL（方便前端直接使用）
        public_url = client.presign(
            oss.GetObjectRequest(
                bucket=settings.oss.OSS_BUCKET_NAME,
                key=oss_key,
            ),
            expires=timedelta(seconds=expire_seconds)  # 10 分钟有效
        )
        return {
            "upload_url": presigned_url.url,  # 前端用这个 PUT 上传
            "oss_key": oss_key,               # 后端后续处理时用这个路径查文件
            "public_url": public_url.url,     # 上传完成后可直接返回给前端展示
        }
    except Exception as e:
        raise RuntimeError(f"OSS 生成 URL 失败: {e}")


def upload_file_bytes(
    file_type: str,
    original_filename: str,
    data: bytes,
    content_type: str = None,
    expire_seconds: int = 3600,
) -> dict:
    """Upload bytes through the backend for app clients that cannot PUT to OSS."""
    logger.debug(f"backend upload content_type: {content_type}")
    ext = Path(original_filename or "").suffix or ".bin"
    timestamp = f"{int(time.time())}_{time.time_ns()}"
    oss_key = f"uploads/{file_type}/{timestamp}{ext}"

    if not content_type:
        content_type, _ = mimetypes.guess_type(original_filename or "")
    content_type = content_type or "application/octet-stream"

    try:
        client.put_object(
            oss.PutObjectRequest(
                bucket=settings.oss.OSS_BUCKET_NAME,
                key=oss_key,
                body=data,
                content_type=content_type
            )
        )

        public_url = client.presign(
            oss.GetObjectRequest(
                bucket=settings.oss.OSS_BUCKET_NAME,
                key=oss_key,
            ),
            expires=timedelta(seconds=expire_seconds)
        )
        return {
            "oss_key": oss_key,
            "public_url": public_url.url,
        }
    except Exception as e:
        raise RuntimeError(f"OSS backend upload failed: {e}")
