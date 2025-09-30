# GuardianAI: On-Device AI Security for Sensitive Data

## Overview
GuardianAI is an Android application designed to detect and redact sensitive data in images, audio, and text entirely offline, without using cloud services. The app provides a secure, modular framework to handle sensitive content while maintaining usability and modern UI. The system integrates Android UI components with Python-based AI/ML backends for content analysis and redaction.

## Key Features
- **Offline Processing:** All detection and redaction happen on-device.
- **Modular Architecture:** Separate modules for Image, Audio, and Text redaction.
- **Secure UI:** Blurred previews of sensitive content are shown by default. Original content is decrypted or displayed only after user authentication.

## Project Structure
The project follows a modular architecture, with a core module for shared logic and dedicated feature modules for each redaction type.
```
GuardianAI/
├── app/                      # Main application module. Handles navigation and UI.
├── audio-redaction/          # Module for on-device audio transcription and redaction.
│   └── src/main/assets/      # Directory for audio-specific ML models (.tflite).
├── core/                     # Shared logic module, including security and utilities.
├── image-redaction/          # Module for on-device image/video object detection and redaction.
│   └── src/main/assets/      # Directory for image-specific ML models (.tflite).
├── text-redaction/           # Module for on-device text analysis and redaction.
│   └── src/main/assets/      # Directory for text-specific ML models (.tflite).
```


## Dependencies
- **Android:** Jetpack Compose, Material3

## Build & Run
```bash
git clone https://github.com/Manushivuz/GuardianAI
cd GuardianAI
```
1. Open the project in Android Studio.
3. Build and run on a device with Android 26+.

## Notes
- The app is fully offline.
- All sensitive data handling is local, encrypted, and protected.
- The UI is modular, allowing future expansion of modules or features without impacting existing code.
