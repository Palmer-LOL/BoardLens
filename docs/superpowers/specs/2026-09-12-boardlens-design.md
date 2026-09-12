# BoardLens design

Build a native Android application with private on-device photo storage that photographs a physical chessboard, recognizes its pieces locally, presents an editable digital board, and exports a six-field FEN.

## Requirements and decisions

- User chose on-device recognition. The APK has no Internet permission, analytics, account, paid API, or remote inference dependency.
- Minimum Android 8 (API 26); compile/target API 35; Java 17; native Android views. Keep the vision and chess core independent of Android and external libraries.
- Capture through the installed camera application with a narrowly scoped temporary URI; import one image with the system picker. Keep imported/captured images in private application storage and disable backup.
- Let the user align four outer board corners and orient square labels. Rectify perspective before recognizing squares. Perspective correction cannot remove physical occlusion; guide users toward a clear overhead view.
- Recognition must use a real, inspectable implementation. A pretrained model is acceptable only if its weights, license, preprocessing, and physical-board use can be verified. Otherwise use local labeled calibration photographs with an explicit setup flow and conservative review flags. Never describe template scores as calibrated probabilities.
- With calibration: label the standard starting position, then a second position with both kings and queens exchanged. This supplies each piece type on both square colors. Let users add corrected photographs as further examples and clear calibration.
- Review all results in an editable 8×8 board. Highlight ambiguous/poor matches and preserve user agency; never insert missing kings or silently repair predictions.
- A photo contains no game history. Default history to white to move, no asserted castling rights, no en passant target, halfmove 0, fullmove 1. Explain and expose all fields before FEN export.
- Support manual position entry, FEN import, board rotation, copy/share of FEN, and basic position-consistency checks. Checks do not prove that a position is reachable by legal play.
- Include reproducible build instructions, a Gradle wrapper, local core tests, Android build/lint CI, and a manual physical-device acceptance checklist.

## Boundaries

`core`: position/FEN, geometry, square descriptors, calibration classifier and serialization; JVM-testable.

`app`: acquisition, image normalization, corner and board views, screen orchestration, private persistence, export.

`docs`: setup, model constraints, privacy, validation evidence, and implementation plan.

## Delivery

The user created the public `Palmer-LOL/BoardLens` repository and authorized publishing the project there. Repository visibility is separate from the app's private, on-device photo storage.

## Appearance update · 2026-09-12

The user supplied six colors: charcoal `#121212`, graphite `#303030`, warm stone `#B9A898`, warm ivory `#FFE5D1`, white `#FFFFFF`, and ember orange `#FF4D00`. Apply these to native screens, dialogs, controls, board rendering and the launcher icon. Use charcoal labels on orange primary buttons and ivory notice text beside orange symbols. Outline board glyphs so either side remains distinguishable on both square colors.
