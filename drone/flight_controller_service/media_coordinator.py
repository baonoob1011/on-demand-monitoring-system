from __future__ import annotations

import asyncio
from datetime import datetime, timezone
import hashlib
from pathlib import Path
from typing import Awaitable, Callable
import uuid
import httpx

from video.video_recorder import RecordingResult, VideoRecorder

from .backend_client import BackendContractError, MediaBackendClient
from .local_media import LocalMedia, LocalMediaRepository, LocalMediaStatus


class MediaCoordinator:
    def __init__(
        self,
        capture_jpeg: Callable[[], bytes | None],
        video_recorder: VideoRecorder,
        repository: LocalMediaRepository,
        backend: MediaBackendClient,
        output_dir: Path,
    ) -> None:
        self._capture_jpeg = capture_jpeg
        self._video_recorder = video_recorder
        self._repository = repository
        self._backend = backend
        self._output_dir = output_dir
        self._recording_drone_id = ""

    async def capture_image(self, mission_id: str, drone_id: str) -> LocalMedia:
        content = await asyncio.to_thread(self._capture_jpeg)
        if not content:
            raise RuntimeError("No camera frame is available")
        now = datetime.now(timezone.utc)
        local_id = str(uuid.uuid4())
        path = self._output_dir / mission_id / f"{local_id}.jpg"
        await asyncio.to_thread(self._write_bytes, path, content)
        media = self._build_media(local_id, mission_id, drone_id, "IMAGE", path, "image/jpeg", now)
        return self._repository.save(media)

    def start_video(self, mission_id: str, drone_id: str) -> Path:
        self._recording_drone_id = drone_id
        return self._video_recorder.start_recording(mission_id)

    def stop_video(self) -> LocalMedia:
        result = self._video_recorder.stop_recording()
        if result is None or result.frames_written <= 0:
            raise RuntimeError("No active video recording with captured frames")
        media = self._media_from_recording(result, self._recording_drone_id)
        self._recording_drone_id = ""
        return self._repository.save(media)

    def list_media(self, mission_id: str = "") -> list[LocalMedia]:
        return self._repository.list(mission_id)

    def is_recording(self) -> bool:
        return self._video_recorder.is_recording()

    def preview_range(
        self, local_media_id: str, offset: int = 0, length: int = 0
    ) -> tuple[LocalMedia, Path, int, int]:
        media = self.get(local_media_id)
        if media.status == LocalMediaStatus.DISCARDED.value:
            raise RuntimeError("Discarded media cannot be previewed")

        path = Path(media.local_path)
        if not path.is_file():
            raise FileNotFoundError("Local media file no longer exists")

        total_size = path.stat().st_size
        if offset < 0 or offset > total_size:
            raise ValueError("Preview offset is outside the media file")
        if length < 0:
            raise ValueError("Preview length cannot be negative")

        available = total_size - offset
        selected_length = available if length == 0 else min(length, available)
        return media, path, total_size, selected_length

    async def refresh_media(
        self, mission_id: str = "", operator_token: str = ""
    ) -> list[LocalMedia]:
        media_items = self._repository.list(mission_id)
        for media in media_items:
            if not media.backend_media_id or media.status == LocalMediaStatus.DISCARDED.value:
                continue
            try:
                response = await self._backend.status(media.backend_media_id, operator_token)
            except (httpx.HTTPError, OSError, BackendContractError):
                continue
            media.status = response.get("status", media.status)
            if media.status == LocalMediaStatus.AVAILABLE.value:
                media.error_code = ""
                media.error_message = ""
            self._repository.save(media)
        return self._repository.list(mission_id)

    def get(self, local_media_id: str) -> LocalMedia:
        media = self._repository.get(local_media_id)
        if media is None:
            raise KeyError("Local media does not exist")
        return media

    def discard(self, local_media_id: str) -> LocalMedia:
        media = self.get(local_media_id)
        if media.status in {LocalMediaStatus.UPLOADING.value, LocalMediaStatus.VALIDATING.value}:
            raise RuntimeError("Media cannot be discarded while upload is active")
        path = Path(media.local_path)
        path.unlink(missing_ok=True)
        media.status = LocalMediaStatus.DISCARDED.value
        return self._repository.save(media)

    async def upload(
        self,
        local_media_id: str,
        on_update: Callable[[LocalMedia], Awaitable[None]],
        operator_token: str = "",
    ) -> LocalMedia:
        media = self.get(local_media_id)
        if media.status == LocalMediaStatus.DISCARDED.value:
            raise RuntimeError("Discarded media cannot be uploaded")
        path = Path(media.local_path)
        if not path.is_file():
            raise RuntimeError("Local media file no longer exists")

        prepared = await self._backend.prepare(
            media.mission_id, self._metadata(media), operator_token
        )
        media.backend_media_id = prepared["mediaId"]
        media.error_code = ""
        media.error_message = ""
        if (
            prepared.get("status") == LocalMediaStatus.AVAILABLE.value
            and not prepared.get("uploadUrl")
        ):
            media.status = LocalMediaStatus.AVAILABLE.value
            self._repository.save(media)
            await on_update(media)
            return media
        if (
            prepared.get("status") == LocalMediaStatus.MANUAL_UPLOAD_REQUIRED.value
            and not prepared.get("uploadUrl")
        ):
            prepared = await self._backend.prepare_manual_upload(
                media.backend_media_id, operator_token
            )
        media.status = LocalMediaStatus.UPLOAD_PENDING.value
        self._repository.save(media)
        await on_update(media)

        while prepared.get("uploadUrl"):
            attempt_id = prepared["attemptId"]
            try:
                media.status = LocalMediaStatus.UPLOADING.value
                self._repository.save(media)
                await on_update(media)
                await self._backend.upload(prepared["uploadUrl"], prepared.get("requiredHeaders", {}), path)
                await self._backend.mark_uploaded(
                    media.backend_media_id, attempt_id, operator_token
                )
                media.status = LocalMediaStatus.VALIDATING.value
                self._repository.save(media)
                await on_update(media)
                for _ in range(30):
                    await asyncio.sleep(2.0)
                    status = await self._backend.status(
                        media.backend_media_id, operator_token
                    )
                    media.status = status.get("status", media.status)
                    self._repository.save(media)
                    await on_update(media)
                    if media.status == LocalMediaStatus.AVAILABLE.value:
                        media.error_code = ""
                        media.error_message = ""
                        self._repository.save(media)
                        return media
                    if media.status == LocalMediaStatus.RETRY_REQUIRED.value:
                        prepared = await self._backend.retry(
                            media.backend_media_id, operator_token
                        )
                        break
                    if media.status == LocalMediaStatus.MANUAL_UPLOAD_REQUIRED.value:
                        return media
                else:
                    return media
            except (httpx.HTTPError, OSError, BackendContractError) as exc:
                code = getattr(exc, "code", "UPLOAD_FAILED")
                prepared = await self._backend.report_failure(
                    media.backend_media_id,
                    attempt_id,
                    code,
                    str(exc),
                    operator_token,
                )
                media.status = prepared.get("status", LocalMediaStatus.RETRY_REQUIRED.value)
                media.error_code = code
                media.error_message = str(exc)
                self._repository.save(media)
                await on_update(media)

        media.status = prepared.get("status", LocalMediaStatus.MANUAL_UPLOAD_REQUIRED.value)
        if media.status == LocalMediaStatus.AVAILABLE.value:
            media.error_code = ""
            media.error_message = ""
        self._repository.save(media)
        await on_update(media)
        return media

    def _media_from_recording(self, result: RecordingResult, drone_id: str) -> LocalMedia:
        local_id = str(uuid.uuid4())
        return self._build_media(
            local_id,
            result.mission_id,
            drone_id,
            "VIDEO",
            result.path,
            "video/mp4",
            datetime.now(timezone.utc),
        )

    def _build_media(
        self,
        local_id: str,
        mission_id: str,
        drone_id: str,
        media_type: str,
        path: Path,
        content_type: str,
        captured_at: datetime,
    ) -> LocalMedia:
        return LocalMedia(
            local_media_id=local_id,
            mission_id=mission_id,
            drone_id=drone_id,
            media_type=media_type,
            status=LocalMediaStatus.REVIEW_PENDING.value,
            file_name=path.name,
            local_path=str(path),
            content_type=content_type,
            file_size=path.stat().st_size,
            checksum_sha256=self._sha256(path),
            captured_at=captured_at.isoformat().replace("+00:00", "Z"),
        )

    def _metadata(self, media: LocalMedia) -> dict[str, object]:
        return {
            "droneId": media.drone_id,
            "localMediaId": media.local_media_id,
            "mediaType": media.media_type,
            "fileName": media.file_name,
            "contentType": media.content_type,
            "fileSize": media.file_size,
            "checksumSha256": media.checksum_sha256,
            "capturedAt": media.captured_at,
        }

    @staticmethod
    def _write_bytes(path: Path, content: bytes) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(content)

    @staticmethod
    def _sha256(path: Path) -> str:
        digest = hashlib.sha256()
        with path.open("rb") as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(chunk)
        return digest.hexdigest()
