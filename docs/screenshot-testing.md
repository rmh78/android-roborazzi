# Screenshot testing (Roborazzi strategy)

The **composable/UI regression layer** — fast, deterministic screenshot tests on the JVM without an emulator or live API.

## When to use Roborazzi

- Visual regression after UI changes
- Theme variants (light/dark, phone/tablet)
- Scroll positions and layout states
- Navigation flows assertable via Compose Test clicks
- Anything verifiable from **rendered pixels** without hardware or network

Do **not** use Roborazzi for live voice session behavior, WebSocket contracts, or tool execution timing — those belong in [voice-e2e-testing.md](voice-e2e-testing.md).

## Why JVM / Robolectric

| Benefit | Detail |
|---------|--------|
| Speed | Full suite runs in seconds on CI |
| Determinism | No emulator boot, no API flakiness |
| CI cost | Runs on `macos-latest`; no KVM emulator farm |
| Developer UX | `./gradlew :app:testDebugUnitTest` locally without AVD |

Stack: Roborazzi 1.64 + Robolectric 4.16, JDK 21. Versions in [`gradle/libs.versions.toml`](../gradle/libs.versions.toml).

## Configuration

In [`app/build.gradle.kts`](../app/build.gradle.kts):

```kotlin
roborazzi {
    outputDir.set(file("src/screenshots"))
}
```

Golden images are committed as WebP in `app/src/screenshots/`. Unit tests include Android resources (`testOptions.unitTests.isIncludeAndroidResources = true`).

## Base class: RoborazziComposeTest

[`RoborazziComposeTest.kt`](../app/src/test/java/com/example/roborazzidemo/RoborazziComposeTest.kt) provides shared setup:

- **Device:** Pixel 5, SDK 33, native graphics (`@GraphicsMode(NATIVE)`)
- **`setThemedContent()`** — wraps content in `RoborazziDemoTheme` + `FuturisticBackground` + **disconnected** `VoiceOverlayChrome` (`VoiceUiState.RoborazziDisconnected`)
- **`captureScreenshot(name)`** — captures root composable as lossless WebP

The disconnected overlay pattern ensures goldens include the voice chrome shell without requiring a live session or network.

## Golden image naming

All screenshot names are constants in [`GoldenImages.kt`](../app/src/test/java/com/example/roborazzidemo/GoldenImages.kt):

| Constant | Golden file |
|----------|-------------|
| `HOME_DEFAULT` | `HomeScreen_default.webp` |
| `HOME_DARK` | `HomeScreen_dark.webp` |
| `homeScreenResolution(slug, theme)` | `HomeScreen_{slug}_{theme}.webp` |
| `ITEM_LIST_SCROLL_TOP/MIDDLE/BOTTOM` | `ItemListScreen_scroll_*.webp` |
| `ITEM_DETAIL_SAMPLE/LONG` | `ItemDetailScreen_*.webp` |
| `ITEM_NOT_FOUND` | `ItemNotFoundScreen_default.webp` |
| `NAV_BROWSE_ITEMS_LIST/DETAIL` | `AppNavHost_browse_items_*.webp` |
| `NAV_ITEM_NOT_FOUND` | `AppNavHost_item_not_found.webp` |

Never hardcode filenames in test classes — always use `GoldenImages` constants.

## Test coverage

| Test class | What it captures |
|------------|------------------|
| `HomeScreenTest` | Home light + dark theme |
| `HomeScreenSmallPhoneTest` / `HomeScreenSmallPhoneNightTest` | Small phone day/night |
| `HomeScreenMediumTabletTest` | Medium tablet day/night |
| `ItemListScreenTest` | List scroll top, middle, bottom |
| `ItemDetailScreenTest` | Sample item + long description |
| `ItemNotFoundScreenTest` | Default not-found state |
| `AppNavHostTest` | Navigation flows with interaction assertions + screenshots |

### JVM unit tests (non-screenshot)

| Test class | What it tests |
|------------|---------------|
| `ScreenContentRegistryTest` | Semantics registry JSON output |
| `ItemListScrollControllerTest` | Voice scroll controller logic |
| `SyntheticMicLevelAnimatorTest` | Debug mic level animation |

## Verify vs record

**Verify** (default — compares against committed goldens):

```bash
./gradlew :app:testDebugUnitTest
```

**Record** (after intentional UI changes):

```bash
./gradlew :app:testDebugUnitTest \
  -Proborazzi.test.verify=false \
  -Proborazzi.test.record=true
```

Commit updated `app/src/screenshots/*.webp` files together with code changes.

**View report:**

```bash
open app/build/reports/roborazzi/debug/index.html
```

## CI

[`.github/workflows/roborazzi.yml`](../.github/workflows/roborazzi.yml) runs on every push to `main` and on all PRs:

- Runner: `macos-latest`, JDK 21
- Command: `./gradlew :app:testDebugUnitTest --stacktrace`
- On failure: uploads `roborazzi-diffs` artifact (compare images, HTML report)
- PR comment workflow ([`roborazzi-comment.yml`](../.github/workflows/roborazzi-comment.yml)) posts inline `*_compare.webp` images

## Checklist: add a new Roborazzi test

1. Add a constant to [`GoldenImages.kt`](../app/src/test/java/com/example/roborazzidemo/GoldenImages.kt).
2. Create or extend a test class extending `RoborazziComposeTest`.
3. Use `setThemedContent { ... }` to render the composable (or `AppNavHost` for navigation tests).
4. Add `assertIsDisplayed` checks for key UI elements.
5. Call `captureScreenshot(GoldenImages.YOUR_CONSTANT)`.
6. Run record mode locally; inspect new `.webp` in `app/src/screenshots/`.
7. Commit golden images with the code change.
8. Verify CI passes on macOS.