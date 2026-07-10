from datetime import datetime, timedelta, timezone
from fastapi import Depends, Header, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from jose import JWTError, jwt
from app.core.config import get_settings

ALGO = "HS256"
bearer = HTTPBearer(auto_error=False)


def issue_token(sub: str, role: str) -> str:
    s = get_settings()
    exp = datetime.now(timezone.utc) + timedelta(minutes=s.JWT_EXPIRY_MINUTES)
    return jwt.encode({"sub": sub, "role": role, "exp": exp}, s.JWT_SECRET, ALGO)


def verify_token(token: str) -> dict:
    try:
        return jwt.decode(token, get_settings().JWT_SECRET, algorithms=[ALGO])
    except JWTError:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "invalid token")


async def current_claims(
    cred: HTTPAuthorizationCredentials | None = Depends(bearer),
) -> dict:
    if cred is None:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "missing token")
    return verify_token(cred.credentials)


def require_role(*roles: str):
    async def dep(claims: dict = Depends(current_claims)) -> dict:
        if claims.get("role") not in roles:
            raise HTTPException(status.HTTP_403_FORBIDDEN, "forbidden")
        return claims
    return dep


async def require_api_key(x_api_key: str = Header(...)) -> str:
    # Phase 2 wires real key store; stub rejects empty.
    if not x_api_key:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "missing api key")
    return x_api_key
