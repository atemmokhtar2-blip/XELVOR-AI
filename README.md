# XELVOR AI

Personal Android AI assistant prototype built with Kotlin.

## Current milestone
- Native Android app shell with Arabic-friendly chat UI.
- Task preview/confirmation flow (local demo only; no actions are executed yet).
- Accessibility service declaration, disabled until the user explicitly enables it in Android Settings.
- GitHub Actions workflow to build and upload a Debug APK artifact.

## Build
Open this project in Android Studio (Hedgehog or newer) and let Gradle sync, then run the `app` configuration.

The GitHub Actions workflow builds `app-debug.apk` and uploads it as an artifact.

## Security status
This is an early prototype. It does not yet connect to an AI provider and does not control other apps. Do not enter passwords, payment-card details, API keys, or other secrets into the demo chat. Before adding cloud AI, use a trusted backend for provider secrets rather than embedding keys in the APK.

Accessibility access is powerful. The service must remain user-enabled, transparent, and limited to explicit tasks. Sensitive actions such as payments, purchases, account changes, and publishing should require review and confirmation.

## Planned milestones
1. Buildable native shell and CI.
2. Chat backend interface and secure provider configuration.
3. Task planning with explicit review and cancellation.
4. Opt-in accessibility integration with visible status and safe action limits.
5. Optional on-device model integration, depending on device capability.
