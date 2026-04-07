# Sleppify

Sleppify es una aplicacion Android que integra productividad personal y herramientas multimedia en una sola experiencia.

La app combina:
- Agenda semanal con recordatorios inteligentes.
- Streaming y gestion de playlists.
- Ecualizacion avanzada y efectos de audio.
- Escaneo de codigos QR/barras.
- Automatizacion y apoyo con IA para sugerencias contextuales.

## Propuesta del producto

Sleppify busca reducir el cambio de contexto entre "organizar el dia" y "ejecutarlo": la misma app administra tareas, apoyo inteligente y consumo multimedia, con sincronizacion de estado en nube para usuarios autenticados.

## Funcionalidades principales

1. Agenda
- Gestion de tareas semanales y bloques horarios.
- Recordatorios locales y resumenes periodicos.
- Sugerencias de descripcion/categoria y apoyo de IA en flujo diario.

2. Streaming
- Exploracion de contenido y reproduccion.
- Vista de playlist y detalle de canciones.
- Cola de descarga offline con WorkManager.

3. Scanner
- Escaneo de codigos QR y de barras usando CameraX + ML Kit.

4. Equalizer
- Ecualizador grafico de 9 bandas.
- Controles de spatial y reverb.
- Persistencia local de ajustes y sincronizacion de preferencias.

5. Apps
- Utilidades de control de apps y soporte por accesibilidad.

## Arquitectura tecnica

Arquitectura modular por pantallas, con orquestacion central en [app/src/main/java/com/example/sleppify/MainActivity.java](app/src/main/java/com/example/sleppify/MainActivity.java).

Capas principales:
- UI: actividades y fragments por dominio (agenda, music, scanner, equalizer, apps, settings).
- Servicios y orquestacion: autenticacion, sincronizacion cloud, audio effects y accesibilidad.
- Datos y sincronizacion: SharedPreferences como fuente local, Firebase para identidad y persistencia remota, WorkManager para trabajos diferidos.

Componentes clave del codigo:
- App bootstrap: [app/src/main/java/com/example/sleppify/SleppifyApp.java](app/src/main/java/com/example/sleppify/SleppifyApp.java)
- Shell/navegacion: [app/src/main/java/com/example/sleppify/MainActivity.java](app/src/main/java/com/example/sleppify/MainActivity.java)
- Auth Google + Firebase: [app/src/main/java/com/example/sleppify/AuthManager.java](app/src/main/java/com/example/sleppify/AuthManager.java)
- Sync de estado en nube: [app/src/main/java/com/example/sleppify/CloudSyncManager.java](app/src/main/java/com/example/sleppify/CloudSyncManager.java)
- Servicio de audio: [app/src/main/java/com/example/sleppify/AudioEffectsService.java](app/src/main/java/com/example/sleppify/AudioEffectsService.java)
- Servicio de accesibilidad: [app/src/main/java/com/example/sleppify/AppAccessibilityService.java](app/src/main/java/com/example/sleppify/AppAccessibilityService.java)
- IA/sugerencias: [app/src/main/java/com/example/sleppify/GeminiIntelligenceService.java](app/src/main/java/com/example/sleppify/GeminiIntelligenceService.java)

Workers activos:
- [app/src/main/java/com/example/sleppify/DailyAgendaNotificationWorker.java](app/src/main/java/com/example/sleppify/DailyAgendaNotificationWorker.java)
- [app/src/main/java/com/example/sleppify/TaskReminderPrepareWorker.java](app/src/main/java/com/example/sleppify/TaskReminderPrepareWorker.java)
- [app/src/main/java/com/example/sleppify/TaskReminderNotifyWorker.java](app/src/main/java/com/example/sleppify/TaskReminderNotifyWorker.java)
- [app/src/main/java/com/example/sleppify/OfflinePlaylistDownloadWorker.java](app/src/main/java/com/example/sleppify/OfflinePlaylistDownloadWorker.java)

## Stack y versiones base

- Android app en Java
- Gradle 9.3.1 ([gradle/wrapper/gradle-wrapper.properties](gradle/wrapper/gradle-wrapper.properties))
- Android Gradle Plugin 9.1.0 ([gradle/libs.versions.toml](gradle/libs.versions.toml))
- Min SDK 24, Target SDK 36 ([app/build.gradle.kts](app/build.gradle.kts))
- Firebase Auth + Firestore
- Google Credentials + Google ID
- WorkManager
- CameraX 1.4.2
- ML Kit Barcode Scanning
- Android YouTube Player
- NewPipeExtractor
- Glide

## Requisitos de entorno

- Android Studio reciente con SDK Android instalado
- JDK 11
- Un proyecto Firebase configurado para Android
- Archivo [app/google-services.json](app/google-services.json)

## Configuracion local

1. Clonar el repositorio.
2. Verificar que [app/google-services.json](app/google-services.json) corresponde a tu proyecto Firebase.
3. Definir la propiedad YOUTUBE_DATA_API_KEY en tu gradle.properties local (idealmente en tu perfil de usuario, no en el repositorio).

Referencia actual de propiedad: [gradle.properties](gradle.properties)

## Comandos de build y pruebas

En Windows:
- .\gradlew.bat assembleDebug
- .\gradlew.bat installDebug
- .\gradlew.bat testDebugUnitTest
- .\gradlew.bat connectedDebugAndroidTest

En macOS/Linux:
- ./gradlew assembleDebug
- ./gradlew installDebug
- ./gradlew testDebugUnitTest
- ./gradlew connectedDebugAndroidTest

## Estructura del repositorio

- [app](app): modulo Android principal.
- [app/src/main](app/src/main): codigo productivo, recursos y manifest.
- [app/src/test](app/src/test): pruebas unitarias locales.
- [app/src/androidTest](app/src/androidTest): pruebas instrumentadas.
- [gradle](gradle): versionado de plugins/dependencias y wrapper.
- [oauth-pages](oauth-pages): paginas estaticas para flujo OAuth/Firebase Hosting.
- [firebase.json](firebase.json): configuracion de hosting/deploy web auxiliar.
- [docs](docs): utilidades y notas tecnicas del proyecto.

## Permisos Android y uso funcional

El manifiesto en [app/src/main/AndroidManifest.xml](app/src/main/AndroidManifest.xml) declara permisos para:
- Red e integraciones online (INTERNET, ACCESS_NETWORK_STATE).
- Audio y reproduccion en foreground service.
- Notificaciones locales.
- Camara para scanner.
- Acceso a almacenamiento/medios para operaciones de contenido.
- Estadisticas de uso y accesibilidad para funciones del modulo Apps.

Se recomienda revisar periodicamente la necesidad de cada permiso y mantener el principio de minimo privilegio.

## Seguridad y datos

- No incluir secretos productivos en commits.
- Rotar credenciales si alguna clave de pruebas fue compartida accidentalmente.
- Para produccion, mover cualquier consumo de IA/API sensible a backend propio cuando aplique.
- Validar reglas de Firestore y politicas de autenticacion antes de publicar.

## Estado actual y contribucion

Estado: desarrollo activo.

Antes de abrir PR:
- Ejecutar pruebas unitarias.
- Validar flujos criticos de Agenda, Streaming y Equalizer en dispositivo real.
- Confirmar que no se agregaron archivos temporales ni artefactos de build al repositorio.

## Licencia

Pendiente de definicion. Sugerido: MIT o Apache-2.0.
