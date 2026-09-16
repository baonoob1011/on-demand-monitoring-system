from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from queue import Full, Empty, Queue
import shutil
import subprocess
import threading
import time
from typing import Any

try:
    import cv2
    import numpy as np
except ImportError:
    cv2 = None
    np = None


@dataclass(frozen=True)
class RecordingResult:
    mission_id: str
    path: Path
    frames_written: int
    dropped_frames: int
    duration_s: float
    configured_fps: float
    width: int
    height: int


class VideoRecorder:
    def __init__(
        self,
        output_dir: Path,
        fps: float = 15.0,
        queue_size: int = 4,
        ffmpeg_binary: str = "ffmpeg",
    ) -> None:
        self.output_dir = output_dir
        self.fps = max(1.0, fps)
        self.queue_size = max(2, queue_size)
        self.ffmpeg_binary = ffmpeg_binary
        self._lock = threading.Lock()
        self._queue: Queue[Any] | None = None
        self._stop_event: threading.Event | None = None
        self._worker: threading.Thread | None = None
        self._writer: cv2.VideoWriter | None = None
        self._recording = False
        self._mission_id = ""
        self._path: Path | None = None
        self._started_at = 0.0
        self._frames_written = 0
        self._dropped_frames = 0
        self._width = 0
        self._height = 0

    def is_recording(self) -> bool:
        with self._lock:
            return self._recording

    def start_recording(self, mission_id: str) -> Path:
        if cv2 is None or np is None:
            raise RuntimeError("OpenCV is required for MP4 recording. Install drone requirements first.")

        with self._lock:
            if self._recording:
                if self._path is None:
                    raise RuntimeError("Recorder is active without an output path")
                return self._path

            self.output_dir.mkdir(parents=True, exist_ok=True)
            timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
            safe_mission = self._safe_segment(mission_id or "UNASSIGNED")
            self._path = self.output_dir / f"{safe_mission}-{timestamp}.mp4"
            self._queue = Queue(maxsize=self.queue_size)
            self._stop_event = threading.Event()
            self._mission_id = mission_id or "UNASSIGNED"
            self._started_at = time.monotonic()
            self._frames_written = 0
            self._dropped_frames = 0
            self._width = 0
            self._height = 0
            self._recording = True
            self._worker = threading.Thread(
                target=self._worker_loop,
                name="video-recorder",
                daemon=True,
            )
            self._worker.start()
            return self._path

    def submit_frame(self, frame: Any) -> None:
        with self._lock:
            queue = self._queue
            recording = self._recording

        if not recording or queue is None:
            return

        try:
            queue.put_nowait(frame)
            return
        except Full:
            pass

        try:
            queue.get_nowait()
            with self._lock:
                self._dropped_frames += 1
        except Empty:
            pass

        try:
            queue.put_nowait(frame)
        except Full:
            with self._lock:
                self._dropped_frames += 1

    def stop_recording(self) -> RecordingResult | None:
        with self._lock:
            if not self._recording:
                return None
            stop_event = self._stop_event
            worker = self._worker
            mission_id = self._mission_id
            path = self._path

        if stop_event is not None:
            stop_event.set()
        if worker is not None:
            worker.join(timeout=8.0)

        with self._lock:
            if self._writer is not None:
                self._writer.release()
                self._writer = None
            duration_s = max(0.0, time.monotonic() - self._started_at)
            result = RecordingResult(
                mission_id=mission_id,
                path=path if path is not None else self.output_dir / "recording.mp4",
                frames_written=self._frames_written,
                dropped_frames=self._dropped_frames,
                duration_s=duration_s,
                configured_fps=self.fps,
                width=self._width,
                height=self._height,
            )
            self._recording = False
            self._queue = None
            self._stop_event = None
            self._worker = None
            self._path = None

        if result.frames_written > 0:
            self._make_browser_compatible(result.path)
        return result

    def _make_browser_compatible(self, source: Path) -> None:
        """Transcode OpenCV's mp4v output to browser-compatible H.264."""
        ffmpeg = shutil.which(self.ffmpeg_binary)
        if ffmpeg is None:
            raise RuntimeError(
                "FFmpeg is required to finalize browser-compatible MP4 video"
            )

        encoded = source.with_name(f"{source.stem}.browser{source.suffix}")
        command = [
            ffmpeg,
            "-hide_banner",
            "-loglevel",
            "error",
            "-y",
            "-i",
            str(source),
            "-an",
            "-c:v",
            "libx264",
            "-preset",
            "veryfast",
            "-crf",
            "23",
            "-pix_fmt",
            "yuv420p",
            "-movflags",
            "+faststart",
            str(encoded),
        ]
        try:
            subprocess.run(
                command,
                check=True,
                capture_output=True,
                text=True,
                timeout=120,
            )
            if not encoded.is_file() or encoded.stat().st_size == 0:
                raise RuntimeError("FFmpeg did not create a playable MP4 file")
            encoded.replace(source)
        except (OSError, subprocess.SubprocessError) as exc:
            detail = getattr(exc, "stderr", "") or str(exc)
            raise RuntimeError(f"Unable to finalize MP4 video: {detail.strip()}") from exc
        finally:
            encoded.unlink(missing_ok=True)

    def _worker_loop(self) -> None:
        while True:
            with self._lock:
                queue = self._queue
                stop_event = self._stop_event
            if queue is None or stop_event is None:
                break

            if stop_event.is_set() and queue.empty():
                break

            try:
                frame = queue.get(timeout=0.2)
            except Empty:
                continue

            bgr = self._frame_to_bgr(frame)
            if bgr is None:
                with self._lock:
                    self._dropped_frames += 1
                continue

            self._write_frame(bgr)

    def _write_frame(self, bgr: Any) -> None:
        height, width = bgr.shape[:2]
        with self._lock:
            if self._writer is None:
                if self._path is None:
                    return
                fourcc = cv2.VideoWriter_fourcc(*"mp4v")
                self._writer = cv2.VideoWriter(
                    str(self._path),
                    fourcc,
                    self.fps,
                    (width, height),
                )
                self._width = width
                self._height = height
            writer = self._writer

        if writer is None or not writer.isOpened():
            with self._lock:
                self._dropped_frames += 1
            return

        writer.write(bgr)
        with self._lock:
            self._frames_written += 1

    def _frame_to_bgr(self, frame: Any) -> np.ndarray | None:
        width = int(getattr(frame, "width", 0))
        height = int(getattr(frame, "height", 0))
        if width <= 0 or height <= 0:
            return None

        raw = bytes(getattr(frame, "data", b""))
        expected_rgb = width * height * 3
        expected_rgba = width * height * 4

        if len(raw) == expected_rgba:
            rgba = np.frombuffer(raw, dtype=np.uint8).reshape((height, width, 4))
            return cv2.cvtColor(rgba, cv2.COLOR_RGBA2BGR)
        if len(raw) == expected_rgb:
            rgb = np.frombuffer(raw, dtype=np.uint8).reshape((height, width, 3))
            return cv2.cvtColor(rgb, cv2.COLOR_RGB2BGR)
        return None

    def _safe_segment(self, value: str) -> str:
        return "".join(ch if ch.isalnum() or ch in "._-" else "_" for ch in value)
