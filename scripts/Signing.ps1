$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$private = Join-Path $root '.private'
New-Item -ItemType Directory -Force $private | Out-Null
$security = New-Object System.Security.AccessControl.DirectorySecurity
$security.SetAccessRuleProtection($true,$false)
$security.AddAccessRule((New-Object System.Security.AccessControl.FileSystemAccessRule([System.Security.Principal.WindowsIdentity]::GetCurrent().User,'FullControl','ContainerInherit,ObjectInherit','None','Allow')))
try { (Get-Item -LiteralPath $private).SetAccessControl($security) } catch { }
$passwordFile = Join-Path $private 'signing-password.dpapi'
if (!(Test-Path $passwordFile)) {
    $bytes = New-Object byte[] 32
    [System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
    $secure = ConvertTo-SecureString ([Convert]::ToBase64String($bytes)) -AsPlainText -Force
    ConvertFrom-SecureString $secure | Set-Content -LiteralPath $passwordFile
}
$secure = ConvertTo-SecureString (Get-Content -LiteralPath $passwordFile -Raw).Trim()
$pointer = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try { $env:BEDMIRROR_SIGNING_PASSWORD = [System.Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
finally { [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
$env:BEDMIRROR_KEYSTORE = Join-Path $private 'bedmirror.p12'
if (!(Test-Path $env:BEDMIRROR_KEYSTORE)) {
    $java = (Get-ChildItem "$root/.tools/java" -Directory | Select-Object -First 1).FullName
    & "$java/bin/keytool.exe" -genkeypair -alias bedmirror -keyalg RSA -keysize 3072 -validity 3650 -dname 'CN=BedMirror Personal Build' -storetype PKCS12 -keystore $env:BEDMIRROR_KEYSTORE -storepass:env BEDMIRROR_SIGNING_PASSWORD -keypass:env BEDMIRROR_SIGNING_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw 'Could not create the private Android signing key.' }
}
