@echo off
rem Portémon: arrastra la carpeta del juego sobre este archivo, o ábrelo y elige la carpeta.
chcp 65001 >nul
where python >nul 2>nul || (
  echo No encontre Python. Instalalo desde https://www.python.org/downloads/ marcando "Add python.exe to PATH".
  pause
  exit /b 1
)
rem Opcional: permite soltar la carpeta directamente sobre la ventana
python -c "import tkinterdnd2" 2>nul || python -m pip install --user --quiet tkinterdnd2 >nul 2>nul
start "" pythonw "%~dp0tools\rmxp_port.py" %*
