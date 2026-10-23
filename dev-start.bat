@echo off
setlocal
cd /d "%~dp0"
if not exist "backend\application-local.yml" (
    if not defined DB_PASSWORD (
        echo Configure DB_PASSWORD and JWT_SECRET before starting. See README.md.
        exit /b 1
    )
    if not defined JWT_SECRET (
        echo Configure JWT_SECRET before starting. See README.md.
        exit /b 1
    )
)
echo Ensure MySQL and Redis are running before starting.
start "algo-backend" cmd /k "cd /d %~dp0backend && mvn spring-boot:run"
start "algo-frontend" cmd /k "cd /d %~dp0frontend && npm run dev"
echo Frontend: http://localhost:5173
pause
