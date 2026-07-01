# Offline LaTeX Editor for Android

A blazing fast, offline-first LaTeX editor and compiler built specifically for Android. Powered by a native port of the **Tectonic** typesetting engine, this app provides a zero-lag authoring experience and instant local PDF generation—no internet connection required.

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

## Download & Installation

Get the latest stable release for your device architecture (ARM64 or ARMv7) from the **[Releases](https://github.com/Chiranth-Janardhan-moger/offline-latex-editor-android/releases)** page.

## Technical Details

- **Language**: Kotlin & Jetpack Compose
- **Engine**: Tectonic (Static Musl builds running via JNI/ProcessBuilder)
- **Minimum SDK**: Android 7.0 (API 24)
- **Target SDK**: Android 14 (API 34)

## SEO Tags & Keywords
*Android LaTeX editor, offline LaTeX compiler, Android Tectonic frontend, local PDF generator, TeX editor Android, best LaTeX app Android.*

## License

This project is licensed under the MIT License.
