# Wall Stickies

This is a PICO Spatial SDK `DefaultStage` app using `STAGE_MIXED` so Persistent
Spatial Anchors can bind content to the real room. `WorldAnchorRepository`
creates, loads and removes the anchor UUIDs that must be stored alongside each
sticky note.

Keep all 2D UI in SpatialUI and wrap every Stage tree in `PicoTheme`; do not
add Material or Material3. Build with `./gradlew.bat assembleDebug`, then use
`pico-cli app install app/build/outputs/apk/debug/app-debug.apk` and
`pico-cli app launch com.spatialapps.wallstickies`.

## Current verification

- `./gradlew.bat assembleDebug --no-daemon` and `testDebugUnitTest` pass.
  `StickyNoteSerializationTest` covers the current JSON format and v1
  pipe-delimited todo compatibility, plus last-known spatial-position fallback.
- Emulator `emulator-5554` launches the app without a crash-buffer entry.
- `artifacts/drag-and-restore.png` is the latest capture. The emulator's
  Stage compositor can black out attachment panels in 2D captures; use the
  runtime `WallStickiesAnchor: loaded anchors=1` log as restart evidence.
- `HomeStage` starts `PlaneTrackingManager` for detected room surfaces; the
  current creation path still needs a plane/raycast hit-selection UI before
  it can claim arbitrary wall/table placement.
- World-anchor restore is delayed one second after Stage activation to avoid
  the Full-Space permission race. On the current emulator, a genuine emulator
  reboot returns anchor error `-7 The specified anchor was not found`; Room
  data remains and every note falls back to its own persisted last position.
- Restore uses `loadAnchor()` with no UUIDs (the SDK's all-app-anchors path),
  then matches returned UUIDs to Room records. It also subscribes to anchor
  updates so late `LOADED`/`UPDATED` poses become visible. Diagnostics use
  `WallStickiesRestore` and `WallStickiesRender` for each note. Emulator run on
  2026-08-10 read 94 Room notes and restored/attached 15; the emulator anchor
  service did not return the remaining 79, so physical-headset validation is
  still required for persistence acceptance.
- Performance acceptance remains open: `pico-cli perf doctor check` reports
  missing Trace Processor and the only target is the Debug emulator. A 60fps
  or <100MB claim requires user-approved profiling on a physical PICO device.
- Release artifact builds to `app/build/outputs/apk/release/app-release-unsigned.apk`.

## Execution rule

When a user-approved implementation plan is active, do not end work after a
phase or a successful build. Continue through every unchecked plan item and
only hand off when the plan is complete or an external dependency is genuinely
blocking progress. Intermediate build, install, and screenshot results are
progress updates, not completion.
