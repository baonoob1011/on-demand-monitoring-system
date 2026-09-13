from __future__ import annotations

from dataclasses import asdict, dataclass
from enum import Enum
import json
from pathlib import Path
import threading


class LocalMediaStatus(str, Enum):
    REVIEW_PENDING = "REVIEW_PENDING"
    UPLOAD_PENDING = "UPLOAD_PENDING"
    UPLOADING = "UPLOADING"
    VALIDATING = "VALIDATING"
    AVAILABLE = "AVAILABLE"
    RETRY_REQUIRED = "RETRY_REQUIRED"
    MANUAL_UPLOAD_REQUIRED = "MANUAL_UPLOAD_REQUIRED"
    DISCARDED = "DISCARDED"
    FAILED = "FAILED"


@dataclass
class LocalMedia:
    local_media_id: str
    mission_id: str
    drone_id: str
    media_type: str
    status: str
    file_name: str
    local_path: str
    content_type: str
    file_size: int
    checksum_sha256: str
    captured_at: str
    backend_media_id: str = ""
    error_code: str = ""
    error_message: str = ""


class LocalMediaRepository:
    """Small durable repository for media waiting on operator review/upload."""

    def __init__(self, state_file: Path) -> None:
        self._state_file = state_file
        self._lock = threading.RLock()
        self._items: dict[str, LocalMedia] = {}
        self._load()

    def save(self, media: LocalMedia) -> LocalMedia:
        with self._lock:
            self._items[media.local_media_id] = media
            self._flush()
            return media

    def get(self, local_media_id: str) -> LocalMedia | None:
        with self._lock:
            return self._items.get(local_media_id)

    def list(self, mission_id: str = "") -> list[LocalMedia]:
        with self._lock:
            values = list(self._items.values())
        if mission_id:
            values = [item for item in values if item.mission_id == mission_id]
        return sorted(values, key=lambda item: item.captured_at, reverse=True)

    def _load(self) -> None:
        if not self._state_file.exists():
            return
        try:
            payload = json.loads(self._state_file.read_text(encoding="utf-8"))
            self._items = {item["local_media_id"]: LocalMedia(**item) for item in payload}
        except (OSError, ValueError, TypeError, KeyError):
            self._items = {}

    def _flush(self) -> None:
        self._state_file.parent.mkdir(parents=True, exist_ok=True)
        temporary = self._state_file.with_suffix(".tmp")
        temporary.write_text(
            json.dumps([asdict(item) for item in self._items.values()], indent=2),
            encoding="utf-8",
        )
        temporary.replace(self._state_file)
