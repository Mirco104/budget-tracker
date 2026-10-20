@echo off
rem Starts backend and frontend in separate windows and opens the app in the browser.
setlocal
set "ROOT=%~dp0"

where java >nul 2>&1 || (echo Java wurde nicht gefunden. Bitte JDK 21 oder neuer installieren. & pause & exit /b 1)
where npm >nul 2>&1 || (echo Node.js wurde nicht gefunden. Bitte Node.js 22.12 oder neuer installieren. & pause & exit /b 1)

if not exist "%ROOT%frontend\node_modules" (
    echo Installiere Frontend-Abhaengigkeiten ...
    pushd "%ROOT%frontend"
    call npm ci || (popd & echo npm ci ist fehlgeschlagen. & pause & exit /b 1)
    popd
)

echo Starte Backend ...
start "Budget Backend" /d "%ROOT%backend" cmd /k "%ROOT%backend\mvnw.cmd" spring-boot:run

echo Starte Frontend ...
start "Budget Frontend" /d "%ROOT%frontend" cmd /k npm run dev

echo Warte auf das Backend (http://localhost:8080) ...
:wait_backend
curl -s -o nul http://localhost:8080/v3/api-docs || (ping -n 3 127.0.0.1 >nul & goto wait_backend)

echo Warte auf das Frontend (http://localhost:5173) ...
:wait_frontend
curl -s -o nul http://localhost:5173 || (ping -n 2 127.0.0.1 >nul & goto wait_frontend)

start "" http://localhost:5173
echo.
echo Die App laeuft unter http://localhost:5173
echo Zum Beenden die Fenster "Budget Backend" und "Budget Frontend" schliessen.
