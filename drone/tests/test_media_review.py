import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from media_review import LocalMediaLibrary


class LocalMediaLibraryTest(unittest.TestCase):
    def test_capture_survives_restart_and_discard_removes_original(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            library = LocalMediaLibrary(root, "mission-1", "DRONE-01")
            saved = library.capture_image(b"\xff\xd8\xfftest-jpeg")
            self.assertEqual("REVIEW_PENDING", saved["status"])
            self.assertTrue(Path(saved["localPath"]).is_file())

            restarted = LocalMediaLibrary(root, "mission-1", "DRONE-01")
            self.assertEqual(saved["localMediaId"], restarted.list_items()[0]["localMediaId"])
            restarted.discard(saved["localMediaId"])
            self.assertEqual([], restarted.list_items())
            self.assertFalse(Path(saved["localPath"]).exists())

    def test_upload_rejects_non_s3_url_without_discarding_capture(self):
        with tempfile.TemporaryDirectory() as directory:
            library = LocalMediaLibrary(Path(directory), "mission-1", "DRONE-01")
            saved = library.capture_image(b"\xff\xd8\xfftest-jpeg")
            with self.assertRaises(ValueError):
                library.transfer(saved["localMediaId"], {
                    "mediaId": "media-1", "uploadMethod": "PUT",
                    "uploadUrl": "http://127.0.0.1:8080/private",
                })
            self.assertEqual("UPLOAD_FAILED", library.get(saved["localMediaId"])["status"])
            self.assertTrue(Path(saved["localPath"]).exists())


if __name__ == "__main__":
    unittest.main()
