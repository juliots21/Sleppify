"""
Sleppify Audio Download Service
Streaming proxy: resolves YouTube audio URLs via yt-dlp and pipes bytes directly to the client.
No files are stored on disk.
"""

import asyncio
import logging
import re
from typing import Optional

from fastapi import FastAPI, Query, HTTPException
from fastapi.responses import StreamingResponse, JSONResponse
import httpx
import yt_dlp

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
logger = logging.getLogger("sleppify")

app = FastAPI(title="Sleppify Download Service", version="1.0.0")

QUALITY_MAP = {
    "low": "96",
    "medium": "128",
    "high": "256",
    "very_high": "320",
}

YDL_BASE_OPTS = {
    "quiet": True,
    "no_warnings": True,
    "skip_download": True,
    "no_playlist": True,
    "geo_bypass": True,
    "socket_timeout": 15,
    "retries": 2,
    "extractor_retries": 2,
    "nocheckcertificate": True,
}


def _build_format_selector(quality: str) -> str:
    """Build yt-dlp format selector string for the requested quality."""
    target_kbps = QUALITY_MAP.get(quality, "128")
    return f"bestaudio[abr<={target_kbps}]/bestaudio/best"


def _resolve_audio_info(video_id: str, quality: str) -> dict:
    """Use yt-dlp to extract the best audio stream URL without downloading."""
    url = f"https://music.youtube.com/watch?v={video_id}"
    fmt = _build_format_selector(quality)

    opts = {
        **YDL_BASE_OPTS,
        "format": fmt,
    }

    with yt_dlp.YoutubeDL(opts) as ydl:
        info = ydl.extract_info(url, download=False)
        if not info:
            raise ValueError("No info returned")

        audio_url = info.get("url")
        if not audio_url:
            formats = info.get("formats", [])
            audio_formats = [f for f in formats if f.get("acodec") != "none" and f.get("vcodec") in (None, "none")]
            if not audio_formats:
                audio_formats = [f for f in formats if f.get("acodec") != "none"]
            if not audio_formats:
                raise ValueError("No audio formats found")

            target = int(QUALITY_MAP.get(quality, "128"))
            audio_formats.sort(key=lambda f: abs((f.get("abr") or f.get("tbr") or 128) - target))
            audio_url = audio_formats[0].get("url")

        if not audio_url:
            raise ValueError("Could not resolve audio URL")

        ext = info.get("ext", "webm")
        if ext in ("webm", "opus", "ogg"):
            content_type = "audio/webm"
        elif ext in ("m4a", "mp4", "aac"):
            content_type = "audio/mp4"
        else:
            content_type = "audio/webm"

        filesize = info.get("filesize") or info.get("filesize_approx")
        http_headers = info.get("http_headers", {})

        return {
            "url": audio_url,
            "content_type": content_type,
            "filesize": filesize,
            "ext": ext,
            "http_headers": http_headers,
        }


@app.get("/health")
async def health():
    return {"status": "ok", "service": "sleppify-download"}


@app.get("/download")
async def download_audio(
    v: str = Query(..., description="YouTube video ID"),
    quality: str = Query("medium", description="Audio quality: low, medium, high, very_high"),
):
    video_id = v.strip()
    if not video_id or not re.match(r"^[a-zA-Z0-9_-]{11}$", video_id):
        raise HTTPException(status_code=400, detail="Invalid video ID")

    quality = quality.lower().strip()
    if quality not in QUALITY_MAP:
        quality = "medium"

    logger.info(f"download:start id={video_id} quality={quality}")

    try:
        loop = asyncio.get_event_loop()
        info = await loop.run_in_executor(None, _resolve_audio_info, video_id, quality)
    except Exception as e:
        logger.error(f"download:resolve_failed id={video_id} error={e}")
        raise HTTPException(status_code=502, detail=f"Failed to resolve audio: {str(e)}")

    audio_url = info["url"]
    content_type = info["content_type"]
    filesize = info.get("filesize")
    http_headers = info.get("http_headers", {})

    logger.info(f"download:resolved id={video_id} type={content_type} size={filesize}")

    async def stream_proxy():
        """Stream bytes from YouTube CDN directly to the client."""
        async with httpx.AsyncClient(timeout=httpx.Timeout(60.0, connect=15.0), follow_redirects=True) as client:
            headers = {
                "User-Agent": http_headers.get("User-Agent", "Mozilla/5.0"),
                "Accept": "*/*",
                "Accept-Encoding": "identity",
            }
            if "Referer" in http_headers:
                headers["Referer"] = http_headers["Referer"]

            async with client.stream("GET", audio_url, headers=headers) as response:
                if response.status_code >= 400:
                    logger.error(f"download:cdn_error id={video_id} status={response.status_code}")
                    return
                async for chunk in response.aiter_bytes(chunk_size=65536):
                    yield chunk

    response_headers = {
        "Content-Type": content_type,
        "X-Sleppify-VideoId": video_id,
        "X-Sleppify-Quality": quality,
        "Cache-Control": "no-store",
    }
    if filesize and filesize > 0:
        response_headers["Content-Length"] = str(filesize)

    return StreamingResponse(
        stream_proxy(),
        media_type=content_type,
        headers=response_headers,
    )


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8000, log_level="info")
