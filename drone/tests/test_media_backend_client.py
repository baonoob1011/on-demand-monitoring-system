import unittest

import httpx

from flight_controller_service.backend_client import BackendContractError, MediaBackendClient


class MediaBackendClientTest(unittest.IsolatedAsyncioTestCase):
    async def test_operator_token_overrides_static_fallback(self) -> None:
        client = MediaBackendClient("http://backend.example", "fallback-token")

        self.assertEqual(
            {"Authorization": "Bearer operator-token"},
            client._headers("operator-token"),
        )
        self.assertEqual(
            {"Authorization": "Bearer fallback-token"},
            client._headers(),
        )

    async def test_mark_uploaded_reconciles_available_media_after_acknowledgement_error(self) -> None:
        client = MediaBackendClient("http://backend.example", "token")
        requests = []

        async def request(method, path, **kwargs):
            requests.append((method, path))
            if method == "POST":
                raise BackendContractError("VERSION_CONFLICT", "Concurrent storage event")
            return {"status": "AVAILABLE"}

        client._request = request

        await client.mark_uploaded("media-1", "attempt-1")

        self.assertEqual(2, len(requests))
        self.assertEqual(("GET", "/api/v1/media/media-1/upload-status"), requests[1])

    async def test_mark_uploaded_preserves_error_if_media_is_not_available(self) -> None:
        client = MediaBackendClient("http://backend.example", "token")

        async def request(method, path, **kwargs):
            if method == "POST":
                raise BackendContractError("VERSION_CONFLICT", "Concurrent storage event")
            return {"status": "VALIDATING"}

        client._request = request

        with self.assertRaises(BackendContractError):
            await client.mark_uploaded("media-1", "attempt-1")

    async def test_request_fails_over_and_reuses_reachable_backend(self) -> None:
        client = MediaBackendClient(
            ["http://localhost:8080", "http://172.26.128.1:8080"],
            "token",
        )
        requested_hosts = []

        async def send(base_url, method, path, **_kwargs):
            requested_hosts.append(base_url)
            request = httpx.Request(method, f"{base_url}{path}")
            if base_url == "http://localhost:8080":
                raise httpx.ConnectError("Connection refused", request=request)
            return httpx.Response(200, request=request, json={"data": {"ready": True}})

        client._send = send

        self.assertEqual({"ready": True}, await client._request("GET", "/health"))
        self.assertEqual({"ready": True}, await client._request("GET", "/health"))

        self.assertEqual(
            [
                "http://localhost:8080",
                "http://172.26.128.1:8080",
                "http://172.26.128.1:8080",
            ],
            requested_hosts,
        )


if __name__ == "__main__":
    unittest.main()
