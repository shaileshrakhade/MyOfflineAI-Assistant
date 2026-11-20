# Subnet roadmap

## Implemented in this source update
- User-started microphone foreground service with persistent notification.
- Wake phrases: Hey Subnet, Hey Subnet AI, Subnet, Hey Bro.
- English speech recognition and spoken replies.
- Contact disambiguation by spoken option number or unique full name.
- Direct Telecom call placement after permissions and contact selection.
- Local notes, learned phrase aliases, command history, and JSON learning-data export/import.
- Speech recognition retry/backoff and service status broadcast to the app UI.

## Remaining work for a production-grade assistant
- Integrate a dedicated low-power keyword spotting model for true wake-word detection rather than repeatedly opening Android SpeechRecognizer sessions.
- Evaluate a local speech-to-text model and local LLM runtime for offline natural-language understanding.
- Add encrypted Room database storage, backup/restore for notes and attachments, and user-controlled retention/deletion.
- Add robust app launching, alarm scheduling, reminders, and notification actions with Android background-execution constraints respected.
- Test on physical devices across Android 8–16 and manufacturer battery-management variants.
- Add automated instrumentation tests and verify calls, lock-screen operation, and recognition reliability on target devices.
