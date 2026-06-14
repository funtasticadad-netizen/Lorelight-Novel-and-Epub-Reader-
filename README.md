# 📖 Lorelight

**Lorelight** is an Android novel and EPUB reader app built with Kotlin and Jetpack Compose. It combines a clean, customizable reading experience with text-to-speech narration, multiple visual themes, and tools for managing and importing your library.

![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)
![License](https://img.shields.io/badge/license-MIT-green)

---

## ✨ Features

- 📚 **Library Management** — organize your novels and EPUBs on a personal bookshelf
- 🔊 **Text-to-Speech Playback** — listen to chapters with adjustable accent, voice, and playback speed
- 🌐 **Web Crawler / Downloader** — import content directly from supported sources
- 🎨 **Multiple Themes** — Midnight, Forest, Golden, Pink, AMOLED Black, and more
- 🔤 **Custom Fonts & Text Filters** — fine-tune text size, spacing, alignment, and filters
- 🛠️ **Self-Healing & Diagnostics** — built-in crash reporting and recovery tools
- 💾 **Backup & Restore** — keep your library and progress safe

---

## 🎨 Themes Showcase

Lorelight supports several visual themes, each with its own color palette and icon set.

<table>
  <tr>
    <td align="center"><b>Midnight</b><br><img src="screenshots/library-midnight.jpg" width="220"/></td>
    <td align="center"><b>Forest</b><br><img src="screenshots/library-forest.jpg" width="220"/></td>
    <td align="center"><b>Golden</b><br><img src="screenshots/library-golden.jpg" width="220"/></td>
  </tr>
  <tr>
    <td align="center"><b>Pink</b><br><img src="screenshots/library-pink.jpg" width="220"/></td>
    <td align="center"><b>Midnight (alt)</b><br><img src="screenshots/library-midnight2.jpg" width="220"/></td>
    <td align="center"><b>Reader View</b><br><img src="screenshots/reader-screen.jpg" width="220"/></td>
  </tr>
</table>

---

## 📱 Screens

<table>
  <tr>
    <td align="center"><b>Book Detail</b><br><img src="screenshots/book-detail-forest.jpg" width="220"/></td>
    <td align="center"><b>Reader Settings</b><br><img src="screenshots/reader-settings-forest.jpg" width="220"/></td>
    <td align="center"><b>TTS Settings</b><br><img src="screenshots/reader-settings-forest2.jpg" width="220"/></td>
  </tr>
</table>

---

## 🚀 Run Locally

**Prerequisites:** [Android Studio](https://developer.android.com/studio)

1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project
4. Create a file named `.env` in the project root and set `GEMINI_API_KEY` (see `.env.example`)
5. In `app/build.gradle.kts`, remove or update the line: `signingConfig = signingConfigs.getByName("debugConfig")` if you don't have a debug signing config set up
6. Run the app on an emulator or physical device

---

## 🛠️ Tech Stack

- **Kotlin** + **Jetpack Compose** for UI
- **Gradle (Kotlin DSL)** for build configuration
- Custom EPUB extraction and generation
- Android `MediaPlayer`/TTS APIs for narration

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
