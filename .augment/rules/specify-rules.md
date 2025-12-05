# TravelCompanion Development Guidelines

Auto-generated from all feature plans. Last updated: 2025-11-11

## Active Technologies
- Kotlin 1.9+ (Android backend), JavaScript ES6 (web frontend) + NanoHTTPD (embedded HTTP server), Room (database), Hilt (DI), Jetpack Compose (UI) (001-resume-movie-uploads)
- Room SQLite database (`upload_sessions` table), Android MediaStore (video files) (001-resume-movie-uploads)
- Kotlin 1.9+ (Android), JavaScript ES6+ (browser client) + NanoHTTPD (existing HTTP server), tus-js-client (new - browser), Room (existing - session persistence) (001-tus-upload-refactor)
- Room Database (session metadata), MediaStore (video files via IS_PENDING pattern) (001-tus-upload-refactor)

- Kotlin 2.1.0 (JVM 17), Android API 34 (HorizonOS)

## Project Structure

```text
src/
tests/
```

## Commands

# Add commands for Kotlin 2.1.0 (JVM 17), Android API 34 (HorizonOS)

## Code Style

Kotlin 2.1.0 (JVM 17), Android API 34 (HorizonOS): Follow standard conventions

## Recent Changes
- 001-tus-upload-refactor: Added Kotlin 1.9+ (Android), JavaScript ES6+ (browser client) + NanoHTTPD (existing HTTP server), tus-js-client (new - browser), Room (existing - session persistence)
- 001-resume-movie-uploads: Added Kotlin 1.9+ (Android backend), JavaScript ES6 (web frontend) + NanoHTTPD (embedded HTTP server), Room (database), Hilt (DI), Jetpack Compose (UI)

<!-- MANUAL ADDITIONS START -->
<!-- MANUAL ADDITIONS END -->
