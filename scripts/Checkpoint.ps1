param([Parameter(Mandatory=$true)][string]$Name)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
if ($Name -notmatch '^[a-zA-Z0-9-]+$') { throw 'Checkpoint name must contain only letters, numbers, and hyphens.' }
$destination = Join-Path $root "artifacts/checkpoints/$Name"
if (Test-Path $destination) { throw 'Checkpoint already exists; passing builds are immutable.' }
$apk = Join-Path $root 'src/android/app/build/outputs/apk/nonRoot/debug/app-nonRoot-debug.apk'
$windows = Join-Path $root 'src/windows/bin/Debug/net10.0-windows'
if (!(Test-Path $apk) -or !(Test-Path "$windows/NyxLink-Windows.exe")) { throw 'Both development builds are required.' }
New-Item -ItemType Directory -Path "$destination/windows" -Force | Out-Null
Copy-Item -Path "$windows/*" -Destination "$destination/windows" -Recurse
Copy-Item -LiteralPath $apk -Destination "$destination/NyxLink-Android-debug.apk"
Copy-Item -Path "$root/artifacts/*tests*.json","$root/artifacts/*integration.log" -Destination $destination -ErrorAction SilentlyContinue
$files = Get-ChildItem -LiteralPath "$root/src","$root/tests","$root/scripts" -Recurse -File | Where-Object { $_.FullName -notmatch '\\(build|bin|obj|\.git|\.gradle|\.cxx)\\' -and $_.Name -ne 'local.properties' }
$hashes = $files | ForEach-Object { [PSCustomObject]@{ path=$_.FullName.Substring($root.Length+1); sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash } }
$hashes | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath "$destination/source-hashes.json"
Copy-Item "$root/dependencies.lock.json" $destination
Write-Output "Preserved $destination"
