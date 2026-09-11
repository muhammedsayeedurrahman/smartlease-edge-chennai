"""
SmartLease Edge — Report Cost Tables & Templates
Chennai-specific repair cost data and report text templates.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))
from config import REPORT


def estimate_repair_cost(defect_type: str, severity: str, area_sqft: float = 1.0) -> tuple:
    """
    Estimate repair cost in INR based on defect type and severity.
    Returns (min_cost, max_cost) tuple.
    """
    cost_table = REPORT["cost_table"]

    if defect_type in cost_table:
        costs = cost_table[defect_type]
        if severity in costs:
            min_cost, max_cost = costs[severity]
            return (int(min_cost * area_sqft), int(max_cost * area_sqft))

    # Default fallback
    return (500, 2000)


def estimate_hollow_tile_cost(void_area_sqft: float) -> tuple:
    """Estimate cost for hollow tile repair."""
    costs = REPORT["cost_table"]["hollow_tile"]["per_sqft"]
    return (int(costs[0] * void_area_sqft), int(costs[1] * void_area_sqft))


def classify_severity_from_area(area_sqft: float) -> str:
    """Classify defect severity from physical area."""
    thresholds = REPORT["severity_thresholds"]
    if area_sqft < thresholds["minor"]:
        return "minor"
    elif area_sqft < thresholds["moderate"]:
        return "moderate"
    return "severe"


def determine_verdict(total_cost_min: int, total_issues: int) -> dict:
    """Determine deposit verdict based on total issues and costs."""
    rules = REPORT["verdict_rules"]

    if total_issues <= rules["full_refund_max_issues"]:
        return {
            "verdict": "FULL REFUND",
            "color": "green",
            "text": "Property is in excellent condition. Full security deposit refund recommended.",
            "icon": "✅",
        }
    elif total_cost_min <= rules["partial_deduction_max_cost"]:
        return {
            "verdict": "PARTIAL DEDUCTION",
            "color": "orange",
            "text": f"Minor issues detected. Recommended deduction: ₹{total_cost_min:,} – estimated repair cost.",
            "icon": "⚠️",
        }
    else:
        return {
            "verdict": "SIGNIFICANT DEDUCTION",
            "color": "red",
            "text": f"Multiple issues requiring repair. Recommended deduction: ₹{total_cost_min:,}+.",
            "icon": "🔴",
        }


# GenieX LLM prompt template
GENIEX_SYSTEM_PROMPT = """You are SmartLease Edge, an offline property condition assessment AI. 
Convert the following sensor telemetry into a clear, professional condition report.

Rules:
- Itemize each defect with exact location, severity grade, and estimated repair cost in INR
- Use these cost benchmarks: minor crack ₹500-1500/sqft, moderate crack ₹1500-4000/sqft, 
  water stain ₹800-2000/sqft, mold remediation ₹1200-3000/sqft, hollow tile repair ₹3000-8000/sqft
- State verdict: FULL REFUND, PARTIAL DEDUCTION, or SIGNIFICANT DEDUCTION
- Be factual. Only report what sensors measured. Do not speculate.
- Format with clear section headers and bullet points."""

REPORT_DISCLAIMER = (
    "This report was generated entirely offline on-device using SmartLease Edge. "
    "No data was transmitted to any external server at any point during the inspection. "
    "This report is provided for reference purposes and does not constitute a legally binding assessment. "
    "Consult qualified professionals for structural concerns."
)
