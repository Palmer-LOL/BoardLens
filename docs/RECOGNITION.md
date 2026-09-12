# Recognition approach and limits

BoardLens 0.1 uses a calibrated local appearance classifier. It is an experimental implementation for a particular board/set and repeatable overhead photography, not a universal chess recognition model.

## Pipeline

1. Normalize image size and EXIF orientation.
2. Let the user mark the four outer board corners with actual chess-square labels.
3. Validate the quadrilateral and solve a homography from the board plane to the photo.
4. Bilinearly resample a 640×640 board image; split it into 64 square crops.
5. Extract color/shape descriptors and compare each square with learned examples from the same square color.
6. Return the nearest class together with heuristic poor-match/ambiguity flags. Do not manufacture kings, remove pawns, or force a legal-looking board.
7. Present editable results and separate historical FEN metadata.

The starting-position reference plus the king/queen-swapped reference supplies all twelve colored piece classes and empty squares on both board colors. More correctly labeled reference photos can expand the appearance coverage. The profile format is bounded, versioned and rejects invalid values.

Each square descriptor has 320 floats. Two reference boards occupy 163,988 serialized bytes; the 20-board cap is 1,639,700 bytes. The profile includes an integrity checksum. Its distance and ambiguity thresholds are engineering heuristics, not thresholds established by a physical-photo benchmark.

## Limits that matter

- A homography aligns the **board plane**. It does not eliminate parallax, occluded pieces, shadows, or pieces projecting into neighboring squares. Use a view close to overhead.
- White/black here means piece side, not a promise about literal object color. The classifier learns the visual appearances in the supplied references.
- A knight rotated on its square, changed lighting, unusual reflections, a different set or board, or a different camera pose may require additional examples and manual corrections.
- Low-distance matches can still be wrong. Review dots are heuristics, not probabilities and not a calibrated confidence interval.
- Reference labels are trusted training input. A mislabeled photo teaches incorrect behavior. The app makes label review and save explicit.
- Synthetic tests verify geometry, matching behavior and persistence, not real-world recognition accuracy. Evaluate unseen physical positions before depending on the output.

## Why no bundled pretrained network

Research on 2026-09-12 did not identify a small pretrained candidate with sufficiently clear artifact licensing, preprocessing and demonstrated physical-board performance to bundle for this initial version.

[Chesscog](https://github.com/georg-wolflein/chesscog) provides a useful physical-board research baseline and supports adapting neural classifiers to new sets. Its occupancy and piece models require specialized contextual crops and total roughly 143 MB uncompressed. Its [original paper](https://arxiv.org/abs/2104.14963) and the later [ChessReD comparison](https://arxiv.org/html/2310.04086v3) show why results on rendered boards or adapted sets should not be presented as general physical-photo performance.

No code or weights from those projects are bundled. In particular, BoardLens's simple calibration is not the neural fine-tuning method in the Chesscog paper and must not inherit that paper's accuracy figures.

The geometry distinction follows the [OpenCV homography documentation](https://docs.opencv.org/4.x/d9/dab/tutorial_homography.html). Android tooling versions follow the [AGP 8.9 compatibility matrix](https://developer.android.com/build/releases/agp-8-9-0-release-notes).

## Physical evaluation protocol

For each target board/set, photograph the two calibration arrangements. Then photograph at least ten unseen middlegame/endgame positions and manually establish their placement strings. Record: exact-board accuracy, per-square accuracy, incorrect squares not flagged, and the number of corrections required. Repeat after realistic lighting and viewpoint changes. Keep those measurements separate from the deterministic test suite. Photos should remain local unless their owner deliberately chooses to contribute them.
