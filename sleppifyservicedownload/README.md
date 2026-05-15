# Sleppify Download Service

Streaming audio proxy for the Sleppify Android app. Resolves YouTube audio URLs using yt-dlp and pipes the bytes directly to the client — no files stored on disk.

## Endpoints

- `GET /health` — health check
- `GET /download?v=VIDEO_ID&quality=medium` — stream audio (quality: low, medium, high, very_high)

## Local Development

```bash
pip install -r requirements.txt
python main.py
```

Server runs at `http://localhost:8000`.

## Deploy to Render

1. Create a new **Web Service** on [render.com](https://render.com)
2. Connect this repo/folder
3. Set **Docker** as the runtime
4. Render will use the `Dockerfile` automatically
5. Your service URL will be `https://sleppify-download.onrender.com`

## Update yt-dlp

If YouTube changes their API, update yt-dlp:
```bash
pip install --upgrade yt-dlp
```
Then redeploy.
