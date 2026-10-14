@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "COMPOSE_DIR=%SCRIPT_DIR%ondemandmonitoring"
set "ACTION=%~1"
if "%ACTION%"=="" set "ACTION=up"

echo ============================================================
echo   ON-DEMAND MONITORING SYSTEM - DOCKER RUNNER
echo ============================================================

cd /d "%COMPOSE_DIR%"

if /i "%ACTION%"=="up" goto do_up
if /i "%ACTION%"=="start" goto do_up
if /i "%ACTION%"=="stop" goto do_stop
if /i "%ACTION%"=="down" goto do_stop
if /i "%ACTION%"=="logs" goto do_logs
if /i "%ACTION%"=="rebuild" goto do_rebuild
if /i "%ACTION%"=="status" goto do_status
if /i "%ACTION%"=="ps" goto do_status

echo [ERROR] Lenh khong hop le: %ACTION%
echo Cach su dung: run-docker.cmd [up^|stop^|logs^|rebuild^|status]
exit /b 1

:do_up
echo [*] Dang khoi dong PostgreSQL va Backend qua Docker Compose...
docker compose up -d
echo [OK] He thong da khoi dong thanh cong!
echo ------------------------------------------------------------
echo   - Backend API:  http://localhost:8080
echo   - Swagger UI:   http://localhost:8080/swagger-ui.html
echo   - OpenAPI Docs: http://localhost:8080/v3/api-docs
echo   - PostgreSQL:   localhost:5433 (ondemand / ondemand123)
echo ------------------------------------------------------------
goto end

:do_stop
echo [*] Dang dung he thong...
docker compose down
echo [OK] Da dung toan bo containers.
goto end

:do_logs
docker compose logs -f app
goto end

:do_rebuild
docker compose build
docker compose up -d
goto end

:do_status
docker compose ps
goto end

:end
endlocal
