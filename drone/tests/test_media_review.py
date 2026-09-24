import sys
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from media_review import LocalMediaLibrary


class LocalMediaLibraryTest(unittest.TestCase):
    def test_capture_requires_bound_mission(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            library = LocalMediaLibrary(root, None, "DRONE-01")

            with self.assertRaisesRegex(ValueError, "Bind an assigned mission"):
                library.capture_image(b"\xff\xd8\xfftest-jpeg")

            self.assertEqual([], library.list_items())
            self.assertEqual([], list(root.glob("*.jpg")))

    @unittest.skipUnless(shutil.which("ffmpeg") and shutil.which("ffprobe"), "FFmpeg is required")
    def test_video_review_uses_h264_and_preserves_original(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            original = root / "capture.mp4"
            subprocess.run(
                ["ffmpeg", "-hide_banner", "-loglevel", "error", "-f", "lavfi",
                 "-i", "testsrc=size=64x64:rate=5", "-t", "1", "-c:v", "mpeg4",
                 "-pix_fmt", "yuv420p", str(original)],
                check=True,
            )

            library = LocalMediaLibrary(root, "mission-1", "DRONE-01")
            item = library.register_video(original)
            preview = Path(item["localPath"])
            self.assertTrue(original.is_file())
            self.assertNotEqual(original, preview)
            self.assertEqual(str(original), item["originalPath"])
            codec = subprocess.check_output(
                ["ffprobe", "-v", "error", "-select_streams", "v:0",
                 "-show_entries", "stream=codec_name", "-of", "default=noprint_wrappers=1:nokey=1", str(preview)],
                text=True,
            ).strip()
            self.assertEqual("h264", codec)
            self.assertEqual(preview.stat().st_size, item["fileSize"])

            restarted = LocalMediaLibrary(root, "mission-1", "DRONE-01")
            self.assertEqual(str(preview), restarted.get(item["localMediaId"])["localPath"])
            restarted.discard(item["localMediaId"])
            self.assertFalse(preview.exists())
            self.assertFalse(original.exists())

    @unittest.skipUnless(shutil.which("ffmpeg") and shutil.which("ffprobe"), "FFmpeg is required")
    def test_pending_legacy_mp4_is_converted_on_restart(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            original = root / "old.mp4"
            subprocess.run(
                ["ffmpeg", "-hide_banner", "-loglevel", "error", "-f", "lavfi",
                 "-i", "testsrc=size=64x64:rate=5", "-t", "1", "-c:v", "mpeg4",
                 "-pix_fmt", "yuv420p", str(original)],
                check=True,
            )
            library = LocalMediaLibrary(root, "mission-1", "DRONE-01")
            legacy = library._register("legacy-video", original, "VIDEO", "video/mp4")
            self.assertEqual(str(original), legacy["localPath"])

            migrated = LocalMediaLibrary(root, "mission-1", "DRONE-01").get("legacy-video")
            self.assertEqual(str(original), migrated["originalPath"])
            self.assertTrue(Path(migrated["localPath"]).is_file())
            self.assertNotEqual(legacy["checksumSha256"], migrated["checksumSha256"])

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

    def test_capture_uses_bound_mission_id_and_code(self):
        with tempfile.TemporaryDirectory() as directory:
            library = LocalMediaLibrary(Path(directory), "backend-uuid", "DRONE-01", "MS-8E897AE9")
            saved = library.capture_image(b"\xff\xd8\xfftest-jpeg")

            self.assertEqual("backend-uuid", saved["missionId"])
            self.assertEqual("MS-8E897AE9", saved["missionCode"])

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

    def test_transfer_in_progress_cannot_be_discarded(self):
        with tempfile.TemporaryDirectory() as directory:
            library = LocalMediaLibrary(Path(directory), "mission-1", "DRONE-01")
            saved = library.capture_image(b"\xff\xd8\xfftest-jpeg")
            library.items[saved["localMediaId"]]["status"] = "UPLOADING"
            with self.assertRaises(ValueError):
                library.discard(saved["localMediaId"])
            self.assertTrue(Path(saved["localPath"]).exists())


if __name__ == "__main__":
    unittest.main()
