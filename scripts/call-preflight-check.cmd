@echo off
setlocal

set "FLIGHT_CONTROL_API_URL=%FLIGHT_CONTROL_API_URL%"
if "%FLIGHT_CONTROL_API_URL%"=="" set "FLIGHT_CONTROL_API_URL=http://localhost:8090"

echo Calling preflight check at %FLIGHT_CONTROL_API_URL% ...

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference='Stop';" ^
  "$base='%FLIGHT_CONTROL_API_URL%'.TrimEnd('/');" ^
  "$start=Invoke-RestMethod -Method Post -Uri ($base + '/api/preflight/check');" ^
  "$checkId=$start.checkId;" ^
  "Write-Host ('Started preflight: ' + $checkId);" ^
  "for ($i=0; $i -lt 6; $i++) {" ^
  "  Start-Sleep -Milliseconds 700;" ^
  "  $status=Invoke-RestMethod -Method Get -Uri ($base + '/api/preflight/' + $checkId);" ^
  "  Write-Host ('Poll ' + ($i + 1) + ': ' + $status.status + ' ' + $status.progress + '%%');" ^
  "}" ^
  "Write-Host 'Done. Check DB table preflight_checks and preflight_check_items.'"

endlocal
