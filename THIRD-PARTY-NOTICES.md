# Third-party software

NyxLink Android is a modified Moonlight Android client, licensed under GPL-3.0-or-later. Upstream: https://github.com/moonlight-stream/moonlight-android, v12.2, commit b48494cb96bff23d8886c4775cc4f39a1075495d. NyxLink uses its own application ID and signing identity and is not an official Moonlight release.

Bundled Sunshine is an unmodified LizardByte portable Windows runtime, GPL-3.0-or-later, v2026.914.233613, commit 63d35f702ee9e362e43263742981836ec0710384. Upstream: https://github.com/LizardByte/Sunshine. Its complete runtime assets and matching host source/build definitions are retained. No host display-changing preparation commands are configured.

Moonlight's native core includes moonlight-common-c (GPL-3.0-or-later), ENet (MIT), nanors, Opus (BSD-style), OpenSSL, and their existing notices/build definitions. Android dependencies include Bouncy Castle (MIT), OkHttp/Okio (Apache-2.0), JCodec (BSD-style), JmDNS (Apache-2.0), and Android desugaring support. Preserve the notices in the supplied source and runtime trees if redistributing.

The .NET Windows companion uses the .NET runtime and Windows Desktop/ASP.NET shared frameworks under their accompanying Microsoft/.NET licenses. Personal companion source additions are provided under GPL-3.0-or-later, consistent with the Android project. See the included GPL license copies for terms and warranty limitations.

The source archive includes NyxLink changes, upstream Android/native sources, matching Sunshine host source, exact upstream revisions, and build scripts. Sunshine's additional upstream dependencies can be retrieved using its `.gitmodules`, package manifests, and build definitions at the recorded commit. The SDK, NDK, JDK, caches, signing keys, pairing credentials, and test-only tools are not redistributed in this archive.
