@echo off
rem RMXP-Android-Ports: borra APKs viejos (deja el mas nuevo de cada juego), restos de
rem compilacion y motores anteriores. Las firmas de los juegos NO se tocan.
chcp 65001 >nul
python "%~dp0tools\rmxp_port.py" --limpiar
pause
