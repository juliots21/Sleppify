# Sleppify

Sleppify es una moderna aplicación Android centrada en la experiencia multimedia, ofreciendo streaming fluido, gestión de playlists y un potente sistema avanzado de ecualización de audio. 

La aplicación está diseñada para brindar un control total sobre la reproducción y la calidad del sonido, permitiendo sincronizar preferencias de usuario en la nube.

## Capturas de Pantalla

### Lista de Playlists
![Lista de Playlists](lista_playlists.png)

### Detalles de la Playlist
![Detalles de la Playlist](detalles_playlist.png)

### Buscador de Música
![Buscador de Música](buscador_musica.png)

### Ecualizador Avanzado
![Módulo Equalizer](modulo_equalizer.png)

## Funcionalidades Principales

### 🎵 Streaming y Gestión Musical
- **Biblioteca y Playlists:** Visualización en grilla de tus listas de reproducción, con pantallas de detalle para gestionar el contenido.
- **Reproductor Integrado:** Reproductor de música en segundo plano (`SongPlayerFragment`) con notificaciones de control.
- **Buscador de Contenido:** Búsqueda ágil e integrada para encontrar nuevas pistas o videos.
- **Modo Offline:** Sistema de descargas en segundo plano garantizado mediante `WorkManager`.

### 🎛️ Ecualizador Avanzado
- **Ecualizador Gráfico de 9 Bandas:** Ajusta las frecuencias exactas para adaptar el sonido a tus auriculares o altavoces.
- **Efectos de Audio:** Controles dedicados para amplitud espacial (Spatial) y Reverberación (Reverb).
- **Perfiles Automáticos:** Los ajustes se guardan localmente y se asocian a tus dispositivos de salida (por ejemplo, perfiles distintos para altavoz vs. Bluetooth). Todo el procesamiento funciona a través del `AudioEffectsService`.

### ☁️ Sincronización y Nube
- Autenticación segura mediante Google y Firebase.
- Tus preferencias de audio y biblioteca se sincronizan a través de `CloudSyncManager` y Firestore, permitiendo tener tu perfil sonoro donde vayas.

## Arquitectura y Tecnologías

El proyecto sigue una arquitectura modular y reactiva:
- **UI:** Actividades y fragmentos nativos, orquestados desde `MainActivity` en Kotlin. Interfaz adaptable a pantallas grandes y modo AMOLED dinámico.
- **Lenguajes:** Kotlin y Java (Soporte para Java 11).
- **Servicios:** `Foreground Services` para la retención del audio en background.
- **Persistencia y Nube:** `SharedPreferences` para respuesta ultrarrápida offline, respaldado por Firebase Firestore.
- **Multimedia:** YouTube Data API para consumo de media.

## Requisitos e Instalación

### Prerrequisitos
- Android Studio reciente.
- JDK 11.
- Archivo `app/google-services.json` configurado con tu proyecto de Firebase.

### Configuración Local
1. Clona el repositorio.
2. Asegúrate de colocar el archivo `google-services.json` en la carpeta `app/`.
3. Define tu API Key para las búsquedas en tu archivo `gradle.properties` global (o en el local, bajo tu propio riesgo):
   ```properties
   YOUTUBE_DATA_API_KEY=tu_clave_aqui
   ```

### Comandos de Construcción (Windows)
```bash
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
```

## Seguridad
⚠️ **Importante:** Nunca realices commits del archivo `google-services.json` ni expongas claves de APIs en repositorios públicos. Mantén tus credenciales seguras.