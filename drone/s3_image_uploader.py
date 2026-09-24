import os
import time
from pathlib import Path

import httpx
from dotenv import load_dotenv
from watchdog.events import FileSystemEventHandler
from watchdog.observers import Observer


load_dotenv(Path(__file__).resolve().parents[1] / "ondemandmonitoring" / ".env", override=True)

PICTURES_DIR = Path(os.getenv("GAZEBO_PICTURES_DIR", "/home/acer/.gz/gui/pictures")).expanduser()
BACKEND_BASE_URL = os.getenv("BACKEND_BASE_URL", "http://localhost:8080").rstrip("/")
FLIGHT_CONTROL_API_PORT = int(os.getenv("FLIGHT_CONTROL_API_PORT", "8090"))
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
    def upload(self, path: Path, retry: bool = False) -> bool:
        if path.suffix.lower() not in (".png", ".jpg", ".jpeg"):
            return True

        if not wait_until_file_ready(path):
            print(f"[BACKEND] File not ready, skipped: {path}")
            return False

        try:
            status = httpx.get(
                f"http://127.0.0.1:{FLIGHT_CONTROL_API_PORT}/api/control/status",
                timeout=2.0,
            )
            status.raise_for_status()
            session = status.json()
            drone_code = str(session.get("deviceCode") or "").strip()
            if not session.get("missionId") or not drone_code or len(drone_code) > 50:
                print("[BACKEND] No verified mission binding; screenshot remains local")
                return False
            with path.open("rb") as image_file:
                files = {
                    "file": (
                        path.name,
                        image_file,
                        content_type_for(path),
                    )
                }
                response = httpx.post(
                    f"{BACKEND_BASE_URL}/api/devices/{drone_code}/images",
                    files=files,
                    timeout=30.0,
                )
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
    print("Drone identity: verified mission binding in Flight Controller")
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
