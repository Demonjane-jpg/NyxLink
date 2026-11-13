$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$sdk=Join-Path $root '.tools/android'
$java=(Get-ChildItem "$root/.tools/java" -Directory|Select-Object -First 1).FullName
$env:JAVA_HOME=$java
$out=Join-Path $root 'artifacts/android-driver'
New-Item -ItemType Directory -Force "$out/classes","$out/dex"|Out-Null
$jar="$sdk/platforms/android-36/android.jar"
& "$java/bin/javac.exe" -encoding UTF-8 -source 8 -target 8 -classpath $jar -d "$out/classes" "$root/tests/android-driver/GestureRunner.java" "$root/tests/android-driver/ClipboardActivity.java"
if($LASTEXITCODE){throw 'Driver Java compilation failed'}
& "$java/bin/jar.exe" cf "$out/classes.jar" -C "$out/classes" .
& "$sdk/build-tools/36.0.0/d8.bat" --lib $jar --min-api 26 --output "$out/dex" "$out/classes.jar"
if($LASTEXITCODE){throw 'Driver DEX compilation failed'}
& "$sdk/build-tools/36.0.0/aapt2.exe" link --manifest "$root/tests/android-driver/AndroidManifest.xml" -I $jar -o "$out/unsigned.apk"
if($LASTEXITCODE){throw 'Driver manifest linking failed'}
& "$java/bin/jar.exe" uf "$out/unsigned.apk" -C "$out/dex" classes.dex
if(!(Test-Path "$out/test.p12")){& "$java/bin/keytool.exe" -genkeypair -keystore "$out/test.p12" -storepass test-only -keypass test-only -alias fixture -keyalg RSA -validity 365 -dname 'CN=NyxLink test fixture' |Out-Null}
& "$sdk/build-tools/36.0.0/apksigner.bat" sign --ks "$out/test.p12" --ks-key-alias fixture --ks-pass pass:test-only --out "$out/driver.apk" "$out/unsigned.apk"
if($LASTEXITCODE){throw 'Driver signing failed'}
