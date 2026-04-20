# Rocket Aerodynamic ROM Tuning Corpus — Mach 0 to 2

## Quick Start
1. A06 Jorgensen-Nelson TM X-3128 — body-alone spine
2. A02/A03 Dupuis Basic Finner — finned-body anchor
3. A09 TMR ATB — transonic grids
4. B14 Head / B12 Eckert / B16 Michel — BL closures (pre-computed in data.json)
5. A10 ThrustCurve API — motor F(t)

## Structure
- BUCKET_A_downloadable/A01-A45 — downloadable datasets
- BUCKET_B_digitizable/B01-B60  — digitizable PDF sources
- synthesis_tables/             — ranked tables by topic
- MASTER_INDEX.json             — full corpus index
- README.md                     — this file

## Digitization Workflow
Sources with status STUB_MANUAL_REQUIRED:
1. Open source link
2. Use WebPlotDigitizer (https://apps.automeris.io/wpd/)
3. Follow digitization_instructions.md in each folder
4. Save as fig_XX_[var]_vs_[axis].json

## Data Convention
- Units: SI (Pa, m, kg, N, K) unless noted
- Angles in degrees unless key ends in _rad
- EQUATION_DERIVED: computed from known closed-form equations
- STUB_MANUAL_REQUIRED: requires PDF plot digitization
