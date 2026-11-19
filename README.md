# Subnet Assistant (SB) — Android starter

Package: `com.subnetbyte`

## What's included in this update

- Refreshed, mobile-friendly home screen with clear sections and larger controls.
- Custom **SB** launcher icon and in-app brand mark.
- A **Suggest a feature** screen: enter an idea in English, save it locally, then share it using Android's share sheet (email, messaging, notes, etc.).
- English-only speech recognition and command parsing.
- Wake phrase activation for **“Hey Subnet”**, **“Subnet”**, **“Hey Bro”**, and **“Bro”** while the app screen is open.
- Local command history (up to 100 recent commands), learned aliases, and JSON export/import.

## Build in Android Studio

1. Extract the ZIP.
2. Open the extracted `MyOfflineAI-Assistant-updated` folder in Android Studio.
3. Allow Gradle sync to complete.
4. Select **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. Find the APK at `app/build/outputs/apk/debug/app-debug.apk`.

The project uses Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Java 17, compileSdk 35, and minSdk 26. XML resources and the command/learning Kotlin files passed local syntax checks. A full Android Gradle build could not be run here because this environment does not have the Android SDK/Gradle wrapper installed; verify the full build in Android Studio before installing.

## Push the project from Termux

First allow Termux to access Android shared storage:

```bash
termux-setup-storage
pkg update
pkg install git unzip -y
```

Assuming the ZIP is in your Android Download folder:

```bash
cd ~/storage/downloads
unzip MyOfflineAI-Assistant-updated.zip
git clone https://github.com/shaileshrakhade/MyOfflineAI-Assistant.git
cd MyOfflineAI-Assistant
cp -r ../MyOfflineAI-Assistant-updated/. .
git add .
git commit -m "Upgrade Subnet Assistant UI and SB branding"
git push
```

This clone-and-copy method preserves the repository's existing Git history. If the repository is empty, it works as well. GitHub may ask you to authenticate; use a GitHub Personal Access Token when prompted for an HTTPS password. Never put your token in source code or commit it.

For future updates, copy the changed project files into the cloned repository folder, then run:

```bash
git add .
git commit -m "Describe your change"
git push
```

## Suggesting ideas from inside the app

Tap **Suggest a feature**, enter a short title and description, then tap **Save & share**. The suggestion is saved in the app's private local files (`feature_suggestions.txt`) and the Android share sheet opens. Choose an app to send it. Suggestions are not automatically uploaded to GitHub; this starter does not contain a server or GitHub token.

## Wake phrase behavior and limitations

- Tap **Start wake listening** and keep the Subnet screen open. The assistant listens for “Hey Subnet” / “Subnet” and “Hey Bro”.
- After a wake phrase, say a command. You can also say the wake phrase and command together, for example: “Hey Bro, open WhatsApp”.
- This implementation uses Android SpeechRecognizer sessions and restarts them while the activity is visible. It is **not** a true always-on, background, locked-screen hotword engine. Android may also require a network-backed speech service unless an on-device English model is installed.
- Background wake-word support would require a dedicated microphone foreground service, persistent notification, Android-version-specific permissions, and a local keyword-spotting model. It is deliberately not claimed by this build.

## Learning behavior

- Commands are stored locally (up to 100 recent entries) to provide a task history.
- Teach custom phrases with commands such as `Teach my office as work`; learned aliases are applied to later commands.
- Learning data can be exported and imported as JSON. This is local, rule-based personalization—not autonomous model training or a cloud AI model.

## Other limitations

- Owner setup stores a local profile only; it does not authenticate a speaker or provide secure voice biometrics.
- Commands use explicit patterns and learned aliases, not a full conversational AI model.
- The app opens the phone dialer for user confirmation rather than placing calls silently.

Do not treat the current owner profile as a security boundary for sensitive actions.


## Build fix in v4

The feature-suggestion title field now uses `EditText.setSingleLine(true)`, fixing the Kotlin compilation error `Unresolved reference: singleLine` reported by GitHub Actions.
