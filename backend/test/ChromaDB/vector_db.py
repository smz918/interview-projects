from app.database.chroma_db import vector_db
latest_long_memory = vector_db.get_latest_long_memory("1")
print(latest_long_memory)