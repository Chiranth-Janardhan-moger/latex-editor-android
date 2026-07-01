# LaTeX Editor - Android

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Platform: Android" />
  <img src="https://img.shields.io/github/v/release/Chiranth-Janardhan-moger/offline-latex-editor-android?style=for-the-badge&color=blue" alt="Release Version" />
  <img src="https://img.shields.io/badge/Engine-Tectonic-8A2BE2?style=for-the-badge" alt="Engine: Tectonic" />
  <img src="https://img.shields.io/badge/License-MIT-green?style=for-the-badge" alt="License: MIT" />
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Language: Kotlin" />
</p>

A blazing fast LaTeX editor and compiler built specifically for Android. Powered by a native port of the **Tectonic** typesetting engine, this app provides a zero-lag authoring experience and instant local PDF generation—no internet connection required.

## Why This App?

The majority of LaTeX editors on Android are either slow, rely heavily on cloud compilers, or lack a proper modern interface. We built this to be different:

- **100% Offline Compilation**: Your proprietary documents never leave your device. The Tectonic engine executes natively on your phone.
- **Uncompromised Performance**: A heavily optimized, custom syntax-highlighting engine ensures smooth, zero-lag typing even on `.tex` files with thousands of lines.
- **Distraction-Free Interface**: Designed with Material 3 principles, offering a clean, geometric aesthetic that gets out of your way.

## Features

- **Tectonic Engine Integration**: Leverages the robust, high-fidelity Tectonic engine for flawless document rendering.
- **Real-Time Syntax Highlighting**: Accurate and instantaneous formatting for commands, equations, and comments.
- **One-Tap Compilation**: Generate beautiful PDFs with a single tap.
- **Expandable Console**: A sleek, pill-shaped floating compile log that expands seamlessly to help you debug LaTeX compilation errors.
- **Hybrid Support**: Automatically downloads missing fonts or packages if you have an active connection, then aggressively caches them for future offline use.

## Screenshots

<p align="center">
  <img src="docs/editor.png" width="45%" alt="Authentic Editor View" />
  &nbsp;&nbsp;
  <img src="docs/console.png" width="45%" alt="Authentic Console View" />
</p>

## Download & Installation

Get the latest stable release for your device architecture (ARM64 or ARMv7) from the **[Releases](https://github.com/Chiranth-Janardhan-moger/offline-latex-editor-android/releases)** page.

## Technical Details

- **Language**: Kotlin & Jetpack Compose
- **Engine**: Tectonic (Static Musl builds running via JNI/ProcessBuilder)
- **Minimum SDK**: Android 7.0 (API 24)
- **Target SDK**: Android 14 (API 34)

## SEO Tags & Keywords
*LaTeX Editor Android, offline LaTeX compiler, Android Tectonic frontend, local PDF generator, TeX editor Android, best LaTeX app Android, offline.*

## License

This project is licensed under the MIT License.
