"""
SmartLease Edge — PDF Report Generator
Generates a professional multi-page inspection report from structured findings.
"""
import sys
import hashlib
from pathlib import Path
from datetime import datetime
from typing import List, Optional

sys.path.insert(0, str(Path(__file__).parent.parent))
from config import OUTPUTS_DIR
from report.templates import (
    estimate_repair_cost, estimate_hollow_tile_cost,
    classify_severity_from_area, determine_verdict, REPORT_DISCLAIMER,
)

from fpdf import FPDF


class SmartLeaseReport(FPDF):
    """Custom PDF class with SmartLease branding."""

    def header(self):
        self.set_fill_color(15, 52, 96)  # Dark navy
        self.rect(0, 0, 210, 28, 'F')
        self.set_font('Helvetica', 'B', 22)
        self.set_text_color(255, 255, 255)
        self.set_y(6)
        self.cell(0, 10, 'SmartLease Edge', new_x="LMARGIN", new_y="NEXT", align='C')
        self.set_font('Helvetica', '', 10)
        self.cell(0, 6, 'AI-Powered Property Condition Report | Fully Offline', new_x="LMARGIN", new_y="NEXT", align='C')
        self.set_text_color(0, 0, 0)
        self.ln(8)

    def footer(self):
        self.set_y(-15)
        self.set_font('Helvetica', 'I', 7)
        self.set_text_color(120, 120, 120)
        self.cell(0, 10, f'SmartLease Edge | Generated Offline | Page {self.page_no()}/{{nb}}', align='C')

    def section_title(self, title: str):
        self.set_font('Helvetica', 'B', 14)
        self.set_text_color(15, 52, 96)
        self.cell(0, 10, title, new_x="LMARGIN", new_y="NEXT")
        self.set_draw_color(233, 69, 96)
        self.line(10, self.get_y(), 200, self.get_y())
        self.set_text_color(0, 0, 0)
        self.ln(4)

    def key_value(self, key: str, value: str):
        self.set_font('Helvetica', 'B', 10)
        self.cell(55, 6, f'{key}:', new_x="RIGHT")
        self.set_font('Helvetica', '', 10)
        self.cell(0, 6, value, new_x="LMARGIN", new_y="NEXT")

    def defect_row(self, icon: str, text: str, severity_color: tuple = None):
        if severity_color:
            self.set_fill_color(*severity_color)
            self.rect(10, self.get_y(), 2, 6, 'F')
        self.set_font('Helvetica', '', 9)
        self.cell(5, 6, '')
        self.cell(0, 6, f'{icon} {text}', new_x="LMARGIN", new_y="NEXT")


def generate_report(findings: dict, output_path: str = None) -> str:
    """
    Generate a SmartLease Edge PDF report.
    
    Args:
        findings: Structured inspection data with rooms, defects, acoustics, appliances
        output_path: Where to save the PDF (auto-generated if None)
    
    Returns:
        Path to generated PDF
    """
    OUTPUTS_DIR.mkdir(parents=True, exist_ok=True)

    if output_path is None:
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        output_path = str(OUTPUTS_DIR / f"inspection_{timestamp}.pdf")

    pdf = SmartLeaseReport()
    pdf.alias_nb_pages()
    pdf.add_page()

    # ─── Property Details ─────────────────────────
    pdf.section_title("Property Details")
    pdf.key_value("Address", findings.get("property_address", "N/A"))
    pdf.key_value("Tenant", findings.get("tenant_name", "N/A"))
    pdf.key_value("Landlord", findings.get("landlord_name", "N/A"))
    pdf.key_value("Inspection Date", findings.get("inspection_date",
                  datetime.now().strftime("%Y-%m-%d %H:%M")))
    pdf.key_value("Report Generated", datetime.now().strftime("%Y-%m-%d %H:%M:%S"))
    pdf.key_value("Mode", "FULLY OFFLINE - Zero cloud dependency")
    pdf.ln(5)

    # ─── Room-by-Room Findings ────────────────────
    total_issues = 0
    total_cost_min = 0
    total_cost_max = 0
    all_costs = []

    for room in findings.get("rooms", []):
        pdf.section_title(f'Room: {room["name"]}')

        # Visual defects
        defects = room.get("vision_defects", [])
        pdf.set_font('Helvetica', 'B', 11)
        pdf.cell(0, 8, f'Visual Defects ({len(defects)} detected)', new_x="LMARGIN", new_y="NEXT")

        if defects:
            for d in defects:
                total_issues += 1
                severity = d.get("severity", classify_severity_from_area(d.get("area_sqft", 1.0)))
                area = d.get("area_sqft", d.get("area_percent", 0))
                cost_min, cost_max = estimate_repair_cost(d["type"], severity, max(area, 1.0))
                total_cost_min += cost_min
                total_cost_max += cost_max

                severity_colors = {
                    "minor": (255, 200, 50),
                    "moderate": (255, 140, 0),
                    "severe": (220, 50, 50),
                }
                icon = {"minor": "●", "moderate": "▲", "severe": "■"}.get(severity, "?")

                pdf.defect_row(
                    icon,
                    f'{d["type"].replace("_", " ").title()} | {severity.upper()} | '
                    f'{area:.1f} sq.ft. | {d.get("location", "N/A")} | '
                    f'Est. repair: Rs.{cost_min:,}-{cost_max:,}',
                    severity_colors.get(severity)
                )
                all_costs.append({
                    "item": f'{d["type"]} ({room["name"]})',
                    "min": cost_min, "max": cost_max,
                })
        else:
            pdf.set_font('Helvetica', '', 9)
            pdf.cell(0, 6, '  No visual defects detected', new_x="LMARGIN", new_y="NEXT")

        pdf.ln(3)

        # Acoustic results
        acoustics = room.get("acoustic_results", [])
        pdf.set_font('Helvetica', 'B', 11)
        pdf.cell(0, 8, f'Acoustic Tap Tests ({len(acoustics)} points)', new_x="LMARGIN", new_y="NEXT")

        for a in acoustics:
            is_hollow = a["result"] == "hollow"
            icon = "!!!" if is_hollow else "OK"
            if is_hollow:
                total_issues += 1
                hc_min, hc_max = estimate_hollow_tile_cost(1.0)
                total_cost_min += hc_min
                total_cost_max += hc_max
                all_costs.append({
                    "item": f'Hollow tile ({room["name"]})',
                    "min": hc_min, "max": hc_max,
                })

            color = (220, 50, 50) if is_hollow else (50, 180, 50)
            pdf.defect_row(
                icon,
                f'{a.get("location", "N/A").replace("_", " ").title()} | '
                f'{a["result"].upper()} | Confidence: {a["confidence"]:.0%}',
                color
            )

        pdf.ln(3)

        # Appliance tests
        appliances = room.get("appliance_tests", [])
        if appliances:
            pdf.set_font('Helvetica', 'B', 11)
            pdf.cell(0, 8, f'Appliance Tests ({len(appliances)})', new_x="LMARGIN", new_y="NEXT")

            for ap in appliances:
                is_ok = ap.get("status") == "functional"
                icon = "OK" if is_ok else "!!!"
                if not is_ok:
                    total_issues += 1

                color = (50, 180, 50) if is_ok else (220, 50, 50)
                ir_status = "IR Verified" if ap.get("ir_response") else "Manual"
                pdf.defect_row(
                    icon,
                    f'{ap["name"]} ({ap.get("brand", "N/A")}) | {ir_status} | {ap["status"].upper()}',
                    color
                )

        pdf.ln(5)

    # ─── Cost Breakdown ──────────────────────────
    pdf.add_page()
    pdf.section_title("Cost Breakdown")

    if all_costs:
        # Table header
        pdf.set_font('Helvetica', 'B', 9)
        pdf.set_fill_color(15, 52, 96)
        pdf.set_text_color(255, 255, 255)
        pdf.cell(90, 7, ' Item', fill=True, new_x="RIGHT")
        pdf.cell(45, 7, ' Min Cost (INR)', fill=True, new_x="RIGHT")
        pdf.cell(45, 7, ' Max Cost (INR)', fill=True, new_x="LMARGIN", new_y="NEXT")
        pdf.set_text_color(0, 0, 0)

        # Table rows
        for i, cost in enumerate(all_costs):
            bg = (245, 245, 245) if i % 2 == 0 else (255, 255, 255)
            pdf.set_fill_color(*bg)
            pdf.set_font('Helvetica', '', 9)
            pdf.cell(90, 6, f' {cost["item"]}', fill=True, new_x="RIGHT")
            pdf.cell(45, 6, f' Rs.{cost["min"]:,}', fill=True, new_x="RIGHT")
            pdf.cell(45, 6, f' Rs.{cost["max"]:,}', fill=True, new_x="LMARGIN", new_y="NEXT")

        # Total
        pdf.set_font('Helvetica', 'B', 10)
        pdf.set_fill_color(233, 69, 96)
        pdf.set_text_color(255, 255, 255)
        pdf.cell(90, 7, ' TOTAL ESTIMATED', fill=True, new_x="RIGHT")
        pdf.cell(45, 7, f' Rs.{total_cost_min:,}', fill=True, new_x="RIGHT")
        pdf.cell(45, 7, f' Rs.{total_cost_max:,}', fill=True, new_x="LMARGIN", new_y="NEXT")
        pdf.set_text_color(0, 0, 0)
    else:
        pdf.set_font('Helvetica', '', 10)
        pdf.cell(0, 8, 'No issues detected — no repair costs estimated.', new_x="LMARGIN", new_y="NEXT")

    pdf.ln(8)

    # ─── Verdict ─────────────────────────────────
    pdf.section_title("Verdict & Recommendation")

    verdict = determine_verdict(total_cost_min, total_issues)
    colors = {"green": (50, 180, 50), "orange": (255, 140, 0), "red": (220, 50, 50)}
    vc = colors.get(verdict["color"], (0, 0, 0))

    pdf.set_font('Helvetica', 'B', 16)
    pdf.set_text_color(*vc)
    pdf.cell(0, 12, f'{verdict["icon"]}  {verdict["verdict"]}', new_x="LMARGIN", new_y="NEXT", align='C')
    pdf.set_text_color(0, 0, 0)
    pdf.set_font('Helvetica', '', 11)
    pdf.multi_cell(0, 7, verdict["text"], align='C')

    pdf.ln(5)
    pdf.set_font('Helvetica', '', 10)
    pdf.cell(0, 7, f'Total issues found: {total_issues}', new_x="LMARGIN", new_y="NEXT")
    pdf.cell(0, 7, f'Estimated repair range: Rs.{total_cost_min:,} - Rs.{total_cost_max:,}', new_x="LMARGIN", new_y="NEXT")

    # ─── Tamper Evidence ─────────────────────────
    pdf.ln(10)
    pdf.section_title("Verification & Integrity")

    report_content = str(findings) + datetime.now().isoformat()
    report_hash = hashlib.sha256(report_content.encode()).hexdigest()

    pdf.set_font('Courier', '', 8)
    pdf.cell(0, 5, f'SHA-256: {report_hash}', new_x="LMARGIN", new_y="NEXT")
    pdf.cell(0, 5, f'Timestamp: {datetime.now().isoformat()}', new_x="LMARGIN", new_y="NEXT")
    pdf.ln(3)

    pdf.set_font('Helvetica', 'I', 8)
    pdf.set_text_color(100, 100, 100)
    pdf.multi_cell(0, 4, REPORT_DISCLAIMER)

    # Save
    pdf.output(output_path)
    print(f"✅ Report saved to: {output_path}")
    return output_path


# ─── Sample Data for Testing ──────────────────────
SAMPLE_FINDINGS = {
    "property_address": "42, Thiruvalluvar Street, T. Nagar, Chennai - 600017",
    "tenant_name": "Rahul Krishnan",
    "landlord_name": "Meena Sundaram",
    "inspection_date": "2026-09-12 14:30",
    "rooms": [
        {
            "name": "Living Room",
            "vision_defects": [
                {"type": "crack", "severity": "moderate", "area_sqft": 3.4,
                 "area_percent": 4.2, "location": "South wall, near window", "confidence": 0.87},
                {"type": "stain", "severity": "minor", "area_sqft": 1.2,
                 "area_percent": 1.5, "location": "Ceiling, NE corner", "confidence": 0.72},
            ],
            "acoustic_results": [
                {"location": "floor_tile_center", "result": "solid", "confidence": 0.97},
                {"location": "floor_tile_near_door", "result": "hollow", "confidence": 0.84},
            ],
            "appliance_tests": [
                {"name": "Split AC", "brand": "Voltas", "ir_response": True, "status": "functional"},
            ],
        },
        {
            "name": "Kitchen",
            "vision_defects": [
                {"type": "stain", "severity": "moderate", "area_sqft": 2.8,
                 "area_percent": 3.1, "location": "Wall behind sink", "confidence": 0.81},
                {"type": "mold", "severity": "minor", "area_sqft": 0.8,
                 "area_percent": 0.9, "location": "Under window sill", "confidence": 0.65},
            ],
            "acoustic_results": [
                {"location": "countertop_tile", "result": "solid", "confidence": 0.92},
            ],
            "appliance_tests": [],
        },
        {
            "name": "Bedroom",
            "vision_defects": [],
            "acoustic_results": [
                {"location": "floor_center", "result": "solid", "confidence": 0.95},
            ],
            "appliance_tests": [
                {"name": "Split AC", "brand": "Daikin", "ir_response": True, "status": "functional"},
            ],
        },
        {
            "name": "Bathroom",
            "vision_defects": [
                {"type": "deterioration", "severity": "minor", "area_sqft": 0.5,
                 "area_percent": 0.6, "location": "Grout near shower", "confidence": 0.58},
            ],
            "acoustic_results": [
                {"location": "floor_tile_shower", "result": "hollow", "confidence": 0.78},
                {"location": "floor_tile_entry", "result": "solid", "confidence": 0.91},
            ],
            "appliance_tests": [],
        },
    ],
}


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="Generate SmartLease Edge report")
    parser.add_argument("--sample", action="store_true", help="Generate sample report")
    parser.add_argument("--output", type=str, help="Output path")
    args = parser.parse_args()

    if args.sample:
        generate_report(SAMPLE_FINDINGS, output_path=args.output)
    else:
        print("Run with --sample to generate a test report")
        print("  python -m report.generator --sample")
