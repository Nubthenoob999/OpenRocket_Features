# Structures Tool Current Audit

Workbook audited: `Rocket Structures V4.xlsx` from the team Google Drive Structures folder.

The workbook's current 4.024-inch configuration is not the
`NASA_26_Huntsville_DOL_ROM.ork` vehicle, whose selected configuration is
approximately 6.1 inches in diameter.  Formula and unit conventions can be
cross-checked between them, but cached output values cannot be compared as if
they described the same rocket.  The workbook also contains stale `#REF!`,
`#DIV/0!`, and `#N/A` cells, so Java regression fixtures use its traceable
input/formula pairs rather than treating every cached result as authoritative.

## Material authority

The workbook uses a single global active-material block on `Configuration`
(`D8:E20`).  That is not suitable for a component-based rocket model because
the body tube, fin set, bulkhead, and centering ring can have different
materials.

The structures tool previously had the same defect in a less visible form:
`MaterialLibrary` inferred Young's modulus, tensile strength, compressive
strength, and Poisson ratio from the assigned material's *name*.  This was a
tool-level override, not a component material property.  It has been removed.

The tool now reads density, in-plane shear modulus, Young's modulus, tensile
allowable, compressive allowable, and Poisson ratio from the material assigned
to the analysed component.  It reports insufficient data for a property that
has not been entered, rather than silently substituting values.

### Implemented material persistence

The core `Material` schema now persists scalar structural data for bulk materials:

- Young's modulus and Poisson ratio;
- tensile and compressive allowables (with a clear yield/ultimate basis);
- a single property basis identified in the material name for directional
  materials.

The fields are carried through the material editor, database records, `.ork`
reader/writer, preset import/export, and material identity.  Existing files
whose legacy material name and density match a known database record are
upgraded on load while retaining their saved density. Truly unknown materials
continue to return `INSUFFICIENT_DATA` until the user supplies properties.
The schema is not an orthotropic laminate model; see
[`material-properties-audit.md`](material-properties-audit.md) for the exact
bases, known gaps, and calculator wiring.

## Formula and modelling review

| Area | Workbook approach | Current implementation | Audit outcome |
| --- | --- | --- | --- |
| Axial loading | Adds thrust, drag, and mass, treating pounds mass as pounds force | Uses `|thrust| + |drag| + mass * g` over positive-thrust samples | Preserves the workbook envelope with valid SI units, avoids double-counting `m*a`, and excludes recovery-deployment drag from powered-ascent compression checks. |
| Tube buckling | Euler/Johnson branch | Euler/Johnson branch in SI | Keep; add an exposed end-condition/effective-length input before claiming design-level fidelity. |
| Tube stress | Spreadsheet shear/moment construction plus a hole correction | Simulation-driven axial and Barrowman-style normal-force/bending estimate | Keep force-consistent core; add component locations, joint/support definitions, and hole geometry before regression comparison. |
| Fin flutter | NACA-style empirical equation, but the current sheet has no shear modulus and a stale zero result | NACA TN 4197 equation 18 in SI with absolute static pressure, plus numerical thickness inversion | Corrected the missing pressure scale and aspect/taper factors. Use the fin component's assigned shear modulus only; treat the result as preliminary screening. |
| Fin root stress | Uses a single max-velocity condition and a fixed AoA input | Uses max dynamic-pressure simulation sample and actual simulation AoA | Keep the simulation-driven approach; improve it to scan the complete time series for maximum root stress. |
| Bulkhead and centering ring | Roark annular-plate coefficients; the shear-capacity cell divides by FoS twice | Reproduces `L6`, `L9`, `C4`, `C7`, `alphaM`, and `alphaQ` in SI, computes raw bending/shear capacity, and compares `capacity / load` with required FoS once | Corrected and regression-locked to the V4 fixtures. Bulkheads assume the workbook's 0.125-inch central load/fixture radius because OpenRocket has no equivalent property. Add explicit support, washer/bolt circle, and load-path inputs before design certification. |
| Coupler/fasteners/composite | Separate workbook sheets, manually populated | Calculators exist but are not integrated into the analysis service | Implement only after their component geometry, connection, and material data are modelled. |

The V4 workbook's `JPS S-Glass Fiberglass` row contains bare-fiber properties
(670 ksi tensile strength and 12.9 Msi modulus).  Those values are valid as
constituents in its rule-of-mixtures sheet, but not as direct properties of a
cured rocket tube.  The legacy `S2Fiberglass` record therefore uses measured
MTM45-1/6781 S2-glass/epoxy laminate properties.  A regression test separately
reproduces the workbook's S-glass/System 2001 constituent calculation so the
two property bases remain explicit.

The Huntsville `.ork` is not the workbook configuration: its analysed internal
plates are saved as Basswood while the workbook specifies Baltic birch. The
legacy file also represents each physical Avionics and Air Brakes sandwich
bulkhead as two contiguous 0.155-0.175-inch `Bulkhead` components. Analysing
each layer independently produced the four repeated Avionics Bay FoS values of
approximately 0.72 even though the FRR describes a 0.32-inch bonded sandwich
and reports a tested FoS of 2.7 at the original 414-lbf load.

The analysis service now groups contiguous, same-name, same-material bulkhead
layers on the same parent, uses the smaller stepped radius and total bonded
thickness, and reports the grouping assumption. The Huntsville Avionics stacks
produce a screening FoS of approximately 2.88. Single plates and centering
rings are still evaluated independently, and genuine failures remain visible.
The warning requires the user to verify that the bonded interface can transfer
interlaminar shear; adjacency alone cannot prove bond quality.

## High-priority implementation plan

1. Seed authoritative, grade-specific bulk-material records with sourced
   structural values.  Do not populate values from names.
2. Make `StructuralMaterial` a direct view of the persisted fields and show
   a per-component completeness status in the Structures dialog.
3. Scan all flight samples for each failure mode; do not combine independently
   selected maxima from different times.
4. Add component-aware load paths: supports/joints, motor/retainer loads,
   fin location, and pressure/normal-force application points.
5. Add SI regression fixtures derived from a deliberately documented workbook
   case.  Retain the corrected Java assumptions where they are more physically
   sound than workbook formulas.

## Verification

The focused database, persistence, structures, and ejection regression matrix
includes an old-format Blue Tube `.ork` round trip,
material and preset persistence, direct component-property use in both tools,
suppression of unsupported nose/coupler placeholder failures, V4 workbook
fixtures, and an end-to-end load of `NASA_26_Huntsville_DOL_ROM.ork`. A component
material named `Carbon fiber` with its own explicit properties is also verified
not to receive an unsafe name-matched override.
