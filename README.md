# HISAAB — Personal Finance Manager

Android personal finance app built with Kotlin, Jetpack Compose and Room.

## Features
- Dark premium dashboard with income, expense, balance and debt summaries
- Daily expense and income ledger with categories and timestamps
- Person-wise udhari ledger (money given / money borrowed) with partial repayments
- Monthly category budgets with spend progress
- Savings goals with contributions and progress
- Hindi/English interface toggle
- Voice-assisted transaction entry using Android speech recognition
- CSV backup export through Android's document picker
- Offline-first Room database; schema migration preserves existing `transactions` records

## Build APK
1. Open this folder in Android Studio (Giraffe or newer).
2. Allow Gradle sync and install Android SDK 35.
3. Run `./gradlew :app:assembleDebug` (or `gradle :app:assembleDebug`).
4. APK: `app/build/outputs/apk/debug/app-debug.apk`.

Every push to `main` also runs GitHub Actions and uploads `HISAAB-debug-APK` as a workflow artifact.

## Data and backup
All records are stored locally in `kharcha.db`. The Room migration from v1 to v2 adds debt, budget and savings tables without dropping the original transaction table. Use the Backup icon in the top bar to export a CSV snapshot. Store the exported file safely; uninstalling the app clears local app data.

## Package
`com.kharcha.app` — Android 8.0 (API 26) and newer.
