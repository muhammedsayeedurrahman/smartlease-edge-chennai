#!/usr/bin/env python3
"""
SmartLease Edge — Automated Dataset Downloader
Downloads public wall damage datasets from common sources.

Usage:
    python download_datasets.py

Requirements:
    pip install requests beautifulsoup4 kaggle roboflow rich

    For Kaggle:
        1. Create ~/.kaggle/kaggle.json with API credentials
        2. Get API key from kaggle.com/[username]/account
"""

import os
import sys
from pathlib import Path
import subprocess
from rich.console import Console
from rich.prompt import Confirm, Prompt
from rich.panel import Panel
from rich.progress import Progress

console = Console()

DATASETS_DIR = Path(__file__).parent / "training_data" / "vision" / "datasets"
DATASETS_DIR.mkdir(parents=True, exist_ok=True)

def check_dependencies():
    """Check if required packages are installed."""
    console.print("[cyan]Checking dependencies...[/cyan]")

    missing = []
    try:
        import requests
    except ImportError:
        missing.append("requests")

    try:
        import bs4
    except ImportError:
        missing.append("beautifulsoup4")

    try:
        import kaggle
    except ImportError:
        missing.append("kaggle")

    try:
        import roboflow
    except ImportError:
        missing.append("roboflow")

    if missing:
        console.print(f"[yellow]Missing packages: {', '.join(missing)}[/yellow]")
        console.print("\nInstall with:")
        console.print(f"  pip install {' '.join(missing)}")
        return False

    console.print("[green]✓ All dependencies installed[/green]")
    return True

def download_kaggle_dataset(dataset_slug, output_dir):
    """
    Download a Kaggle dataset.

    Args:
        dataset_slug: Kaggle dataset identifier (e.g., 'arunrk7/surface-crack-detection')
        output_dir: Destination directory
    """
    try:
        from kaggle.api.kaggle_api_extended import KaggleApi

        api = KaggleApi()
        api.authenticate()

        console.print(f"[cyan]Downloading Kaggle dataset: {dataset_slug}[/cyan]")

        with Progress() as progress:
            task = progress.add_task("[cyan]Downloading...", total=100)

            api.dataset_download_files(
                dataset_slug,
                path=output_dir,
                unzip=True
            )

            progress.update(task, completed=100)

        console.print(f"[green]✓ Downloaded to {output_dir}[/green]")
        return True

    except Exception as e:
        console.print(f"[red]✗ Kaggle download failed: {e}[/red]")
        console.print("\n[yellow]Make sure you have:[/yellow]")
        console.print("  1. Kaggle account")
        console.print("  2. API key at ~/.kaggle/kaggle.json")
        console.print("  3. Dataset exists and is public")
        return False

def download_roboflow_dataset(workspace, project, version, api_key, output_dir, format="yolov8"):
    """
    Download a Roboflow dataset.

    Args:
        workspace: Roboflow workspace name
        project: Project name
        version: Dataset version number
        api_key: Roboflow API key
        output_dir: Destination directory
        format: Export format (default: yolov8)
    """
    try:
        from roboflow import Roboflow

        console.print(f"[cyan]Downloading Roboflow dataset: {workspace}/{project}[/cyan]")

        rf = Roboflow(api_key=api_key)
        project_obj = rf.workspace(workspace).project(project)
        dataset = project_obj.version(version).download(format, location=str(output_dir))

        console.print(f"[green]✓ Downloaded to {output_dir}[/green]")
        return True

    except Exception as e:
        console.print(f"[red]✗ Roboflow download failed: {e}[/red]")
        console.print("\n[yellow]Make sure you have:[/yellow]")
        console.print("  1. Valid Roboflow API key")
        console.print("  2. Access to the dataset (public or in your workspace)")
        return False

def interactive_mode():
    """Interactive dataset download wizard."""
    console.print(Panel.fit(
        "[bold white]SmartLease Edge — Dataset Downloader[/bold white]\n"
        "Download pre-annotated wall damage datasets for YOLOv8-Seg training",
        border_style="cyan"
    ))

    console.print("\n[bold cyan]Recommended Public Datasets[/bold cyan]\n")
    console.print("1. Kaggle: Surface Crack Detection (arunrk7/surface-crack-detection)")
    console.print("2. Roboflow: Wall Crack Detection (search on universe.roboflow.com)")
    console.print("3. Manual download (I'll provide links, you download manually)")

    choice = Prompt.ask(
        "\n[cyan]Choose download method[/cyan]",
        choices=["1", "2", "3", "skip"],
        default="3"
    )

    if choice == "1":
        # Kaggle download
        dataset_slug = Prompt.ask(
            "[cyan]Enter Kaggle dataset slug[/cyan]",
            default="arunrk7/surface-crack-detection"
        )

        output_name = Prompt.ask(
            "[cyan]Save as folder name[/cyan]",
            default="kaggle_surface_crack"
        )

        output_dir = DATASETS_DIR / output_name
        download_kaggle_dataset(dataset_slug, output_dir)

    elif choice == "2":
        # Roboflow download
        console.print("\n[yellow]You'll need:[/yellow]")
        console.print("  1. Roboflow account (free)")
        console.print("  2. API key from roboflow.com/settings/api")
        console.print("  3. Dataset URL (e.g., roboflow.com/workspace/project/version)")

        api_key = Prompt.ask("[cyan]Enter Roboflow API key[/cyan]")
        workspace = Prompt.ask("[cyan]Enter workspace name[/cyan]")
        project = Prompt.ask("[cyan]Enter project name[/cyan]")
        version = Prompt.ask("[cyan]Enter version number[/cyan]", default="1")

        output_name = Prompt.ask(
            "[cyan]Save as folder name[/cyan]",
            default=f"roboflow_{project}"
        )

        output_dir = DATASETS_DIR / output_name
        download_roboflow_dataset(workspace, project, int(version), api_key, output_dir)

    elif choice == "3":
        # Manual download instructions
        console.print("\n[bold cyan]Manual Download Instructions[/bold cyan]\n")
        console.print("[yellow]OPTION A: Roboflow Universe[/yellow]")
        console.print("  1. Visit: https://universe.roboflow.com")
        console.print("  2. Search: 'wall damage' or 'surface crack'")
        console.print("  3. Filter: Segmentation datasets only")
        console.print("  4. Click dataset → Download Dataset")
        console.print("  5. Format: Select 'YOLOv8' or 'YOLO Segmentation'")
        console.print(f"  6. Save to: {DATASETS_DIR.absolute()}/[dataset_name]/")

        console.print("\n[yellow]OPTION B: Kaggle[/yellow]")
        console.print("  1. Visit: https://www.kaggle.com/datasets")
        console.print("  2. Search: 'wall crack segmentation' or 'concrete defect'")
        console.print("  3. Download dataset (requires Kaggle account)")
        console.print(f"  4. Extract to: {DATASETS_DIR.absolute()}/[dataset_name]/")

        console.print("\n[yellow]OPTION C: GitHub[/yellow]")
        console.print("  1. Search: github.com/search → 'wall damage dataset'")
        console.print("  2. Clone or download repo")
        console.print(f"  3. Copy image data to: {DATASETS_DIR.absolute()}/[dataset_name]/")

        console.print(f"\n[green]Save all datasets to:[/green]\n  {DATASETS_DIR.absolute()}\n")

    else:
        console.print("[yellow]Skipping dataset download[/yellow]")

def main():
    console.print("[bold cyan]SmartLease Edge — Dataset Downloader[/bold cyan]\n")

    # Check if dependencies are installed
    if not check_dependencies():
        console.print("\n[yellow]Install missing packages and run again[/yellow]")
        return 1

    # Run interactive mode
    interactive_mode()

    # Show final status
    console.print("\n[bold cyan]Downloaded Datasets:[/bold cyan]")

    if not DATASETS_DIR.exists() or not list(DATASETS_DIR.iterdir()):
        console.print("[yellow]No datasets downloaded yet[/yellow]")
        console.print(f"\nManually add datasets to: {DATASETS_DIR.absolute()}")
    else:
        for dataset_dir in DATASETS_DIR.iterdir():
            if dataset_dir.is_dir():
                image_count = len(list(dataset_dir.glob("**/*.jpg"))) + len(list(dataset_dir.glob("**/*.png")))
                console.print(f"  ✓ {dataset_dir.name}: ~{image_count} images")

    console.print("\n[green]Next step:[/green] Run python validate_training_data.py")
    return 0

if __name__ == "__main__":
    exit(main())
