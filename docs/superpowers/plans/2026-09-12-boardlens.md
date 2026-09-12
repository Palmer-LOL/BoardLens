# BoardLens Implementation Plan

> **For agentic workers:** Use superpowers:subagent-driven-development for isolated core tasks; integrate and review in this session.

**Goal:** Turn a physical chessboard photo into an editable digital position and FEN on Android, entirely on device.

**Architecture:** Native Android acquisition and review UI around a dependency-free Java chess/vision core. Four user-aligned corners establish perspective and orientation. Recognition uses verified local weights or labeled local calibration, with uncertainty exposed for review.

**Tech Stack:** Java 17, Android API 26–35, AGP 8.9.2, Gradle 8.11.1.

**Spec:** `docs/superpowers/specs/2026-09-12-boardlens-design.md`

## Global constraints

- No Internet permission or runtime network calls.
- No invented FEN history or silent correction of recognized pieces.
- Image access only through explicit capture/import; private storage and backup disabled.
- Recognition accuracy on unseen physical boards must not be inferred from synthetic tests.

## Task 1: Chess position and FEN

- [x] Implement `core/.../Position.java` with strict six-field parsing, placement serialization, square names, rotation, metadata, and consistency warnings.
- [x] Exercise starting-position round trips, asymmetric rotation, malformed ranks, invalid pieces, overflow, castling, and en passant in `PositionTest.java`.
- [x] Compile/run actual Java core tests without Android.

## Task 2: On-device vision

- [x] Verify pretrained candidates before making a model dependency; otherwise implement a calibrated square classifier with color-aware descriptors and review flags.
- [x] Implement quadrilateral validation, projective mapping and bilinear resampling in `core/.../BoardGeometry.java` with geometry tests.
- [x] Implement bounded/versioned calibration persistence; reject malformed/nonfinite data.
- [x] Test real classification logic with deterministic image fixtures for both square colors, low matches, orientation, and save/load. Record the limits of fixture evidence.

## Task 3: Android experience

- [x] Add app/Gradle/manifest/resources; camera and system image picker with cancellation, orientation, size limits, and private URIs.
- [x] Add four-corner alignment, labeled orientation, calibration guidance, and background recognition.
- [x] Add accessible square editing, explicit FEN fields, validation, copy/share, and FEN import.
- [x] Preserve work across activity recreation; reject stale background results and delete temporary captures after use.

## Task 4: Build, review, deliver

- [x] Run core suite, debug assembly and Android lint; review source for permissions, lifecycle, geometry, and FEN correctness.
- [x] Add CI build with downloadable APK, instructions, privacy notes, and physical-device test checklist.
- [x] Commit the verified source locally and prepare the complete project for publication.
- [x] Deliver the initial project/APK archive and obtain an empty user-created `Palmer-LOL/BoardLens` repository.
- [x] Apply the requested charcoal, graphite, stone, ivory, white and ember palette; rerun the complete local build gate.

## Delivery

The initial archive delivery passed 66 core test groups, Gradle APK assembly, Android lint and APK signature verification. The 0.1.1 appearance update passed the same gate with 0 lint errors and 7 warnings. Physical-device recognition and UI acceptance remain to be performed.

The user created the public [Palmer-LOL/BoardLens](https://github.com/Palmer-LOL/BoardLens) repository and authorized publication there. The source includes the Android Actions workflow and build instructions; the [workflow page](https://github.com/Palmer-LOL/BoardLens/actions/workflows/android.yml) reports remote run status.
