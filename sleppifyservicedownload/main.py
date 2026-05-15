"""
Sleppify Audio Download Service
Streaming proxy: resolves YouTube audio URLs via yt-dlp and pipes bytes directly to the client.
No files are stored on disk.
"""

import asyncio
import logging
import os
import re
import tempfile
from typing import Optional

from fastapi import FastAPI, Query, HTTPException
from fastapi.responses import StreamingResponse
import httpx
import yt_dlp

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
logger = logging.getLogger("sleppify")

app = FastAPI(title="Sleppify Download Service", version="1.0.0")

# Write YouTube cookies from env var to a temp file for yt-dlp.
# Set YOUTUBE_COOKIES env var in Render with the contents of a Netscape cookies.txt file.
_cookies_file: Optional[str] = None
_raw_cookies = os.environ.get("YOUTUBE_COOKIES", "").strip()
if _raw_cookies:
    _tf = tempfile.NamedTemporaryFile(mode="w", suffix=".txt", delete=False, prefix="yt_cookies_")
    _tf.write(_raw_cookies)
    _tf.flush()
    _tf.close()
    _cookies_file = _tf.name
    logger.info(f"cookies:loaded path={_cookies_file} size={len(_raw_cookies)}")
else:
    logger.warning("cookies:not_set — Set YOUTUBE_COOKIES env var to avoid bot detection")

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


def _pick_best_audio(formats: list, quality: str) -> Optional[dict]:
    """Pick the best audio-only format from a formats list, closest to the target bitrate."""
    target = int(QUALITY_MAP.get(quality, "128"))
    audio_only = [f for f in formats if f.get("acodec") != "none" and f.get("vcodec") in (None, "none", "")]
    if not audio_only:
        audio_only = [f for f in formats if f.get("acodec") not in (None, "none", "")]
    if not audio_only:
        return None
    audio_only.sort(key=lambda f: abs((f.get("abr") or f.get("tbr") or 128) - target))
    return audio_only[0]


def _extract_info_with_format(url: str, fmt: str, opts_base: dict) -> dict:
    opts = {**opts_base, "format": fmt}
    with yt_dlp.YoutubeDL(opts) as ydl:
        info = ydl.extract_info(url, download=False)
        if not info:
            raise ValueError("No info returned")
        return info


def _resolve_audio_info(video_id: str, quality: str) -> dict:
    """Resolve the best audio stream URL for a YouTube video via yt-dlp."""
    url = f"https://www.youtube.com/watch?v={video_id}"

    opts_base = {**YDL_BASE_OPTS}
    if _cookies_file:
        opts_base["cookiefile"] = _cookies_file

    # Try progressively looser format selectors until one works.
    format_attempts = [
        "bestaudio[ext=webm]/bestaudio[ext=m4a]/bestaudio/best[acodec!=none]",
        "bestaudio",
        "best",
    ]

    info = None
    last_error = None
    for fmt in format_attempts:
        try:
            logger.info(f"resolve:try id={video_id} format={fmt}")
            info = _extract_info_with_format(url, fmt, opts_base)
            break
        except Exception as e:
            last_error = e
            logger.warning(f"resolve:format_failed id={video_id} format={fmt} error={e}")

    if info is None:
        raise ValueError(f"All format selectors failed: {last_error}")

    all_formats = info.get("formats", [])
    logger.info(f"resolve:ok id={video_id} formats={len(all_formats)} ext={info.get('ext')} format_id={info.get('format_id')}")

    audio_url = info.get("url")
    chosen_ext = info.get("ext", "webm")
    http_headers = info.get("http_headers", {})
    filesize = info.get("filesize") or info.get("filesize_approx")

    if not audio_url and all_formats:
        best = _pick_best_audio(all_formats, quality)
        if best:
            audio_url = best.get("url")
            chosen_ext = best.get("ext", "webm")
            http_headers = best.get("http_headers", http_headers)
            filesize = best.get("filesize") or best.get("filesize_approx") or filesize

    if not audio_url:
        raise ValueError("Could not resolve audio URL from any format")

    if chosen_ext in ("webm", "opus", "ogg"):
        content_type = "audio/webm"
    elif chosen_ext in ("m4a", "mp4", "aac"):
        content_type = "audio/mp4"
    else:
        content_type = "audio/webm"

    return {
        "url": audio_url,
        "content_type": content_type,
        "filesize": filesize,
        "ext": chosen_ext,
        "http_headers": http_headers,
    }


@app.get("/")
async def root():
    return {"status": "ok", "service": "sleppify-download", "usage": "/download?v=VIDEO_ID&quality=medium"}


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
