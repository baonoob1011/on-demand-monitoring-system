import sys
import unittest
from pathlib import Path
from unittest.mock import Mock

import httpx

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from devicecheck_media_probe import verify_media_storage_probe


class _ClientContext:
    def __init__(self, client):
        self.client = client

    def __enter__(self):
        return self.client

    def __exit__(self, *_args):
        return False


class DeviceCheckMediaProbeTest(unittest.TestCase):
    def test_posts_probe_to_dedicated_endpoint(self):
        response = Mock()
        response.raise_for_status.return_value = None
        response.json.return_value = {
            "data": {
                "status": "PASSED",
                "checksumVerified": True,
                "storageVerified": True,
                "cleanupVerified": True,
                "message": "round-trip verified",
            }
        }
        client = Mock()
        client.post.return_value = response

        passed, message = verify_media_storage_probe(
            "http://backend:8080",
            "run-1",
            "access-token",
            b"jpeg-bytes",
            client_factory=lambda **_kwargs: _ClientContext(client),
        )

        self.assertTrue(passed)
        self.assertEqual("round-trip verified", message)
        args, kwargs = client.post.call_args
        self.assertEqual(
            "http://backend:8080/api/internal/v1/pre-device-checks/run-1/media-probe",
            args[0],
        )
        self.assertEqual("Bearer access-token", kwargs["headers"]["Authorization"])
        self.assertIn("checksumSha256", kwargs["data"])
        self.assertEqual("file", next(iter(kwargs["files"])))

    def test_retries_transient_server_error(self):
        request = httpx.Request("POST", "http://backend/probe")
        failed = httpx.Response(503, request=request)
        succeeded = Mock()
        succeeded.raise_for_status.return_value = None
        succeeded.json.return_value = {
            "data": {
                "status": "PASSED",
                "checksumVerified": True,
                "storageVerified": True,
                "cleanupVerified": True,
                "message": "ok",
            }
        }
        client = Mock()
        client.post.side_effect = [
            httpx.HTTPStatusError("unavailable", request=request, response=failed),
            succeeded,
        ]

        passed, _ = verify_media_storage_probe(
            "http://backend",
            "run-1",
            None,
            b"jpeg",
            client_factory=lambda **_kwargs: _ClientContext(client),
        )

        self.assertTrue(passed)
        self.assertEqual(2, client.post.call_count)


if __name__ == "__main__":
    unittest.main()
