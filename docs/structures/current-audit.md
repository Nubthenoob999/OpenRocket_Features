# Structures Tool Current Audit

Workbook audited: `/Users/opteron92/Downloads/Rocket Structures V2.xlsx`

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

### Required follow-up

The core `Material` schema now persists structural data for bulk materials:

- Young's modulus and Poisson ratio;
- tensile and compressive allowables (with a clear yield/ultimate basis);
- optional directional laminate properties and allowables for composites.

The fields are carried through the material editor, database records, `.ork`
reader/writer, preset import/export, and material identity.  Existing files
continue to read with these values unset, so the structures tool returns
`INSUFFICIENT_DATA` until a user selects or creates a fully defined material.

## Formula and modelling review

| Area | Workbook approach | Current implementation | Audit outcome |
| --- | --- | --- | --- |
| Axial loading | Adds thrust, drag, and mass (dimensionally inconsistent) | Uses `|thrust| + |drag| + |mass * axial acceleration|` | Keep Java approach. |
| Tube buckling | Euler/Johnson branch | Euler/Johnson branch in SI | Keep; add an exposed end-condition/effective-length input before claiming design-level fidelity. |
| Tube stress | Spreadsheet shear/moment construction plus a hole correction | Simulation-driven axial and Barrowman-style normal-force/bending estimate | Keep force-consistent core; add component locations, joint/support definitions, and hole geometry before regression comparison. |
| Fin flutter | NACA-style empirical equation | SI-consistent form and numerical thickness inversion | Keep, but use the fin component's assigned shear modulus only.  Require a valid value. |
| Fin root stress | Uses a single max-velocity condition and a fixed AoA input | Uses max dynamic-pressure simulation sample and actual simulation AoA | Keep the simulation-driven approach; improve it to scan the complete time series for maximum root stress. |
| Bulkhead and centering ring | Plate formulas divide by safety factor in multiple places | Applies allowable stress once, then compares capacity to load | Keep Java approach; add support/boundary condition, bolt circle, and load-path inputs. |
| Coupler/fasteners/composite | Separate workbook sheets, manually populated | Calculators exist but are not integrated into the analysis service | Implement only after their component geometry, connection, and material data are modelled. |

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

`StructuresCalculatorsTest` passed after the material-authority change
(16 tests).  The added test verifies that a component material named
`Carbon fiber` does not receive structural values from a name-matched table.
