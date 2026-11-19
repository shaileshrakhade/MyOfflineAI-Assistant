# Subnet Assistant (SB) — Android starter

Package: `com.subnetbyte`

## What's included in this update

- Refreshed, mobile-friendly home screen with clear sections and larger controls.
- Custom **SB** launcher icon and in-app brand mark.
- A **Suggest a feature** screen: enter an idea in Marathi or English, save it locally, then share it using Android's share sheet (email, messaging, notes, etc.).
- The existing command, contact disambiguation, owner profile, and learned-alias export/import starter features.

## Build in Android Studio

1. Extract the ZIP.
2. Open the extracted `Subnet-Assistant-Upgrade` folder in Android Studio.
3. Allow Gradle sync to complete.
4. Select **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. Find the APK at `app/build/outputs/apk/debug/app-debug.apk`.

The project uses Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Java 17, compileSdk 35, and minSdk 26. A build has not been verified in this environment; resolve any Gradle/SDK setup errors in Android Studio before installing.

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
unzip Subnet-Assistant-Upgrade-v3.zip
git clone https://github.com/shaileshrakhade/MyOfflineAI-Assistant.git
cd MyOfflineAI-Assistant
cp -r ../Subnet-Assistant-Upgrade/. .
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

## Important limitations

- **Wake phrase:** "Hey Subnet" always-listening activation is not implemented. The current microphone button launches Android speech recognition.
- **Voice identity:** owner setup stores a local profile only. It does not authenticate a speaker or provide secure voice biometrics.
- **Cloud sync:** learned aliases can be exported/imported manually as JSON. There is no central multi-device sync server yet.
- **Language understanding:** Marathi/English support currently uses phrase patterns, not a full conversational AI model.
- **Offline speech:** requesting offline recognition does not guarantee that the phone's speech service has an offline Marathi model installed.

Do not treat the current owner profile as a security boundary for sensitive actions.
