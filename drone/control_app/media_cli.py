from __future__ import annotations

import argparse
import asyncio
import os
from pathlib import Path
import sys
import uuid

import grpc

DRONE_DIR = Path(__file__).resolve().parents[1]
GENERATED_DIR = DRONE_DIR / "generated"
sys.path.insert(0, str(DRONE_DIR))
sys.path.insert(0, str(GENERATED_DIR))

from generated.flight_controller.v1 import media_pb2, media_pb2_grpc


def command_id() -> str:
    return str(uuid.uuid4())


async def run(args: argparse.Namespace) -> None:
    token = os.getenv("FLIGHT_CONTROLLER_GRPC_TOKEN", "")
    metadata = (("authorization", f"Bearer {token}"),) if token else ()
    ca_path = os.getenv("FLIGHT_CONTROLLER_GRPC_CA", "")
    if ca_path:
        credentials = grpc.ssl_channel_credentials(Path(ca_path).read_bytes())
        channel = grpc.aio.secure_channel(args.address, credentials)
    else:
        channel = grpc.aio.insecure_channel(args.address)

    async with channel:
        client = media_pb2_grpc.MediaServiceStub(channel)
        if args.action == "capture":
            reply = await client.CaptureImage(
                media_pb2.CaptureImageRequest(
                    command_id=command_id(), mission_id=args.mission, drone_id=args.drone
                ), metadata=metadata
            )
            print(reply)
        elif args.action == "start-video":
            reply = await client.StartVideo(
                media_pb2.StartVideoRequest(
                    command_id=command_id(), mission_id=args.mission, drone_id=args.drone
                ), metadata=metadata
            )
            print(reply)
        elif args.action == "stop-video":
            reply = await client.StopVideo(
                media_pb2.StopVideoRequest(command_id=command_id()), metadata=metadata
            )
            print(reply)
        elif args.action == "list":
            reply = await client.ListMedia(
                media_pb2.ListMediaRequest(mission_id=args.mission), metadata=metadata
            )
            print(reply)
        elif args.action == "discard":
            reply = await client.DiscardMedia(
                media_pb2.DiscardMediaRequest(
                    command_id=command_id(), local_media_id=args.media_id
                ), metadata=metadata
            )
            print(reply)
        elif args.action == "upload":
            cid = command_id()
            reply = await client.UploadMedia(
                media_pb2.UploadMediaRequest(command_id=cid, local_media_id=args.media_id),
                metadata=metadata,
            )
            print(reply)
            async for update in client.WatchCommand(
                media_pb2.WatchCommandRequest(command_id=cid), metadata=metadata
            ):
                print(update)


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description="Flight Controller media operator client")
    result.add_argument(
        "action", choices=("capture", "start-video", "stop-video", "list", "discard", "upload")
    )
    result.add_argument("--address", default=os.getenv("FLIGHT_CONTROLLER_GRPC_ADDRESS", "localhost:50051"))
    result.add_argument("--mission", default=os.getenv("MISSION_ID", ""))
    result.add_argument("--drone", default=os.getenv("DRONE_ID", ""))
    result.add_argument("--media-id", default="")
    return result


if __name__ == "__main__":
    asyncio.run(run(parser().parse_args()))
