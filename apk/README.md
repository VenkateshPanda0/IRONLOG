# Ironlog downloads

| File | Phone | What it is |
|---|---|---|
| [`ironlog-android.apk`](ironlog-android.apk) | Android 8.0 or newer | Ready to install |
| [`ironlog-ios.ipa`](ironlog-ios.ipa) | iPhone, iOS 15 or newer | Unsigned; you sign it with your Apple ID while installing |

Both files are rebuilt by GitHub Actions after every change to `main`. The Android app is published only when all tests and lint pass.

## Android

1. Open [`ironlog-android.apk`](ironlog-android.apk) on your phone and tap **Download raw file**.
2. Open the downloaded file and allow installs from that app when Android asks.

New versions install over the old one and keep your data.

## iPhone

Apple only lets an iPhone install apps from the App Store or signed with your Apple ID, so the `.ipa` is installed with a free signing tool on a computer:

1. On a Windows PC or Mac, install **iTunes** (Windows only, from apple.com, not the Microsoft Store) and **[Sideloadly](https://sideloadly.io)**.
2. Download [`ironlog-ios.ipa`](ironlog-ios.ipa) to the computer and connect the iPhone with a cable.
3. In Sideloadly, choose the `.ipa`, enter your Apple ID and press **Start**.
4. On the iPhone: **Settings › General › VPN & Device Management**, tap your Apple ID and **Trust**. On iOS 16+, also turn on **Settings › Privacy & Security › Developer Mode**.

With a free Apple ID the install lasts 7 days; repeat step 3 to renew it (your data stays). A paid Apple Developer account removes that limit and allows TestFlight.
