#!/usr/bin/env python3
"""
SmartLeaseEdge – iQOO Load Test & Performance Monitor
=======================================================
Target device : iQOO I2501 (Android 16, 8-core, ~15.6 GB RAM)
Target app    : com.smartlease.edge  (LLM inference app)

What this script does
---------------------
1. PHASE 0  – Baseline  : Collect 30 s of idle metrics (CPU, RAM, GPU, temp,
               battery, frame-time) before stress begins.
2. PHASE 1  – App launch : Start the SmartLeaseEdge app, let it settle (10 s).
3. PHASE 2  – LLM load  : Fire repeated monkey-runner input events (touch/swipe
               simulating user interaction) so the on-device LLM is continuously
               exercised, while collecting metrics every 2 s.
4. PHASE 3  – Full-throttle : Push all 8 cores hard from device shell via a
               CPU-burn shell loop, running in parallel with the app.
5. PHASE 4  – Cooldown   : Stop the stressor, keep monitoring for 30 s.
6. REPORT    : Save a CSV + produce a multi-panel PNG chart showing:
               • CPU utilisation (per-core + average)
               • RAM used / available (MB)
               • Device temperature (CPU zone)
               • Battery level & drain rate
               • App-specific PSS memory (RSS) from dumpsys
               Annotated vertical lines mark phase transitions.
               A "saturation" line shows when resources hit >= 90 % util.

Usage
-----
    python tools/load_test_monitor.py [--duration 300] [--interval 2] [--out results/]

Arguments
---------
--duration  Total monitoring seconds during PHASE 2+3   (default 300 = 5 min)
--interval  Sampling interval in seconds                (default 2)
--out       Directory for CSV + PNG outputs             (default tools/perf_results/)
--package   App package to monitor                      (default com.smartlease.edge)
--no-stress Skip the CPU stress phase (monitor only)
--baseline  Baseline-only seconds                       (default 30)
"""

import argparse
import csv
import os
import re
import subprocess
import sys
import threading
import time
from datetime import datetime
from pathlib import Path

import matplotlib
matplotlib.use("Agg")           # headless - saves to file
import matplotlib.pyplot as plt
import matplotlib.ticker as ticker
import pandas as pd
import numpy as np

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
ADB = str(Path(os.environ["LOCALAPPDATA"]) / "Android/Sdk/platform-tools/adb.exe")
DEVICE_SERIAL = "10BFAT1RTA000XP"
APP_PACKAGE   = "com.smartlease.edge"
APP_ACTIVITY  = ".MainActivity"          # fallback - resolved at runtime

# Colour palette (dark-mode style chart)
PALETTE = {
    "cpu_avg"   : "#00D4FF",
    "cpu_cores" : ["#FF6B6B","#FFC300","#48C774","#23D160",
                   "#3273DC","#B86BFF","#FF9F43","#FF6348"],
    "ram_used"  : "#FF6B6B",
    "ram_avail" : "#48C774",
    "temp"      : "#FFC300",
    "battery"   : "#23D160",
    "pss"       : "#B86BFF",
    "bg"        : "#0D1117",
    "grid"      : "#21262D",
    "text"      : "#C9D1D9",
    "phase"     : ["#264653","#2A9D8F","#E9C46A","#E76F51"],
}

# ---------------------------------------------------------------------------
# ADB helpers
# ---------------------------------------------------------------------------

def adb(*args, timeout=10):
    """Run an adb command and return stdout as a string."""
    cmd = [ADB, "-s", DEVICE_SERIAL] + list(args)
    try:
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
        return result.stdout.strip()
    except subprocess.TimeoutExpired:
        return ""
    except Exception:
        return ""


def adb_shell(cmd, timeout=10):
    return adb("shell", cmd, timeout=timeout)


# ---------------------------------------------------------------------------
# Metric collectors
# ---------------------------------------------------------------------------

def get_cpu_usage():
    """Return overall CPU % and list of per-core % [0..100]."""
    raw = adb_shell("cat /proc/stat", timeout=5)
    lines = raw.splitlines()
    totals, idles = [], []
    for line in lines:
        if not line.startswith("cpu"):
            break
        parts = line.split()
        vals = list(map(int, parts[1:8]))   # user,nice,system,idle,iowait,irq,softirq
        tot  = sum(vals)
        idle = vals[3] + vals[4]
        totals.append(tot)
        idles.append(idle)

    # Store for delta calculation (first call returns 0)
    if not hasattr(get_cpu_usage, "_prev"):
        get_cpu_usage._prev = (totals, idles)
        return 0.0, []

    prev_totals, prev_idles = get_cpu_usage._prev
    get_cpu_usage._prev = (totals, idles)

    results = []
    for i in range(len(totals)):
        dt = totals[i] - prev_totals[i] if i < len(prev_totals) else 0
        di = idles[i]  - prev_idles[i]  if i < len(prev_idles) else 0
        pct = 100.0 * (1.0 - di / max(dt, 1))
        results.append(max(0.0, min(100.0, pct)))

    # results[0] = aggregate cpu, results[1:] = per-core
    overall = results[0] if results else 0.0
    cores   = results[1:] if len(results) > 1 else []
    return overall, cores


def get_ram_mb():
    """Return (used_mb, avail_mb, total_mb)."""
    raw = adb_shell("cat /proc/meminfo", timeout=5)
    info = {}
    for line in raw.splitlines():
        parts = line.split()
        if len(parts) >= 2:
            info[parts[0].rstrip(":")] = int(parts[1])
    total = info.get("MemTotal", 0) // 1024
    avail = info.get("MemAvailable", 0) // 1024
    used  = total - avail
    return used, avail, total


def get_temperature():
    """Return best-available CPU temperature in degrees C."""
    zones = {
        "cpu"    : adb_shell("cat /sys/class/thermal/thermal_zone0/temp 2>/dev/null"),
        "gpu"    : adb_shell("cat /sys/class/thermal/thermal_zone1/temp 2>/dev/null"),
        "battery": adb_shell("cat /sys/class/power_supply/battery/temp  2>/dev/null"),
    }
    temps = {}
    for k, v in zones.items():
        try:
            val = int(v.strip())
            # values > 1000 are in milli-degrees
            temps[k] = val / 1000 if val > 200 else val
        except Exception:
            temps[k] = None

    # Also try dumpsys thermalservice for labelled zones
    raw = adb_shell("dumpsys thermalservice 2>/dev/null | head -30")
    for line in raw.splitlines():
        m = re.search(r"Temperature\{mValue=([\d.]+),.*?mName=(\w+)", line)
        if m:
            val, name = float(m.group(1)), m.group(2).lower()
            if "cpu" in name:
                temps["cpu_labeled"] = val
                break

    return temps


def get_battery():
    """Return (level_pct, voltage_mv, current_ma, charging_bool)."""
    raw = adb_shell("dumpsys battery", timeout=5)
    info = {}
    for line in raw.splitlines():
        kv = line.strip().split(":", 1)
        if len(kv) == 2:
            info[kv[0].strip()] = kv[1].strip()
    level   = int(info.get("level", 0))
    voltage = int(info.get("voltage", 0))
    current_raw = adb_shell(
        "cat /sys/class/power_supply/battery/current_now 2>/dev/null"
    )
    try:
        current_ma = abs(int(current_raw.strip())) // 1000
    except Exception:
        current_ma = 0
    charging = info.get("status", "") in ("2", "Charging")
    return level, voltage, current_ma, charging


def get_app_pss_mb(package):
    """Return PSS memory of the app process in MB."""
    raw = adb_shell(f"dumpsys meminfo {package} 2>/dev/null | grep 'TOTAL PSS'", timeout=8)
    for line in raw.splitlines():
        m = re.search(r"(\d+)", line)
        if m:
            return int(m.group(1)) // 1024   # KB -> MB
    # fallback: pidof -> /proc/pid/status
    pid_raw = adb_shell(f"pidof {package}")
    try:
        pid = pid_raw.strip().split()[0]
        vm_rss = adb_shell(f"cat /proc/{pid}/status | grep VmRSS")
        m = re.search(r"(\d+)", vm_rss)
        if m:
            return int(m.group(1)) // 1024
    except Exception:
        pass
    return 0


def get_gpu_usage():
    """Return GPU busy % if available (Adreno-specific)."""
    raw = adb_shell("cat /sys/class/kgsl/kgsl-3d0/gpubusy 2>/dev/null")
    try:
        parts = raw.strip().split()
        if len(parts) == 2:
            busy, total = int(parts[0]), int(parts[1])
            return round(100.0 * busy / max(total, 1), 1)
    except Exception:
        pass
    return None


# ---------------------------------------------------------------------------
# App / stress control
# ---------------------------------------------------------------------------

def launch_app(package, activity=None):
    """Force-start the app."""
    if activity:
        adb_shell(f"am start -n {package}/{activity}")
    else:
        adb_shell(f"monkey -p {package} -c android.intent.category.LAUNCHER 1")
    print(f"  [+] Launched {package}")
    time.sleep(5)


def detect_main_activity(package):
    """Attempt to detect the main activity of the package."""
    raw = adb_shell(f"cmd package resolve-activity --brief {package} 2>/dev/null")
    for line in raw.splitlines():
        if "/" in line and package in line:
            return line.strip().split("/")[1]
    return None


def send_monkey_events(package, count=200, throttle_ms=200):
    """Send random UI events to keep the app busy."""
    cmd = (f"monkey -p {package} --ignore-crashes --ignore-timeouts "
           f"--throttle {throttle_ms} -v {count}")
    adb_shell(cmd, timeout=60)


def start_cpu_stress():
    """Launch an 8-core CPU burner on the device."""
    script = (
        "for i in $(seq 1 8); do "
        "  (while true; do :; done) & "
        "done; "
        "echo STRESS_STARTED; "
        "sleep 999"
    )
    proc = subprocess.Popen(
        [ADB, "-s", DEVICE_SERIAL, "shell", script],
        stdout=subprocess.PIPE, stderr=subprocess.PIPE
    )
    time.sleep(2)   # let the stress loops spin up
    return proc


def stop_cpu_stress(proc):
    """Kill the stress process group."""
    try:
        adb_shell("pkill -f 'while true; do :; done' 2>/dev/null; true")
        proc.terminate()
    except Exception:
        pass


def stop_app(package):
    adb_shell(f"am force-stop {package}")


# ---------------------------------------------------------------------------
# Main data-collection loop
# ---------------------------------------------------------------------------

class Collector:
    def __init__(self, package, interval=2):
        self.package  = package
        self.interval = interval
        self.rows     = []
        self._stop    = threading.Event()
        self.phase    = "idle"
        self.phase_marks = []   # [(elapsed_sec, phase_name)]
        self._start_time = None

    def mark_phase(self, name):
        elapsed = self.rows[-1]["elapsed"] if self.rows else 0.0
        self.phase = name
        self.phase_marks.append((elapsed, name))
        print(f"\n{'='*50}")
        print(f"  PHASE: {name.upper()}  @  t={elapsed:.1f}s")
        print(f"{'='*50}\n")

    def sample(self):
        cpu_avg, cores = get_cpu_usage()
        ram_used, ram_avail, ram_total = get_ram_mb()
        temps = get_temperature()
        batt_lvl, batt_v, batt_ma, charging = get_battery()
        pss_mb  = get_app_pss_mb(self.package)
        gpu_pct = get_gpu_usage()

        cpu_temp = (
            temps.get("cpu_labeled")
            or temps.get("cpu")
            or temps.get("battery")
            or 0.0
        )

        row = {
            "elapsed"      : 0.0,   # filled by caller
            "timestamp"    : datetime.now().isoformat(timespec="seconds"),
            "phase"        : self.phase,
            "cpu_avg_pct"  : round(cpu_avg, 1),
            "ram_used_mb"  : ram_used,
            "ram_avail_mb" : ram_avail,
            "ram_total_mb" : ram_total,
            "cpu_temp_c"   : round(float(cpu_temp or 0), 1),
            "battery_pct"  : batt_lvl,
            "battery_ma"   : batt_ma,
            "app_pss_mb"   : pss_mb,
            "gpu_pct"      : gpu_pct if gpu_pct is not None else "",
        }
        for i, c in enumerate(cores):
            row[f"core{i}_pct"] = round(c, 1)

        return row

    def run(self, total_seconds):
        """Collect samples for `total_seconds`."""
        if self._start_time is None:
            self._start_time = time.time()

        start = time.time()
        self._stop.clear()
        while not self._stop.is_set():
            t0 = time.time()
            row = self.sample()
            row["elapsed"] = round(time.time() - self._start_time, 1)
            self.rows.append(row)
            self._print_live(row)
            elapsed_in_phase = time.time() - start
            sleep_time = max(0, self.interval - (time.time() - t0))
            if self._stop.wait(timeout=sleep_time):
                break
            if elapsed_in_phase >= total_seconds:
                break

    def _print_live(self, row):
        cores_str = "  ".join(
            f"C{i}:{row.get(f'core{i}_pct', 0):.0f}%"
            for i in range(8)
            if f"core{i}_pct" in row
        )
        batt_str = f"{row['battery_pct']}% {row['battery_ma']}mA"
        gpu_str  = f"GPU:{row['gpu_pct']}%" if row['gpu_pct'] != "" else ""
        print(
            f"[{row['elapsed']:>7.1f}s] [{row['phase']:<14}] "
            f"CPU:{row['cpu_avg_pct']:>5.1f}%  "
            f"RAM:{row['ram_used_mb']:>5}/{row['ram_total_mb']} MB  "
            f"Temp:{row['cpu_temp_c']:>5.1f}C  "
            f"Batt:{batt_str}  {gpu_str}"
        )
        if cores_str:
            print(f"             Per-core: {cores_str}")

    def stop(self):
        self._stop.set()

    def save_csv(self, path):
        if not self.rows:
            return
        fieldnames = list(self.rows[0].keys())
        with open(path, "w", newline="") as f:
            writer = csv.DictWriter(f, fieldnames=fieldnames)
            writer.writeheader()
            writer.writerows(self.rows)
        print(f"\n[+] CSV saved -> {path}")

    def save_charts(self, path):
        if not self.rows:
            print("[!] No data to chart.")
            return

        df = pd.DataFrame(self.rows)
        x  = df["elapsed"]

        fig, axes = plt.subplots(
            5, 1, figsize=(18, 26), sharex=True,
            gridspec_kw={"hspace": 0.45}
        )
        fig.patch.set_facecolor(PALETTE["bg"])
        for ax in axes:
            ax.set_facecolor(PALETTE["bg"])
            ax.tick_params(colors=PALETTE["text"])
            ax.yaxis.label.set_color(PALETTE["text"])
            ax.xaxis.label.set_color(PALETTE["text"])
            ax.title.set_color(PALETTE["text"])
            for spine in ax.spines.values():
                spine.set_edgecolor(PALETTE["grid"])
            ax.grid(True, color=PALETTE["grid"], linewidth=0.6, linestyle="--")

        # Phase bands
        phase_times = list(self.phase_marks)
        phase_times.append((float(x.iloc[-1]), "_end"))

        phase_colors = {
            "baseline"     : "#264653",
            "app_launch"   : "#2A9D8F",
            "llm_load"     : "#E9C46A",
            "full_throttle": "#E76F51",
            "cooldown"     : "#264653",
        }

        for ax in axes:
            for idx, (pt, pn) in enumerate(phase_times[:-1]):
                end = phase_times[idx + 1][0]
                color = phase_colors.get(pn, "#333")
                ax.axvspan(pt, end, alpha=0.12, color=color, zorder=0)
            for pt, pn in phase_times[:-1]:
                ax.axvline(pt, color="#FFFFFF", alpha=0.3, linewidth=0.8,
                           linestyle=":", zorder=1)

        # Panel 0: CPU utilisation
        ax0 = axes[0]
        core_cols = [c for c in df.columns if re.match(r"core\d+_pct", c)]
        for i, col in enumerate(core_cols):
            ax0.plot(x, df[col], linewidth=0.7,
                     color=PALETTE["cpu_cores"][i % len(PALETTE["cpu_cores"])],
                     alpha=0.55, label=f"Core {i}")
        ax0.plot(x, df["cpu_avg_pct"], linewidth=2.5,
                 color=PALETTE["cpu_avg"], label="CPU Avg", zorder=5)
        ax0.axhline(90, color="#FF4444", linewidth=1.2, linestyle="--",
                    label="90% Saturation")
        sat = df[df["cpu_avg_pct"] >= 90]["elapsed"]
        if not sat.empty:
            first_sat = sat.iloc[0]
            ax0.annotate(
                f"Saturation @{first_sat:.0f}s",
                xy=(first_sat, 90),
                xytext=(first_sat + 5, 80),
                color="#FF4444", fontsize=8,
                arrowprops=dict(arrowstyle="->", color="#FF4444"),
            )
        ax0.set_ylim(0, 105)
        ax0.set_ylabel("CPU %", fontsize=10)
        ax0.set_title("CPU Utilisation - All Cores + Average", fontsize=11, pad=6)
        ax0.legend(fontsize=7, ncol=5, loc="upper left",
                   facecolor="#1C2129", labelcolor=PALETTE["text"])
        ax0.yaxis.set_major_formatter(ticker.FormatStrFormatter("%.0f%%"))

        # Panel 1: RAM
        ax1 = axes[1]
        ax1.fill_between(x, df["ram_used_mb"], alpha=0.35, color=PALETTE["ram_used"])
        ax1.plot(x, df["ram_used_mb"], linewidth=2,
                 color=PALETTE["ram_used"], label="RAM Used")
        ax1.plot(x, df["ram_avail_mb"], linewidth=1.5, linestyle="--",
                 color=PALETTE["ram_avail"], label="RAM Available")
        if "app_pss_mb" in df and df["app_pss_mb"].max() > 0:
            ax1.fill_between(x, df["app_pss_mb"], alpha=0.4, color=PALETTE["pss"])
            ax1.plot(x, df["app_pss_mb"], linewidth=1.8,
                     color=PALETTE["pss"], label=f"App PSS")
        ram_total_val = int(df["ram_total_mb"].iloc[0])
        ax1.axhline(ram_total_val * 0.9, color="#FF4444", linewidth=1.0,
                    linestyle="--", label="90% RAM limit")
        ax1.set_ylim(0, ram_total_val + 500)
        ax1.set_ylabel("Memory (MB)", fontsize=10)
        ax1.set_title("RAM Usage - System & App PSS", fontsize=11, pad=6)
        ax1.legend(fontsize=8, loc="upper left",
                   facecolor="#1C2129", labelcolor=PALETTE["text"])
        ax1.yaxis.set_major_formatter(
            ticker.FuncFormatter(
                lambda v, _: f"{v/1024:.1f} GB" if v >= 1024 else f"{v:.0f} MB"
            )
        )

        # Panel 2: Temperature
        ax2 = axes[2]
        ax2.plot(x, df["cpu_temp_c"], linewidth=2.2,
                 color=PALETTE["temp"], label="CPU Temp")
        ax2.fill_between(x, df["cpu_temp_c"], alpha=0.2, color=PALETTE["temp"])
        ax2.axhline(45, color="#FF8C00", linewidth=1, linestyle="--",
                    label="Throttle warn (45C)")
        ax2.axhline(55, color="#FF4444", linewidth=1, linestyle="--",
                    label="Throttle critical (55C)")
        max_temp = df["cpu_temp_c"].max()
        max_t_idx = df["cpu_temp_c"].idxmax()
        max_t_time = df.loc[max_t_idx, "elapsed"]
        ax2.annotate(
            f"Peak {max_temp:.1f}C",
            xy=(max_t_time, max_temp),
            xytext=(max_t_time + 3, max_temp - 5),
            color=PALETTE["temp"], fontsize=9,
            arrowprops=dict(arrowstyle="->", color=PALETTE["temp"]),
        )
        ax2.set_ylabel("Temperature (C)", fontsize=10)
        ax2.set_title("CPU Temperature & Thermal Throttle Zones", fontsize=11, pad=6)
        ax2.legend(fontsize=8, loc="upper left",
                   facecolor="#1C2129", labelcolor=PALETTE["text"])
        ax2.yaxis.set_major_formatter(ticker.FormatStrFormatter("%.0f C"))

        # Panel 3: Battery
        ax3 = axes[3]
        ax3_twin = ax3.twinx()
        ax3_twin.set_facecolor(PALETTE["bg"])
        ax3_twin.tick_params(colors=PALETTE["text"])
        ax3_twin.yaxis.label.set_color("#FF9F43")
        ax3_twin.spines["right"].set_edgecolor(PALETTE["grid"])

        ax3.plot(x, df["battery_pct"], linewidth=2.2,
                 color=PALETTE["battery"], label="Battery %")
        ax3.fill_between(x, df["battery_pct"], alpha=0.2, color=PALETTE["battery"])
        ax3.set_ylabel("Battery Level (%)", fontsize=10)
        ax3.set_ylim(0, 105)
        ax3.yaxis.set_major_formatter(ticker.FormatStrFormatter("%.0f%%"))

        ax3_twin.plot(x, df["battery_ma"], linewidth=1.5, linestyle=":",
                      color="#FF9F43", label="Current (mA)")
        ax3_twin.set_ylabel("Discharge Current (mA)", fontsize=9, color="#FF9F43")

        if len(df) > 5:
            batt_drain = df["battery_pct"].diff() / (x.diff() / 60)
            ax3.plot(x, (batt_drain.rolling(5, min_periods=1).mean() * -1),
                     linewidth=1.2, linestyle="-.", color="#E74C3C",
                     label="Drain rate (%/min)", alpha=0.7)

        lines1, labels1 = ax3.get_legend_handles_labels()
        lines2, labels2 = ax3_twin.get_legend_handles_labels()
        ax3.legend(lines1 + lines2, labels1 + labels2, fontsize=8,
                   loc="upper right", facecolor="#1C2129", labelcolor=PALETTE["text"])
        ax3.set_title("Battery Level, Discharge Current & Drain Rate", fontsize=11, pad=6)

        # Panel 4: GPU or App PSS
        ax4 = axes[4]
        gpu_series = pd.to_numeric(df["gpu_pct"], errors="coerce")
        has_gpu = gpu_series.notna().any() and gpu_series.max() > 0
        if has_gpu:
            ax4.plot(x, gpu_series, linewidth=2.0,
                     color="#FF6348", label="GPU Busy %")
            ax4.fill_between(x, gpu_series, alpha=0.3, color="#FF6348")
            ax4.set_ylabel("GPU Busy %", fontsize=10)
            ax4.set_title("GPU Utilisation (Adreno kgsl)", fontsize=11, pad=6)
            ax4.yaxis.set_major_formatter(ticker.FormatStrFormatter("%.0f%%"))
        elif "app_pss_mb" in df and df["app_pss_mb"].max() > 0:
            ax4.bar(x, df["app_pss_mb"], width=self.interval * 0.8,
                    color=PALETTE["pss"], alpha=0.7, label="App PSS MB")
            ax4.set_ylabel("PSS Memory (MB)", fontsize=10)
            ax4.set_title(f"App PSS Memory - {self.package}", fontsize=11, pad=6)
        else:
            ax4.text(0.5, 0.5, "GPU sysfs not exposed on this device",
                     transform=ax4.transAxes, ha="center", va="center",
                     color=PALETTE["text"], fontsize=12)
            ax4.set_title("GPU / Extra Metrics", fontsize=11, pad=6)
        ax4.legend(fontsize=8, loc="upper left",
                   facecolor="#1C2129", labelcolor=PALETTE["text"])

        # Phase labels
        for ax in axes:
            ylim = ax.get_ylim()
            for pt, pn in phase_times[:-1]:
                ax.text(pt + 1, ylim[1] * 0.97, pn,
                        color="#FFFFFF", fontsize=7, alpha=0.7,
                        verticalalignment="top", rotation=90)

        axes[-1].set_xlabel("Elapsed Time (seconds)", fontsize=11,
                            color=PALETTE["text"])

        total_dur = float(x.iloc[-1])
        max_cpu   = df["cpu_avg_pct"].max()
        avg_cpu   = df["cpu_avg_pct"].mean()
        peak_ram  = df["ram_used_mb"].max()
        batt_drop = df["battery_pct"].iloc[0] - df["battery_pct"].iloc[-1]
        sat_t     = df[df["cpu_avg_pct"] >= 90]["elapsed"]
        sat_str   = f"{sat_t.iloc[0]:.0f}s" if not sat_t.empty else "never"

        title = (
            f"SmartLeaseEdge – iQOO I2501 Load & Performance Report\n"
            f"Duration: {total_dur:.0f}s  |  "
            f"Peak CPU: {max_cpu:.1f}%  |  Avg CPU: {avg_cpu:.1f}%  |  "
            f"Peak RAM: {peak_ram} MB ({peak_ram/1024:.1f} GB)  |  "
            f"Battery drop: {batt_drop:.1f}%  |  "
            f"Saturation: {sat_str}"
        )
        fig.suptitle(title, fontsize=10, color=PALETTE["text"],
                     y=0.99, ha="center")

        plt.tight_layout(rect=[0, 0, 1, 0.98])
        fig.savefig(path, dpi=150, bbox_inches="tight",
                    facecolor=PALETTE["bg"])
        plt.close(fig)
        print(f"[+] Chart saved -> {path}")


# ---------------------------------------------------------------------------
# Orchestrator
# ---------------------------------------------------------------------------

def run_load_test(args):
    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)

    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    csv_path   = out_dir / f"perf_{ts}.csv"
    chart_path = out_dir / f"perf_{ts}.png"

    collector = Collector(args.package, interval=args.interval)

    print("\n" + "="*60)
    print("  SmartLeaseEdge – iQOO Load Test & Performance Monitor")
    print("="*60)
    print(f"  Device  : {DEVICE_SERIAL}")
    print(f"  App     : {args.package}")
    print(f"  Interval: {args.interval}s  |  Load duration: {args.duration}s")
    print(f"  Output  : {out_dir}")
    print("="*60 + "\n")

    devices = adb("devices")
    if DEVICE_SERIAL not in devices:
        print("[ERROR] Device not found! Ensure USB debugging is enabled.")
        sys.exit(1)

    # PHASE 0: Baseline
    collector.mark_phase("baseline")
    print(f"  Collecting {args.baseline}s baseline (app not running)...")
    stop_app(args.package)
    time.sleep(2)
    get_cpu_usage()   # warm up delta tracker

    collector._start_time = time.time()
    collector.run(args.baseline)

    # PHASE 1: App launch
    collector.mark_phase("app_launch")
    act = detect_main_activity(args.package) or APP_ACTIVITY
    launch_app(args.package, act)
    time.sleep(10)
    get_cpu_usage()
    collector.run(10)   # capture launch settle time

    # PHASE 2: Fluctuating LLM + CPU Load
    collector.mark_phase("fluctuating_load")
    print(f"  Fluctuating load for {args.duration}s (1m ON / 1m OFF)...")

    stop_monkey = threading.Event()
    def monkey_loop():
        while not stop_monkey.is_set():
            send_monkey_events(args.package, count=150, throttle_ms=300)
            if stop_monkey.wait(timeout=2):
                break

    monkey_thread = threading.Thread(target=monkey_loop, daemon=True)
    monkey_thread.start()

    start_t = time.time()
    while time.time() - start_t < args.duration:
        # Stress ON
        print("  [+] STRESS ON")
        stress_proc = start_cpu_stress()
        collector.run(min(60, args.duration - (time.time() - start_t)))
        stop_cpu_stress(stress_proc)
        
        if time.time() - start_t >= args.duration:
            break
            
        # Stress OFF
        print("  [-] STRESS OFF")
        collector.run(min(60, args.duration - (time.time() - start_t)))

    stop_monkey.set()
    monkey_thread.join(timeout=5)

    # PHASE 4: Cooldown
    collector.mark_phase("cooldown")
    print(f"  Cooldown: monitoring for 30s...")
    stop_app(args.package)
    collector.run(30)

    # Save outputs
    collector.save_csv(csv_path)
    collector.save_charts(chart_path)

    # Summary
    df = pd.DataFrame(collector.rows)
    print("\n" + "="*60)
    print("  LOAD TEST SUMMARY")
    print("="*60)

    for phase in df["phase"].unique():
        sub = df[df["phase"] == phase]
        if len(sub) == 0:
            continue
        print(f"\n  Phase: {phase}")
        elapsed_range = sub['elapsed'].max() - sub['elapsed'].min()
        print(f"    Duration     : {elapsed_range:.1f}s")
        print(f"    CPU avg      : {sub['cpu_avg_pct'].mean():.1f}%  "
              f"(peak {sub['cpu_avg_pct'].max():.1f}%)")
        print(f"    RAM used     : {sub['ram_used_mb'].mean():.0f} MB avg  "
              f"(peak {sub['ram_used_mb'].max()} MB)")
        print(f"    CPU temp     : {sub['cpu_temp_c'].mean():.1f}C avg  "
              f"(peak {sub['cpu_temp_c'].max():.1f}C)")
        batt_start = sub['battery_pct'].iloc[0]
        batt_end   = sub['battery_pct'].iloc[-1]
        print(f"    Battery      : {batt_start}% -> {batt_end}%  "
              f"(-{batt_start - batt_end:.1f}%)")
        pss_vals = sub["app_pss_mb"][sub["app_pss_mb"] > 0]
        if not pss_vals.empty:
            print(f"    App PSS      : {pss_vals.mean():.0f} MB avg  "
                  f"(peak {pss_vals.max()} MB)")

    sat = df[df["cpu_avg_pct"] >= 90]["elapsed"]
    if not sat.empty:
        print(f"\n  First CPU >= 90% saturation : {sat.iloc[0]:.1f}s into test")
    else:
        print(f"\n  CPU never hit 90% saturation")

    total_batt = df["battery_pct"].iloc[0] - df["battery_pct"].iloc[-1]
    total_min  = df["elapsed"].iloc[-1] / 60
    drain_rate = total_batt / total_min if total_min > 0 else 0
    print(f"  Battery drain rate        : {drain_rate:.2f}%/min  "
          f"({drain_rate * 60:.1f}%/hr)")
    print(f"\n  CSV   -> {csv_path}")
    print(f"  Chart -> {chart_path}")
    print("="*60 + "\n")

    return csv_path, chart_path


# ---------------------------------------------------------------------------
# CLI entry point
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(
        description="iQOO SmartLeaseEdge – Load Test & Perf Monitor"
    )
    parser.add_argument("--duration",  type=int,   default=300,
                        help="Seconds of monitoring in PHASE 2+3 (default 300)")
    parser.add_argument("--interval",  type=float, default=2,
                        help="Sampling interval seconds (default 2)")
    parser.add_argument("--baseline",  type=int,   default=30,
                        help="Baseline idle seconds (default 30)")
    parser.add_argument("--out",       type=str,
                        default=str(Path(__file__).parent / "perf_results"),
                        help="Output directory for CSV + PNG")
    parser.add_argument("--package",   type=str,   default=APP_PACKAGE,
                        help="App package name to monitor")
    parser.add_argument("--no-stress", action="store_true",
                        help="Skip the 8-core CPU stress phase")
    args = parser.parse_args()
    run_load_test(args)


if __name__ == "__main__":
    main()
