# BrilliantBoard

A fully-loaded Android chess intelligence and game review suite: dual-platform account
import (Chess.com + Lichess), opponent scouting, local Stockfish analysis, Chess.com-style
move classification, and an interactive Compose chessboard.

- **Package:** `com.fredsystems.brilliantboard`
- **License:** GPL-3.0
- **Stack:** Kotlin 2.1, Jetpack Compose / Material 3, Koin, Ktor + kotlinx.serialization, Room, Coroutines/StateFlow

## Features

### Import & sync
- Chess.com public API with custom `User-Agent`, ETag conditional requests, incremental
  monthly archive sync and a polite 300 ms rate limiter.
- Lichess public API with NDJSON streaming of the games export (`pgnInJson=true&clocks=true&evals=true&opening=true`),
  rating history and activity. Cloud evaluations in PGN comments are reused.
- Universal PGN parser: ECO, opening, Elo, time control, termination, `[%clk]` clocks,
  `[%eval]` scores and move-by-move FENs.

### My Profile
Unified rating curves, opening leak finder (WDL by ECO, collapse move), clock IQ
(seconds/move, time-trouble and berserk blunder rates, timeouts), phase mastery,
endgame conversion lab, tilt analytics and hourly/weekday performance heatmaps, and
tactical/style profiling.

### Opponent Scout
Search any player to build a **Gameplan Card**: worst openings by colour, most-played
first moves with exact frequencies, aggression / trade-happiness / flagging / tilt
psychology, and automatic head-to-head detection against your linked accounts.

### Board & analysis
60 fps Compose canvas board with piece slide animation, last-move highlights, legal-move
indicators, multi-coloured arrows, board themes, flip, FEN/PGN paste and Lichess Opening
Explorer overlays. Ratings and statistics charts are drawn on Compose Canvas.

### Game review
Embedded Stockfish (`arm64-v8a` and `x86_64`) over UCI on `Dispatchers.Default`, MultiPV 3,
depth 12–22, live win-probability bar and eval graph. Moves are classified as Book, Forced,
Best, Great `!`, Brilliant `!!`, Good, Inaccuracy `?!`, Mistake `?` or Blunder `??` using
the win-probability model:

```
win% = 50 + 50 * (2 / (1 + exp(-0.00368208 * cp)) - 1)
```

Overall accuracy and estimated performance Elo are computed for both sides. Every critical
move exposes **Show best line** and **Retry mistake** drill actions.

## Building

```bash
./gradlew testDebugUnitTest   # pure-logic unit tests
./gradlew assembleDebug       # debug APK
./gradlew downloadStockfish   # fetch native binaries (optional)
./gradlew assembleRelease     # signed release APK (needs signing env vars)
```

On machines with very little RAM, R8 may exhaust the heap. Use
`./gradlew assembleRelease -PskipMinify` for a local signed build; CI always runs the
fully minified release.

Java 17+ and Android SDK 35 are required. `local.properties` is gitignored; set
`sdk.dir` if Android Studio does not generate it.

### Stockfish binaries

Release builds download the official Android binaries via `app/src/main/jniLibs` setup:

```bash
./gradlew downloadStockfish
# or
bash scripts/setup-stockfish.sh
```

The Gradle task is fault tolerant: if the network is unavailable, it warns and continues,
so unit tests still compile and run. Place binaries at:

```
app/src/main/jniLibs/arm64-v8a/libstockfish.so
app/src/main/jniLibs/x86_64/libstockfish.so   # optional; no official x86_64 Android asset
```

`extractNativeLibs=true` and legacy JNI packaging are configured so `ProcessBuilder` can
execute the binary from `nativeLibraryDir`.

### Release signing

`app/build.gradle.kts` reads `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and
`KEY_PASSWORD` from the environment. Secrets are never committed. Locally you may instead
provide a gitignored `keystore.properties` with `storeFile`, `storePassword`, `keyAlias`,
`keyPassword`.

## CI/CD

`.github/workflows/release.yml` runs on `v*` tags or manual dispatch. It sets up JDK 17 and
the Android SDK, downloads and vendors the Stockfish binaries, decodes the signing keystore,
runs `./gradlew assembleRelease`, renames the APK to `BrilliantBoard-vX.Y.Z-release.apk`,
and attaches it to a GitHub Release in this repository.

## Project layout

```
app/src/main/java/com/fredsystems/brilliantboard/
├── data/        local (Room), remote (Ktor DTOs/APIs), scraper (PGN), repository, sync
├── domain/      chess rules engine, models, review math, scouting analytics
├── engine/      UCI Stockfish controller, PGN-eval fallback evaluator
├── di/          Koin modules
└── ui/          theme, common Canvas charts, board, screens, ViewModels, navigation
```
