import os
import time
from pathlib import Path

import httpx
from dotenv import load_dotenv
from watchdog.events import FileSystemEventHandler
from watchdog.observers import Observer


load_dotenv()

PICTURES_DIR = Path(os.getenv("GAZEBO_PICTURES_DIR", "/home/acer/.gz/gui/pictures")).expanduser()
BACKEND_BASE_URL = os.getenv("BACKEND_BASE_URL", "http://localhost:8080").rstrip("/")
DEVICE_CODE = os.getenv("DEVICE_CODE", "DRONE-01")
RETRY_SECONDS = float(os.getenv("IMAGE_UPLOAD_RETRY_SECONDS", "5"))


def wait_until_file_ready(path: Path, timeout_seconds: float = 10.0) -> bool:
    deadline = time.time() + timeout_seconds
    previous_size = -1

    while time.time() < deadline:
        if not path.exists() or not path.is_file():
            time.sleep(0.2)
            continue

        current_size = path.stat().st_size
        if current_size > 0 and current_size == previous_size:
            return True

        previous_size = current_size
        time.sleep(0.3)

    return False


def content_type_for(path: Path) -> str:
    if path.suffix.lower() == ".png":
        return "image/png"
    if path.suffix.lower() in (".jpg", ".jpeg"):
        return "image/jpeg"
    return "application/octet-stream"


class BackendImageUploader:
    def __init__(self) -> None:
        self.url = f"{BACKEND_BASE_URL}/api/devices/{DEVICE_CODE}/images"

    def upload(self, path: Path, retry: bool = False) -> bool:
        if path.suffix.lower() not in (".png", ".jpg", ".jpeg"):
            return True

        if not wait_until_file_ready(path):
            print(f"[BACKEND] File not ready, skipped: {path}")
            return False

        try:
            with path.open("rb") as image_file:
                files = {
                    "file": (
                        path.name,
                        image_file,
                        content_type_for(path),
                    )
                }
                response = httpx.post(self.url, files=files, timeout=30.0)
        except httpx.ConnectError:
            print("[BACKEND] Unavailable - image upload retry later")
            return False
        except httpx.TimeoutException:
            print("[BACKEND] Timeout while uploading image")
            return False
        except httpx.HTTPError as exc:
            print(f"[BACKEND] Network error while uploading image - {exc}")
            return False

        if 200 <= response.status_code < 300:
            print(f"[BACKEND] Image sent to Java S3 uploader - HTTP {response.status_code}")
            return True

        print(f"[BACKEND] Image upload failed - HTTP {response.status_code}")
        print(response.text)

        if retry:
            time.sleep(RETRY_SECONDS)
            return self.upload(path, retry=False)

        return False


class ScreenshotHandler(FileSystemEventHandler):
    def __init__(self, uploader: BackendImageUploader) -> None:
        self.uploader = uploader
        self.uploaded: set[Path] = set()

    def handle(self, raw_path: str) -> None:
        path = Path(raw_path)
        if path in self.uploaded:
            return
        if self.uploader.upload(path, retry=True):
            self.uploaded.add(path)

    def on_created(self, event) -> None:
        if not event.is_directory:
            self.handle(event.src_path)

    def on_moved(self, event) -> None:
        if not event.is_directory:
            self.handle(event.dest_path)


def main() -> None:
    print("========================================")
    print(" Gazebo Screenshot Backend Uploader")
    print("========================================")
    print(f"Watch dir: {PICTURES_DIR}")
    print(f"Backend: {BACKEND_BASE_URL}")
    print(f"Device: {DEVICE_CODE}")
    print()

    if not PICTURES_DIR.exists():
        PICTURES_DIR.mkdir(parents=True, exist_ok=True)

    uploader = BackendImageUploader()
    handler = ScreenshotHandler(uploader)

    existing_images = sorted(
        PICTURES_DIR.glob("*"),
        key=lambda item: item.stat().st_mtime if item.exists() else 0,
    )
    for image_path in existing_images:
        handler.handle(str(image_path))

    observer = Observer()
    observer.schedule(handler, str(PICTURES_DIR), recursive=False)
    observer.start()

    print("[BACKEND] Watching for new Gazebo screenshots...")
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print("\n[SHUTDOWN] Stopped by user")
    finally:
        observer.stop()
        observer.join()


if __name__ == "__main__":
    main()
