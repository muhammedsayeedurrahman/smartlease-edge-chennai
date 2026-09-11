#!/usr/bin/env python3
"""
SmartLease Edge — Training Data Validation Script
Run this at the end of Day 2 to verify data collection is complete.

Usage:
    python validate_training_data.py

Requirements:
    pip install rich
"""

import os
from pathlib import Path
from collections import defaultdict
from rich.console import Console
from rich.table import Table
from rich.panel import Panel
from rich import print as rprint

console = Console()

# Paths
BASE_DIR = Path(__file__).parent / "training_data"
ACOUSTIC_DIR = BASE_DIR / "acoustic"
VISION_DIR = BASE_DIR / "vision"

# Targets from the 12-day plan
ACOUSTIC_MIN = 30  # per class (hollow, solid)
VISION_MIN = 200   # total images

def validate_acoustic_data():
    """Check acoustic tap samples."""
    console.print("\n[bold cyan]1. Acoustic Tap Audio Validation[/bold cyan]")

    if not ACOUSTIC_DIR.exists():
        console.print(f"[red]✗ Directory not found: {ACOUSTIC_DIR}[/red]")
        return False

    hollow_dir = ACOUSTIC_DIR / "hollow"
    solid_dir = ACOUSTIC_DIR / "solid"

    # Count files
    hollow_count = len(list(hollow_dir.glob("*.wav"))) + len(list(hollow_dir.glob("*.m4a"))) if hollow_dir.exists() else 0
    solid_count = len(list(solid_dir.glob("*.wav"))) + len(list(solid_dir.glob("*.m4a"))) if solid_dir.exists() else 0

    table = Table(title="Acoustic Sample Counts")
    table.add_column("Class", style="cyan")
    table.add_column("Samples", justify="right")
    table.add_column("Target", justify="right")
    table.add_column("Status", justify="center")

    hollow_ok = hollow_count >= ACOUSTIC_MIN
    solid_ok = solid_count >= ACOUSTIC_MIN

    table.add_row(
        "Hollow",
        str(hollow_count),
        f"{ACOUSTIC_MIN}+",
        "✓" if hollow_ok else "✗"
    )
    table.add_row(
        "Solid",
        str(solid_count),
        f"{ACOUSTIC_MIN}+",
        "✓" if solid_ok else "✗"
    )

    console.print(table)

    if hollow_ok and solid_ok:
        console.print("[green]✓ Acoustic data collection complete[/green]")
        return True
    else:
        console.print(f"[yellow]⚠ Need {ACOUSTIC_MIN - hollow_count if not hollow_ok else 0} more hollow samples, "
                     f"{ACOUSTIC_MIN - solid_count if not solid_ok else 0} more solid samples[/yellow]")
        return False

def validate_vision_data():
    """Check wall damage images and labels."""
    console.print("\n[bold cyan]2. Vision Training Data Validation[/bold cyan]")

    if not VISION_DIR.exists():
        console.print(f"[red]✗ Directory not found: {VISION_DIR}[/red]")
        return False

    images_dir = VISION_DIR / "images"
    labels_dir = VISION_DIR / "labels"
    datasets_dir = VISION_DIR / "datasets"

    # Count images
    image_exts = ["*.jpg", "*.jpeg", "*.png"]
    image_count = 0
    if images_dir.exists():
        for ext in image_exts:
            image_count += len(list(images_dir.glob(ext)))

    # Count labels
    label_count = len(list(labels_dir.glob("*.txt"))) if labels_dir.exists() else 0

    # Count dataset downloads
    dataset_count = len(list(datasets_dir.iterdir())) if datasets_dir.exists() and datasets_dir.is_dir() else 0

    table = Table(title="Vision Dataset Inventory")
    table.add_column("Category", style="cyan")
    table.add_column("Count", justify="right")
    table.add_column("Notes")

    table.add_row("Images (own photos)", str(image_count), "Optional if using public datasets")
    table.add_row("Labels (annotations)", str(label_count), "Should match image count if annotated")
    table.add_row("Public datasets", str(dataset_count), "Roboflow/Kaggle downloads")

    console.print(table)

    # Check if we have enough total data
    # Either from own images OR from public datasets
    total_estimate = image_count + (dataset_count * 150)  # Assume 150 images per dataset

    if total_estimate >= VISION_MIN:
        console.print(f"[green]✓ Vision data collection complete (estimated {total_estimate} images)[/green]")
        return True
    else:
        console.print(f"[yellow]⚠ Need ~{VISION_MIN - total_estimate} more images or {(VISION_MIN - total_estimate) // 150 + 1} more datasets[/yellow]")
        return False

def check_directory_structure():
    """Verify training_data/ structure exists."""
    console.print("\n[bold cyan]3. Directory Structure Check[/bold cyan]")

    required_dirs = [
        ACOUSTIC_DIR / "hollow",
        ACOUSTIC_DIR / "solid",
        VISION_DIR / "images",
        VISION_DIR / "labels",
        VISION_DIR / "datasets"
    ]

    all_exist = True
    for dir_path in required_dirs:
        exists = dir_path.exists()
        status = "[green]✓[/green]" if exists else "[red]✗[/red]"
        console.print(f"{status} {dir_path.relative_to(BASE_DIR.parent)}")
        if not exists:
            all_exist = False

    if not all_exist:
        console.print("\n[yellow]Creating missing directories...[/yellow]")
        for dir_path in required_dirs:
            dir_path.mkdir(parents=True, exist_ok=True)
        console.print("[green]✓ Directories created[/green]")

    return True

def main():
    console.print(Panel.fit(
        "[bold white]SmartLease Edge — Day 2 Data Collection Validation[/bold white]\n"
        "Verifying training data before Day 3 model training begins",
        border_style="cyan"
    ))

    # Check structure first
    structure_ok = check_directory_structure()

    # Validate data
    acoustic_ok = validate_acoustic_data()
    vision_ok = validate_vision_data()

    # Final summary
    console.print("\n[bold cyan]Summary[/bold cyan]")

    if acoustic_ok and vision_ok:
        console.print(Panel(
            "[bold green]✓ Day 2 data collection COMPLETE[/bold green]\n\n"
            "Ready to begin Day 3 model training.\n"
            "Next steps:\n"
            "  1. Backup training_data/ folder\n"
            "  2. Review DATA_COLLECTION_GUIDE.md checklist\n"
            "  3. Proceed to acoustic classifier training (Day 3)",
            border_style="green"
        ))
        return 0
    else:
        missing = []
        if not acoustic_ok:
            missing.append("acoustic samples")
        if not vision_ok:
            missing.append("vision images")

        console.print(Panel(
            f"[bold yellow]⚠ Data collection INCOMPLETE[/bold yellow]\n\n"
            f"Missing: {', '.join(missing)}\n\n"
            f"Review DATA_COLLECTION_GUIDE.md and complete missing items before Day 3.",
            border_style="yellow"
        ))
        return 1

if __name__ == "__main__":
    exit(main())
