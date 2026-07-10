from functools import lru_cache
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="forbid")

    ENV: str = "dev"
    DATABASE_URL: str
    REDIS_URL: str
    NEO4J_URI: str
    NEO4J_USER: str
    NEO4J_PASSWORD: str

    GROQ_API_KEY: str = ""
    OPENAI_API_KEY: str = ""
    ANTHROPIC_API_KEY: str = ""
    GEMINI_API_KEY: str = ""
    LLM_FALLBACK_ORDER: str = "groq,anthropic,openai,gemini"
    EMBEDDING_PROVIDER: str = "openai"
    EMBEDDING_MODEL: str = "text-embedding-3-small"

    TASK_MODEL_VERDICT: str = "groq/llama-3.3-70b-versatile"
    TASK_MODEL_REDFLAG_EXPLAIN: str = "anthropic/claude-haiku-4-5-20251001"
    TASK_MODEL_DEEPCHECK: str = "groq/llama-3.3-70b-versatile"
    HIGH_CONFIDENCE_THRESHOLD: float = 0.85
    LLM_CONFIDENCE_THRESHOLD: float = 0.70

    WHATSAPP_PROVIDER: str = "twilio"
    META_WABA_TOKEN: str = ""
    META_PHONE_NUMBER_ID: str = ""
    META_WEBHOOK_SECRET: str = ""  # Used for Meta Cloud API webhook signature verification
    TWILIO_ACCOUNT_SID: str = ""
    TWILIO_AUTH_TOKEN: str = ""
    TWILIO_WHATSAPP_NUMBER: str = ""

    MINIO_ENDPOINT: str
    MINIO_ACCESS_KEY: str
    MINIO_SECRET_KEY: str
    MINIO_BUCKET: str = "kavach-evidence"

    JWT_SECRET: str
    JWT_EXPIRY_MINUTES: int = 60
    LOG_LEVEL: str = "INFO"

    # Phase 2 — alert fan-out
    FCM_SERVER_KEY: str = ""          # Firebase Cloud Messaging server key
    ALERT_COOLDOWN_MINUTES: int = 15  # re-notify cooldown for same incident+guardian

    # Phase 4 — CORS
    CORS_ALLOWED_ORIGINS: list[str] = ["http://localhost:3000", "http://localhost:5173"]

    # Phase 4 — rate limiting (token-bucket, per-sender per second)
    RATE_LIMIT_ENABLED: bool = True
    RATE_LIMIT_WHATSAPP_RPS: int = 10  # per sender_phone
    RATE_LIMIT_SIGNALS_RPS: int = 5    # per device_id

    @property
    def llm_fallback_list(self) -> list[str]:
        return [p.strip() for p in self.LLM_FALLBACK_ORDER.split(",") if p.strip()]

    @property
    def task_model_map(self) -> dict[str, str]:
        return {
            "verdict": self.TASK_MODEL_VERDICT,
            "redflag_explain": self.TASK_MODEL_REDFLAG_EXPLAIN,
            "deepcheck_reasoning": self.TASK_MODEL_DEEPCHECK,
        }


@lru_cache
def get_settings() -> Settings:
    return Settings()
