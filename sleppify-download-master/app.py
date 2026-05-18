import os
import requests as req
import yt_dlp
from flask import Flask, render_template, request, flash, redirect, url_for, send_file, jsonify, Response

app = Flask(__name__)
app.secret_key = 'downloadmp3'

@app.errorhandler(500)
def handle_500(e):
    import traceback
    return jsonify({"status": "error", "error_type": type(e).__name__, "error_message": str(e), "traceback": traceback.format_exc()}), 500

@app.errorhandler(Exception)
def handle_exception(e):
    import traceback
    return jsonify({"status": "error", "error_type": type(e).__name__, "error_message": str(e), "traceback": traceback.format_exc()}), 500

# Carpeta de descargas
DOWNLOAD_FOLDER = './downloads'
COOKIES_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'cookies.txt')
os.makedirs(DOWNLOAD_FOLDER, exist_ok=True)

# Si no existe cookies.txt pero hay variable de entorno, la escribimos
if not os.path.exists(COOKIES_FILE):
    _cookies_b64 = os.environ.get('YOUTUBE_COOKIES_B64', '')
    if _cookies_b64:
        import base64
        with open(COOKIES_FILE, 'wb') as _f:
            _f.write(base64.b64decode(_cookies_b64))

def download_video_as_mp3(youtube_url, output_path=DOWNLOAD_FOLDER):
    if not os.path.exists(output_path):
        os.makedirs(output_path)

    ydl_opts = {
        'format': 'bestaudio/best',
        'postprocessors': [{
            'key': 'FFmpegExtractAudio',
            'preferredcodec': 'mp3',
            'preferredquality': '192',
        }],
        'ffmpeg_location': './ffmpeg.exe',
        'outtmpl': f'{output_path}/%(title)s.%(ext)s',
    }

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            ydl.download([youtube_url])
    except Exception as e:
        print(f"Error downloading {youtube_url}: {e}")
        return None

    for file in os.listdir(output_path):
        if file.endswith(".mp3"):
            return os.path.join(output_path, file)
    return None

@app.route('/', methods=['POST', 'GET'])
def single_track():
    file_path = None
    if request.method == 'POST':
        youtube_url = request.form.get('videoLink')
        if youtube_url:
            try:
                file_path = download_video_as_mp3(youtube_url)
                if file_path:
                    flash("MP3 Downloaded Successfully!", "success")
                else:
                    flash("Error: MP3 file not found.", "danger")
            except Exception as e:
                flash(f"Error: {str(e)}", "danger")
        return render_template('index.html', file_path=file_path)
    return render_template('index.html', file_path=None)

@app.route('/api/stream', methods=['POST'])
def api_stream():
    """Stream audio: extract URL with yt-dlp API and proxy bytes to client."""
    data = request.get_json()
    if not data or 'url' not in data:
        return jsonify({"error": "URL missing"}), 400

    youtube_url = data['url']

    ydl_opts = {
        'format': 'bestaudio[ext=m4a]/bestaudio',
        'quiet': True,
        'no_warnings': True,
        'no_playlist': True,
        'skip_download': True,
        **(({'cookiefile': COOKIES_FILE}) if os.path.exists(COOKIES_FILE) else {}),
    }

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(youtube_url, download=False)
            if not info:
                return jsonify({"error": "No info extracted"}), 500

            stream_url = info.get('url')
            if not stream_url:
                formats = info.get('formats', [])
                audio_formats = [f for f in formats if f.get('acodec') != 'none' and f.get('vcodec') in ('none', None)]
                if not audio_formats:
                    audio_formats = [f for f in formats if f.get('acodec') != 'none']
                if audio_formats:
                    m4a_formats = [f for f in audio_formats if f.get('ext') == 'm4a']
                    chosen = m4a_formats[-1] if m4a_formats else audio_formats[-1]
                    stream_url = chosen.get('url')

            if not stream_url:
                return jsonify({"error": "No stream URL found"}), 500

            ext = info.get('ext', 'm4a')
            title = info.get('title', 'download')
            filename = f"{title}.{ext}"

    except Exception as e:
        print(f"yt-dlp extract error: {e}")
        return jsonify({"error": str(e)}), 500

    def generate():
        try:
            headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'}
            http_headers = info.get('http_headers', {})
            if http_headers:
                headers.update(http_headers)

            with req.get(stream_url, headers=headers, stream=True, timeout=(15, 120)) as r:
                r.raise_for_status()
                for chunk in r.iter_content(chunk_size=16384):
                    if chunk:
                        yield chunk
        except Exception as e:
            print(f"Stream proxy error: {e}")

    mimetype = "audio/mp4" if ext == "m4a" else "audio/mpeg"
    return Response(generate(), mimetype=mimetype,
                    headers={"Content-Disposition": f"attachment; filename=\"{filename}\""})

@app.route('/open/<path:filename>')
def open_file(filename):
    return send_file(filename)

@app.route('/api/stream_url', methods=['POST'])
def api_stream_url():
    """OPCIÓN 1: Devuelve solo el link directo (Streaming ultrarrápido < 1s)"""
    data = request.get_json()
    if not data or 'url' not in data:
        return jsonify({"error": "Falta la URL de YouTube"}), 400
    
    try:
        # Buscamos el mejor audio que no requiera conversión
        ydl_opts = {
            'format': 'bestaudio[ext=m4a]/bestaudio',
            'quiet': True,
            'no_warnings': True,
            **(({'cookiefile': COOKIES_FILE}) if os.path.exists(COOKIES_FILE) else {}),
        }
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(data['url'], download=False)
            return jsonify({
                "stream_url": info['url'],
                "title": info.get('title', 'Audio'),
                "ext": info.get('ext', 'm4a'),
                "http_headers": info.get('http_headers', {})
            })
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.route('/api/download', methods=['POST'])
def api_download_fast():
    """Descarga audio con yt-dlp nativo (maneja throttling bypass internamente)."""
    data = request.get_json()
    if not data or 'url' not in data:
        return jsonify({"error": "URL missing"}), 400

    youtube_url = data['url']

    try:
        import tempfile, glob
        with tempfile.TemporaryDirectory() as tmpdir:
            dl_opts = {
                'format': 'bestaudio[ext=m4a]/bestaudio',
                'quiet': True,
                'no_warnings': True,
                'no_playlist': True,
                'outtmpl': os.path.join(tmpdir, '%(title)s.%(ext)s'),
                'http_chunk_size': 10485760,
                **(({'cookiefile': COOKIES_FILE}) if os.path.exists(COOKIES_FILE) else {}),
            }
            with yt_dlp.YoutubeDL(dl_opts) as ydl:
                ydl.download([youtube_url])

            files = glob.glob(os.path.join(tmpdir, '*'))
            if not files:
                return jsonify({"error": "Download produced no file"}), 500

            out_file = files[0]
            ext = out_file.rsplit('.', 1)[-1]
            filename = os.path.basename(out_file)
            audio_data = open(out_file, 'rb').read()

        mimetype = "audio/mp4" if ext == "m4a" else "audio/mpeg"
        return Response(audio_data, mimetype=mimetype,
                        headers={"Content-Disposition": f"attachment; filename=\"{filename}\""})
    except Exception as e:
        print(f"yt-dlp download error: {e}")
        return jsonify({"error": str(e)}), 500

@app.route('/api/health', methods=['GET'])
def api_health():
    """Health check - returns yt-dlp version and server status."""
    try:
        version = yt_dlp.version.__version__
    except Exception:
        version = "unknown"
    return jsonify({"status": "ok", "yt_dlp_version": version})

@app.route('/api/test', methods=['POST'])
def api_test():
    """Debug endpoint: tries to extract info and returns detailed error."""
    import traceback
    data = request.get_json()
    if not data or 'url' not in data:
        return jsonify({"error": "URL missing"}), 400

    youtube_url = data['url']
    ydl_opts = {
        'format': 'bestaudio[ext=m4a]/bestaudio',
        'quiet': False,
        'no_warnings': False,
        'no_playlist': True,
        'skip_download': True,
        **(({'cookiefile': COOKIES_FILE}) if os.path.exists(COOKIES_FILE) else {}),
    }

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(youtube_url, download=False)
            if not info:
                return jsonify({"status": "fail", "reason": "No info extracted"})

            stream_url = info.get('url')
            formats_count = len(info.get('formats', []))
            audio_formats = [f for f in info.get('formats', []) if f.get('acodec') != 'none' and f.get('vcodec') in ('none', None)]

            return jsonify({
                "status": "ok",
                "title": info.get('title'),
                "duration": info.get('duration'),
                "ext": info.get('ext'),
                "has_direct_url": bool(stream_url),
                "total_formats": formats_count,
                "audio_only_formats": len(audio_formats),
                "sample_url_prefix": (stream_url or "")[:100] if stream_url else None,
            })
    except BaseException as e:
        return jsonify({
            "status": "error",
            "error_type": type(e).__name__,
            "error_message": str(e),
            "traceback": traceback.format_exc()
        }), 500

if __name__ == "__main__":
    app.run(debug=True, host="0.0.0.0")
