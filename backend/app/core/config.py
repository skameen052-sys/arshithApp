import json
from typing import List, Union, Optional
from pydantic import field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    PROJECT_NAME: str = "Arshith Connect API"
    API_V1_STR: str = "/api"
    SECRET_KEY: str = "arshith_connect_secure_jwt_secret_key_2026_dev_prod"
    ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 1440  # 24 hours

    POSTGRES_SERVER: str = "localhost"
    POSTGRES_PORT: int = 5432
    POSTGRES_USER: str = "postgres"
    POSTGRES_PASSWORD: str = "Ameen9080"
    POSTGRES_DB: str = "arshith_connect_db"

    DATABASE_URL: str = "postgresql+psycopg://postgres:Ameen9080@localhost:5432/arshith_connect_db"

    # Database Backend Selector: "firebase" (default) or "postgres"
    DATABASE_BACKEND: str = "firebase"

    # Firebase Admin & Cloud Firestore Configuration
    # Set FIREBASE_DATABASE_NAME to "(default)" or your specific Firestore database name/ID
    FIREBASE_DATABASE_NAME: str = "(default)"
    FIREBASE_PROJECT_ID: str = "arshith-connect-attendance"
    FIREBASE_STORAGE_BUCKET: str = "arshith-connect-attendance.firebasestorage.app"
    FIREBASE_CREDENTIALS_PATH: str = "serviceAccountKey.json"
    FIREBASE_WEB_API_KEY: str = "AIzaSyCdc8YjhNXXS0dSdYs1iKgJvL44G_6MAM8"
    GOOGLE_APPLICATION_CREDENTIALS: Optional[str] = None
    FIREBASE_CREDENTIALS_JSON: Optional[str] = None

    # Attendance & Photo Storage Configuration
    ATTENDANCE_TIMEZONE: str = "Asia/Kolkata"
    ATTENDANCE_PHOTO_DIR: str = ""
    MAX_PHOTO_SIZE_BYTES: int = 5 * 1024 * 1024  # 5 MB

    # Official Office Geofencing Constants (Single Source of Truth: 12.9104417, 77.6442435, 100m)
    COMPANY_OFFICE_NAME: Optional[str] = None
    COMPANY_OFFICE_ADDRESS: Optional[str] = None
    COMPANY_OFFICE_LATITUDE: float = 12.9104417
    COMPANY_OFFICE_LONGITUDE: float = 77.6442435
    OFFICE_GEOFENCE_RADIUS_METERS: float = 250.0
    MAX_GPS_ACCURACY_METERS: float = 100.0

    @field_validator("COMPANY_OFFICE_LATITUDE")
    @classmethod
    def validate_latitude(cls, v: float) -> float:
        if not (-90.0 <= v <= 90.0):
            raise ValueError("COMPANY_OFFICE_LATITUDE must be between -90.0 and 90.0 degrees.")
        return v

    @field_validator("COMPANY_OFFICE_LONGITUDE")
    @classmethod
    def validate_longitude(cls, v: float) -> float:
        if not (-180.0 <= v <= 180.0):
            raise ValueError("COMPANY_OFFICE_LONGITUDE must be between -180.0 and 180.0 degrees.")
        return v

    @field_validator("OFFICE_GEOFENCE_RADIUS_METERS")
    @classmethod
    def validate_radius(cls, v: float) -> float:
        if v <= 0:
            raise ValueError("OFFICE_GEOFENCE_RADIUS_METERS must be a positive number of meters.")
        return v

    @field_validator("MAX_GPS_ACCURACY_METERS")
    @classmethod
    def validate_accuracy_threshold(cls, v: float) -> float:
        if v <= 0:
            raise ValueError("MAX_GPS_ACCURACY_METERS must be a positive number of meters.")
        return v

    FRONTEND_CORS_ORIGINS: List[str] = [
        "http://localhost:5173",
        "http://127.0.0.1:5173",
        "http://localhost:3000",
        "http://127.0.0.1:3000",
    ]

    @field_validator("FRONTEND_CORS_ORIGINS", mode="before")
    @classmethod
    def assemble_cors_origins(cls, v: Union[str, List[str]]) -> List[str]:
        if isinstance(v, str) and not v.startswith("["):
            return [i.strip() for i in v.split(",")]
        elif isinstance(v, str) and v.startswith("["):
            return json.loads(v)
        return v

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=True,
        extra="allow",
    )


settings = Settings()
