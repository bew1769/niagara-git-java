# Create a self-signed code-signing cert and sign a Niagara module jar.
# The keystore is written under signing\ and is gitignored. Never commit it.
#
# Usage:
#   .\scripts\sign-module.ps1 -Jar niagara-git-rt\build\libs\niagara-git-rt.jar
param(
  [Parameter(Mandatory = $true)]
  [string]$Jar
)

$ErrorActionPreference = "Stop"
if (-not (Test-Path -LiteralPath $Jar)) {
  Write-Error "jar not found: $Jar"
}

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$signing = Join-Path $root "signing"
$alias = if ($env:NIAGARA_GIT_ALIAS) { $env:NIAGARA_GIT_ALIAS } else { "niagara-git" }
$dname = if ($env:NIAGARA_GIT_DNAME) { $env:NIAGARA_GIT_DNAME } else { "CN=Bryan Williams, OU=Controls, O=bew1769, C=US" }
$ks = Join-Path $signing "niagara-git-dev.jks"
$passfile = Join-Path $signing "password.txt"
$cer = Join-Path $signing "niagara-git.cer"

New-Item -ItemType Directory -Force -Path $signing | Out-Null
if (-not (Test-Path -LiteralPath $passfile)) {
  $bytes = New-Object byte[] 24
  [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
  [Convert]::ToBase64String($bytes) | Set-Content -LiteralPath $passfile -NoNewline
  Write-Host "wrote $passfile (gitignored). Keep this with the keystore."
}
$storepass = (Get-Content -LiteralPath $passfile -Raw).Trim()

if (-not (Test-Path -LiteralPath $ks)) {
  & keytool -genkeypair `
    -alias $alias `
    -keyalg RSA `
    -keysize 2048 `
    -validity 3650 `
    -keystore $ks `
    -storetype JKS `
    -storepass $storepass `
    -keypass $storepass `
    -dname $dname
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
  Write-Host "created $ks"
}

& keytool -exportcert -alias $alias -keystore $ks -storepass $storepass -file $cer
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host "exported public cert $cer"

& jarsigner `
  -keystore $ks `
  -storetype JKS `
  -storepass $storepass `
  -keypass $storepass `
  -digestalg SHA-256 `
  -sigalg SHA256withRSA `
  $Jar `
  $alias
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

& jarsigner -verify $Jar
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host "signed $Jar"
Write-Host "Trust $cer on the JACE before Software Manager will install the module."
