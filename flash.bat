@echo off
setlocal enabledelayedexpansion
title Granna Button Flasher

echo.
echo  =============================================
echo   Granna Button Flasher — Aitrip ESP32-S3
echo  =============================================
echo.

set SKETCH=%~dp0esp32_firmware\granna_button\granna_button.ino
set ARDUINO_CLI=%~dp0..\assistant_button\tools\arduino-cli.exe
set DATA_DIR=C:\sv

REM ── Install BLE library if needed ────────────────────────────────────────
set LIB_DIR=%DATA_DIR%\libraries\ESP32-BLE-Keyboard
if not exist "%LIB_DIR%" (
    echo Downloading BLE library...
    powershell -Command "Invoke-WebRequest -Uri 'https://github.com/T-vK/ESP32-BLE-Keyboard/archive/refs/heads/master.zip' -OutFile 'C:\sv\blekeyboard.zip'"
    powershell -Command "Expand-Archive -Path 'C:\sv\blekeyboard.zip' -DestinationPath 'C:\sv\libraries' -Force"
    rename "C:\sv\libraries\ESP32-BLE-Keyboard-master" "ESP32-BLE-Keyboard" 2>nul
    del "C:\sv\blekeyboard.zip" 2>nul
)

REM ── Auto-detect COM port ──────────────────────────────────────────────────
set COMPORT=
for /f "tokens=1" %%p in ('"%ARDUINO_CLI%" --config-dir "%DATA_DIR%" board list 2^>nul ^| findstr /i "ESP32\|CP210\|CH340\|CH9102"') do (
    if "!COMPORT!"=="" set COMPORT=%%p
)

if "%COMPORT%"=="" (
    echo No ESP32 detected automatically.
    "%ARDUINO_CLI%" --config-dir "%DATA_DIR%" board list 2>nul
    set /p COMPORT="Enter COM port (e.g. COM3): "
)
echo Using port: %COMPORT%

REM ── Compile and flash ─────────────────────────────────────────────────────
echo Compiling...
"%ARDUINO_CLI%" --config-dir "%DATA_DIR%" compile ^
    --fqbn esp32:esp32:esp32s3 ^
    --libraries "%DATA_DIR%\libraries" ^
    "%SKETCH%"

if errorlevel 1 (
    echo ERROR: Compilation failed.
    pause & exit /b 1
)

echo Flashing to %COMPORT%...
"%ARDUINO_CLI%" --config-dir "%DATA_DIR%" upload ^
    --fqbn esp32:esp32:esp32s3 ^
    --port %COMPORT% ^
    "%SKETCH%"

if errorlevel 1 (
    echo ERROR: Upload failed. Try holding BOOT button while pressing reset.
    pause & exit /b 1
)

echo.
echo  =============================================
echo   Done! Now build and install the Android app.
echo  =============================================
pause
