
import logging
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles

from app.core.firebase import init_firebase
from app.routers import chat, teams

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("uvicorn.error")

BASE_DIR = Path(__file__).resolve().parent
STATIC_DIR = BASE_DIR / "static"
UPLOAD_DIR = STATIC_DIR / "chat_uploads"

STATIC_DIR.mkdir(parents=True, exist_ok=True)
UPLOAD_DIR.mkdir(parents=True, exist_ok=True)


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("Starting Arshith Connect Chat API...")

    firebase_connected = init_firebase()

    if not firebase_connected:
        raise RuntimeError(
            "Firebase connection failed. Check serviceAccountKey.json "
            "and Firebase project configuration."
        )

    logger.info("Firebase connected successfully.")
    yield
    logger.info("Shutting down Arshith Connect Chat API.")


app = FastAPI(
    title="Arshith Connect Chat API",
    description="Backend API for Android and website chat",
    version="1.0.0",
    lifespan=lifespan,
)

app.mount(
    "/static",
    StaticFiles(directory=str(STATIC_DIR)),
    name="static",
)

app.include_router(chat.router, prefix="/api")
app.include_router(teams.router, prefix="/api")


@app.get("/")
def home():
    return {
        "status": "running",
        "service": "Arshith Connect Chat API",
    }


@app.get("/health")
def health():
    return {
        "status": "healthy",
        "firebase": "connected",
    }
