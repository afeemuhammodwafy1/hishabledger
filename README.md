# Hishab Ledger

**An offline-first Android app for tracking personal dues, receivables, and payments.**

Hishab Ledger helps you keep track of money owed to or received from people and shops. Create accounts, record due/payment transactions, review balances, and share account statements—all with ledger data stored locally on the device.

<p align="left">
  <a href="https://apkpure.com/p/com.wafyro.hishabledger">App listing / download</a> ·
  <a href="https://github.com/afeemuhammodwafy1/hishabledger">Source repository</a>
</p>

## Overview

- **App name:** Hishab Ledger
- **Current source version:** 1.1.1 (`versionCode` 3)
- **Platform:** Android
- **Application ID:** `com.wafyro.hishabledger`
- **Minimum Android version:** Android 8.0 (API 26)
- **Language:** Kotlin
- **UI toolkit:** Jetpack Compose with Material 3

## Features

### Ledger management

- Create accounts for shops or individuals.
- Save optional phone numbers, addresses, and opening balances.
- Record due and payment transactions with an amount, note, transaction date, and entry timestamp.
- Review account balances, total dues, total payments, and transaction history.
- Receive a warning when an account has a matching name or phone number.
- Restore recently deleted accounts and transactions during the 30-day retention period, or permanently delete them.

### Statements and data portability

- Export active transactions to CSV for use in spreadsheet software.
- Share a text statement containing account totals and up to 15 recent entries.
- Generate and share a PDF statement containing the account's full history.
- Export a JSON backup and restore it later.
- Choose to merge imported records with existing records or replace the current ledger.
- Start a backup upload through the installed Google Drive app, when available.

> Google Drive integration opens the Drive upload flow; it is not automatic cloud backup or synchronization.

### Privacy and personalization

- Core ledger functionality works offline and does not require account sign-up.
- Choose English or Bangla in Settings.
- Choose Light, Dark, or System appearance.
- Enable an optional app lock with a PIN, biometric/device-credential authentication where supported, and a configurable lock timeout.
- The app temporarily locks PIN entry after repeated incorrect attempts.
- Use home-screen app shortcuts to start adding a due or payment.

## Screenshots

Screenshots are not included in this repository yet. Add current screenshots here to make the app easier to evaluate, for example:

```markdown
![Home screen](docs/screenshots/home.png)
![Account details](docs/screenshots/account-details.png)
![Settings](docs/screenshots/settings.png)
```

Only add image paths after committing the corresponding screenshot files.

## Tech stack

| Area | Implementation |
|---|---|
| Language | Kotlin 2.0.21 |
| UI | Jetpack Compose, Material 3 |
| Android build | Android Gradle Plugin 8.7.3, Gradle 8.9 wrapper |
| Java compatibility | Java 17 |
| Android SDK | Compile SDK 35; target SDK 35; minimum SDK 26 |
| AndroidX | Core KTX, Activity Compose, Lifecycle ViewModel / Process, SplashScreen, Biometric |
| Local ledger storage | JSON file in app-private internal storage |
| Local preferences | Android SharedPreferences |
| Release optimization | R8/ProGuard minification and resource shrinking |

## Architecture and code map

The code is organized by responsibility within `app/src/main/java/com/wafyro/hishabledger/`.

| File | Responsibility |
|---|---|
| `MainActivity.kt` | Android activity entry point and startup setup |
| `AppRoot.kt` | App root, screen routing, and app-lock integration |
| `Models.kt` | Account and transaction data models |
| `HishabViewModel.kt` | Ledger operations, balance calculations, and state updates |
| `LocalStore.kt` | Local JSON persistence, backup encoding/validation, and legacy data migration |
| `SecurityStore.kt` | PIN verification, lock preferences, and app lifecycle lock gate |
| `LockScreen.kt` | PIN and biometric/device-credential unlock UI |
| `HomeScreen.kt`, `ShopScreen.kt` | Account overview and account transaction history |
| `AddShopDialog.kt`, `AddTxDialog.kt` | Account and transaction creation/editing flows |
| `SettingsScreen.kt` | Preferences, backup/restore, and exports |
| `RecentlyDeletedScreen.kt` | Restore or permanently delete removed records |
| `StatementShare.kt`, `PdfExport.kt` | Text and PDF statement generation/sharing |
| `Localization.kt`, `Formatters.kt`, `Theme.kt` | Language, formatting, and theme helpers |
| `OnboardingScreen.kt` | First-run guidance |
| `ShortcutRouter.kt`, `ExternalActions.kt` | App shortcuts and external sharing/actions |

### Data flow at a glance

1. UI screens collect and send user actions to `HishabViewModel`.
2. The ViewModel updates account and transaction state and derives ledger totals.
3. `LocalStore` serializes ledger records as JSON and saves them to app-private storage.
4. Backup, restore, CSV export, and statement sharing are initiated by the user from the relevant UI.

## Getting started

### Requirements

- Android Studio with Android SDK Platform 35 installed
- JDK 17
- An Android device or emulator running Android 8.0 (API 26) or newer

### Clone and open

```bash
git clone https://github.com/afeemuhammodwafy1/hishabledger.git
cd hishabledger
```

Open the project root in Android Studio, allow Gradle to sync, then run the `app` configuration on a device or emulator.

### Build a debug APK

On macOS/Linux:

```bash
./gradlew assembleDebug
```

On Windows:

```bat
gradlew.bat assembleDebug
```

The debug APK is normally generated under `app/build/outputs/apk/debug/`.

## Build a release

Release signing credentials must remain private and should not be committed to the repository.

1. Create or choose your Android release keystore and keep a secure backup.
2. Copy `keystore.properties.example` to `keystore.properties`.
3. Set `storeFile`, `storePassword`, `keyAlias`, and `keyPassword` in `keystore.properties`.
4. Build the release APK or Android App Bundle:

```bash
./gradlew assembleRelease
./gradlew bundleRelease
```

The release build enables code minification and resource shrinking. Test the signed release build before distributing it. Losing the signing key may prevent you from updating an existing app listing.

## Data storage, privacy, and security notes

- Ledger records are stored in the app's private internal storage as JSON. Settings and app-lock preferences are stored locally with `SharedPreferences`.
- The manifest does not declare the Android `INTERNET` permission; normal ledger operations are designed to work offline.
- Exported backups contain account details and transaction records. Treat backup files as sensitive personal/financial data and store them securely.
- Backup export and sharing are user-initiated. Google Drive upload is not an automatic sync service.
- Uninstalling the app or losing the device can result in loss of locally stored ledger data. Export backups regularly and verify that they can be restored.
- The optional app-lock PIN is stored as a salted SHA-256 hash, not as plaintext. App lock is an access-control measure; it does **not** encrypt the ledger file or exported backups.
- If the device has no screen lock, using the “Forgot PIN?” reset path may erase local ledger data. Keep a separate backup before relying on app-lock recovery.

## Version 1.1.1 notes

The source includes these changes and behaviors for version 1.1.1:

- Transaction entries show the transaction date separately from the entry timestamp.
- App-lock grace period options include immediate, 30 seconds, 1 minute, and 5 minutes.
- “Forgot PIN?” can use the device's screen-lock credential to reset the app PIN while retaining ledger data; the no-device-lock recovery path can erase local ledger data.
- Balance totals are precomputed when ledger data changes, and normal saves run in the background.
- Duplicate account warnings cover matching names or phone numbers.
- Text statements include up to 15 recent entries; PDF statements include the full account history.
- Release signing configuration and minification/resource shrinking are configured in Gradle.

## License

This repository does not currently include a `LICENSE` file. Until a license is added, do not assume the source code is available for reuse, modification, or redistribution. Add an explicit license if you want to grant those permissions.

## Maintainer

Created by **Wafyro Software**.
