# NyxLink test report

## Connection recovery update

On the Galaxy A17, Refresh reported that nearby PCs were updated and the named PC connected using its saved pairing. The separate legacy Saved PC entry was rejected by the PC. The Windows companion restarted its Sunshine process and restored discovery after the test host was stopped.

## Version 0.1.4 live monitor switching and touch

The signed Galaxy A17 app switched to the AW2725QF in 1.34 seconds and to the VG27VQ3B in 1.24 seconds. Times run from selecting the output to Sunshine reporting capture. Both transitions occurred inside the same connected stream. The Monitor button stays at the front of the toolbar.

The Windows integration suite passed all 43 checks, including both monitor selections, unchanged display topology and audio endpoint assignments, privacy pause, and revocation.

The Windows pairing dialog displayed all five digits and the Stop pairing button at the desktop's current scale. The test prompt was canceled.

On the Galaxy A17, a one-finger swipe moved the Windows pointer without clicking. A single tap produced exactly one left click. A two-finger tap produced exactly one right click. All three positions were within two Windows pixels of the expected point on the 1920×1080 monitor. A temporary Windows window contained the click tests. The 19 Java model checks passed, including touch bounds for a differently shaped monitor.

## Version 0.1.3 nearby pairing

On November 10, 2026, the Galaxy A17 used the signed release APK with an isolated Windows host identity. The phone discovered the PC by name, requested a five-digit code on Windows, paired, and started a hardware-decoded 720p stream. Reopening the Android app and tapping the saved PC started a second stream without a code. The host log confirmed the reconnect after the first stream disconnected.

Forget Device removed the test pairing on the phone and Windows, and the phone offered to pair again. Stop Pairing dismissed the phone prompt and restored the Pair action. A separate local probe verified the Windows host canceled the request and accepted a fresh one. The original saved PC remained on the phone.

The 17 Java core checks and 43 Windows integration checks passed. The Windows checks covered both monitors, unchanged display topology and audio endpoint assignments, privacy pause, authenticated forgetting, and revocation. The physical pairing checks used an existing Private-network firewall allowance on the test EXE path. Reconnection across a PC address change was not exercised on hardware.

Functional validation: October 28, 2026. Both the earlier 0.1.0 development build and the signed NyxLink 0.1.1 release were tested; their evidence is separated below. Report finalized October 29, 2026.

## Version 0.1.1 branding checks

- NyxLink names applied to the Windows companion, Android launcher/home screen, and release packages. Internal application IDs and credential storage remain compatible.
- User-supplied artwork generated at all five Android launcher densities and as a seven-size Windows ICO (16–256 pixels). ICO decoding succeeded.
- Targeted Java model tests: 17 passed. Windows core checks: 17 passed.
- The release was subsequently tested on hardware as described in the following section.

## Signed 0.1.1 hands-on verification

The packaged Windows EXE and signed release APK were used on the Galaxy A17. Evidence: `artifacts/hands-on-nyxlink/`. This session required no manual setup or typing from the user. One physical Escape interruption was respected; the remaining work resumed after explicit authorization.

- Secure pairing, both monitor streams, authenticated monitor switching, fullscreen and landscape playback passed. Observed stream statistics were approximately 30 FPS and 2–8 ms network latency, not end-to-end input latency.
- Manual Samsung Keyboard opening and view resizing passed. Real soft-key input reached the isolated Windows field. The Samsung suggestion replaced `Th` with `The `; Backspace produced `The`, Enter produced a newline, and the toolbar's Ctrl+A selected remote text.
- Native Windows telemetry confirmed a direct tap generated exactly one down/up pair; double-tap generated neither click; direct dragging selected 40 characters. Trackpad pointer motion was observed. A stationary trackpad long press generated balanced down/up events, but full trackpad drag-and-drop is not claimed.
- Double-tap hid/restored controls in fullscreen, normal viewing, Touch Lock, and View Only. Locked double-taps and a subsequent swipe did not move the remote cursor or increment click counts. Long-press unlock succeeded.
- Android PiP entry and return through the OS expand control passed. Privacy Pause stopped Sunshine and cleared displayed video to black. Resume succeeded without pairing again.
- Reconnect and explicit Disconnect followed by remembered-pairing reconnect passed. Revocation interrupted active playback, emptied the companion's paired-device list, and the old client was rejected by TLS (`TLSV1_ALERT_UNKNOWN_CA`). Fresh pairing succeeded afterwards.
- Ten assertions against saved hardware evidence passed: unchanged endpoint assignments, unchanged Windows mute states, unchanged display bounds/primary monitor, unchanged Discord process IDs/start times, click suppression, dragging, trackpad motion, locked-input suppression, and no held mouse buttons/modifiers.
- The full isolated Windows integration suite was rerun after the hands-on checks: **35/35 passed**, including authentication, revocation, privacy, both monitors, temporary power-request release, and unchanged audio assignments. Unsupported YUV444 encoder probes logged errors while supported stream encoders and the checks succeeded.
- The Android UI inspection helper now uses a unique dump filename and rejects failed dumps, preventing stale files from being reused. Only test helpers changed; the tested release EXE/APK binaries are unchanged.

Discord was never restarted, closed, muted, deafened, or used as a text-entry target. No Windows/Discord audio-device changes, microphone mute toggles, speaker mute toggles, system-volume commands, media commands, or PC locking were performed. Discord process continuity and Windows endpoint/mute preservation are verified; a direct assertion of remote participants' call continuity or perceived audio quality is not claimed.

## Automated checks

- **17 Java model tests passed:** deferred composition, committed text, duplicate notifications, correction/replacement, Unicode/emoji, deletion, selection replacement, focus reset, shortcut parsing, and coordinate transforms for letterboxing/rotation/zoom/pan.
- **17 Windows core checks passed.** The complete Windows integration run passed **35 checks including those core checks**: host startup, both physical monitors, unchanged display topology, mutual TLS, missing/different/disabled/revoked identities, unavailable LAN administration port, cursor query, temporary sleep requests, privacy stop/resume, unchanged audio endpoint assignments, and explicit disconnect.
- The revocation test uses an already-established HTTPS client connection. Removing its pairing prevents further API requests. An empty pairing list is also rejected correctly.
- Both development applications compiled incrementally. Immutable checkpoints are retained in `artifacts/checkpoints/phase1-core`, `phase2-verified-controls`, and `phase3-verified` with source hashes.

## Earlier 0.1.0 development-device checks

Test device: Samsung Galaxy A17 **SM-A176W**, Android **16**, connected through wireless ADB. These were real device checks, not emulator screenshots.

Streaming/control checks used development builds. The final signed release APK was separately installed and its Home activity launched successfully. The packaged self-contained Windows EXE also completed diagnostics with exit code 0 and resolved its bundled Sunshine runtime correctly.

- APK installation, secure pairing, remembered pairing across development updates, monitor listing, and monitor switching succeeded.
- Video was received from **AW2725QF (3840×2160)** and **VG27VQ3B (1920×1080)**. NVENC encoding and the phone's **c2.exynos.hevc.decoder** hardware decoder were observed.
- **720p/30** and **1080p/60** playback were observed on the phone. FPS/latency values displayed by the app came from upstream measurements; no end-to-end touch latency measurement is claimed.
- Double-tap hid/restored controls in fullscreen, normal viewing, landscape, and Touch Lock. The unlock control remained available while the main toolbar was hidden, and deliberate long-press unlocking succeeded.
- Landscape rotation retained live video. Fullscreen exit/re-entry worked. Android PiP entry was confirmed by the OS reporting a visible **pinned** streaming activity.
- Privacy Pause stopped the host process/encoding and displayed the paused screen. Resuming through the remembered connection succeeded without pairing again.
- The manual Keyboard button opened the installed **Samsung Keyboard**, with the NyxLink toolbar above it and the viewing area resized.
- Eight exact Windows-side assertions passed through the phone's real native editor/InputConnection, encrypted streaming input, and Sunshine input injection: deferred composition, committed `the `, corrected `The `, `The 世界 😀`, emoji deletion, ordinary deletion, and Ctrl+A replacement ending in `Shortcuts OK`.

For deterministic composition operations, the instrumentation test detached the live Samsung IME after checking that it opened, then exercised the editor's InputConnection. This avoids Samsung IME reacting to a second synthetic input client. A focus guard permitted each operation only while the isolated Windows test field was selected, and checked the actual Windows text before advancing.

## Limits of the testing

Gboard is not installed on the phone. Natural swipe typing, voice dictation, typing into Discord/Windows Search/browser fields, physical volume-button long presses, every custom shortcut, pinch/multifinger scrolling gestures, trackpad drag-and-drop, and screen-off/lock recovery were not individually verified on hardware. Their implemented paths reuse the native IME and authenticated input/streaming components; application-specific behavior still needs user validation.

Windows speaker/microphone controls and Discord shortcuts were not toggled during testing to avoid interrupting existing playback or calls. Audio packets and AudioTrack initialization were observed; an audible sound-quality assessment is not claimed. OS limitations for elevated/secure desktops and protected video remain documented in the README.

## Release verification

The packaging script publishes a self-contained Windows EXE, runs Gradle's release task, verifies the resulting APK signature, packages the exact runtime/source/licenses, and promotes only a complete candidate. Successful output is recorded in `RELEASE-VERIFICATION.json`; artifact hashes are in `SHA256SUMS.txt`. Detailed development logs and physical-device evidence remain in the workspace's `artifacts` directory.
