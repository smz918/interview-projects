import uuid
import datetime
import chromadb
from typing import List
from loguru import logger
from app.core.config import settings
from app.utils.time_logger import time_logger

class VectorDB:
    """
    short_memory: 存储最近12条对话、提取后的长期记忆
    long_memory: 存储经过提炼的长期记忆、存储并向量检索
    """
    def __init__(self, path: str):
        # 初始化持久化客户端
        self.client = chromadb.PersistentClient(path=path)
        # 获取或创建集合（Collection），这里可以理解为关系型数据库的 "表"
        self.collection = self.client.get_or_create_collection(
            name="user_memories",
            metadata={"hnsw:space": "cosine"} # 使用余弦相似度
        )
    
    def get_short_memories(self, user_id: str, clone_id: str, n_results: int = 12):
        """ 获取指定用户的最近N条对话，用于短期记忆参考 """
        results = self.collection.get(
            where={"$and": [
                {"user_id": user_id},
                {"clone_id": clone_id},
                {"type": "short"}
            ]},
        )
        # 如果需要限制数量，可以取最后 n_results 条
        ids = results.get("ids", [])
        docs = results.get("documents", [])
        return ids[-n_results:], docs[-n_results:]

    def save_short_memory(self, user_id: str, clone_id: str, prompt: str, response: str, embedding: List[float]):
        """ 存储短期对话 """
        doc_id = str(uuid.uuid4())
        content = f"User: {prompt}\nMe: {response}"
        
        self.collection.add(
            ids=[doc_id],
            embeddings=[embedding],
            documents=[content],
            metadatas=[{
                "user_id": user_id, 
                "clone_id": clone_id,
                "type": "short", 
                "timestamp": str(datetime.datetime.now())
            }]
        )
        return doc_id

    def delete_short_memories(self, ids: List[str]):
        """ 删除已经提炼过的短期记忆 ID 列表 """
        if ids:
            self.collection.delete(ids=ids)

    def get_count(self, user_id: str, clone_id: str, mem_type: str = "short") -> int:
        """ 获取当前短期对话的数量 """
        res = self.collection.get(where={"$and": [
            {"user_id": user_id}, 
            {"clone_id": clone_id},
            {"type": mem_type}
        ]})
        return len(res["ids"])

    def get_latest_long_memory(self, user_id: str, clone_id: str) -> str:
        """ 获取当前用户的最新一次提炼的长期记忆 """
        res = self.collection.get(
            where={
                "$and": [
                    {"user_id": user_id}, 
                    {"clone_id": clone_id},
                    {"type": "long"},
                    {"is_latest": "true"} # 获取最新一次的
                ]
            }
        )
        docs = res.get("documents", [])
        # 先判断列表是否为空（是否已有长期记忆）
        if not docs:
            return ""
        return docs[0]

    def save_long_memory(self, user_id: str, clone_id: str, long_memory: str, embedding: List[float]):
        """ 存储经过提炼的长期记忆，并将旧记忆标记为过时 """
        # 查找该用户之前标记为最新的长期记忆
        old_latest = self.collection.get(
            where={
                "$and": [
                    {"user_id": user_id},
                    {"clone_id": clone_id},
                    {"type": "long"},
                    {"is_latest": "true"}
                ]
            }
        )
        # 如果存在旧记忆，直接执行物理删除
        if old_latest and old_latest["ids"]:
            self.collection.delete(ids=old_latest["ids"])

        # 存储新的长期记忆，标记为最新的 (true)
        doc_id = f"long_{str(uuid.uuid4())[:8]}"
        self.collection.add(
            ids=[doc_id],
            embeddings=[embedding],
            documents=[long_memory],
            metadatas=[{
                "user_id": user_id, 
                "clone_id": clone_id,
                "type": "long", 
                "is_latest": "true",  # 核心标记
                "timestamp": str(datetime.datetime.now())
            }]
        )
        return doc_id
    
    def get_recent_short_memories(self, user_id: str, clone_id: str, n: int = 6) -> List[str]:
        """ 按时间顺序获取最近 N 条对话，并带上时间标签注入上下文 """
        results = self.collection.get(
            where={"$and": [
                {"user_id": user_id},
                {"clone_id": clone_id},
                {"type": "short"}
            ]},
            include=["documents", "metadatas"]
        )
        docs      = results.get("documents", [])
        metadatas = results.get("metadatas", [])
        
        if not docs:
            return []

        # 组合数据并按时间排序
        combined = sorted(zip(metadatas, docs), key=lambda x: x[0].get("timestamp", ""))
        # 选取最近的 n 条
        recent_items = combined[-n:]
        # 格式化输出：[2026-04-08 21:00] User: xxx \n Me: xxx
        formatted_memories = []
        for meta, doc in recent_items:
            raw_ts = meta.get("timestamp", "未知时间")
            # 格式化时间戳，只保留到分钟，增强可读性
            try:
                ts = raw_ts.split(".")[0]  # 移除毫秒部分
            except:
                ts = raw_ts
            formatted_memories.append(f"对话时间[{ts}]:\n{doc}")
        return formatted_memories

    @time_logger
    def query_combined_memory(self, user_id: str, clone_id: str, query_embedding: List[float]) -> str:
        """ 联合检索：长期记忆 + 近期对话窗口 + 向量相关片段 """
        all_memories = []

        # 长期记忆（人设/事实背景）
        latest_long = self.get_latest_long_memory(user_id, clone_id)
        if latest_long:
            all_memories.append(f"【长期背景/人设】（最高优先级）：\n{latest_long}")
        # 最近 5 轮对话（直接注入，保证上下文连贯）
        recent = self.get_recent_short_memories(user_id, clone_id, n=6)
        if recent:
            all_memories.append("【最近对话记录】（高优先级）：\n" + "\n".join(recent))
        # 向量检索相关片段（可能召回更远的相关记忆）排除已经在近期窗口里的内容，避免重复
        recent_set = set(recent)
        try:
            short_res = self.collection.query(
                query_embeddings=[query_embedding],
                n_results=3,
                where={"$and": [
                    {"user_id": user_id},
                    {"clone_id": clone_id},
                    {"type": "short"}]}
            )
            related = [
                doc for doc in short_res.get("documents", [[]])[0]
                if doc not in recent_set
            ]
            if related:
                all_memories.append("【相关记忆片段】（参考）：\n" + "\n".join(related))
        except Exception as e:
            logger.error(f"向量库查询异常: {e}") # 短期记忆不足 n_results 条时 chromadb 会抛异常
        return "\n\n".join(all_memories)


vector_db = VectorDB(settings.database.CHROMA_DB_PATH)

