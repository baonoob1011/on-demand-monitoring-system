import asyncio
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import Mock

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


if __name__ == "__main__":
    unittest.main()
