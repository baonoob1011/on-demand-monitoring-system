from datetime import datetime, timezone
from pathlib import Path
import subprocess

import httpx

from video.video_recorder import RecordingResult


class BackendUrlResolver:
    def __init__(self, configured_base_url: str) -> None:
        self.configured_base_url = configured_base_url.rstrip("/")

    def candidates(self) -> list[str]:
        configured = self.configured_base_url
        candidates = [configured]

        if configured in {"http://localhost:8080", "http://127.0.0.1:8080"}:
            try:
                route = subprocess.check_output(
                    ["ip", "-4", "route", "show", "default"],
                    text=True,
                    timeout=1.0,
                )
                parts = route.split()
                if "via" in parts:
                    candidates.append(f"http://{parts[parts.index('via') + 1]}:8080")
            except (OSError, subprocess.SubprocessError, IndexError):
                pass
            candidates.append("http://host.docker.internal:8080")
            try:
                output = subprocess.check_output(
                    ["sh", "-lc", "awk '/^nameserver / {print $2; exit}' /etc/resolv.conf"],
                    text=True,
                    timeout=1.0,
                ).strip()
                if output:
                    candidates.append(f"http://{output}:8080")
            except (OSError, subprocess.SubprocessError):
                pass

        deduped = []
        for url in candidates:
            if url and url not in deduped:
                deduped.append(url)
        return deduped

    def reachable(self, timeout_s: float = 0.8) -> tuple[bool, str]:
        for base_url in self.candidates():
            try:
                with httpx.Client(timeout=timeout_s) as client:
                    response = client.get(f"{base_url}/simulation-viewer/index.html")
                if response.status_code < 500:
                    return True, base_url
            except httpx.HTTPError:
                continue
        return False, self.configured_base_url


class MediaUploader:
    def __init__(
        self,
        backend_urls: BackendUrlResolver,
        drone_id: str,
        default_mission_id: str,
        video_upload_timeout_s: float,
    ) -> None:
        self.backend_urls = backend_urls
        self.drone_id = drone_id
        self.default_mission_id = default_mission_id
        self.video_upload_timeout_s = video_upload_timeout_s

    async def upload_image(self, jpeg: bytes) -> None:
        captured_at = utc_timestamp()
        timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        filename = f"{self.drone_id}-downward-{timestamp}.jpg"
        data = {
            "droneId": self.drone_id,
            "capturedAt": captured_at,
        }

        response = None
        last_error = None
        async with httpx.AsyncClient(timeout=30.0) as client:
            for base_url in self.backend_urls.candidates():
                upload_url = f"{base_url}/api/missions/{self.default_mission_id}/images"
                files = {"image": (filename, jpeg, "image/jpeg")}
                try:
                    response = await client.post(upload_url, data=data, files=files)
                    break
                except httpx.HTTPError as exc:
                    if not is_connection_level_http_error(exc):
                        raise
                    last_error = f"{base_url}: {exc}"

        if response is None:
            print(f"[CAMERA] Backend unavailable ({last_error or 'no route worked'})")
            return

        if 200 <= response.status_code < 300:
            print_media_upload_success("[CAMERA] Image uploaded successfully", response)
            return

        print(f"[CAMERA] Upload failed - HTTP {response.status_code}")
        print(response.text[:500])

    async def upload_video(self, recording: RecordingResult) -> None:
        path = recording.path
        if not path.exists() or path.stat().st_size <= 0:
            print(f"[VIDEO] Upload skipped - missing or empty file: {path}", flush=True)
            return

        size = path.stat().st_size
        data = {
            "droneId": self.drone_id,
            "capturedAt": utc_timestamp(),
            "mediaType": "VIDEO",
        }
        print(
            f"[VIDEO] Upload started mission={recording.mission_id} size={size} path={path}",
            flush=True,
        )

        response = None
        last_error = None
        async with httpx.AsyncClient(timeout=self.video_upload_timeout_s) as client:
            for base_url in self.backend_urls.candidates():
                upload_url = f"{base_url}/api/missions/{recording.mission_id}/media"
                try:
                    response = await post_video_file(client, upload_url, data, path)
                    break
                except httpx.HTTPError as exc:
                    if not is_connection_level_http_error(exc):
                        raise
                    last_error = f"{base_url}: {exc}"

        if response is None:
            print(f"[VIDEO] Backend unavailable ({last_error or 'no route worked'})", flush=True)
            return

        if 200 <= response.status_code < 300:
            print_media_upload_success("[VIDEO] Upload success", response, flush=True)
            return

        print(f"[VIDEO] Upload failed HTTP {response.status_code}", flush=True)
        print(response.text[:500], flush=True)


async def post_video_file(
    client: httpx.AsyncClient,
    upload_url: str,
    data: dict[str, str],
    path: Path,
) -> httpx.Response:
    with path.open("rb") as video_file:
        files = {"file": (path.name, video_file, "video/mp4")}
        return await client.post(upload_url, data=data, files=files)


def utc_timestamp() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def is_connection_level_http_error(exc: httpx.HTTPError) -> bool:
    return isinstance(
        exc,
        (
            httpx.ConnectError,
            httpx.ConnectTimeout,
            httpx.NetworkError,
            httpx.PoolTimeout,
        ),
    )


def print_media_upload_success(prefix: str, response: httpx.Response, flush: bool = False) -> None:
    try:
        payload = response.json()
    except ValueError:
        print(prefix, flush=flush)
        return

    media = payload.get("data") or {}
    storage_provider = media.get("storageProvider", "UNKNOWN")
    if storage_provider == "S3":
        print(f"{prefix} provider=S3 key={media.get('s3Key')}", flush=flush)
    elif storage_provider == "LOCAL":
        print(f"{prefix} provider=LOCAL path={media.get('s3Url')}", flush=flush)
    else:
        print(f"{prefix} provider={storage_provider}", flush=flush)
