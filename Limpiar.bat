@echo off
chcp 65001 >nul
setlocal
 title Portémon - Limpieza
where python >nul 2>nul || (
  echo No se encontro Python. Instala Python y activa Add Python to PATH.
  pause
  exit /b 1
)
echo.
echo   PORTEMON / LIMPIEZA
 echo   Las copias de juegos, firmas, partidas y registros se conservan.
echo.
echo   1. Revisar APK anteriores y motores antiguos
 echo   2. Lo anterior y archivos regenerables de compilacion
 echo   3. Solo ver que se puede limpiar
 echo   0. Salir
 echo.
choice /c 1230 /n /m "Selecciona una opcion: "
if errorlevel 4 exit /b 0
if errorlevel 3 goto preview
if errorlevel 2 goto build
python "%~dp0tools\rmxp_port.py" --limpiar
goto end
:build
python "%~dp0tools\rmxp_port.py" --limpiar --incluir-compilacion
goto end
:preview
python "%~dp0tools\rmxp_port.py" --ver-limpieza --incluir-compilacion
:end
set "CLEAN_STATUS=%ERRORLEVEL%"
echo.
pause
endlocal & exit /b %CLEAN_STATUS%
