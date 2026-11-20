# Changelog

## 2.4.0
- Fixed the typed-command path so app-opening and alarm commands execute instead of only displaying a help message.
- Added a shared device-action executor for voice and typed commands.
- Added generic launcher-app matching plus common app shortcuts and Android settings panels.
- Added alarm handoff to the installed Clock app.
- Reduced unnecessary permissions requested when starting the wake listener; contacts/call permissions are requested only when those features are used.
- Improved speech-provider fallback, offline preference, reconnection backoff, and visible TTS diagnostics.
- Documented Android restrictions and the current lack of biometric owner voice verification.
