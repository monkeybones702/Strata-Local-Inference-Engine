@echo off
setlocal EnableDelayedExpansion

echo =========================================================
echo Strata MoE Local Engine: Windows Launcher
echo =========================================================

:: 1. Check Python
where python >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Python is not installed or not in PATH.
    pause
    exit /b 1
)

:: 2. Setup Venv
if not exist "venv" (
    echo [INFO] Setting up Python virtual environment...
    python -m venv venv
)

call venv\Scripts\activate.bat

:: 3. GPU Detection
where nvidia-smi >nul 2>nul
if %ERRORLEVEL% equ 0 (
    echo [INFO] NVIDIA GPU Detected:
    nvidia-smi --query-gpu=name,memory.total,memory.free --format=csv,noheader
) else (
    echo [WARN] No NVIDIA GPU detected with nvidia-smi. System will run in host RAM/CPU mode.
)

:: 4. Install Dependencies
echo [INFO] Checking dependencies...
python -m pip install --upgrade pip
pip install -r requirements.txt

:: 5. Create storage directories
if not exist "cache\moe_nvme" mkdir cache\moe_nvme
if not exist "weights" mkdir weights

:: 6. Launch Server
echo [INFO] Starting Strata Localhost API Gateway on http://127.0.0.1:8000 ...
python server.py

pause
