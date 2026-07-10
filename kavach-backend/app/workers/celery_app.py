from celery import Celery
from app.core.config import get_settings

s = get_settings()
celery_app = Celery("kavach", broker=s.REDIS_URL, backend=s.REDIS_URL)
celery_app.conf.task_track_started = True
