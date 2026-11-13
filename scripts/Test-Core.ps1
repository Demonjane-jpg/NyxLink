$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$java = (Get-ChildItem "$root/.tools/java" -Directory | Select-Object -First 1).FullName
New-Item -ItemType Directory -Force "$root/artifacts/java-tests" | Out-Null
& "$java/bin/javac.exe" -encoding UTF-8 -d "$root/artifacts/java-tests" "$root/src/android/app/src/main/java/com/limelight/bedmirror/KeyChord.java" "$root/src/android/app/src/main/java/com/limelight/bedmirror/TextSynchronizer.java" "$root/src/android/app/src/main/java/com/limelight/bedmirror/ViewportMapper.java" "$root/tests/java/BedMirrorCoreTests.java"
if ($LASTEXITCODE -ne 0) { throw 'Java test compilation failed.' }
& "$java/bin/java.exe" -cp "$root/artifacts/java-tests" BedMirrorCoreTests
if ($LASTEXITCODE -ne 0) { throw 'Java core tests failed.' }
dotnet run --project "$root/tests/windows/BedMirror.Tests.csproj"
if ($LASTEXITCODE -ne 0) { throw 'Windows core tests failed.' }
