#Requires -RunAsAdministrator
$ErrorActionPreference = 'Stop'
$exe = Join-Path $PSScriptRoot 'NyxLink-Windows.exe'
if (!(Test-Path $exe)) { throw 'Run this script from the extracted NyxLink Windows package.' }
$group='NyxLink private LAN'
Get-NetFirewallRule -Group $group -ErrorAction SilentlyContinue | Remove-NetFirewallRule
New-NetFirewallRule -DisplayName 'NyxLink private LAN TCP' -Group $group -Direction Inbound -Action Allow -Program $exe -Protocol TCP -LocalPort 47984,47989,47995,48010 -RemoteAddress LocalSubnet,100.64.0.0/10 -Profile Any | Out-Null
New-NetFirewallRule -DisplayName 'NyxLink private LAN UDP' -Group $group -Direction Inbound -Action Allow -Program $exe -Protocol UDP -LocalPort 47996,47998,47999,48000 -RemoteAddress LocalSubnet,100.64.0.0/10 -Profile Any | Out-Null
Write-Output 'Allowed NyxLink from the local subnet and authorized private network (100.64.0.0/10). Normal app use does not require administrator privileges.'
