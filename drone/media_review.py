"""Local capture review and direct-to-S3 transfer for the packaged controller."""

from datetime import datetime, timezone
from pathlib import Path
from threading import RLock
from urllib.parse import urlparse
import hashlib
import json
import os
import uuid

import httpx


class LocalMediaLibrary:
    def __init__(self, root: Path, mission_id: str, drone_code: str) -> None:
        self.root = root
        self.mission_id = mission_id
        self.drone_code = drone_code
        self.lock = RLock()
        self.root.mkdir(parents=True, exist_ok=True)
        self.manifest = self.root / "media-index.json"
        self.items = self._load()

    def _load(self) -> dict[str, dict]:
        if not self.manifest.exists():
            return {}
        with self.manifest.open("r", encoding="utf-8") as source:
            data = json.load(source)
        return {item["localMediaId"]: item for item in data if Path(item["localPath"]).is_file()}

    def _persist(self) -> None:
        temp = self.manifest.with_suffix(".tmp")
        with temp.open("w", encoding="utf-8") as output:
            json.dump(list(self.items.values()), output, indent=2)
        os.replace(temp, self.manifest)

    def capture_image(self, jpeg: bytes) -> dict:
        if not jpeg.startswith(b"\xff\xd8\xff"):
            raise ValueError("Camera did not return a JPEG")
        local_id = str(uuid.uuid4())
        path = self.root / f"{local_id}.jpg"
        path.write_bytes(jpeg)
        return self._register(local_id, path, "IMAGE", "image/jpeg")

    def register_video(self, path: Path) -> dict:
        if not path.is_file() or path.stat().st_size == 0:
            raise ValueError("Recording is empty")
        return self._register(str(uuid.uuid4()), path, "VIDEO", "video/mp4")

    def _register(self, local_id: str, path: Path, media_type: str, content_type: str) -> dict:
        digest = hashlib.sha256()
        with path.open("rb") as source:
            for chunk in iter(lambda: source.read(1024 * 1024), b""):
                digest.update(chunk)
        item = {
            "localMediaId": local_id, "missionId": self.mission_id,
            "droneCode": self.drone_code, "mediaType": media_type,
            "fileName": path.name, "localPath": str(path.resolve()),
            "contentType": content_type, "fileSize": path.stat().st_size,
            "checksumSha256": digest.hexdigest(),
            "capturedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
            "status": "REVIEW_PENDING",
        }
        with self.lock:
            self.items[local_id] = item
            self._persist()
        return item.copy()

    def list_items(self) -> list[dict]:
        with self.lock:
            return [item.copy() for item in self.items.values()]

    def get(self, local_id: str) -> dict:
        with self.lock:
            if local_id not in self.items:
                raise KeyError(local_id)
            return self.items[local_id].copy()

    def discard(self, local_id: str) -> None:
        with self.lock:
            item = self.get(local_id)
            if item["status"] not in {"REVIEW_PENDING", "UPLOAD_FAILED"}:
                raise ValueError("Media cannot be discarded during upload")
            Path(item["localPath"]).unlink(missing_ok=True)
            del self.items[local_id]
            self._persist()

    def transfer(self, local_id: str, plan: dict) -> dict:
        """Transfer using backend-signed URLs; the caller completes/acknowledges backend state."""
        item = self.get(local_id)
        path = Path(item["localPath"])
        if not path.is_file() or path.stat().st_size != item["fileSize"]:
            raise ValueError("Local capture is missing or changed")
        method = plan.get("uploadMethod")
        with self.lock:
            self.items[local_id]["status"] = "UPLOADING"
            self._persist()
        try:
            if method == "PUT":
                self._put(path, plan["uploadUrl"], plan.get("uploadHeaders", {}))
                result = {"parts": []}
            elif method == "MULTIPART":
                result = self._multipart(path, plan)
            else:
                raise ValueError("Unsupported upload method")
        except Exception:
            with self.lock:
                self.items[local_id]["status"] = "UPLOAD_FAILED"
                self._persist()
            raise
        with self.lock:
            self.items[local_id]["status"] = "VALIDATING"
            self.items[local_id]["backendMediaId"] = plan["mediaId"]
            self._persist()
        return result

    def _multipart(self, path: Path, plan: dict) -> dict:
        part_urls = plan["partUrls"]
        if len(part_urls) != plan["partCount"]:
            raise ValueError("Incomplete multipart plan")
        parts = []
        with path.open("rb") as source:
            for index, entry in enumerate(part_urls, start=1):
                chunk = source.read(plan["partSizeBytes"])
                if not chunk or entry["partNumber"] != index:
                    raise ValueError("Invalid multipart plan")
                response = self._put_bytes(chunk, entry["uploadUrl"], entry.get("uploadHeaders", {}))
                etag = response.headers.get("ETag")
                if not etag:
                    raise ValueError("S3 did not return an ETag")
                parts.append({"partNumber": index, "eTag": etag})
        return {"parts": parts}

    def _put(self, path: Path, url: str, headers: dict) -> httpx.Response:
        return self._put_bytes(path.read_bytes(), url, headers)

    def _put_bytes(self, body, url: str, headers: dict) -> httpx.Response:
        parsed = urlparse(url)
        if parsed.scheme != "https" or not parsed.hostname or not parsed.hostname.endswith(".amazonaws.com"):
            raise ValueError("Upload URL must be a presigned AWS HTTPS endpoint")
        flat_headers = {key: values[0] for key, values in headers.items() if values}
        with httpx.Client(timeout=120.0, follow_redirects=False) as client:
            response = client.put(url, content=body, headers=flat_headers)
            response.raise_for_status()
            return response
