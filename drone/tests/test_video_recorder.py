from pathlib import Path
from subprocess import CalledProcessError
from tempfile import TemporaryDirectory
from unittest import TestCase
from unittest.mock import patch

from video.video_recorder import VideoRecorder


class VideoRecorderBrowserCompatibilityTest(TestCase):
    def test_transcodes_mp4v_to_h264_and_replaces_source(self):
        with TemporaryDirectory() as directory:
            source = Path(directory) / "recording.mp4"
            source.write_bytes(b"mp4v-source")
            recorder = VideoRecorder(Path(directory))

            def encode(command, **_kwargs):
                Path(command[-1]).write_bytes(b"h264-output")

            with patch("video.video_recorder.shutil.which", return_value="/usr/bin/ffmpeg"), patch(
                "video.video_recorder.subprocess.run", side_effect=encode
            ) as run:
                recorder._make_browser_compatible(source)

            self.assertEqual(b"h264-output", source.read_bytes())
            command = run.call_args.args[0]
            self.assertIn("libx264", command)
            self.assertIn("yuv420p", command)
            self.assertIn("+faststart", command)

    def test_keeps_source_and_removes_partial_output_when_ffmpeg_fails(self):
        with TemporaryDirectory() as directory:
            source = Path(directory) / "recording.mp4"
            source.write_bytes(b"mp4v-source")
            recorder = VideoRecorder(Path(directory))

            def fail(command, **_kwargs):
                Path(command[-1]).write_bytes(b"partial")
                raise CalledProcessError(1, command, stderr="encoder failed")

            with patch("video.video_recorder.shutil.which", return_value="/usr/bin/ffmpeg"), patch(
                "video.video_recorder.subprocess.run", side_effect=fail
            ):
                with self.assertRaisesRegex(RuntimeError, "encoder failed"):
                    recorder._make_browser_compatible(source)

            self.assertEqual(b"mp4v-source", source.read_bytes())
            self.assertFalse((Path(directory) / "recording.browser.mp4").exists())
