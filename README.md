# android-roborazzi

A small Jetpack Compose demo app showing how to use [Roborazzi](https://github.com/takahirom/roborazzi) for screenshot regression testing on the JVM with Robolectric — no emulator required.

The app navigates between a home screen, an item list, item details, and a not-found screen. Tests capture golden images and verify them on every run.

## Stack

- Kotlin 2.4, Compose, Navigation Compose
- Roborazzi 1.64 + Robolectric 4.16
- JDK 21
- Golden images stored as WebP in `app/`

## Prerequisites

- JDK 21
- Android SDK (for Gradle/AGP)

## Run the app

```bash
./gradlew :app:installDebug
```

## Run tests

Verify screenshot tests (default — compares against committed golden images):

```bash
./gradlew :app:testDebugUnitTest
```

View the Roborazzi HTML report after a run:

```bash
open app/build/reports/roborazzi/debug/index.html
```

## Update golden images

When you intentionally change the UI, record new goldens:

```bash
./gradlew :app:testDebugUnitTest \
  -Proborazzi.test.verify=false \
  -Proborazzi.test.record=true
```

Commit the updated `app/*.webp` files together with your code changes.

## What's tested

| Test class | Coverage |
|---|---|
| `HomeScreenTest` | Home screen (light + dark theme) |
| `ItemListScreenTest` | List scroll positions (top, middle, bottom) |
| `ItemDetailScreenTest` | Sample item and long description |
| `AppNavHostTest` | Navigation flows with interaction assertions + screenshots |

Tests share a common setup in `RoborazziComposeTest` (Pixel 5, SDK 33, native graphics, WebP output). Screenshot names live in `GoldenImages.kt`.

## CI

GitHub Actions runs Roborazzi tests on every push to `main` and on pull requests.

- **Workflow:** [`.github/workflows/roborazzi.yml`](.github/workflows/roborazzi.yml)
- **Runner:** `macos-latest` with JDK 21
- **On failure:** uploads a `roborazzi-diffs` artifact with compare images and the HTML report

### PR diff comments

When a pull request fails screenshot tests, [`.github/workflows/roborazzi-comment.yml`](.github/workflows/roborazzi-comment.yml) posts a comment on the PR with inline diff images (`*_compare.webp`). No need to download artifacts for a quick visual review.

For the full report, download the `roborazzi-diffs` artifact from the failed run and open `reports/roborazzi/debug/index.html`.

## Project layout

```
app/
├── *.webp                          # Golden images (committed)
├── src/main/                       # Compose UI + navigation
└── src/test/
    ├── RoborazziComposeTest.kt     # Shared test base class
    ├── GoldenImages.kt             # Screenshot name constants
    └── *Test.kt                    # Per-screen and navigation tests
```

## License

Demo project — use as a reference for your own Roborazzi setup.