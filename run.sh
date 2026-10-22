#!/usr/bin/env bash
set -e

echo "=== Strata MoE Engine Environment Setup & Launcher ==="

# 1. Check Python
if ! command -v python3 &> /dev/null; then
    echo "[ERROR] Python 3 is not installed or not in PATH."
    exit 1
fi

# 2. Check Virtual Environment
if [ ! -d "venv" ]; then
    echo "[INFO] Creating virtual environment..."
    python3 -m venv venv
fi

source venv/bin/activate

# 3. Detect Acceleration Hardware
echo "[INFO] Detecting hardware accelerators..."
if command -v nvidia-smi &> /dev/null; then
    echo "[INFO] NVIDIA GPU detected:"
    nvidia-smi --query-gpu=name,memory.total,memory.free --format=csv,noheader
elif command -v rocm-smi &> /dev/null; then
    echo "[INFO] AMD ROCm GPU detected."
else
    echo "[WARN] No dedicated GPU detected. Falling back to CPU/RAM offload tier."
fi

# 4. Install Dependencies
echo "[INFO] Verifying Python package dependencies..."
pip install --upgrade pip
pip install -r requirements.txt

# 5. Initialize Cache Directories
mkdir -p ./cache/moe_nvme
mkdir -p ./weights

# 6. Launch Server
echo "[INFO] Starting Strata API Server on http://0.0.0.0:8000 ..."
python3 server.py
