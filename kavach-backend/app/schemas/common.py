from pydantic import BaseModel


class ErrorResponse(BaseModel):
    code: str
    message: str
    detail: dict | None = None


class HealthResponse(BaseModel):
    status: str
    db: str
    redis: str
    neo4j: str
