from .chroma_db import VectorDB
from .sqlite_db import RelationalDB
from app.core.config import settings

# 集中实例化
vector_db = VectorDB(path=settings.database.CHROMA_DB_PATH)
relational_db = RelationalDB(settings.database.SQLITE_DB_PATH)
__all__ = ["vector_db","relational_db"]