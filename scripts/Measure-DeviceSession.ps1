param([int]$Minutes=15,[string]$OutputName='session-samples.jsonl')
$ErrorActionPreference='Stop'
. "$PSScriptRoot/Device-UI.ps1"
$root=Split-Path $PSScriptRoot -Parent
$output=Join-Path $root ('artifacts/extended-tests/'+[IO.Path]::GetFileName($OutputName))
$finish=[DateTime]::UtcNow.AddMinutes($Minutes)
do {
 $battery=(& $script:BedMirrorAdb -s $script:BedMirrorSerial shell dumpsys battery)-join "`n"
 $activity=[string](& $script:BedMirrorAdb -s $script:BedMirrorSerial shell dumpsys activity activities|Select-String 'topResumedActivity='|Select-Object -First 1)
 $appPid=(& $script:BedMirrorAdb -s $script:BedMirrorSerial shell pidof dev.bedmirror.android)-join ' '
 function Number([string]$name){$m=[regex]::Match($battery,'(?m)^\s*'+[regex]::Escape($name)+':\s*(-?\d+)');if($m.Success){return [int]$m.Groups[1].Value};return $null}
 [pscustomobject]@{utc=[DateTime]::UtcNow.ToString('o');level=Number 'level';temperatureTenthsC=Number 'temperature';chargeCounter=Number 'Charge counter';appPid=$appPid;streamActivity=$activity.Contains('dev.bedmirror.android/com.limelight.Game');hostRunning=[bool](Get-Process sunshine -ErrorAction SilentlyContinue)}|ConvertTo-Json -Compress|Add-Content -LiteralPath $output
 if([DateTime]::UtcNow -ge $finish){break};Start-Sleep -Seconds 20
}while($true)
