# ClipCash

**Watch Videos. Play Mini Games. Earn Rewards.**

ClipCash is a modern Android rewards app built with Kotlin and Jetpack Compose (Material Design 3). Users can watch short-form clips, play interactive mini-games, complete daily quests, and track their coin balance and redemptions in an integrated wallet.

---

## Features

- **Watch & Earn (`WatchScreen` & `VideoPlayerScreen`)**: Browse curated short videos by category, watch clips in an interactive vertical player with playback tracking, and earn coin rewards upon completion.
- **Play & Earn Arcade (`GamesScreen`)** with category filters (`Arcade`, `Puzzle`, `Brain`, `Luck`) and 10 playable games:
  - **Coin Catcher Blitz**: Catch falling coins and cash bills while dodging bombs.
  - **Lucky Cash Wheel**: Spin the prize wheel for instant coin rewards.
  - **Memory Match Flip**: Flip cards and pair all 6 icon matches in minimal moves.
  - **Balloon Pop Rush**: Pop rising regular and golden balloons while avoiding spiked ones.
  - **Math Dash Sprint**: Rapid-fire arithmetic sprint with 2x–3x streak multipliers.
  - **Reaction Flash**: 5-round millisecond reflex test.
  - **Scratch & Win**: Touch-scratchable metallic foil card with match-3 prizes.
  - **Slide & Merge 256**: 4x4 swipe and arrow-controlled number merging puzzle.
  - **Color Beat Simon**: Repeat growing 4-pad neon light sequences.
  - **Whack-a-Coin**: 3x3 grid whack arcade for coins and gems.
- **Daily Quests & Check-In (`QuestsScreen`)**: Claim daily check-in bonuses and complete milestone quests as your watch and play progress grows.
- **Wallet & Account Management (`WalletScreen`)**: View your coin balance, transaction history, cash-out options, and manage account security.
- **Authentication & Verification (`AuthScreen` & `VerificationScreens`)**: Complete Sign In, Sign Up, OTP email verification, Forgot/Reset Password, and Change Email flows.
- **Adaptive Responsive UI**: Supports phone bottom navigation (`NavigationBar`) and tablet/expanded screen layouts (`NavigationRail`) with edge-to-edge rendering.

---

## Tech Stack

- **Language**: Kotlin (`2.4.21`)
- **UI**: Jetpack Compose, Material Design 3, Material Icons Extended, Navigation Compose
- **Architecture**: MVVM (`ViewModel` + `StateFlow` / `collectAsStateWithLifecycle`), Repository pattern
- **Persistence & Networking**: Room (`2.7.0` with KSP), DataStore Preferences, OkHttp, Coil
- **Build & Tooling**:
  - Android Gradle Plugin `9.1.1`
  - Gradle `9.3.1`
  - JDK `21`
  - `minSdk`: `24` | `targetSdk`: `36` | `compileSdk`: `36.1`
  - Current Version: `3.0.0` (`versionCode = 3`)

---

## Project Structure

```text
.
├── .github/workflows/
│   └── build-apk.yml          # GitHub Actions CI workflow to build & upload debug APK
├── app/
│   ├── build.gradle.kts       # App module config, dependencies, and signingConfigs
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/
│       │   ├── MainActivity.kt
│       │   ├── data/          # Room database, DAO, entities, repository & API client
│       │   ├── model/         # Domain models & clip catalog
│       │   ├── ui/
│       │   │   ├── components/# Shared top bar, banners, and reusable UI components
│       │   │   ├── screens/   # Splash, Auth, Verification, Watch, Player, Games, Quests, Wallet
│       │   │   └── theme/     # Material 3 color scheme, typography, and shapes
│       │   └── viewmodel/     # ClipCashViewModel and UI state management
│       └── res/               # Drawables, mipmap adaptive icons, raw clips, and strings
├── gradle/
│   ├── libs.versions.toml     # Version catalog
│   └── wrapper/
│       └── gradle-wrapper.properties
└── settings.gradle.kts
```

---

## Getting Started

### Prerequisites

- **JDK 21**
- **Android SDK** (API 36.1 / Build-Tools 36.0.0)
- **Gradle 9.3.1**

### Build Locally

1. Clone the repository:
   ```bash
   git clone <your-repository-url>
   cd <repository-folder>
   ```
2. Generate a debug signing keystore if `debug.keystore` does not exist:
   ```bash
   keytool -genkeypair -v -keystore debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
   ```
3. Build the debug APK:
   ```bash
   gradle assembleDebug
   ```
   The generated APK will be at:
   ```text
   app/build/outputs/apk/debug/app-debug.apk
   ```

### Run Unit Tests

```bash
gradle :app:testDebugUnitTest
```

---

## Download APK via GitHub Actions

This repository includes `.github/workflows/build-apk.yml`, which automatically builds a debug APK on every push and also supports manual runs (`workflow_dispatch`).

1. Open the **Actions** tab in your GitHub repository.
2. Select the latest **Build Debug APK** workflow run (or click **Run workflow** to trigger one manually).
3. Download the **`clipcash-debug-apk`** artifact from the workflow summary to install the APK on an Android device.
