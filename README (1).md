# Subnet Voice Assistant

An Android voice-assistant starter app using Android SpeechRecognizer and TextToSpeech. The app stores its profile, learned phrase aliases, task history, notes, and feature suggestions locally on the device.

## Current capabilities

- Wake phrases: **Hey Subnet**, **Hey Subnet AI**, **Subnet**, and **Hey Bro**.
- English speech recognition (`en-US`) and spoken responses.
- User-started microphone foreground service. Once enabled from the visible app, listening can continue when the activity is in the background and while the screen is locked. An ongoing notification provides a Stop action.
- Contact lookup by name. When several phone-number matches are found, Subnet speaks numbered choices; reply with an option number or a unique full contact name.
- Direct call placement through Android Telecom after the user grants Contacts and Phone permissions. A single match is called directly; multiple matches require a spoken choice.
- Local notes, learned phrase aliases (`Teach ... as ...`), task history, and JSON import/export of learning data.

## Important Android limitations

This is not a privileged system assistant and is not equivalent to Google Assistant. Android restricts microphone foreground services. The user must start listening while Subnet is visible and grant microphone permission. The foreground service can keep running with the screen locked, but battery-management policies and device manufacturers may stop it. A force-stop, uninstall, revoked microphone permission, or some system shutdown states stop listening. Android 14+ does not allow ordinary apps targeting current SDKs to start a microphone foreground service from a boot receiver, so this project does not silently restart microphone capture after reboot. Open Subnet and tap Start background wake listening again.

SpeechRecognizer depends on an installed Android speech-recognition provider. On-device recognition is selected when the provider reports support; otherwise the configured provider may require network access. A network error is reported and retried with backoff. For a reliable always-on low-power wake word comparable to a system assistant, integrate a dedicated on-device keyword-spotting model and obtain the appropriate platform privileges where required.

Opening other apps and setting alarms from a locked/background state can be blocked by Android background-activity-launch rules; the current voice service explains when the user needs to unlock/open Subnet. Calls are direct once the contact choice is resolved and CALL_PHONE permission is granted.

## What “self-learning” means here

The current learning feature is local personalization: users can teach phrase aliases, and Subnet stores a bounded command history. It does **not** train or fine-tune an AI/LLM model, and it does not upload data to a cloud service. Add a local LLM or a separate model runtime if generative reasoning is required.

## Build

Open the project in Android Studio and build `app` > `assembleDebug`. The project uses Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Java 17, compile/target SDK 35, and minimum SDK 26.

## First run

1. Open Subnet and complete owner setup.
2. Tap **Start background wake listening**.
3. Grant microphone, contacts, phone, and notification permissions as prompted.
4. Keep the ongoing Subnet notification visible. Say “Hey Subnet” or “Hey Bro”.
5. For calls, say “Call Bhargavi”. If multiple matches appear, say “option two” or a unique full name.

The app's notes and suggestions are kept in its private local app storage. Learned phrases and command history are kept in app-private preferences. Export learning data before uninstalling if you want to preserve it.
