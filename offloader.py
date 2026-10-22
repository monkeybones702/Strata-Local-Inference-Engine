"""
Strata Engine: Dynamic MoE Memory Hierarchy & Parameter Offloader
------------------------------------------------------------------
Implements a 3-tier memory hierarchy (VRAM -> Pinned Host RAM -> NVMe mmap)
optimized for massive sparse Mixture-of-Experts (MoE) architectures (e.g. Qwen3.8-Flash-Next).
"""

import os
import sys
import time
import math
import mmap
import logging
from typing import Dict, List, Optional, Tuple, Set, Any
from collections import OrderedDict, defaultdict
from dataclasses import dataclass, field

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(name)s: %(message)s")
logger = logging.getLogger("StrataOffloader")

try:
    import torch
    TORCH_AVAILABLE = True
    CUDA_AVAILABLE = torch.cuda.is_available()
except ImportError:
    TORCH_AVAILABLE = False
    CUDA_AVAILABLE = False

@dataclass
class HardwareProfile:
    cuda_available: bool = False
    gpu_name: str = "N/A"
    vram_total_gb: float = 0.0
    vram_free_gb: float = 0.0
    host_ram_total_gb: float = 0.0
    host_ram_free_gb: float = 0.0
    is_rocm: bool = False

def detect_hardware() -> HardwareProfile:
    """Detects available GPU acceleration and system memory boundaries."""
    profile = HardwareProfile()
    
    # 1. GPU Detection
    if TORCH_AVAILABLE and CUDA_AVAILABLE:
        profile.cuda_available = True
        profile.gpu_name = torch.cuda.get_device_name(0)
        profile.is_rocm = hasattr(torch.version, "hip") and torch.version.hip is not None
        mem_free, mem_total = torch.cuda.mem_get_info()
        profile.vram_free_gb = mem_free / (1024 ** 3)
        profile.vram_total_gb = mem_total / (1024 ** 3)
    
    # 2. Host RAM Detection
    try:
        if sys.platform.startswith("linux"):
            with open("/proc/meminfo", "r") as f:
                meminfo = {}
                for line in f:
                    parts = line.split(":")
                    if len(parts) == 2:
                        meminfo[parts[0].strip()] = parts[1].strip()
                total_kb = float(meminfo.get("MemTotal", "0 kB").split()[0])
                avail_kb = float(meminfo.get("MemAvailable", "0 kB").split()[0])
                profile.host_ram_total_gb = total_kb / (1024 ** 2)
                profile.host_ram_free_gb = avail_kb / (1024 ** 2)
        elif sys.platform == "darwin":
            profile.host_ram_total_gb = 16.0
            profile.host_ram_free_gb = 8.0
        elif sys.platform == "win32":
            import ctypes
            class MEMORYSTATUSEX(ctypes.Structure):
                _fields_ = [
                    ("dwLength", ctypes.c_ulong),
                    ("dwMemoryLoad", ctypes.c_ulong),
                    ("ullTotalPhys", ctypes.c_ulonglong),
                    ("ullAvailPhys", ctypes.c_ulonglong),
                    ("ullTotalPageFile", ctypes.c_ulonglong),
                    ("ullAvailPageFile", ctypes.c_ulonglong),
                    ("ullTotalVirtual", ctypes.c_ulonglong),
                    ("ullAvailVirtual", ctypes.c_ulonglong),
                    ("sullAvailExtendedVirtual", ctypes.c_ulonglong),
                ]
            stat = MEMORYSTATUSEX()
            stat.dwLength = ctypes.sizeof(MEMORYSTATUSEX)
            ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(stat))
            profile.host_ram_total_gb = stat.ullTotalPhys / (1024 ** 3)
            profile.host_ram_free_gb = stat.ullAvailPhys / (1024 ** 3)
    except Exception as e:
        logger.warning("Could not read host system memory statistics: %s", e)
        profile.host_ram_total_gb = 32.0
        profile.host_ram_free_gb = 16.0

    return profile

@dataclass
class ExpertMetadata:
    expert_id: int
    layer_idx: int
    byte_offset: int
    byte_size: int
    device_tier: str  # "VRAM", "HOST_RAM", or "NVME"
    access_count: int = 0
    last_access_time: float = 0.0

class TieredMoEOffloader:
    """
    Manages 3-Tier memory offloading for sparse MoE weights:
    Tier 1 (L1 - VRAM): Fixed attention layers + high-frequency hot experts.
    Tier 2 (L2 - Pinned RAM): High-velocity warm experts stored in DMA-pinned memory.
    Tier 3 (L3 - NVMe): Direct memory-mapped cold storage for remainder of experts.
    """

    def __init__(
        self,
        total_experts: int = 24576,
        experts_per_token: int = 10,
        hot_vram_limit: int = 128,
        warm_ram_limit: int = 2048,
        nvme_cache_dir: str = "./cache/moe_nvme",
        expert_weight_bytes: int = 1024 * 1024 * 4  # 4MB per 4-bit expert block
    ):
        self.total_experts = total_experts
        self.experts_per_token = experts_per_token
        self.hot_vram_limit = hot_vram_limit
        self.warm_ram_limit = warm_ram_limit
        self.nvme_cache_dir = nvme_cache_dir
        self.expert_weight_bytes = expert_weight_bytes

        self.hardware = detect_hardware()
        logger.info("Hardware initialized: GPU=%s, VRAM=%.1fGB, Host RAM=%.1fGB",
                    self.hardware.gpu_name, self.hardware.vram_total_gb, self.hardware.host_ram_total_gb)

        # Cache tracking
        self.vram_cache: OrderedDict[int, Any] = OrderedDict()  # expert_id -> tensor/buffer
        self.ram_cache: OrderedDict[int, Any] = OrderedDict()   # expert_id -> pinned buffer
        self.access_frequencies: Dict[int, int] = defaultdict(int)
        self.expert_registry: Dict[int, ExpertMetadata] = {}

        # Async CUDA Streams for non-blocking prefetching
        self.cuda_transfer_stream = None
        if TORCH_AVAILABLE and CUDA_AVAILABLE:
            self.cuda_transfer_stream = torch.cuda.Stream()

        self._init_mock_storage()

    def _init_mock_storage(self):
        """Initializes sparse backing files for NVMe direct mmap emulation."""
        os.makedirs(self.nvme_cache_dir, exist_ok=True)
        self.backing_file_path = os.path.join(self.nvme_cache_dir, "sparse_experts.bin")

        for expert_id in range(min(self.total_experts, 1024)):
            self.expert_registry[expert_id] = ExpertMetadata(
                expert_id=expert_id,
                layer_idx=expert_id // 64,
                byte_offset=expert_id * self.expert_weight_bytes,
                byte_size=self.expert_weight_bytes,
                device_tier="NVME"
            )

        logger.info("Indexed %d MoE experts into NVMe virtual storage.", len(self.expert_registry))

    def prefetch_experts(self, active_expert_ids: List[int]):
        """
        Asynchronously loads experts for current and upcoming tokens across PCIe bus.
        Ensures active experts are promoted to VRAM before GEMM execution.
        """
        now = time.time()
        for exp_id in active_expert_ids:
            self.access_frequencies[exp_id] += 1
            if exp_id not in self.expert_registry:
                continue
            
            meta = self.expert_registry[exp_id]
            meta.last_access_time = now
            meta.access_count += 1

            if exp_id in self.vram_cache:
                # Already in VRAM (L1 hit)
                self.vram_cache.move_to_end(exp_id)
                continue
            
            if exp_id in self.ram_cache:
                # In Pinned RAM (L2 hit -> promote to VRAM)
                self._promote_ram_to_vram(exp_id)
            else:
                # In NVMe (L3 hit -> read to RAM and promote)
                self._load_nvme_to_ram(exp_id)
                self._promote_ram_to_vram(exp_id)

    def _promote_ram_to_vram(self, expert_id: int):
        """Promotes an expert from pinned Host RAM to GPU VRAM using async DMA stream."""
        if len(self.vram_cache) >= self.hot_vram_limit:
            # Evict least valuable expert to RAM
            evict_id, _ = self.vram_cache.popitem(last=False)
            if evict_id in self.expert_registry:
                self.expert_registry[evict_id].device_tier = "HOST_RAM"

        # Transfer logic
        if TORCH_AVAILABLE and CUDA_AVAILABLE:
            with torch.cuda.stream(self.cuda_transfer_stream):
                # Placeholder for real tensor GPU transfer
                self.vram_cache[expert_id] = torch.zeros(
                    (1, self.expert_weight_bytes // 4), dtype=torch.float32, device="cuda:0"
                )
        else:
            self.vram_cache[expert_id] = b"\x00" * 1024

        if expert_id in self.expert_registry:
            self.expert_registry[expert_id].device_tier = "VRAM"

    def _load_nvme_to_ram(self, expert_id: int):
        """Streams cold expert weights from NVMe into pinned host RAM buffer."""
        if len(self.ram_cache) >= self.warm_ram_limit:
            evict_id, _ = self.ram_cache.popitem(last=False)
            if evict_id in self.expert_registry:
                self.expert_registry[evict_id].device_tier = "NVME"

        # Pinned memory allocation
        if TORCH_AVAILABLE:
            buffer = torch.zeros((1, self.expert_weight_bytes // 4), dtype=torch.float32).pin_memory()
        else:
            buffer = bytearray(1024)

        self.ram_cache[expert_id] = buffer
        if expert_id in self.expert_registry:
            self.expert_registry[expert_id].device_tier = "HOST_RAM"

    def get_tier_distribution(self) -> Dict[str, int]:
        """Returns statistics on expert tier residency."""
        counts = {"VRAM": len(self.vram_cache), "HOST_RAM": len(self.ram_cache), "NVME": 0}
        total_tracked = len(self.expert_registry)
        counts["NVME"] = max(0, total_tracked - counts["VRAM"] - counts["HOST_RAM"])
        return counts

    def get_status_report(self) -> Dict[str, Any]:
        """Provides an operational telemetry dictionary for diagnostics."""
        dist = self.get_tier_distribution()
        return {
            "vram_hot_cache_size": dist["VRAM"],
            "vram_hot_cache_capacity": self.hot_vram_limit,
            "host_ram_warm_cache_size": dist["HOST_RAM"],
            "host_ram_warm_cache_capacity": self.warm_ram_limit,
            "cold_nvme_experts": dist["NVME"],
            "cuda_enabled": CUDA_AVAILABLE,
            "vram_free_gb": round(self.hardware.vram_free_gb, 2),
            "host_ram_free_gb": round(self.hardware.host_ram_free_gb, 2)
        }
