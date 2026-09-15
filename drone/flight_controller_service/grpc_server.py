from __future__ import annotations

import asyncio
from dataclasses import dataclass
import hmac
from pathlib import Path
from typing import Awaitable, Callable

import grpc

from generated.flight_controller.v1 import media_pb2, media_pb2_grpc

from .local_media import LocalMedia
from .media_coordinator import MediaCoordinator


TERMINAL_STATES = {
    media_pb2.COMMAND_STATE_SUCCEEDED,
    media_pb2.COMMAND_STATE_FAILED,
}


@dataclass
class CommandOperation:
    update: media_pb2.MediaCommandUpdate
    queue: asyncio.Queue[media_pb2.MediaCommandUpdate]


class MediaGrpcService(media_pb2_grpc.MediaServiceServicer):
    def __init__(self, coordinator: MediaCoordinator, auth_token: str = "") -> None:
        self._coordinator = coordinator
        self._auth_token = auth_token
        self._operations: dict[str, CommandOperation] = {}
        self._lock = asyncio.Lock()

    async def CaptureImage(self, request, context):
        await self._authorize(context)
        return await self._execute(
            request.command_id,
            lambda: self._coordinator.capture_image(request.mission_id, request.drone_id),
        )

    async def StartVideo(self, request, context):
        await self._authorize(context)

        async def action():
            self._coordinator.start_video(request.mission_id, request.drone_id)
            return None

        return await self._execute(request.command_id, action)

    async def StopVideo(self, request, context):
        await self._authorize(context)

        async def action():
            return await asyncio.to_thread(self._coordinator.stop_video)

        return await self._execute(request.command_id, action)

    async def ListMedia(self, request, context):
        await self._authorize(context)
        media = await self._coordinator.refresh_media(request.mission_id)
        return media_pb2.ListMediaResponse(
            media=[to_proto(item) for item in media]
        )

    async def DiscardMedia(self, request, context):
        await self._authorize(context)

        async def action():
            return await asyncio.to_thread(self._coordinator.discard, request.local_media_id)

        return await self._execute(request.command_id, action)

    async def UploadMedia(self, request, context):
        await self._authorize(context)
        operation, created = await self._accept(request.command_id)
        if created:
            asyncio.create_task(self._run_upload(request.command_id, request.local_media_id))
        return ack_from_update(operation.update)

    async def WatchCommand(self, request, context):
        await self._authorize(context)
        operation = self._operations.get(request.command_id)
        if operation is None:
            await context.abort(grpc.StatusCode.NOT_FOUND, "Command does not exist")
        yield operation.update
        while operation.update.state not in TERMINAL_STATES:
            yield await operation.queue.get()

    async def _execute(
        self, command_id: str, action: Callable[[], Awaitable[LocalMedia | None]]
    ) -> media_pb2.MediaCommandAck:
        operation, created = await self._accept(command_id)
        if not created:
            return ack_from_update(operation.update)
        await self._publish(command_id, media_pb2.COMMAND_STATE_RUNNING)
        try:
            media = await action()
            await self._publish(command_id, media_pb2.COMMAND_STATE_SUCCEEDED, media)
        except (RuntimeError, KeyError, OSError) as exc:
            await self._publish(
                command_id,
                media_pb2.COMMAND_STATE_FAILED,
                error_code="MEDIA_COMMAND_FAILED",
                error_message=str(exc),
            )
        return ack_from_update(self._operations[command_id].update)

    async def _run_upload(self, command_id: str, local_media_id: str) -> None:
        await self._publish(command_id, media_pb2.COMMAND_STATE_RUNNING)

        async def on_update(media: LocalMedia) -> None:
            await self._publish(command_id, media_pb2.COMMAND_STATE_RUNNING, media)

        try:
            media = await self._coordinator.upload(local_media_id, on_update)
            state = (
                media_pb2.COMMAND_STATE_FAILED
                if media.status in {"FAILED", "MANUAL_UPLOAD_REQUIRED"}
                else media_pb2.COMMAND_STATE_SUCCEEDED
            )
            await self._publish(command_id, state, media)
        except Exception as exc:
            await self._publish(
                command_id,
                media_pb2.COMMAND_STATE_FAILED,
                error_code=getattr(exc, "code", "MEDIA_UPLOAD_FAILED"),
                error_message=str(exc),
            )

    async def _accept(self, command_id: str) -> tuple[CommandOperation, bool]:
        if not command_id.strip():
            raise ValueError("command_id is required")
        async with self._lock:
            if len(self._operations) >= 2_000:
                completed = [
                    key for key, value in self._operations.items()
                    if value.update.state in TERMINAL_STATES
                ]
                for key in completed[:1_000]:
                    self._operations.pop(key, None)
            existing = self._operations.get(command_id)
            if existing is not None:
                return existing, False
            update = media_pb2.MediaCommandUpdate(
                command_id=command_id, state=media_pb2.COMMAND_STATE_ACCEPTED
            )
            operation = CommandOperation(update=update, queue=asyncio.Queue())
            self._operations[command_id] = operation
            return operation, True

    async def _publish(
        self,
        command_id: str,
        state: int,
        media: LocalMedia | None = None,
        error_code: str = "",
        error_message: str = "",
    ) -> None:
        operation = self._operations[command_id]
        update = media_pb2.MediaCommandUpdate(
            command_id=command_id,
            state=state,
            error_code=error_code,
            error_message=error_message,
        )
        if media is not None:
            update.media.CopyFrom(to_proto(media))
        operation.update = update
        await operation.queue.put(update)

    async def _authorize(self, context) -> None:
        if not self._auth_token:
            return
        metadata = dict(context.invocation_metadata())
        if not hmac.compare_digest(
            metadata.get("authorization", ""), f"Bearer {self._auth_token}"
        ):
            await context.abort(grpc.StatusCode.UNAUTHENTICATED, "Invalid Flight Controller token")


async def start_media_grpc_server(
    coordinator: MediaCoordinator,
    bind_address: str,
    auth_token: str = "",
    certificate_path: str = "",
    private_key_path: str = "",
) -> grpc.aio.Server:
    server = grpc.aio.server(options=(("grpc.keepalive_time_ms", 20_000),))
    media_pb2_grpc.add_MediaServiceServicer_to_server(
        MediaGrpcService(coordinator, auth_token), server
    )
    if certificate_path and private_key_path:
        certificate = Path(certificate_path).read_bytes()
        private_key = Path(private_key_path).read_bytes()
        credentials = grpc.ssl_server_credentials(((private_key, certificate),))
        bound_port = server.add_secure_port(bind_address, credentials)
    else:
        bound_port = server.add_insecure_port(bind_address)
    if bound_port == 0:
        raise RuntimeError(f"Cannot bind Flight Controller gRPC server to {bind_address}")
    await server.start()
    return server


def ack_from_update(update: media_pb2.MediaCommandUpdate) -> media_pb2.MediaCommandAck:
    return media_pb2.MediaCommandAck(
        command_id=update.command_id,
        state=update.state,
        media=update.media,
        error_code=update.error_code,
        error_message=update.error_message,
    )


def to_proto(media: LocalMedia | None) -> media_pb2.LocalMedia:
    if media is None:
        return media_pb2.LocalMedia()
    media_type = (
        media_pb2.MEDIA_TYPE_VIDEO if media.media_type == "VIDEO" else media_pb2.MEDIA_TYPE_IMAGE
    )
    status = getattr(
        media_pb2,
        f"LOCAL_MEDIA_STATUS_{media.status}",
        media_pb2.LOCAL_MEDIA_STATUS_UNSPECIFIED,
    )
    return media_pb2.LocalMedia(
        local_media_id=media.local_media_id,
        mission_id=media.mission_id,
        drone_id=media.drone_id,
        media_type=media_type,
        status=status,
        file_name=media.file_name,
        local_path=media.local_path,
        content_type=media.content_type,
        file_size=media.file_size,
        checksum_sha256=media.checksum_sha256,
        captured_at=media.captured_at,
        backend_media_id=media.backend_media_id,
        error_code=media.error_code,
        error_message=media.error_message,
    )
