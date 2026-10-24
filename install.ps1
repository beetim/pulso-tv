# Installs pulso.tv on the TV via adb, downloading the APK from the latest GitHub Release.
# Usage:  irm https://raw.githubusercontent.com/beetim/pulso-tv/main/install.ps1 | iex
#   or: .\install.ps1 -Ip 192.168.x.x
param([string]$Ip)

$ErrorActionPreference = 'Stop'
$repo = 'beetim/pulso-tv'

$adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
if (-not $adb) {
    Write-Host 'adb not found. Install platform-tools and run again:'
    Write-Host 'https://developer.android.com/tools/releases/platform-tools'
    return
}

if (-not $Ip) { $Ip = Read-Host 'TV IP address (e.g. 192.168.x.x)' }
$Ip = $Ip.Trim()
if (-not $Ip) { Write-Host 'Enter the TV IP address (Settings > Network).'; return }
$target = if ($Ip -match ':') { $Ip } else { "${Ip}:5555" }

Write-Host 'Looking for the latest version...'
$release = Invoke-RestMethod "https://api.github.com/repos/$repo/releases/latest" -Headers @{ 'User-Agent' = 'pulso-install' }
$asset = $release.assets | Where-Object { $_.name -like '*.apk' } | Select-Object -First 1
if (-not $asset) { Write-Host 'No APK found in the latest release.'; return }

$file = Join-Path $env:TEMP 'Pulso.apk'
Write-Host "Downloading $($release.tag_name) ($([math]::Round($asset.size / 1MB, 1)) MB)..."
Invoke-WebRequest $asset.browser_download_url -OutFile $file -UseBasicParsing

Write-Host "Connecting to $target (accept the authorization on the TV if asked)..."
& $adb connect $target | Out-Host
Write-Host 'Installing...'
& $adb -s $target install -r $file | Out-Host
if ($LASTEXITCODE -ne 0) {
    Write-Host 'Install failed. Check that Network debugging is on and that you authorized this PC on the TV.'
    return
}
& $adb -s $target shell am start -n com.pulso.tv/.ui.pulso.PulsoHomeActivity | Out-Null
Write-Host 'Done! pulso.tv was installed and opened on the TV.'
