@echo off
setlocal
cd /d "%~dp0"

if not exist "run\eula.txt" (
    echo Initializing the Paper 26.2 test server...
    call gradlew.bat runTestServer --console=plain
)

findstr /R /C:"^eula=true$" "run\eula.txt" >nul 2>&1
if errorlevel 1 (
    echo.
    echo The test server is ready, but Mojang's EULA has not been accepted.
    echo Review https://aka.ms/MinecraftEULA and then set eula=true in run\eula.txt.
    exit /b 1
)

call gradlew.bat runTestServer --console=plain
exit /b %errorlevel%
