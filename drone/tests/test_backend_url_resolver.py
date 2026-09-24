import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from media_uploader import BackendUrlResolver


class BackendUrlResolverTest(unittest.TestCase):
    @patch("media_uploader.subprocess.check_output")
    def test_localhost_adds_current_wsl_gateway(self, check_output):
        check_output.side_effect = [
            "default via 172.26.128.1 dev eth0 proto kernel\n",
            "nameserver 10.255.255.254\n",
        ]

        candidates = BackendUrlResolver("http://localhost:8080").candidates()

        self.assertEqual("http://localhost:8080", candidates[0])
        self.assertEqual("http://172.26.128.1:8080", candidates[1])
        self.assertNotIn("http://172.20.176.1:8080", candidates)

    @patch("media_uploader.subprocess.check_output")
    def test_explicit_backend_url_is_not_overridden(self, check_output):
        candidates = BackendUrlResolver("http://backend.internal:8080/").candidates()

        self.assertEqual(["http://backend.internal:8080"], candidates)
        check_output.assert_not_called()


if __name__ == "__main__":
    unittest.main()
