from __future__ import annotations

import hashlib
from collections.abc import Callable

import httpx


def verify_media_storage_probe(
    base_url: str,
    run_id: str,
    access_token: str | None,
    jpeg: bytes,
    *,
    attempts: int = 2,
    client_factory: Callable[..., httpx.Client] | None = None,
) -> tuple[bool, str]:
    """Run the pre-device media probe without creating business media records."""
    if not jpeg:
        return False, "Media probe JPEG is empty"

    create_client = client_factory or httpx.Client
    headers = {"Authorization": f"Bearer {access_token}"} if access_token else {}
    checksum = hashlib.sha256(jpeg).hexdigest()
    endpoint = f"{base_url}/api/internal/v1/pre-device-checks/{run_id}/media-probe"
    last_error = "Media storage probe failed"

    for attempt in range(max(1, attempts)):
        try:
            with create_client(timeout=8.0) as client:
                response = client.post(
                    endpoint,
                    headers=headers,
                    data={"checksumSha256": checksum},
                    files={"file": ("pre-device-media-probe.jpg", jpeg, "image/jpeg")},
                )
                response.raise_for_status()
                payload = response.json()
                data = payload.get("data") if isinstance(payload, dict) else None
                if not isinstance(data, dict):
                    return False, "Media probe response did not contain result data"

                verified = (
                    data.get("status") == "PASSED"
                    and data.get("checksumVerified") is True
                    and data.get("storageVerified") is True
                    and data.get("cleanupVerified") is True
                )
                message = str(data.get("message") or "Media storage probe returned an incomplete result")
                return verified, message
        except httpx.HTTPStatusError as exc:
            last_error = _http_error_message(exc.response)
            if exc.response.status_code < 500 or attempt + 1 >= attempts:
                return False, last_error
        except (httpx.TimeoutException, httpx.TransportError) as exc:
            last_error = f"Media probe backend unavailable: {exc}"
            if attempt + 1 >= attempts:
                return False, last_error
        except (ValueError, TypeError) as exc:
            return False, f"Invalid media probe response: {exc}"

    return False, last_error


def _http_error_message(response: httpx.Response) -> str:
    try:
        payload = response.json()
    except ValueError:
        payload = None
    if isinstance(payload, dict):
        message = payload.get("message") or payload.get("error")
        if message:
            return f"Media storage probe rejected: {message}"
    return f"Media storage probe failed with HTTP {response.status_code}"
