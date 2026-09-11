"""
SmartLease Edge — Unified Gradio Demo App
Combines Vision + Acoustic + Report subsystems into one interactive UI.
Run: python app.py
"""
import sys
import os
from pathlib import Path
from datetime import datetime

import numpy as np
import gradio as gr

sys.path.insert(0, str(Path(__file__).parent))
from config import MODELS_DIR, OUTPUTS_DIR, ACOUSTIC


# ─── Lazy-load models ──────────────────────────────
vision_predictor = None
acoustic_predictor = None

# Accumulate findings across tabs for report generation
session_findings = {
    "property_address": "",
    "tenant_name": "",
    "landlord_name": "",
    "inspection_date": datetime.now().strftime("%Y-%m-%d %H:%M"),
    "rooms": [],
}
current_room = {
    "name": "Living Room",
    "vision_defects": [],
    "acoustic_results": [],
    "appliance_tests": [],
}


def get_vision_predictor():
    global vision_predictor
    if vision_predictor is None:
        try:
            from vision.predict import VisionPredictor
            vision_predictor = VisionPredictor()
        except Exception as e:
            print(f"Vision model load error: {e}")
    return vision_predictor


def get_acoustic_predictor():
    global acoustic_predictor
    if acoustic_predictor is None:
        try:
            from acoustic.predict import AcousticPredictor
            acoustic_predictor = AcousticPredictor()
        except Exception as e:
            print(f"Acoustic model load error: {e}")
    return acoustic_predictor


def extract_audio_from_media(media_path: str) -> str:
    """Extract audio track from MP4/MOV/M4A video/audio containers to 16kHz WAV."""
    if not media_path:
        return None
    path = Path(media_path)
    if path.suffix.lower() == ".wav":
        return str(path)

    out_wav = path.parent / f"{path.stem}_audio.wav"
    if out_wav.exists() and out_wav.stat().st_size > 1000:
        return str(out_wav)

    # 1. Try imageio_ffmpeg
    try:
        import imageio_ffmpeg
        ffmpeg_exe = imageio_ffmpeg.get_ffmpeg_exe()
        import subprocess
        subprocess.run([
            ffmpeg_exe, "-y", "-i", str(path),
            "-vn", "-ac", "1", "-ar", "16000", str(out_wav)
        ], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        if out_wav.exists():
            return str(out_wav)
    except ImportError:
        import os, sys
        os.system(f"{sys.executable} -m pip install -q imageio-ffmpeg")
        try:
            import imageio_ffmpeg
            ffmpeg_exe = imageio_ffmpeg.get_ffmpeg_exe()
            import subprocess
            subprocess.run([
                ffmpeg_exe, "-y", "-i", str(path),
                "-vn", "-ac", "1", "-ar", "16000", str(out_wav)
            ], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            if out_wav.exists():
                return str(out_wav)
        except Exception:
            pass
    except Exception:
        pass

    # 2. Try system ffmpeg
    try:
        import subprocess
        subprocess.run([
            "ffmpeg", "-y", "-i", str(path),
            "-vn", "-ac", "1", "-ar", "16000", str(out_wav)
        ], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        if out_wav.exists():
            return str(out_wav)
    except Exception:
        pass

    return str(path)


# ─── Vision Tab Handler ────────────────────────────
def analyze_wall(image, video_file, depth_meters, room_name):
    actual_image = image

    # If video file (.mp4) was provided instead of image
    if actual_image is None and video_file is not None:
        try:
            import cv2
            cap = cv2.VideoCapture(str(video_file))
            total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
            if total_frames > 1:
                cap.set(cv2.CAP_PROP_POS_FRAMES, total_frames // 2)
            ret, frame = cap.read()
            cap.release()
            if ret and frame is not None:
                actual_image = frame[:, :, ::-1]  # BGR to RGB
        except Exception as e:
            return None, f"❌ Video frame extraction error: {e}"

    if actual_image is None:
        return None, "Please upload a photo or MP4 video of the wall first."

    predictor = get_vision_predictor()
    if predictor is None:
        return actual_image, "❌ Vision model not available. Run: python -m vision.train"

    try:
        annotated, defects = predictor.predict(actual_image, depth_meters=depth_meters)

        if not defects:
            report = "✅ No defects detected — wall looks clean!"
        else:
            lines = [f"**{len(defects)} defect(s) found:**\n"]
            for d in defects:
                icon = {"minor": "🟡", "moderate": "🟠", "severe": "🔴"}.get(d.severity, "⚪")
                lines.append(
                    f"{icon} **{d.class_name.replace('_', ' ').title()}** — "
                    f"{d.severity.upper()} — {d.area_percent:.1f}% area — "
                    f"{d.confidence:.0%} confidence"
                )
                if d.area_sqft:
                    lines.append(f"   📐 Estimated: {d.area_sqft:.2f} sq.ft.")

            report = "\n".join(lines)

            # Store in session
            current_room["name"] = room_name
            current_room["vision_defects"] = [
                {
                    "type": d.class_name,
                    "severity": d.severity,
                    "area_sqft": d.area_sqft or 0,
                    "area_percent": d.area_percent,
                    "location": room_name,
                    "confidence": d.confidence,
                }
                for d in defects
            ]

        # Convert BGR to RGB for Gradio
        if annotated is not None:
            annotated = annotated[:, :, ::-1]

        return annotated, report

    except Exception as e:
        return actual_image, f"❌ Error: {str(e)}"


# ─── Acoustic Tab Handler ─────────────────────────
def classify_tap(uploaded_file, mic_audio, room_name, location):
    target = uploaded_file or mic_audio
    if target is None:
        return "Upload an audio/video file (.mp4, .m4a, .wav, .mp3) or record a live tap first."

    # Extract audio track if MP4/video container
    audio_path = extract_audio_from_media(target)

    predictor = get_acoustic_predictor()
    if predictor is None:
        return "❌ Acoustic model not available. Run:\n1. python data/generate_audio.py\n2. python -m acoustic.train"

    try:
        result = predictor.predict(audio_path=audio_path)

        if result.prediction == "hollow":
            report = (
                f"⚠️ **HOLLOW** detected ({result.confidence:.0%} confidence)\n\n"
                f"Possible void or loose tile behind surface.\n"
                f"Recommended: Further inspection by qualified professional.\n\n"
                f"---\n"
                f"CNN: {result.cnn_prediction} ({result.cnn_confidence:.0%})\n"
                f"RF: {result.rf_prediction} ({result.rf_confidence:.0%})"
            )
        else:
            report = (
                f"✅ **SOLID** surface ({result.confidence:.0%} confidence)\n\n"
                f"Surface appears structurally sound.\n\n"
                f"---\n"
                f"CNN: {result.cnn_prediction} ({result.cnn_confidence:.0%})\n"
                f"RF: {result.rf_prediction} ({result.rf_confidence:.0%})"
            )

        # Store in session
        current_room["acoustic_results"].append({
            "location": location or "unknown",
            "result": result.prediction,
            "confidence": result.confidence,
        })

        return report

    except Exception as e:
        return f"❌ Error: {str(e)}"


# ─── Report Tab Handler ──────────────────────────
def generate_full_report(address, tenant, landlord, room_name):
    try:
        from report.generator import generate_report

        # Build findings from session data
        findings = {
            "property_address": address or "Not specified",
            "tenant_name": tenant or "Not specified",
            "landlord_name": landlord or "Not specified",
            "inspection_date": datetime.now().strftime("%Y-%m-%d %H:%M"),
            "rooms": [{
                "name": room_name or "Living Room",
                "vision_defects": current_room.get("vision_defects", []),
                "acoustic_results": current_room.get("acoustic_results", []),
                "appliance_tests": current_room.get("appliance_tests", []),
            }],
        }

        output_path = str(OUTPUTS_DIR / f"report_{datetime.now().strftime('%H%M%S')}.pdf")
        path = generate_report(findings, output_path=output_path)

        return f"✅ Report generated!\n📄 Path: {path}", path

    except Exception as e:
        return f"❌ Error: {str(e)}", None


def generate_sample_report():
    """Generate a report with sample data."""
    try:
        from report.generator import generate_report, SAMPLE_FINDINGS

        output_path = str(OUTPUTS_DIR / f"sample_report_{datetime.now().strftime('%H%M%S')}.pdf")
        path = generate_report(SAMPLE_FINDINGS, output_path=output_path)

        return f"✅ Sample report generated!\n📄 Path: {path}", path

    except Exception as e:
        return f"❌ Error: {str(e)}", None


# ─── Build Gradio UI ─────────────────────────────
def create_app():
    with gr.Blocks(
        title="SmartLease Edge — Property Inspection",
        theme=gr.themes.Soft(
            primary_hue=gr.themes.colors.blue,
            secondary_hue=gr.themes.colors.red,
        ),
        css="""
        .gradio-container { max-width: 1200px !important; }
        .main-title { text-align: center; margin-bottom: 0; }
        .sub-title { text-align: center; color: #666; margin-top: 0; }
        """
    ) as app:

        gr.Markdown("# 🏠 SmartLease Edge", elem_classes="main-title")
        gr.Markdown("### AI-Powered Property Inspection | Fully Offline | Practice Prototype", elem_classes="sub-title")

        with gr.Tab("🔍 Vision — Wall Damage"):
            gr.Markdown("Upload a wall photo or **MP4 video** to detect cracks, stains, mold, and damage.")
            with gr.Row():
                with gr.Column(scale=1):
                    img_input = gr.Image(type="numpy", label="Wall Photo", height=320)
                    video_wall_input = gr.File(
                        label="Or Upload Wall Video (.mp4 / .mov)",
                        file_types=[".mp4", ".mov", ".avi"],
                        type="filepath",
                    )
                    with gr.Row():
                        depth_slider = gr.Slider(0.5, 5.0, value=2.0, step=0.1,
                                                 label="Distance to wall (meters)")
                        room_vision = gr.Textbox(value="Living Room", label="Room Name")
                    btn_analyze = gr.Button("🔍 Analyze Wall", variant="primary", size="lg")

                with gr.Column(scale=1):
                    img_output = gr.Image(label="Detection Result", height=400)
                    vision_report = gr.Markdown(label="Analysis")

            btn_analyze.click(
                analyze_wall,
                inputs=[img_input, video_wall_input, depth_slider, room_vision],
                outputs=[img_output, vision_report],
            )

            # Pre-loaded test wall samples
            sample_img_dir = Path("datasets/wall-defects/valid/images")
            sample_imgs = [str(f) for f in sample_img_dir.glob("*.jpg")][:4] if sample_img_dir.exists() else []
            if sample_imgs:
                gr.Examples(
                    examples=[[img, None, 2.0, "Living Room"] for img in sample_imgs],
                    inputs=[img_input, video_wall_input, depth_slider, room_vision],
                    label="📸 Click any sample wall image below to test instantly:",
                )

        with gr.Tab("🔊 Acoustic — Tap Test"):
            gr.Markdown("Upload a tap recording or video (**MP4, M4A, WAV, MP3**) or record live with your microphone.")
            with gr.Row():
                with gr.Column(scale=1):
                    file_input = gr.File(
                        label="Upload Tap Video or Audio (.mp4, .m4a, .wav, .mp3)",
                        file_types=[".mp4", ".mov", ".m4a", ".wav", ".mp3", ".ogg", ".aac", ".webm"],
                        type="filepath",
                    )
                    mic_input = gr.Audio(
                        type="filepath",
                        label="Or Record Live Tap via Microphone",
                        sources=["microphone"],
                    )
                    with gr.Row():
                        room_acoustic = gr.Textbox(value="Living Room", label="Room")
                        tap_location = gr.Textbox(value="floor_tile_center", label="Tap Location")
                    btn_classify = gr.Button("🔊 Classify Tap", variant="primary", size="lg")

                with gr.Column(scale=1):
                    acoustic_report = gr.Markdown(label="Result")

            btn_classify.click(
                classify_tap,
                inputs=[file_input, mic_input, room_acoustic, tap_location],
                outputs=[acoustic_report],
            )

            # Pre-loaded sample taps
            hollow_sample = Path("data/synthetic_audio/hollow/synthetic_0000.wav")
            solid_sample = Path("data/synthetic_audio/solid/synthetic_0000.wav")
            hollow_test = Path("outputs/test_sounds/test_tap_hollow_cavity.wav")
            solid_test = Path("outputs/test_sounds/acoustic_test_solid.wav")
            acoustic_examples = []
            if hollow_test.exists():
                acoustic_examples.append([str(hollow_test), None, "Bathroom", "cavity_hollow_impulse"])
            elif hollow_sample.exists():
                acoustic_examples.append([str(hollow_sample), None, "Bathroom", "shower_tile_hollow_test"])
            if solid_test.exists():
                acoustic_examples.append([str(solid_test), None, "Living Room", "solid_bonded_impact_600hz"])
            elif solid_sample.exists():
                acoustic_examples.append([str(solid_sample), None, "Living Room", "floor_tile_solid_test"])
            if acoustic_examples:
                gr.Examples(
                    examples=acoustic_examples,
                    inputs=[file_input, mic_input, room_acoustic, tap_location],
                    label="🔊 Click any sample tap below to test instantly:",
                )

        with gr.Tab("📄 Report Generation"):
            gr.Markdown("Generate an inspection report from accumulated findings, or use sample data.")

            with gr.Row():
                with gr.Column():
                    address = gr.Textbox(
                        value="42, Thiruvalluvar St, T. Nagar, Chennai - 600017",
                        label="Property Address"
                    )
                    tenant = gr.Textbox(value="Rahul Krishnan", label="Tenant Name")
                    landlord = gr.Textbox(value="Meena Sundaram", label="Landlord Name")
                    room_report = gr.Textbox(value="Living Room", label="Room Name")

                with gr.Column():
                    report_status = gr.Markdown()
                    report_file = gr.File(label="Download Report")

            with gr.Row():
                btn_report = gr.Button("📄 Generate Report (from session)", variant="primary")
                btn_sample = gr.Button("📄 Generate Sample Report", variant="secondary")

            btn_report.click(
                generate_full_report,
                inputs=[address, tenant, landlord, room_report],
                outputs=[report_status, report_file],
            )
            btn_sample.click(
                generate_sample_report,
                outputs=[report_status, report_file],
            )

        with gr.Tab("ℹ️ System Status"):
            gr.Markdown(f"""
## Model Status

| Subsystem | Model File | Status |
|-----------|-----------|--------|
| Vision (YOLOv8-Seg) | `models/vision_best.pt` | {'✅ Found' if (MODELS_DIR / 'vision_best.pt').exists() else '❌ Not trained'} |
| Acoustic CNN | `models/acoustic_cnn.pt` | {'✅ Found' if (MODELS_DIR / 'acoustic_cnn.pt').exists() else '❌ Not trained'} |
| Acoustic RF | `models/acoustic_rf.pkl` | {'✅ Found' if (MODELS_DIR / 'acoustic_rf.pkl').exists() else '❌ Not trained'} |

## Quick Setup Commands

```bash
# 1. Generate synthetic audio data
python data/generate_audio.py

# 2. Generate sample vision dataset
python data/download_dataset.py --sample

# 3. Train vision model
python -m vision.train

# 4. Train acoustic model
python -m acoustic.train

# 5. Generate sample report
python -m report.generator --sample

# 6. Launch this demo
python app.py
```

## Architecture
- **Vision**: YOLOv8n-Seg → defect segmentation → area quantification
- **Acoustic**: Log-Mel spectrogram → CNN + Random Forest ensemble → hollow/solid
- **Report**: Structured findings → cost estimation → signed PDF
- **All offline**: No network calls at any point
            """)

    return app


if __name__ == "__main__":
    app = create_app()
    app.launch(
        server_name="0.0.0.0",
        server_port=7860,
        share=False,
        show_error=True,
    )
