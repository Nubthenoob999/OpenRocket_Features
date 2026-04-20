# RASAero II User Manual + ARCAS Validation

## Source
- **Bucket:** B (Digitizable)
- **Link:** https://www.rasaero.com/dloads/RASAero%20II%20Users%20Manual.pdf
- **Status:** STUB
- **Phase:** Phase 1

## Priority Figures
- ARCAS Cd vs Mach
- Transonic CD model equations

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
