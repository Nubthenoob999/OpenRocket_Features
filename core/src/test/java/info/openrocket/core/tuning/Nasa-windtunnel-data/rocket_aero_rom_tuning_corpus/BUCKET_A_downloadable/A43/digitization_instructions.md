# ERCOFTAC Classic Database (QNET-CFD)

## Source
- **Bucket:** A (Downloadable)
- **Link:** https://www.ercoftac.org/special_interest_groups/sig15_qual_trust_indus_les/
- **Status:** STUB_MANUAL_REQUIRED
- **Phase:** Phase 1

## Priority Figures
- Download from source link
- Run CFD case or fetch API data

## Use Case
ROM use: medium

## Digitization Steps
1. Open source PDF at the link above
2. Use WebPlotDigitizer: https://apps.automeris.io/wpd/
3. Load PDF page, calibrate axes, extract all curves
4. Save each figure as `fig_XX_[var]_vs_[axis].json` in this folder
5. Commit to corpus with source ID reference

## Recommended Tool
WebPlotDigitizer (https://apps.automeris.io/wpd/) — free, web-based, supports PDF
