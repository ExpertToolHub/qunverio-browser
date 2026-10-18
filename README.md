# Qunverio Browser

Native Android browser (Kotlin, Jetpack Compose, Android System WebView).

## Status: Milestone 1 (minimum working browser)
Nothing here is verified until the GitHub Actions workflow has run green and the APK
has been installed on a real device.

## Build (cloud)
Push to `main` -> Actions -> "Android CI" -> download artifact `qunverio-debug-apk`.

## Build (local, optional)
JDK 17 + Android SDK 34 + Gradle 8.7: `gradle testDebugUnitTest assembleDebug`

## Debug vs release
Debug APKs are signed with an auto-generated debug key and are for testing only.
Release signing is not configured yet. Never commit keystores or passwords.
