# BoardLens

An Android app that turns a photograph of a **physical chessboard** into an editable digital board and a six-field FEN. Recognition runs entirely on the phone.

**Status: experimental calibration-based prototype.** This is a real local appearance-matching implementation, not a bundled general-purpose neural model. It needs reference photos of your own board and pieces and works best with a repeatable overhead view and lighting. Review the result before using it. Physical-board accuracy has not yet been measured.

## Features

- Capture a full-resolution image through your camera app, or import one image with the system picker.
- Align four labeled board corners, straighten perspective, and choose board orientation.
- Learn your set from labeled reference photos; retain up to 20 references.
- Recognize 64 squares locally and flag ambiguous or poor matches for review.
- Edit each square, flip the display, import FEN, and copy/share FEN.
- Set side to move, castling rights, en passant target, halfmove clock, and fullmove number explicitly.
- Check common position inconsistencies without pretending to prove complete chess legality.
- No Internet, camera, broad media-library, or storage permissions; no accounts, analytics, advertisements, or API keys.

## Appearance

The interface uses charcoal (`#121212`) backgrounds, graphite (`#303030`) cards and borders, warm stone (`#B9A898`) metadata, warm ivory (`#FFE5D1`) body text, white headings, and ember orange (`#FF4D00`) actions. Primary buttons use charcoal labels. Board pieces have solid contrasting outlines, and review notices pair an orange symbol with ivory text.

## First use

1. Install the debug APK, or build the project below. Android 8.0 (API 26) or later is required.
2. In **Teach it your chess set**, select **1 · Starting setup**. Photograph a normal starting position from overhead with White nearest you.
3. Drag the handles to the **outside** board corners. Match `a8`, `h8`, `h1`, `a1` to the physical board. Use **Rotate labels** when needed. The grid should follow all 64 squares.
4. Open the reference review. The displayed pieces are the expected starting-position labels, **not recognition results**. Verify every label against the photo, then save.
5. Exchange the white king and queen, and exchange the black king and queen. Leave all other pieces in place. Select **2 · Swap K / Q**, photograph, align, review and save. This supplies king and queen examples on both square colors.
6. Recognition becomes available once all 13 classes—including empty—have examples on both square colors. Photograph a new position using **Take a photo** or **Choose photo**.
7. Review the digital board. A dot marks a square needing review; select its piece or confirm the current choice. Check unmarked squares too.
8. Set the history fields, then copy or share the FEN. You can save a correctly labeled scan as another reference.

Use **Flip view** for Black's perspective; it changes only the display, not the FEN. Fix recognition orientation in the corner-alignment screen.

## What the photo cannot tell you

Piece placement does not establish whose turn it is, whether kings or rooks moved earlier, the last pawn move, or the move counters. New scans use explicitly unconfirmed defaults:

```text
<piece-placement> w - - 0 1
```

Before export, the app asks you to review those fields. It does not infer castling just because a king and rook occupy their starting squares. Imported FEN retains its supplied metadata.

## Build and test

Prerequisites: JDK 17 and an Android SDK with **platforms;android-35** and **build-tools;35.0.0**. Android Studio can install these. Set `ANDROID_HOME`, or put `sdk.dir=/absolute/path/to/sdk` in an untracked `local.properties`.

```bash
./gradlew :core:coreTest :app:assembleDebug :app:lintDebug
```

Windows:

```powershell
.\gradlew.bat :core:coreTest :app:assembleDebug :app:lintDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.

The Gradle wrapper is pinned to 8.11.1 with its official distribution SHA-256; AGP is pinned to 8.9.2. The first build downloads development dependencies. The **installed app** performs no network requests.

Run the dependency-free core tests without Gradle or an Android SDK:

```bash
bash scripts/test-core.sh
```

CI builds and lints the app, runs the core suite, and uploads a debug APK under the workflow's artifacts. A debug APK is for local testing; production distribution needs your own release signing configuration. Never commit signing keys.

## Layout

| Location | Responsibility |
|---|---|
| `core/.../Position.java` | FEN parsing/serialization, rotations, structural checks |
| `core/.../BoardGeometry.java` | Corner validation, projective mapping and bilinear resampling |
| `core/.../SquareClassifier.java` | Local descriptors, calibrated matching, review flags and bounded model format |
| `app/.../AppSession.java` | Background jobs and private state; survives activity recreation |
| `app/.../MainActivity.java` | Capture/import, calibration, alignment, review and FEN export |
| `app/.../CaptureProvider.java` | Narrow temporary camera URI access |
| `docs/RECOGNITION.md` | Recognition design, limits and research decision |
| `docs/PRIVACY.md` | Data flow and retention |
| `docs/VALIDATION.md` | Verification results and physical-device acceptance checklist |

## Repository

Source and build history: [Palmer-LOL/BoardLens](https://github.com/Palmer-LOL/BoardLens).

```bash
git clone https://github.com/Palmer-LOL/BoardLens.git
cd BoardLens
```

No remote service or model download is required to operate the app after installation.
