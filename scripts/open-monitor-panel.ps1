$ErrorActionPreference = "Stop"

$distro = "Ubuntu-24.04"
$scriptRoot = "/mnt/c/Users/ACER/Documents/GitHub/doan/on-demand-monitoring-system/scripts"

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

function Start-WslMonitor {
    param([string]$Command)
    Start-Process wsl.exe -ArgumentList "-d", $distro, "--", "bash", "-lc", $Command
}

$form = New-Object System.Windows.Forms.Form
$form.Text = "Drone Monitor"
$form.StartPosition = "CenterScreen"
$form.Size = New-Object System.Drawing.Size(360, 230)
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

$telemetry = New-Object System.Windows.Forms.Button
$telemetry.Text = "Open Telemetry"
$telemetry.Size = New-Object System.Drawing.Size(280, 34)
$telemetry.Location = New-Object System.Drawing.Point(28, 145)
$telemetry.Add_Click({
    Start-WslMonitor "exec $scriptRoot/wsl-telemetry.sh"
})
$form.Controls.Add($telemetry)

[void]$form.ShowDialog()
