import argparse
from pathlib import Path

import matplotlib.pyplot as plt
import pandas as pd


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Visualize phase-two ROM validation outputs")
    parser.add_argument("reports_dir", help="Directory containing phase-two-summary.csv and phase-two-quantities.csv")
    return parser.parse_args()


def require_file(path: Path) -> Path:
    if not path.exists():
        raise FileNotFoundError(f"Missing expected report file: {path}")
    return path


def plot_scores(summary: pd.DataFrame, out_dir: Path) -> None:
    plt.figure(figsize=(11, 5))
    x = range(len(summary))
    plt.bar(x, summary["fullScore"], label="fullScore", alpha=0.85)
    plt.plot(x, summary["boostScore"], marker="o", label="boostScore")
    plt.plot(x, summary["coastScore"], marker="o", label="coastScore")
    plt.plot(x, summary["descentScore"], marker="o", label="descentScore")
    plt.axhline(80, color="orange", linestyle="--", linewidth=1, label="warning")
    plt.axhline(65, color="red", linestyle="--", linewidth=1, label="critical")
    plt.xticks(list(x), summary["dataset"], rotation=30, ha="right")
    plt.ylabel("Score")
    plt.title("Phase-Two ROM Scores by Dataset")
    plt.legend()
    plt.tight_layout()
    plt.savefig(out_dir / "dashboard_scores.png", dpi=160)
    plt.close()


def plot_quantities(quantities: pd.DataFrame, out_dir: Path) -> None:
    ref = quantities[quantities["source"] == "reference"].copy()
    cand = quantities[quantities["source"] == "candidate"].copy()
    merged = ref.merge(cand, on="dataset", suffixes=("_ref", "_cand"))

    plt.figure(figsize=(11, 5))
    x = range(len(merged))
    width = 0.35
    plt.bar([i - width / 2 for i in x], merged["cdProxyMean_ref"], width=width, label="CdProxy reference")
    plt.bar([i + width / 2 for i in x], merged["cdProxyMean_cand"], width=width, label="CdProxy candidate")
    plt.xticks(list(x), merged["dataset"], rotation=30, ha="right")
    plt.ylabel("Cd Proxy")
    plt.title("Cd Proxy Comparison (Reference vs Candidate)")
    plt.legend()
    plt.tight_layout()
    plt.savefig(out_dir / "dashboard_cd_proxy.png", dpi=160)
    plt.close()

    plt.figure(figsize=(11, 5))
    plt.bar([i - width / 2 for i in x], merged["accelAbsPeak_ref"], width=width, label="|AccelZ| peak ref")
    plt.bar([i + width / 2 for i in x], merged["accelAbsPeak_cand"], width=width, label="|AccelZ| peak cand")
    plt.xticks(list(x), merged["dataset"], rotation=30, ha="right")
    plt.ylabel("m/s^2")
    plt.title("Vertical Acceleration Peaks")
    plt.legend()
    plt.tight_layout()
    plt.savefig(out_dir / "dashboard_accel_peaks.png", dpi=160)
    plt.close()


def main() -> None:
    args = parse_args()
    reports_dir = Path(args.reports_dir)

    summary_path = require_file(reports_dir / "phase-two-summary.csv")
    quantities_path = require_file(reports_dir / "phase-two-quantities.csv")

    summary = pd.read_csv(summary_path)
    quantities = pd.read_csv(quantities_path)

    numeric_cols = ["fullScore", "boostScore", "coastScore", "descentScore", "referenceCdProxy", "candidateCdProxy"]
    for col in numeric_cols:
        if col in summary.columns:
            summary[col] = pd.to_numeric(summary[col], errors="coerce")

    for col in ["velocityMean", "velocityAbsPeak", "accelMean", "accelAbsPeak", "cdProxyMean"]:
        if col in quantities.columns:
            quantities[col] = pd.to_numeric(quantities[col], errors="coerce")

    plot_scores(summary, reports_dir)
    plot_quantities(quantities, reports_dir)
    print("Wrote dashboard images:")
    print(f" - {reports_dir / 'dashboard_scores.png'}")
    print(f" - {reports_dir / 'dashboard_cd_proxy.png'}")
    print(f" - {reports_dir / 'dashboard_accel_peaks.png'}")


if __name__ == "__main__":
    main()