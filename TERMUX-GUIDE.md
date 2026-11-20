# Termux quick push guide

Allow shared storage access and install Git:

```bash
termux-setup-storage
pkg update
pkg install git unzip -y
```

Assuming the ZIP is in Downloads:

```bash
cd ~/storage/downloads
unzip MyOfflineAI-Assistant-updated.zip
git clone https://github.com/shaileshrakhade/MyOfflineAI-Assistant.git
cd MyOfflineAI-Assistant
cp -r ../MyOfflineAI-Assistant-updated/. .
git add .
git commit -m "Upgrade Subnet Assistant"
git push
```

This keeps the remote repository's existing commit history. GitHub may ask for authentication; use a Personal Access Token for HTTPS when prompted, and never commit it.

For later updates, copy changed files into the cloned repository folder and run:

```bash
git add .
git commit -m "Describe your update"
git push
```
