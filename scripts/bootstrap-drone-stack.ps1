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

function ConvertTo-WslPath([string]$WindowsPath) {
    $fullPath = (Resolve-Path $WindowsPath).Path
    if ($fullPath -notmatch "^([A-Za-z]):\\(.*)$") {
        throw "Cannot convert path to WSL format: $fullPath"
    }

    $drive = $Matches[1].ToLowerInvariant()
    $rest = $Matches[2] -replace "\\", "/"
    return "/mnt/$drive/$rest"
}

function Convert-ShellScriptsToLf([string]$Root) {
    $paths = @()
    $paths += Get-ChildItem -Path (Join-Path $Root "scripts") -Filter "*.sh" -File -Recurse -ErrorAction SilentlyContinue
    $wslBin = Join-Path $Root "scripts\wsl-bin"
    if (Test-Path $wslBin) {
        $paths += Get-ChildItem -Path $wslBin -File -Recurse -ErrorAction SilentlyContinue
    }

    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    foreach ($path in $paths) {
        $content = [System.IO.File]::ReadAllText($path.FullName)
        $normalized = $content -replace "`r`n", "`n"
        $normalized = $normalized -replace "`r", "`n"
        if ($normalized -ne $content) {
            [System.IO.File]::WriteAllText($path.FullName, $normalized, $utf8NoBom)
        }
    }
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
Convert-ShellScriptsToLf $repoRoot
$repoRootWsl = ConvertTo-WslPath $repoRoot
$bootstrapWsl = "$repoRootWsl/scripts/wsl-bootstrap-drone-stack.sh"

Write-Step "Checking drone simulation dependencies inside $UbuntuDistro"
& wsl.exe -d $UbuntuDistro -- bash -lc "PROJECT_PATH='$repoRootWsl' exec '$bootstrapWsl'"
if ($LASTEXITCODE -ne 0) {
    throw "Drone stack bootstrap failed inside $UbuntuDistro."
}

Write-Step "Drone simulation dependencies are ready"
