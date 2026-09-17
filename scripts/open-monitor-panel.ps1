$ErrorActionPreference = "Stop"

function ConvertTo-WslPath([string]$WindowsPath) {
    $fullPath = (Resolve-Path $WindowsPath).Path
    if ($fullPath -notmatch "^([A-Za-z]):\\(.*)$") {
        throw "Cannot convert path to WSL format: $fullPath"
    }

    $drive = $Matches[1].ToLowerInvariant()
    $rest = $Matches[2] -replace "\\", "/"
    return "/mnt/$drive/$rest"
}

$distro = "Ubuntu-24.04"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$repoRootWsl = ConvertTo-WslPath $repoRoot
$scriptRoot = "$repoRootWsl/scripts"

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

function Start-WslMonitor {
    param([string]$Command)
    Start-Process wsl.exe -ArgumentList "-d", $distro, "--", "bash", "-lc", $Command
}

$form = New-Object System.Windows.Forms.Form
$form.Text = "Drone Monitor"
$form.StartPosition = "CenterScreen"
$form.Size = New-Object System.Drawing.Size(360, 275)
$form.FormBorderStyle = "FixedDialog"
$form.MaximizeBox = $false

$label = New-Object System.Windows.Forms.Label
$label.Text = "Open monitoring screens only when needed"
$label.AutoSize = $true
$label.Location = New-Object System.Drawing.Point(28, 20)
$form.Controls.Add($label)

$camera = New-Object System.Windows.Forms.Button
$camera.Text = "Open Camera"
$camera.Size = New-Object System.Drawing.Size(280, 34)
$camera.Location = New-Object System.Drawing.Point(28, 55)
$camera.Add_Click({
    Start-WslMonitor "SIM_WORLD=compact exec $scriptRoot/wsl-camera-view.sh"
})
$form.Controls.Add($camera)

$sensor = New-Object System.Windows.Forms.Button
$sensor.Text = "Open LiDAR / Sensors"
$sensor.Size = New-Object System.Drawing.Size(280, 34)
$sensor.Location = New-Object System.Drawing.Point(28, 100)
$sensor.Add_Click({
    Start-WslMonitor "SIM_WORLD=compact exec $scriptRoot/wsl-sensor-monitor.sh"
})
$form.Controls.Add($sensor)

$thermal = New-Object System.Windows.Forms.Button
$thermal.Text = "Open Thermal Camera"
$thermal.Size = New-Object System.Drawing.Size(280, 34)
$thermal.Location = New-Object System.Drawing.Point(28, 145)
$thermal.Add_Click({
    Start-WslMonitor "SIM_WORLD=compact exec $scriptRoot/wsl-thermal-view.sh"
})
$form.Controls.Add($thermal)

$telemetry = New-Object System.Windows.Forms.Button
$telemetry.Text = "Open Telemetry"
$telemetry.Size = New-Object System.Drawing.Size(280, 34)
$telemetry.Location = New-Object System.Drawing.Point(28, 190)
$telemetry.Add_Click({
    Start-WslMonitor "exec $scriptRoot/wsl-telemetry.sh"
})
$form.Controls.Add($telemetry)

[void]$form.ShowDialog()
