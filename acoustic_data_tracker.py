#!/usr/bin/env python3
"""
SmartLeaseEdge — Acoustic Data Collection Tracker
Interactive tracker for acoustic tap sample collection.

Usage:
    python acoustic_data_tracker.py

Helps you track progress during the 2-3 hour acoustic recording session.
"""

import os
from pathlib import Path
from collections import defaultdict
from rich.console import Console
from rich.table import Table
from rich.panel import Panel
from rich.progress import Progress, BarColumn, TextColumn, TaskProgressColumn
from rich.prompt import Prompt, Confirm
from rich import print as rprint

console = Console()

BASE_DIR = Path(__file__).parent / "training_data" / "acoustic"

# Target samples per surface type
HOLLOW_TARGETS = {
    "door": 15,
    "drywall": 15,
    "cardboard": 10,
    "plastic": 10
}

SOLID_TARGETS = {
    "concrete": 15,
    "wood": 15,
    "filled": 10,
    "tile": 10
}

def count_samples():
    """Count existing samples in the directory."""
    hollow_counts = defaultdict(int)
    solid_counts = defaultdict(int)

    hollow_dir = BASE_DIR / "hollow"
    solid_dir = BASE_DIR / "solid"

    if hollow_dir.exists():
        for file in hollow_dir.iterdir():
            if file.suffix in ['.wav', '.m4a']:
                # Extract surface type from filename (e.g., "hollow_door_01.wav" → "door")
                parts = file.stem.split('_')
                if len(parts) >= 2:
                    surface_type = parts[1]
                    hollow_counts[surface_type] += 1

    if solid_dir.exists():
        for file in solid_dir.iterdir():
            if file.suffix in ['.wav', '.m4a']:
                parts = file.stem.split('_')
                if len(parts) >= 2:
                    surface_type = parts[1]
                    solid_counts[surface_type] += 1

    return hollow_counts, solid_counts

def display_progress():
    """Display current progress with visual progress bars."""
    hollow_counts, solid_counts = count_samples()

    console.print(Panel.fit(
        "[bold white]Acoustic Tap Collection Progress[/bold white]\n"
        "Track your recording session progress",
        border_style="cyan"
    ))

    # Hollow samples table
    console.print("\n[bold cyan]Hollow Tap Samples[/bold cyan]")
    table = Table()
    table.add_column("Surface", style="cyan")
    table.add_column("Current", justify="right")
    table.add_column("Target", justify="right")
    table.add_column("Progress", justify="left")
    table.add_column("Status", justify="center")

    total_hollow = 0
    total_hollow_target = sum(HOLLOW_TARGETS.values())

    for surface, target in HOLLOW_TARGETS.items():
        current = hollow_counts.get(surface, 0)
        total_hollow += current
        percentage = (current / target * 100) if target > 0 else 0

        # Progress bar
        bar_length = 20
        filled = int(bar_length * current / target) if target > 0 else 0
        bar = "█" * filled + "░" * (bar_length - filled)

        status = "✓" if current >= target else "⋯"
        status_color = "green" if current >= target else "yellow"

        table.add_row(
            surface.capitalize(),
            str(current),
            str(target),
            f"[cyan]{bar}[/cyan] {percentage:.0f}%",
            f"[{status_color}]{status}[/{status_color}]"
        )

    console.print(table)
    console.print(f"[bold]Total Hollow: {total_hollow}/{total_hollow_target}[/bold]\n")

    # Solid samples table
    console.print("[bold cyan]Solid Tap Samples[/bold cyan]")
    table = Table()
    table.add_column("Surface", style="cyan")
    table.add_column("Current", justify="right")
    table.add_column("Target", justify="right")
    table.add_column("Progress", justify="left")
    table.add_column("Status", justify="center")

    total_solid = 0
    total_solid_target = sum(SOLID_TARGETS.values())

    for surface, target in SOLID_TARGETS.items():
        current = solid_counts.get(surface, 0)
        total_solid += current
        percentage = (current / target * 100) if target > 0 else 0

        # Progress bar
        bar_length = 20
        filled = int(bar_length * current / target) if target > 0 else 0
        bar = "█" * filled + "░" * (bar_length - filled)

        status = "✓" if current >= target else "⋯"
        status_color = "green" if current >= target else "yellow"

        table.add_row(
            surface.capitalize(),
            str(current),
            str(target),
            f"[cyan]{bar}[/cyan] {percentage:.0f}%",
            f"[{status_color}]{status}[/{status_color}]"
        )

    console.print(table)
    console.print(f"[bold]Total Solid: {total_solid}/{total_solid_target}[/bold]\n")

    # Overall summary
    total_current = total_hollow + total_solid
    total_target = total_hollow_target + total_solid_target
    overall_percentage = (total_current / total_target * 100) if total_target > 0 else 0

    if total_current >= total_target:
        console.print(Panel(
            f"[bold green]✓ COMPLETE: {total_current}/{total_target} samples collected ({overall_percentage:.0f}%)[/bold green]\n"
            "Ready for Day 3 model training!",
            border_style="green"
        ))
    else:
        remaining = total_target - total_current
        console.print(Panel(
            f"[bold yellow]In Progress: {total_current}/{total_target} samples ({overall_percentage:.0f}%)[/bold yellow]\n"
            f"Remaining: {remaining} samples\n\n"
            f"Estimated time: {remaining * 2} minutes ({remaining * 2 // 60}h {remaining * 2 % 60}m)",
            border_style="yellow"
        ))

def suggest_next_task():
    """Suggest what to record next based on current progress."""
    hollow_counts, solid_counts = count_samples()

    console.print("\n[bold cyan]Suggested Next Task:[/bold cyan]")

    # Find the surface type with the most shortage
    max_shortage = 0
    next_task = None
    task_type = None

    for surface, target in HOLLOW_TARGETS.items():
        current = hollow_counts.get(surface, 0)
        shortage = target - current
        if shortage > max_shortage:
            max_shortage = shortage
            next_task = surface
            task_type = "hollow"

    for surface, target in SOLID_TARGETS.items():
        current = solid_counts.get(surface, 0)
        shortage = target - current
        if shortage > max_shortage:
            max_shortage = shortage
            next_task = surface
            task_type = "solid"

    if next_task:
        current = (hollow_counts if task_type == "hollow" else solid_counts).get(next_task, 0)
        target = (HOLLOW_TARGETS if task_type == "hollow" else SOLID_TARGETS)[next_task]

        console.print(f"[yellow]→ Record {task_type} {next_task} samples[/yellow]")
        console.print(f"  Current: {current}/{target}")
        console.print(f"  Need: {target - current} more samples")
        console.print(f"  Est. time: {(target - current) * 2} minutes\n")

        # Show tips for this surface type
        tips = {
            "door": "Tap center panel of hollow interior doors, not the frame",
            "drywall": "Find hollow area between studs — knock along wall to locate",
            "cardboard": "Use empty Amazon/delivery boxes, medium-to-large size",
            "plastic": "Empty storage bins or buckets work well",
            "concrete": "Exterior walls, parking structures, balcony walls",
            "wood": "Solid furniture, dining tables, solid-core doors",
            "filled": "Full water jugs, filled cardboard boxes (80%+ full)",
            "tile": "Bathroom/kitchen floor tiles over solid substrate"
        }

        if next_task in tips:
            console.print(f"[dim]Tip: {tips[next_task]}[/dim]")
    else:
        console.print("[green]All samples collected! ✓[/green]")

def main():
    console.clear()

    while True:
        display_progress()
        suggest_next_task()

        console.print("\n[bold]Actions:[/bold]")
        console.print("  [r] Refresh progress")
        console.print("  [h] Show recording tips")
        console.print("  [q] Quit")

        action = Prompt.ask("\nWhat do you want to do?", choices=["r", "h", "q"], default="r")

        if action == "r":
            console.clear()
            continue
        elif action == "h":
            console.print(Panel(
                "[bold]Recording Tips:[/bold]\n\n"
                "1. Phone distance: 10-20cm from surface\n"
                "2. Tapping: Use middle knuckle, medium force\n"
                "3. Pattern: 5-7 taps per recording, 0.5s apart\n"
                "4. Duration: 3-5 seconds per recording\n"
                "5. Background: Minimize noise (no TV, music, traffic)\n"
                "6. Format: WAV or M4A, 44.1kHz+\n"
                "7. Listen back: Check quality immediately after recording",
                border_style="cyan"
            ))
            Prompt.ask("\nPress Enter to continue")
            console.clear()
        elif action == "q":
            break

if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        console.print("\n[yellow]Session interrupted. Progress saved.[/yellow]")
