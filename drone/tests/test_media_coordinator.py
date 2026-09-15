import asyncio
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import AsyncMock, Mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from flight_controller_service.local_media import LocalMediaRepository, LocalMediaStatus
from flight_controller_service.media_coordinator import MediaCoordinator


class MediaCoordinatorTest(unittest.TestCase):
    def test_capture_is_durable_and_requires_separate_upload(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            repository = LocalMediaRepository(root / "state.json")
            coordinator = MediaCoordinator(
                lambda: b"\xff\xd8\xfftest-jpeg",
                Mock(),
                repository,
                Mock(),
                root / "media",
            )

            media = asyncio.run(coordinator.capture_image("mission-1", "DRONE-01"))

            self.assertEqual(LocalMediaStatus.REVIEW_PENDING.value, media.status)
            self.assertTrue(Path(media.local_path).is_file())
            restored = LocalMediaRepository(root / "state.json").get(media.local_media_id)
            self.assertIsNotNone(restored)
            self.assertEqual(media.checksum_sha256, restored.checksum_sha256)

    def test_discard_removes_bytes_and_keeps_audit_record(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            coordinator = MediaCoordinator(
                lambda: b"\xff\xd8\xfftest-jpeg",
                Mock(),
                LocalMediaRepository(root / "state.json"),
                Mock(),
                root / "media",
            )
            media = asyncio.run(coordinator.capture_image("mission-1", "DRONE-01"))

            discarded = coordinator.discard(media.local_media_id)

            self.assertEqual(LocalMediaStatus.DISCARDED.value, discarded.status)
            self.assertFalse(Path(discarded.local_path).exists())

    def test_refresh_media_reconciles_available_status_and_clears_stale_error(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            repository = LocalMediaRepository(root / "state.json")
            backend = Mock()
            backend.status = AsyncMock(return_value={"status": "AVAILABLE"})
            coordinator = MediaCoordinator(
                lambda: b"\xff\xd8\xfftest-jpeg",
                Mock(),
                repository,
                backend,
                root / "media",
            )
            media = asyncio.run(coordinator.capture_image("mission-1", "DRONE-01"))
            media.backend_media_id = "backend-media-1"
            media.status = LocalMediaStatus.VALIDATING.value
            media.error_code = "UPLOAD_FAILED"
            media.error_message = "stale error"
            repository.save(media)

            refreshed = asyncio.run(coordinator.refresh_media("mission-1"))

            self.assertEqual(LocalMediaStatus.AVAILABLE.value, refreshed[0].status)
            self.assertEqual("", refreshed[0].error_code)
            self.assertEqual("", refreshed[0].error_message)
            backend.status.assert_awaited_once_with("backend-media-1")


if __name__ == "__main__":
    unittest.main()
