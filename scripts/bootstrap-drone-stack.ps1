param(
    [string]$UbuntuDistro = "Ubuntu-24.04",
    [switch]$SkipWslInstall
)

$ErrorActionPreference = "Stop"

function Write-Step([string]$Message) {
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Test-CommandExists([string]$Name) {
    return $null -ne (Get-Command $Name -ErrorAction SilentlyContinue)
}

if (-not (Test-CommandExists "wsl.exe")) {
    throw "WSL is not available on this Windows installation. Install WSL first, then run Start Drone Stack again."
}

$distros = (& wsl.exe -l -q 2>$null) -replace "`0", "" | ForEach-Object { $_.Trim() } | Where-Object { $_ }
$hasDistro = $distros -contains $UbuntuDistro

if (-not $hasDistro) {
    if ($SkipWslInstall) {
        throw "$UbuntuDistro is not installed. Install it with: wsl --install -d $UbuntuDistro"
    }

    Write-Step "$UbuntuDistro is missing; asking WSL to install it"
    Write-Host "If Windows asks for admin permission or a restart, finish that first and press Start Drone Stack again." -ForegroundColor Yellow
    & wsl.exe --install -d $UbuntuDistro
    if ($LASTEXITCODE -ne 0) {
        throw "WSL could not install $UbuntuDistro automatically. Run manually: wsl --install -d $UbuntuDistro"
    }
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$repoRootWsl = (& wsl.exe -d $UbuntuDistro -- wslpath -a "$repoRoot").Trim()
$bootstrapWsl = "$repoRootWsl/scripts/wsl-bootstrap-drone-stack.sh"

Write-Step "Checking drone simulation dependencies inside $UbuntuDistro"
& wsl.exe -d $UbuntuDistro -- bash -lc "PROJECT_PATH='$repoRootWsl' exec '$bootstrapWsl'"
if ($LASTEXITCODE -ne 0) {
    throw "Drone stack bootstrap failed inside $UbuntuDistro."
}

Write-Step "Drone simulation dependencies are ready"
