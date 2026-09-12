# Privacy and data flow

BoardLens has no `INTERNET` permission and no networking dependency. Recognition, descriptors and FEN processing are local. It does not request an account or API key and has no telemetry.

## Acquisition

The app invokes the installed camera application without requesting the camera permission itself. The camera receives read/write grants for a single unpredictable JPEG URI backed by a pre-created private cache file. The non-exported provider resolves only UUID-shaped filenames within that cache subdirectory; it exposes no directory browsing, deletion or arbitrary file paths. The grant is revoked and the temporary capture deleted after import or cancellation.

The system photo picker (or document picker on older devices) grants access to one explicitly selected image. No broad media or storage permission is requested. The app copies at most 24 MiB, verifies dimensions, downsamples large images, applies EXIF orientation and writes a fresh JPEG without original metadata. Temporary import bytes are removed in a `finally` block. The source image in the system gallery is never modified.

Accepted photos use unique filenames with an associated purpose. The new image is saved before a checked metadata commit makes it current; obsolete accepted photos are removed afterward. Interrupted operations may leave obsolete files until **Remove photo** is used. Camera URI bookkeeping is committed before the camera is launched.

The installed camera and picker are separate applications with their own behavior and privacy settings. BoardLens controls its own data flow.

## Retained data

- Current normalized source photo and its rectified board in app-private files.
- Current FEN, corner coordinates, review flags and workflow state in private preferences.
- Versioned, bounded feature descriptors for local calibration. Old raw reference photographs are not retained; the most recently processed photograph remains visible until replaced or removed.
- Temporary camera files while a capture is outstanding. Failed camera imports retain the capture for explicit retry or discard. Interrupted imports may leave temporary bytes until **Remove photo** or Android app storage is cleared.

Android app backup is disabled. Android 12+ data-extraction rules explicitly exclude all app-data domains from cloud backup and device transfer.

## User controls

**Help & local data → Remove photo** deletes the current normalized and rectified photos plus app-owned import/capture cache files, while retaining the editable position and learned descriptors. **Reset calibration** deletes the descriptors. Android **Clear storage** or uninstall removes app-private data. These controls do not remove the original image from the device gallery or data owned by the camera application.

**Copy FEN** writes only the FEN to the system clipboard. **Share FEN** sends only FEN text through the system chooser after an explicit action. The receiving app or system clipboard may have its own syncing behavior.

No guarantees are made against an already compromised operating system, rooted device, or independently retained original photo.
