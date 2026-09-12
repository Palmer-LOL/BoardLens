# Working on BoardLens

## Current product boundary

- This is an Android app for physical-board photos. Recognition is local and requires explicit calibration. Preserve the no-network default; any future remote option needs a deliberate product decision and an accurate data-flow explanation.
- Keep chess position history separate from visual recognition. Do not infer turn, castling, en passant or counters from placement alone.
- Do not silently coerce predictions into a legal board. Surface uncertainty and let the user edit.
- Keep features and tests honest: synthetic fixtures are not evidence of physical-photo accuracy.
- Do not add secrets, signing keys, personal reference photos, generated build outputs, or machine-specific configuration to git.

## Development

Use Java 17, Android API 26+ and the pinned Gradle toolchain. Keep the core free of Android imports and external dependencies. Run `bash scripts/test-core.sh`, `./gradlew :app:assembleDebug`, and `./gradlew :app:lintDebug` for relevant changes; document concrete environmental blockers. Use the manual acceptance list for acquisition, accessibility and lifecycle work.

Treat these as the current design decisions, with rationale in `docs/`; revisit them explicitly if the project's goals change. Do not turn a dated implementation choice into an unexplained permanent restriction.
