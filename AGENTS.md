# AGENTS.md - Sleppify Android App

./gradlew installDebug

## Project Structure
- **Main entry point**: `app/src/main/java/com/example/sleppify/MainActivity.java`
- **App bootstrap**: `app/src/main/java/com/example/sleppify/SleppifyApp.java`
- **Source code**: `app/src/main/java/com/example/sleppify/`

## Key Dependencies & Services
- Firebase Auth + Firestore (requires `app/google-services.json`)
- WorkManager for background tasks (workers in root package)
- CameraX + ML Kit for QR/barcode scanning
- YouTube Data API via `YOUTUBE_DATA_API_KEY` in gradle.properties

## Important Config
- **compileSdk**: 35, **minSdk**: 24, **targetSdk**: 35
- **Java**: VERSION_11
- **AGP**: 9.1.0, **Gradle**: 9.3.1

## Security Note
- `gradle.properties` contains a YouTube API key - do not commit secrets
- `app/google-services.json` contains Firebase config - verify before commit

## Branch Convention
- Main development branch: `sleppy`
- Default branch on GitHub should be set to `sleppy`

## Reglas para ti
- en el modulo music todas las activities estan en el mismo modulo.. por lo que en todas las ediciones debes tener en cuenta que todos los archivos relacionados deben comunicarse correctamente.. 
- nunca compiles para verificar.. solo hazlo si te lo pido.. pero no lo hagas..