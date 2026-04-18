# Project Spine

Xposed/DexKit module for Spotify modifications.

## Quick Start

### Prerequisites
- Android Studio / Gradle 9+
- JDK 17
- Xposed Framework or LSPosed installed

### Build

**Debug build:**
```bash
./gradlew assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`

**Release build:**
```bash
./gradlew assembleRelease
```
APK: `app/build/outputs/apk/release/app-release.apk`

### Install

1. Build debug APK
2. Open LSPosed Manager → Modules
3. Select the module and enable it
4. Force stop Spotify and reopen

### Credits
Based on RevancedXposed_Spotify by chsbuffer
[DexKit](https://luckypray.org/DexKit/en/): a high-performance dex runtime parsing library.