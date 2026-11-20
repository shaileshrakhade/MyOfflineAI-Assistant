# Subnet Voice Assistant (Android)

Subnet is an Android voice-command assistant using the Android speech-recognition and Text-to-Speech services. It supports English commands, local phrase aliases/history, app launching, selected Android settings screens, alarms, notes, and contact calling (with permissions).

## Build

- Android Studio with JDK 17, Android SDK 35, and Gradle plugin 8.7.3.
- Open this folder in Android Studio and run `:app:assembleDebug`, or use the included GitHub Actions workflow.
- Package: `com.subnetbyte`; min Android version: 8.0 (API 26).

## First run

1. Install and open Subnet.
2. Grant microphone permission when enabling wake listening. Contacts and phone permissions are only needed for contact calling.
3. Check Android Settings → General management/System → Text-to-speech output and ensure an English TTS voice is installed.
4. Ensure a speech-recognition provider is installed/enabled. On Android 12+, Subnet prefers the on-device recognizer when Android reports one is available and requests offline recognition. If the provider is missing, the app reports this instead of pretending speech is offline.
5. Tap **Start background wake listening**, then say **Hey Subnet** followed by a command. The foreground notification has a Stop listening action.

## Example commands

- `open WhatsApp`, `launch YouTube`, `open Camera`, `open Settings`
- `open Wi-Fi`, `open Bluetooth`, `open Location`, `open Display settings`
- `set alarm for 7 AM`
- `call Alex`
- `save note buy groceries`
- `teach start music as open Spotify`

App names are matched against launcher apps installed on the phone. Some protected system functions cannot be toggled directly by third-party apps; Subnet opens the relevant Android settings/panel instead. Android may also prevent an app from launching another activity while Subnet is running in the background or the screen is locked. Unlocking the phone may be required.

## Speech and privacy limitations

- Android's `SpeechRecognizer` is provided by the device's installed recognition service. Subnet cannot repair a broken/missing provider or guarantee offline recognition on every phone. For best offline results, install the phone's supported offline English speech model/provider.
- The app only listens while the user explicitly starts its microphone foreground service; stop it from the app or its notification to release the microphone.
- Android/Google Assistant's separate **Hey Google** hotword is controlled by the system/Google app, not by Subnet.
- Owner profile currently stores the owner's display name and local learning data. It does **not** implement reliable biometric speaker identification. A voice sample or phrase alone is not secure proof of identity; do not treat this build as owner-only voice authentication. Reliable local speaker verification needs a dedicated speaker-embedding model and device-side evaluation.
- Voice commands and app actions are limited by Android version, OEM restrictions, app installation, permissions, and lock-screen/background-launch rules.

## Local data

Learned phrase aliases and command history are stored in app-private SharedPreferences. Use Export/Import in the app to back up or transfer learning data. Notes and feature suggestions are also stored in app-private files.
