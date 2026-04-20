# McCoy — 7.62mm Bullet Aeroballistics (BRL-MR-3733)

## Source
- **Bucket:** B (Digitizable)
- **Link:** https://apps.dtic.mil/sti/tr/pdf/ADA205633.pdf
- **Status:** STUB
- **Phase:** Phase 1

## Priority Figures
- CD0 vs Mach transonic
- CNalpha vs Mach

## Use Case
Data hint: fleeman_cd0

## Digitization Steps
1. Open source PDF at the link above
2. Use WebPlotDigitizer: https://apps.automeris.io/wpd/
3. Load PDF page, calibrate axes, extract all curves
4. Save each figure as `fig_XX_[var]_vs_[axis].json` in this folder
5. Commit to corpus with source ID reference

## Recommended Tool
WebPlotDigitizer (https://apps.automeris.io/wpd/) — free, web-based, supports PDF
