#!/usr/bin/env python3
from __future__ import annotations

import argparse
import struct
import threading

from gz.msgs10.image_pb2 import Image, PixelFormatType
from gz.transport13 import Node


def main() -> int:
    parser = argparse.ArgumentParser(description="Inspect one Gazebo thermal image frame without dumping binary pixels.")
    parser.add_argument("topic")
    parser.add_argument("--resolution", type=float, default=0.01, help="Kelvin represented by one L16 unit")
    parser.add_argument("--timeout", type=float, default=8.0)
    args = parser.parse_args()

    received = threading.Event()
    result: dict[str, object] = {}

    def on_frame(message: Image, *_args) -> None:
        if received.is_set():
            return
        format_name = PixelFormatType.Name(message.pixel_format_type)
        result.update(width=message.width, height=message.height, step=message.step, format=format_name)
        if format_name == "L_INT16" and message.data:
            count = len(message.data) // 2
            raw = struct.unpack(f"<{count}H", message.data[:count * 2])
            result.update(
                min_c=min(raw) * args.resolution - 273.15,
                avg_c=(sum(raw) / count) * args.resolution - 273.15,
                max_c=max(raw) * args.resolution - 273.15,
            )
        received.set()

    node = Node()
    if not node.subscribe(Image, args.topic, on_frame):
        raise RuntimeError(f"Could not subscribe to {args.topic}")
    if not received.wait(args.timeout):
        raise TimeoutError(f"No frame received from {args.topic}")

    print(
        f"topic={args.topic} format={result['format']} "
        f"size={result['width']}x{result['height']} step={result['step']}"
    )
    if "min_c" in result:
        print(f"temperature_c min={result['min_c']:.2f} avg={result['avg_c']:.2f} max={result['max_c']:.2f}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
