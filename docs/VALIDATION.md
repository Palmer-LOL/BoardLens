# Validation and acceptance

## Automated scope

The dependency-free Java suite covers strict FEN parsing and round trips, orientation, inconsistent metadata, homography and bilinear sampling, descriptor matching, ambiguous/unseen appearances, coverage requirements, model corruption/size bounds, and atomic storage failure handling.

Deterministic raster fixtures exercise actual recognition code. They are not photographs of physical boards and do not establish physical recognition accuracy.

The Android build gate is:

```bash
./gradlew :core:coreTest :app:assembleDebug :app:lintDebug
```

The full gate passed again for the 0.1.1 palette update on 2026-09-12 with JDK 17, Android SDK 35, AGP 8.9.2 and Gradle 8.11.1:

| Check | Result |
|---|---|
| FEN/position suite | 35 test groups passed; 8,858 assertions |
| Geometry suite | 10 tests passed |
| Classifier suite | 13 tests passed |
| Atomic-storage suite | 8 tests passed |
| Debug APK assembly | Passed |
| Android lint | 0 errors, 7 warnings |
| APK signature verification | Passed; APK Signature Scheme v2 |
| Packaged manifest permissions | No permissions declared |

The lint warnings concern platform EXIF use, small drawing-time allocations, programmatic view constructors and text composition. The app intentionally uses platform APIs and programmatically constructed views. These warnings are not suppressed and remain available in the generated report.

The [GitHub Actions workflow](https://github.com/Palmer-LOL/BoardLens/actions/workflows/android.yml) repeats this gate on pushes and pull requests. The results above are from the local build; the workflow page reports remote run status and provides the debug APK artifact.

## Palette checks

Measured contrast for the final colors (opaque sRGB):

| Foreground / background | Contrast |
|---|---|
| Charcoal button label / ember orange | 5.63:1 |
| Warm ivory body text / charcoal | 15.50:1 |
| Warm ivory body text / graphite | 10.92:1 |
| Warm stone metadata / graphite | 5.73:1 |
| Ember orange label / charcoal | 5.63:1 |
| White heading / graphite | 13.20:1 |

Orange symbols on graphite have 3.97:1 contrast. Notices use ivory text beside the symbols; small orange text is reserved for charcoal backgrounds. Board glyphs use white or charcoal fills with solid contrasting outlines. Disabled buttons and keyboard-focus outlines have explicit palette states.

These checks and Android lint do not replace visual, keyboard or TalkBack testing on a device. No emulator screenshot or physical-device UI validation was performed for this update.

## Physical-device acceptance checklist

These checks require an Android device and physical chess set. They have not been run in this workspace.

- [ ] Install and launch on a supported phone; verify landscape, display-cutout handling, large text and TalkBack square names. Use numeric corner controls and square-name editing without drag gestures.
- [ ] Capture and import a photo; check orientation for portrait, landscape and EXIF-mirrored input. Cancel both acquisition flows without changing the position.
- [ ] Complete both reference arrangements, verify all labels, and confirm scanning unlocks only with complete class/color coverage.
- [ ] Scan unseen middlegame and endgame positions. Record exact-board accuracy, incorrect unflagged squares and correction count using `RECOGNITION.md`'s protocol.
- [ ] Reorient a photo through corner labels; verify asymmetric positions. Flip the review display and confirm FEN is unchanged.
- [ ] Confirm individual squares, edit every piece type, import a FEN, and copy/share all six fields. Check unconfirmed history is presented before export.
- [ ] Rotate/background the app during decoding, recognition and saving. Return from the camera/picker after process recreation. Confirm the last valid image/position and review flags remain consistent.
- [ ] Rapidly tap retry while capture bookkeeping is saving; confirm only one camera activity is launched and cancellation releases the capture guard.
- [ ] Simulate storage exhaustion; confirm a failed camera import retains a retryable capture and failed position saves show an error. Recover storage and retry.
- [ ] Remove photo and reset calibration; restart and confirm the requested data was removed. Check interrupted temporary captures can be discarded.
- [ ] Run without connectivity. Inspect installed permissions and confirm there is no Internet, camera, broad media-library or storage permission.

## Distribution notes

Debug APKs are intended for testing. Different build environments normally use different debug signing keys; an APK from another environment may require uninstalling the earlier build, which deletes app-private calibration and photos. Keep a consistent local signing setup if retaining test data across builds matters. No signing keys are committed or included in the source archive.
