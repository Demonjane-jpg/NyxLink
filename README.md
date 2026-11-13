# NyxLink

NyxLink mirrors an existing Windows monitor to an Android phone over your local network. It uses Sunshine for capture/NVENC/streaming and a customized Moonlight Android client for hardware decoding and input.

## Download and install

Download the Windows package and Android APK from the [NyxLink releases page](https://github.com/Demonjane-jpg/NyxLink/releases/latest).

1. On your PC, download and extract **NyxLink-Windows.zip**. Keep the extracted `sunshine` folder alongside `NyxLink-Windows.exe`.
2. Open **NyxLink-Windows.exe**. If Windows Firewall asks, allow access on your private network. The companion normally runs as your Windows user.
3. On your Android phone, download and open **NyxLink-Android.apk**. If Android asks, allow installation from the app you used to download it, then return to the APK and install it. The APK supports ARM64 devices running Android 8 or later.

## Pair and connect

1. Connect the PC and phone to the same local network, and open NyxLink on both devices.
2. On the phone, find your PC under **Nearby PCs** and tap **Pair**.
3. A five-digit code appears in the Windows app. Enter it on the phone and tap **Pair**. The desktop stream then opens.
4. Next time, tap **Connect** on the saved PC. You do not need another code while the pairing remains valid.

To cancel a pairing attempt, tap **Stop pairing**. To remove a pairing, choose **Forget device** on the phone or revoke the device on the PC. Pair again to restore access after forgetting or revoking it.

If the PC is missing, check that the Windows app is running, both devices are on the same network, and your network allows devices to communicate with each other. Tap **Refresh nearby PCs**. If a saved device is rejected, verify you selected the correct PC and pair it again if its pairing was revoked.

NyxLink uses its own host configuration and keeps your existing monitor topology, resolution, refresh rate, and HDR settings. Local use requires no cloud account, virtual display, or virtual audio driver.

For access from another network, first pair locally. The optional **Remote Access** settings provide setup instructions for a private encrypted network such as Tailscale on both devices. In **Streaming settings → Remote Access**, enable remote access and configure the PC's private remote address if needed.

## Optional crash reports and diagnostics

Automatic crash reports and anonymous diagnostics are **off by default**. You can turn either option on or off at any time in **Streaming settings → Privacy & Feedback** on the phone. Bug reports, feedback, and feature requests are submitted only when you choose to send them. **No contact information is required.** An email address is optional and is used only if you choose to provide it.

An automatic crash report contains information about the error, such as its time, error and stack trace, app version, Android version, and device model. If you separately enable anonymous diagnostics, reports can also include device and performance details such as memory, display resolution, and hardware information. A bug report can include the description and steps you enter, plus an optional screenshot and contact email if you choose to provide them.

Reports are sent over the authenticated encrypted connection to your paired Windows companion and can be viewed or deleted in its **Developer Dashboard**. If the PC is unavailable, a report may be queued on the phone for a later connection. Turning off automatic crash reporting clears pending reports on the phone. Reports already saved on the PC can be deleted from the dashboard.

## Viewing and control

- Double-tap the viewing surface to hide or restore controls. This remains available in fullscreen, View Only, and Touch Lock. Single clicks wait for Android's double-tap recognition window; use **Actions → Remote double-click** for a Windows double-click.
- The top toolbar scrolls horizontally. It provides Keyboard, Touch mode, Touch Lock, Monitor, Fullscreen, Rotate, Actions, Privacy, Options, Reconnect, and Disconnect.
- **Direct touch** moves the Windows pointer to your finger. Tap with one finger to left-click, tap with two fingers to right-click, and hold then move to drag. **Trackpad** moves the pointer relative to your finger. Two-finger swipes scroll.
- Pinch to magnify. While magnified, move two fingers to pan. **Options → Fit / reset zoom** restores the full monitor. Coordinates account for letterboxing, rotation, zoom, pan, and the keyboard.
- Touch Lock blocks remote interaction. Its small **Hold to unlock** control remains available when the toolbar is hidden. Double-tap restores the toolbar without unlocking.
- **Keyboard** opens the phone's installed keyboard. The toolbar above it provides Esc, Tab, Ctrl, Alt, Shift, Windows, arrows, and Hide; scroll it horizontally to reach all keys. Modifier buttons show a dot while selected. Predictive composition stays local until committed; committed corrections update the known text instead of adding another copy.
- **Actions** provides Discord's configured keybind, browser fullscreen, Alt+Tab, Escape, Show desktop, media, Lock Windows, clipboard paste, and three configurable shortcut buttons. Configure Discord's keybind to match your own Discord settings.
- **Privacy** hides the displayed video, mutes phone stream audio, and blocks remote input. Tap Privacy again to resume viewing.
- **Options** contains Windows volume/media controls, separate Windows microphone mute, phone audio/volume, window brightness, cursor sensitivity/location, View Only, PiP, battery mode, sleep prevention, statistics, and click feedback.

## Audio and power

Windows playback and Discord devices are preserved. PC audio continues playing. Phone mute and phone stream volume affect only NyxLink's AudioTrack. Windows microphone mute targets the default **communications** capture endpoint; Discord mute is a separate configurable shortcut and never changes Discord devices.

Audio capture failure leaves video usable. No audio driver is installed. Sunshine has an upstream startup behavior that redirects an already-default Steam Streaming Speakers endpoint; NyxLink detects that configuration and refuses to start rather than silently changing it. Choose a physical playback device yourself if you encounter that explicit message.

PC sleep prevention uses a temporary operating-system power request, expires when heartbeats stop, and is released on pause/disconnect. It does not change power plans or prevent Windows from locking. Phone brightness is window-local; System default restores the phone's normal brightness. Phone volume buttons retain ordinary behavior unless the optional long-press mapping is enabled.

## Connection and privacy details

Sunshine listens only on loopback. NyxLink forwards the required encrypted streaming/control traffic on the selected private IPv4 address: TCP 47984, 47989, 48010 and UDP 47998–48000. Discovery uses UDP 47996 and only answers requests from the selected interface's local subnet. The pairing code uses Sunshine's four-digit PIN plus a fifth check digit for typos. The discovery packets contain neither. Sunshine administration stays on loopback port 47990. The companion API uses mutual TLS on port 47995 and checks the paired certificate on every request. Revoke a paired phone or disconnect it in the Windows companion.

Windows credentials/configuration live in `%LOCALAPPDATA%\BedMirror`, protected for the current Windows user. Android pairing keys are encrypted with Android Keystore. Do not share these credentials. Stream content and typed text are not uploaded to a cloud service.

## Practical limits

- Nearby PCs requires both devices to be on the same private IPv4 subnet with local discovery traffic allowed. Use the Keyboard button to open the Android keyboard.
- The phone's **Monitor** button switches physical outputs inside the current stream. Windows applications remain running and the display configuration stays in place. A differently shaped monitor may add black bars inside the existing video frame. Quality changes can reconnect the stream.
- Automatic quality conservatively drops an unstable higher-quality session to 720p/30; it does not change the PC monitor mode. Battery mode also uses 720p/30. Native capture may reduce repeated frames when idle.
- PiP displays video with minimal interaction. Return to the main activity for remote control. Android navigation can be recovered with the normal system edge gesture.
- Screen-off or leaving the activity without PiP may end streaming. Return and choose the remembered monitor; pairing is retained. Network recovery retries automatically and also has a manual Reconnect button.
- Native cursor capture remains enabled. Locate cursor and click feedback supplement it; hiding an already-encoded cursor is not offered.
- IME corrections apply to the text NyxLink has tracked in the current typing context. Clicking elsewhere or using shortcuts clears that context. Emoji rendering and editing still depend on the Windows application.
- UAC/secure desktops, elevated applications, protected video, and OS lock screens can restrict capture/input. NyxLink does not bypass these restrictions.
- Personal builds are not commercially code-signed. See `TEST-REPORT.md` for what was actually tested.

## Develop without full rebuilds

Source builds require PowerShell 7.3 or later and the .NET 10 SDK. Run `scripts/Setup-Tools.ps1` to install the pinned project-local Java/Android tools and portable host. Review the SDK licenses when prompted.

`scripts/Android-Build.ps1` uses the project-local Java/SDK/NDK and a reversible `B:` drive mapping because ndk-build cannot handle spaces in paths. `scripts/Test-Core.ps1` runs the focused Java models and Windows core tests. Run `dotnet run --project tests/windows/BedMirror.Tests.csproj -- --integration` at phase boundaries, with the companion stopped so test ports are available.

Use normal incremental `dotnet build` and Gradle tasks; do not clean between features. `scripts/Checkpoint.ps1 -Name <unique-name>` preserves development binaries and their source hashes and refuses to overwrite an existing checkpoint. Passing checkpoints remain in `artifacts/checkpoints` when later work fails.

`scripts/Package.ps1` publishes self-contained Windows x64 output, builds and verifies the signed release APK, packages source/runtime/licenses, and promotes a complete candidate to `dist`. It preserves any previous distribution. `.private/bedmirror.p12` and its DPAPI-protected password are the personal signing credentials; keep a private backup. They are excluded from the source archive and distribution. Future Android updates must use the same signing key.

Upstream versions and exact commits are recorded in `dependencies.lock.json`. Build the vendored Android tree with its included native submodule sources. Sunshine is shipped as its verified upstream portable runtime; matching host source and its dependency-fetch/build definitions are included in the source archive.
