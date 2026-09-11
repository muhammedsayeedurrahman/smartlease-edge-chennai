"""
SmartLease Edge — One-command setup and verification
Run: python setup.py
"""
import subprocess
import sys
import os
from pathlib import Path


def run_cmd(cmd: str, description: str) -> bool:
    """Run a command and report success/failure."""
    print(f"\n{'='*60}")
    print(f"  {description}")
    print(f"{'='*60}")
    try:
        result = subprocess.run(
            cmd, shell=True, check=True,
            capture_output=True, text=True
        )
        if result.stdout:
            # Only print last few lines to keep output clean
            lines = result.stdout.strip().split('\n')
            for line in lines[-5:]:
                print(f"  {line}")
        print(f"  ✅ {description} — OK")
        return True
    except subprocess.CalledProcessError as e:
        print(f"  ❌ {description} — FAILED")
        if e.stderr:
            for line in e.stderr.strip().split('\n')[-3:]:
                print(f"  {line}")
        return False


def main():
    root = Path(__file__).parent
    os.chdir(root)

    print("╔══════════════════════════════════════════════════════╗")
    print("║        SmartLease Edge — Project Setup               ║")
    print("║        Practice Prototype for iQOO Hackathon         ║")
    print("╚══════════════════════════════════════════════════════╝")

    results = {}

    # Step 1: Check Python
    results["Python"] = run_cmd(
        f"{sys.executable} --version",
        "Checking Python version"
    )

    # Step 2: Create virtual environment
    venv_path = root / "venv"
    if not venv_path.exists():
        results["Venv"] = run_cmd(
            f"{sys.executable} -m venv venv",
            "Creating virtual environment"
        )
    else:
        print(f"\n  ℹ️  Virtual environment already exists at {venv_path}")
        results["Venv"] = True

    # Step 3: Install dependencies
    if os.name == 'nt':
        pip_cmd = str(venv_path / "Scripts" / "pip")
        python_cmd = str(venv_path / "Scripts" / "python")
    else:
        pip_cmd = str(venv_path / "bin" / "pip")
        python_cmd = str(venv_path / "bin" / "python")

    results["Dependencies"] = run_cmd(
        f'"{pip_cmd}" install -r requirements.txt',
        "Installing dependencies (this may take a few minutes)"
    )

    # Step 4: Create directories
    for d in ['models', 'outputs', 'datasets', 'data/audio/hollow', 'data/audio/solid',
              'data/synthetic_audio']:
        (root / d).mkdir(parents=True, exist_ok=True)
    print("\n  ✅ Directory structure created")
    results["Directories"] = True

    # Step 5: Verify imports
    results["Imports"] = run_cmd(
        f'"{python_cmd}" -c "import ultralytics, librosa, gradio, torch, fpdf2; print(\'All imports OK\')"',
        "Verifying library imports"
    )

    # Step 6: Generate sample data
    results["Sample Audio"] = run_cmd(
        f'"{python_cmd}" data/generate_audio.py --count 30 --no-augment',
        "Generating sample audio data (30 per class, no augmentation)"
    )

    results["Sample Vision"] = run_cmd(
        f'"{python_cmd}" data/download_dataset.py --sample',
        "Generating sample vision dataset"
    )

    # Summary
    print("\n")
    print("╔══════════════════════════════════════════════════════╗")
    print("║                    Setup Summary                     ║")
    print("╚══════════════════════════════════════════════════════╝")
    all_ok = True
    for step, ok in results.items():
        icon = "✅" if ok else "❌"
        print(f"  {icon} {step}")
        if not ok:
            all_ok = False

    if all_ok:
        print(f"\n  🎉 Setup complete! Next steps:")
        print(f"  ────────────────────────────────")
        activate = "venv\\Scripts\\activate" if os.name == 'nt' else "source venv/bin/activate"
        print(f"  1. Activate venv:  {activate}")
        print(f"  2. Train acoustic: python -m acoustic.train")
        print(f"  3. Train vision:   python -m vision.train")
        print(f"  4. Test report:    python -m report.generator --sample")
        print(f"  5. Launch demo:    python app.py")
        print(f"\n  The demo will open at http://localhost:7860")
    else:
        print(f"\n  ⚠️  Some steps failed. Fix the errors above and re-run setup.py")

    return all_ok


if __name__ == "__main__":
    success = main()
    sys.exit(0 if success else 1)
