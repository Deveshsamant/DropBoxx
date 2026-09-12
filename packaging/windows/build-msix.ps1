<#
.SYNOPSIS
  Wraps the Compose Desktop app-image into an MSIX package for the Microsoft Store.
.EXAMPLE
  ./gradlew :desktopApp:createReleaseDistributable
  powershell -File packaging/windows/build-msix.ps1 -Version 1.0.0.0 -IdentityName 12345Publisher.DropBoxx -Publisher "CN=ABCDEF12-3456-..." -PublisherDisplayName "Your Name"
  # add -SelfSign to install locally for testing (creates + trusts a dev certificate)
#>
param(
  [string]$Version = "1.0.0.0",
  [string]$IdentityName = "DropBoxx.Dev",
  [string]$Publisher = "CN=DropBoxx Dev",
  [string]$PublisherDisplayName = "DropBoxx",
  [switch]$SelfSign
)
$ErrorActionPreference = "Stop"
$root = Resolve-Path "$PSScriptRoot\..\.."
$appImage = Get-ChildItem "$root\desktopApp\build\compose\binaries\main-release\app\DropBoxx" -ErrorAction SilentlyContinue
if (-not $appImage) { $appImage = Get-ChildItem "$root\desktopApp\build\compose\binaries\main\app\DropBoxx" -ErrorAction SilentlyContinue }
if (-not $appImage) { throw "Run ./gradlew :desktopApp:createReleaseDistributable first" }

$sdkBin = Get-ChildItem "${env:ProgramFiles(x86)}\Windows Kits\10\bin\10.*\x64" -ErrorAction SilentlyContinue | Sort-Object Name -Descending | Select-Object -First 1
if (-not $sdkBin) { throw "Windows SDK not found (needs makeappx.exe). Install 'Windows 11 SDK' from the Visual Studio Installer or 'winget install Microsoft.WindowsSDK'." }
$makeappx = Join-Path $sdkBin.FullName "makeappx.exe"
$signtool = Join-Path $sdkBin.FullName "signtool.exe"

$stage = "$root\desktopApp\build\msix\stage"
$out = "$root\desktopApp\build\msix\DropBoxx.msix"
if (Test-Path $stage) { Remove-Item $stage -Recurse -Force }
New-Item -ItemType Directory -Force "$stage\Assets" | Out-Null
Copy-Item $appImage.FullName "$stage\DropBoxx" -Recurse

# Manifest with the identity Partner Center assigned.
$manifest = Get-Content "$PSScriptRoot\AppxManifest.xml" -Raw
$manifest = $manifest.Replace("__IDENTITY_NAME__", $IdentityName).Replace("__PUBLISHER__", $Publisher).Replace("__VERSION__", $Version).Replace("__PUBLISHER_DISPLAY__", $PublisherDisplayName)
Set-Content "$stage\AppxManifest.xml" $manifest -Encoding UTF8

# Tile assets rendered from the vector logo (solid indigo tile with white arrow).
Add-Type -AssemblyName System.Drawing
function Tile($w, $h, $file) {
  $bmp = New-Object System.Drawing.Bitmap $w, $h
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.SmoothingMode = "AntiAlias"
  $g.Clear([System.Drawing.Color]::FromArgb(255, 79, 91, 213))
  $s = [Math]::Min($w, $h) * 0.55; $cx = $w / 2; $cy = $h / 2
  $pts = @([System.Drawing.PointF]::new($cx, $cy - $s/2), [System.Drawing.PointF]::new($cx + $s/2, $cy), [System.Drawing.PointF]::new($cx + $s/6, $cy),
           [System.Drawing.PointF]::new($cx + $s/6, $cy + $s/2), [System.Drawing.PointF]::new($cx - $s/6, $cy + $s/2), [System.Drawing.PointF]::new($cx - $s/6, $cy), [System.Drawing.PointF]::new($cx - $s/2, $cy))
  $g.FillPolygon([System.Drawing.Brushes]::White, $pts)
  $bmp.Save($file, [System.Drawing.Imaging.ImageFormat]::Png); $g.Dispose(); $bmp.Dispose()
}
Tile 50 50 "$stage\Assets\StoreLogo.png"
Tile 150 150 "$stage\Assets\Square150x150Logo.png"
Tile 44 44 "$stage\Assets\Square44x44Logo.png"
Tile 310 150 "$stage\Assets\Wide310x150Logo.png"

& $makeappx pack /d $stage /p $out /o
if ($LASTEXITCODE -ne 0) { throw "makeappx failed" }

if ($SelfSign) {
  $cert = Get-ChildItem Cert:\CurrentUser\My | Where-Object { $_.Subject -eq $Publisher } | Select-Object -First 1
  if (-not $cert) {
    $cert = New-SelfSignedCertificate -Type Custom -Subject $Publisher -KeyUsage DigitalSignature -FriendlyName "DropBoxx dev" -CertStoreLocation Cert:\CurrentUser\My -TextExtension @("2.5.29.37={text}1.3.6.1.5.5.7.3.3", "2.5.29.19={text}")
    Export-Certificate -Cert $cert -FilePath "$root\desktopApp\build\msix\dev.cer" | Out-Null
    Write-Host "Trust the dev certificate once (admin PowerShell): Import-Certificate -FilePath desktopApp\build\msix\dev.cer -CertStoreLocation Cert:\LocalMachine\Root"
  }
  & $signtool sign /fd SHA256 /sha1 $cert.Thumbprint $out
  if ($LASTEXITCODE -ne 0) { throw "signtool failed" }
}
Write-Host "MSIX ready: $out"
