import uvicorn
from app.core.config import settings

if __name__ == "__main__":
    uvicorn.run(
        "main:app",
        host=settings.service.HOST,
        port=settings.service.PORT,
        reload=settings.service.DEBUG
    )