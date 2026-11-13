param([string]$Serial='')
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$adb=Join-Path $root '.tools/android/platform-tools/adb.exe'
if(!$Serial){$connected=@(& $adb devices | Where-Object {$_ -match '^\S+\s+device$'});if($connected.Count -ne 1){throw 'Specify one test phone serial explicitly.'};$Serial=($connected[0]-split '\s+')[0]}
$log=Join-Path $root 'artifacts/device-input-instrumentation.log'
$errorLog=Join-Path $root 'artifacts/device-input-instrumentation-error.log'
$process=Start-Process -FilePath $adb -ArgumentList '-s',$Serial,'shell','am','instrument','-w','-e','class','com.limelight.bedmirror.DeviceInputTest','dev.bedmirror.android.debug.test/androidx.test.runner.AndroidJUnitRunner' -WindowStyle Hidden -PassThru -RedirectStandardOutput $log -RedirectStandardError $errorLog
$lastGate=''
$checks=@()
$expected=@{compose='';commit='';correct='the ';unicode='The ';deleteEmoji='The 世界 😀';deleteSpace='The 世界 ';shortcut='The 世界'}
$deadline=[DateTime]::UtcNow.AddMinutes(8)
try {
    while(!$process.HasExited -and [DateTime]::UtcNow -lt $deadline) {
        $content=Get-Content -LiteralPath $log -Raw -ErrorAction SilentlyContinue
        if(!$content){Start-Sleep -Milliseconds 150;$process.Refresh();continue}
        $gates=[regex]::Matches([string]$content,'bedmirrorGate=([^\r\n]+)')
        if($gates.Count -gt 0) {
            $gate=$gates[$gates.Count-1].Groups[1].Value
            if($gate -ne $lastGate) {
                try { $target=Get-Content -LiteralPath "$root/artifacts/input-target-state.json" -Raw | ConvertFrom-Json } catch { $target=$null }
                if($target -and $target.foreground) {
                    if($expected.ContainsKey($gate)) { if($target.text -cne $expected[$gate]){throw "Windows text mismatch before $gate. Expected [$($expected[$gate])], received [$($target.text)]"};$checks+=@{name=$gate;passed=$true;windowsText=$target.text} }
                    & $adb -s $Serial shell run-as dev.bedmirror.android.debug touch files/bedmirror-test-continue
                    if($LASTEXITCODE -ne 0){throw 'Could not signal the test gate.'}
                    $lastGate=$gate
                    Write-Output "Focused target confirmed: $gate"
                }
            }
        }
        Start-Sleep -Milliseconds 150
        $process.Refresh()
    }
    if(!$process.HasExited){throw 'Typing test timed out waiting for focus.'}
    $target=Get-Content -LiteralPath "$root/artifacts/input-target-state.json" -Raw | ConvertFrom-Json
    if($target.text -cne 'Shortcuts OK'){throw 'Windows did not receive the final shortcut replacement.'}
    $checks+=@{name='Ctrl+A replacement';passed=$true;windowsText=$target.text}
    $checks|ConvertTo-Json|Set-Content "$root/artifacts/device-input-tests.json"
    Get-Content -LiteralPath $log -Tail 16
} finally { if(!$process.HasExited){Stop-Process -Id $process.Id} }
