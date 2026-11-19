# Subnet Voice Assistant

Android starter app. Package: `com.subnetbyte`.

## Included
- Speech input through Android's installed recognition provider, requesting offline-preferred mode.
- Text command input for testing.
- Launch installed apps by visible app name.
- Set alarms through the Android alarm intent.
- Save notes in app-private local storage.
- Find a contact and open the dialer for user-confirmed calling.

## Privacy and offline limits
There is no backend, analytics, or remote database in this starter. Notes are stored in the app's private files directory. `EXTRA_PREFER_OFFLINE` is only a preference: Android's speech provider may still use a network service. Strict offline speech requires bundling and testing an on-device speech model with network access disabled.

The app does not silently alter protected system settings or auto-send messages. Those require additional supported APIs, permissions, and user confirmation.

## Build
The GitHub Actions workflow builds a debug APK on pushes and manual workflow runs. Download `subnet-voice-assistant-debug-apk` from the successful run's Artifacts section.
