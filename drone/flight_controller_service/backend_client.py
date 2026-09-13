from __future__ import annotations

from pathlib import Path
from typing import Any
import asyncio

import httpx


class BackendContractError(RuntimeError):
    def __init__(self, code: str, message: str) -> None:
        super().__init__(message)
        self.code = code


class MediaBackendClient:
    def __init__(self, base_url: str, bearer_token: str, timeout_seconds: float = 120.0) -> None:
        self._base_url = base_url.rstrip("/")
        self._bearer_token = bearer_token.strip()
        self._timeout = timeout_seconds

    def _headers(self) -> dict[str, str]:
        return {"Authorization": f"Bearer {self._bearer_token}"} if self._bearer_token else {}

    async def prepare(self, mission_id: str, metadata: dict[str, Any]) -> dict[str, Any]:
        return await self._request("POST", f"/api/v1/missions/{mission_id}/media-uploads", json=metadata)

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

    async def mark_uploaded(self, media_id: str, attempt_id: str) -> None:
        await self._request(
            "POST", f"/api/v1/media/{media_id}/upload-attempts/{attempt_id}/uploaded")

    async def report_failure(
        self, media_id: str, attempt_id: str, code: str, message: str
    ) -> dict[str, Any]:
        return await self._request(
            "POST",
            f"/api/v1/media/{media_id}/upload-attempts/{attempt_id}/failures",
            json={"code": code, "message": message[:1000]},
        )

    async def status(self, media_id: str) -> dict[str, Any]:
        return await self._request("GET", f"/api/v1/media/{media_id}/upload-status")

    async def retry(self, media_id: str) -> dict[str, Any]:
        return await self._request("POST", f"/api/v1/media/{media_id}/upload-attempts")

    async def prepare_manual_upload(self, media_id: str) -> dict[str, Any]:
        return await self._request("POST", f"/api/v1/media/{media_id}/manual-upload-attempts")

    async def _request(self, method: str, path: str, **kwargs: Any) -> dict[str, Any]:
        async with httpx.AsyncClient(
            base_url=self._base_url, timeout=self._timeout, headers=self._headers()
        ) as client:
            response = await client.request(method, path, **kwargs)
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
