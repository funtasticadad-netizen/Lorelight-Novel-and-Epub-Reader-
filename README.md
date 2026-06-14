# Lorelight

An Android app built with Kotlin/Jetpack Compose, originally created in Google AI Studio.

## Features

- EPUB reading and library management
- Text-to-speech playback
- Web crawler/downloader for content
- Customizable themes and fonts
- Self-healing and diagnostic logging

## Run Locally

**Prerequisites:** [Android Studio](https://developer.android.com/studio)

1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project
4. Create a file named `.env` in the project root and set `GEMINI_API_KEY` (see `.env.example`)
5. In `app/build.gradle.kts`, remove or update the line: `signingConfig = signingConfigs.getByName("debugConfig")` if you don't have a debug signing config set up
6. Run the app on an emulator or physical device

## Original AI Studio App

View the original app in AI Studio: https://ai.studio/apps/4da5b561-e880-4c3e-8cc9-57f976fbeb49

## License

See [LICENSE](LICENSE).
