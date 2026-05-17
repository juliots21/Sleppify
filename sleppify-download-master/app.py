import os
import subprocess
try:
    import pandas as pd
except ImportError:
    pd = None
import yt_dlp
from flask import Flask, render_template, request, flash, redirect, url_for, send_file, jsonify, Response

app = Flask(__name__)
app.secret_key = 'downloadmp3'

# Carpeta de descargas
DOWNLOAD_FOLDER = './downloads'
os.makedirs(DOWNLOAD_FOLDER, exist_ok=True)

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
    data = request.get_json()
    if not data or 'url' not in data:
        return jsonify({"error": "URL missing"}), 400
    
    youtube_url = data['url']
    try:
        with yt_dlp.YoutubeDL({'quiet': True}) as ydl:
            info = ydl.extract_info(youtube_url, download=False)
            filename = f"{info['title']}.mp3"
    except Exception:
        filename = "download.mp3"

    def generate():
        import sys
        # Forzamos el uso de python3.12 y el directorio actual
        ytdlp_cmd = ['python3.12', '-m', 'yt_dlp', '-f', 'bestaudio', '--no-playlist', '--quiet', '-o', '-', youtube_url]
        
        # Buscamos el binario de FFmpeg
        if os.name == 'nt':
            ffmpeg_bin = './ffmpeg.exe'
        else:
            ffmpeg_bin = '/home/sleppifydownloader/www/ffmpeg'
            if not os.path.exists(ffmpeg_bin):
                ffmpeg_bin = './ffmpeg'

        ffmpeg_cmd = [ffmpeg_bin, '-i', 'pipe:0', '-f', 'mp3', '-ab', '192k', 'pipe:1']
        
        # Obtenemos la ruta absoluta de la carpeta actual
        current_dir = os.path.dirname(os.path.abspath(__file__))
        
        p1 = subprocess.Popen(ytdlp_cmd, stdout=subprocess.PIPE, cwd=current_dir)
        p2 = subprocess.Popen(ffmpeg_cmd, stdin=p1.stdout, stdout=subprocess.PIPE, cwd=current_dir)
        
        try:
            while True:
                chunk = p2.stdout.read(4096)
                if not chunk: break
                yield chunk
        except Exception as e:
            print(f"Error en el stream: {e}")
        finally:
            p1.kill()
            p2.kill()
            p1.wait()
            p2.wait()

    return Response(generate(), mimetype="audio/mpeg", 
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
        }
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(data['url'], download=False)
            return jsonify({
                "stream_url": info['url'],
                "title": info.get('title', 'Audio'),
                "ext": info.get('ext', 'm4a')
            })
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.route('/api/download', methods=['POST'])
def api_download_fast():
    """OPCIÓN 2: Proxy de descarga sin FFmpeg (Descarga nativa en ~1.5s)"""
    data = request.get_json()
    if not data or 'url' not in data:
        return jsonify({"error": "URL missing"}), 400
    
    youtube_url = data['url']
    try:
        with yt_dlp.YoutubeDL({'quiet': True}) as ydl:
            info = ydl.extract_info(youtube_url, download=False)
            filename = f"{info['title']}.m4a"
    except Exception:
        filename = "download.m4a"

    def generate():
        import sys
        # Usamos m4a para que Android lo lea nativamente y no tener que usar FFmpeg
        ytdlp_cmd = ['python3.12', '-m', 'yt_dlp', '-f', 'bestaudio[ext=m4a]/bestaudio', '--no-playlist', '--quiet', '-o', '-', youtube_url]
        
        current_dir = os.path.dirname(os.path.abspath(__file__))
        p1 = subprocess.Popen(ytdlp_cmd, stdout=subprocess.PIPE, cwd=current_dir)
        
        try:
            while True:
                chunk = p1.stdout.read(8192) # Aumentamos el buffer para más velocidad
                if not chunk: break
                yield chunk
        except Exception as e:
            print(f"Error en descarga rápida: {e}")
        finally:
            p1.kill()
            p1.wait()

    return Response(generate(), mimetype="audio/mp4", 
                    headers={"Content-Disposition": f"attachment; filename=\"{filename}\""})

if __name__ == "__main__":
    app.run(debug=True, host="0.0.0.0")
