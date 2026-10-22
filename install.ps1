# Instala o pulso.tv na TV via adb, baixando o APK do ultimo Release do GitHub.
# Uso:  irm https://raw.githubusercontent.com/beetim/pulso-tv/main/install.ps1 | iex
#   ou: .\install.ps1 -Ip 192.168.0.11
param([string]$Ip)

$ErrorActionPreference = 'Stop'
$repo = 'beetim/pulso-tv'

$adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
if (-not $adb) {
    Write-Host 'adb nao encontrado. Instale o platform-tools e rode de novo:'
    Write-Host 'https://developer.android.com/tools/releases/platform-tools'
    return
}

if (-not $Ip) { $Ip = Read-Host 'IP da TV (ex.: 192.168.0.11)' }
$Ip = $Ip.Trim()
if (-not $Ip) { Write-Host 'Informe o IP da TV (Configuracoes > Rede).'; return }
$target = if ($Ip -match ':') { $Ip } else { "${Ip}:5555" }

Write-Host 'Buscando a ultima versao...'
$release = Invoke-RestMethod "https://api.github.com/repos/$repo/releases/latest" -Headers @{ 'User-Agent' = 'pulso-install' }
$asset = $release.assets | Where-Object { $_.name -like '*.apk' } | Select-Object -First 1
if (-not $asset) { Write-Host 'Nenhum APK encontrado no ultimo Release.'; return }

$file = Join-Path $env:TEMP 'Pulso.apk'
Write-Host "Baixando $($release.tag_name) ($([math]::Round($asset.size / 1MB, 1)) MB)..."
Invoke-WebRequest $asset.browser_download_url -OutFile $file -UseBasicParsing

Write-Host "Conectando em $target (aceite a autorizacao na TV, se aparecer)..."
& $adb connect $target | Out-Host
Write-Host 'Instalando...'
& $adb -s $target install -r $file | Out-Host
if ($LASTEXITCODE -ne 0) {
    Write-Host 'Falha na instalacao. Confira se a Depuracao por rede esta ligada e se voce autorizou o PC na TV.'
    return
}
& $adb -s $target shell am start -n com.pulso.tv/.ui.pulso.PulsoHomeActivity | Out-Null
Write-Host 'Pronto! O pulso.tv foi instalado e aberto na TV.'
