#Requires -Version 7.3
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
New-Item -ItemType Directory -Force "$root/.tools/java","$root/.tools/android/cmdline-tools","$root/vendor/sunshine" | Out-Null
function Fetch([string]$Url,[string]$Path,[string]$Sha256='') {
    if(!(Test-Path $Path)){Invoke-WebRequest -Uri $Url -OutFile $Path}
    if($Sha256 -and (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash -ine $Sha256){throw "Checksum mismatch: $Path"}
}
if(!(Get-ChildItem "$root/.tools/java" -Directory|Select-Object -First 1)) {
    Fetch 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_windows_hotspot_21.0.12.1_1.zip' "$root/.tools/jdk.zip" 'f9d6e191ab098c0d416e7d588a24420a8621cd2f4720dab2459b8b7b2d2d8b4e'
    tar.exe -xf "$root/.tools/jdk.zip" -C "$root/.tools/java"
    if($LASTEXITCODE -ne 0){throw 'Java extraction failed.'}
}
if(!(Test-Path "$root/.tools/android/cmdline-tools/latest/bin/sdkmanager.bat")) {
    Fetch 'https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip' "$root/.tools/android-commandline.zip" '98b565cb657b012dae6794cefc0f66ae1efb4690c699b78a614b4a6a3505b003'
    tar.exe -xf "$root/.tools/android-commandline.zip" -C "$root/.tools/android/cmdline-tools"
    if($LASTEXITCODE -ne 0){throw 'Android tools extraction failed.'}
    Move-Item -LiteralPath "$root/.tools/android/cmdline-tools/cmdline-tools" -Destination "$root/.tools/android/cmdline-tools/latest"
}
$env:JAVA_HOME=(Get-ChildItem "$root/.tools/java" -Directory|Select-Object -First 1).FullName
$sdkRoot=Join-Path $root '.tools/android'
Write-Output 'Review the Android SDK licenses when prompted.'
& "$sdkRoot/cmdline-tools/latest/bin/sdkmanager.bat" "--sdk_root=$sdkRoot" --licenses
& "$sdkRoot/cmdline-tools/latest/bin/sdkmanager.bat" "--sdk_root=$sdkRoot" 'platform-tools' 'platforms;android-36' 'build-tools;36.0.0' 'ndk;29.0.14206865'
if($LASTEXITCODE -ne 0){throw 'SDK package installation failed.'}
if(!(Test-Path "$root/vendor/sunshine/Sunshine/sunshine.exe")) {
    Fetch 'https://github.com/LizardByte/Sunshine/releases/download/v2026.914.233613/Sunshine-Windows-AMD64-lite.zip' "$root/.tools/sunshine.zip" '233008e46f4c0e501a586cbfd6c4fd4a4c0d414a0b5fc7f13c070eb92ec3824b'
    tar.exe -xf "$root/.tools/sunshine.zip" -C "$root/vendor/sunshine"
    if($LASTEXITCODE -ne 0){throw 'Sunshine extraction failed.'}
}
Write-Output 'Build dependencies ready. Install the .NET 10 SDK separately if dotnet is unavailable.'
