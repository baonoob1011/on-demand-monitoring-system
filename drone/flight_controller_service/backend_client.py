from __future__ import annotations

from collections.abc import Iterable
from pathlib import Path
from typing import Any
import asyncio

import httpx


class BackendContractError(RuntimeError):
    def __init__(self, code: str, message: str) -> None:
        super().__init__(message)
        self.code = code


class MediaBackendClient:
    def __init__(
        self,
        base_urls: str | Iterable[str],
        bearer_token: str,
        timeout_seconds: float = 120.0,
    ) -> None:
        candidates = [base_urls] if isinstance(base_urls, str) else list(base_urls)
        self._base_urls = list(
            dict.fromkeys(url.rstrip("/") for url in candidates if url.strip())
        )
        if not self._base_urls:
            raise ValueError("At least one Backend base URL is required")
        self._bearer_token = bearer_token.strip()
        self._timeout = timeout_seconds
        self._request_timeout = httpx.Timeout(
            timeout_seconds,
            connect=min(3.0, max(0.1, timeout_seconds)),
        )

    def _headers(self, bearer_token: str = "") -> dict[str, str]:
        token = bearer_token.strip() or self._bearer_token
        return {"Authorization": f"Bearer {token}"} if token else {}

    async def prepare(
        self, mission_id: str, metadata: dict[str, Any], bearer_token: str = ""
    ) -> dict[str, Any]:
        return await self._request(
            "POST",
            f"/api/v1/missions/{mission_id}/media-uploads",
            bearer_token=bearer_token,
            json=metadata,
        )

    async def upload(self, url: str, required_headers: dict[str, list[str]], path: Path) -> None:
        headers = {
            name: ",".join(values)
            for name, values in required_headers.items()
            if name.lower() != "host"
        }
        await asyncio.to_thread(self._put_file, url, headers, path)

    def _put_file(self, url: str, headers: dict[str, str], path: Path) -> None:
        with httpx.Client(timeout=self._timeout) as client:
            with path.open("rb") as stream:
                response = client.put(url, headers=headers, content=stream)
        response.raise_for_status()

    async def mark_uploaded(
        self, media_id: str, attempt_id: str, bearer_token: str = ""
    ) -> None:
        try:
            await self._request(
                "POST",
                f"/api/v1/media/{media_id}/upload-attempts/{attempt_id}/uploaded",
                bearer_token=bearer_token,
            )
        except (BackendContractError, httpx.HTTPError):
            try:
                media = await self.status(media_id, bearer_token)
            except (BackendContractError, httpx.HTTPError):
                raise
            if media.get("status") != "AVAILABLE":
                raise

    async def report_failure(
        self, media_id: str, attempt_id: str, code: str, message: str,
        bearer_token: str = "",
    ) -> dict[str, Any]:
        return await self._request(
            "POST",
            f"/api/v1/media/{media_id}/upload-attempts/{attempt_id}/failures",
            bearer_token=bearer_token,
            json={"code": code, "message": message[:1000]},
        )

    async def status(self, media_id: str, bearer_token: str = "") -> dict[str, Any]:
        return await self._request(
            "GET", f"/api/v1/media/{media_id}/upload-status", bearer_token=bearer_token
        )

    async def retry(self, media_id: str, bearer_token: str = "") -> dict[str, Any]:
        return await self._request(
            "POST", f"/api/v1/media/{media_id}/upload-attempts", bearer_token=bearer_token
        )

    async def prepare_manual_upload(
        self, media_id: str, bearer_token: str = ""
    ) -> dict[str, Any]:
        return await self._request(
            "POST",
            f"/api/v1/media/{media_id}/manual-upload-attempts",
            bearer_token=bearer_token,
        )

    async def _request(
        self, method: str, path: str, bearer_token: str = "", **kwargs: Any
    ) -> dict[str, Any]:
        response = None
        last_connection_error: httpx.HTTPError | None = None
        for base_url in tuple(self._base_urls):
            try:
                response = await self._send(
                    base_url,
                    method,
                    path,
                    bearer_token=bearer_token,
                    **kwargs,
                )
                self._promote(base_url)
                break
            except (httpx.ConnectError, httpx.ConnectTimeout) as exc:
                last_connection_error = exc

        if response is None:
            if last_connection_error is not None:
                raise last_connection_error
            raise RuntimeError("Backend request did not produce a response")

        return self._parse_response(response)

    async def _send(
        self,
        base_url: str,
        method: str,
        path: str,
        bearer_token: str = "",
        **kwargs: Any,
    ) -> httpx.Response:
        async with httpx.AsyncClient(
            base_url=base_url,
            timeout=self._request_timeout,
            headers=self._headers(bearer_token),
        ) as client:
            return await client.request(method, path, **kwargs)

    def _promote(self, base_url: str) -> None:
        if self._base_urls[0] == base_url:
            return
        self._base_urls.remove(base_url)
        self._base_urls.insert(0, base_url)

    @staticmethod
    def _parse_response(response: httpx.Response) -> dict[str, Any]:
        try:
            body = response.json()
        except ValueError:
            body = {}
        if response.is_error:
            raise BackendContractError(
                str(body.get("code") or f"HTTP_{response.status_code}"),
                str(body.get("message") or response.text[:500]),
            )
        return body.get("data") or {}
