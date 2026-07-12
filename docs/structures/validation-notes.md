# Structures Tool Validation Notes

Workbook inspected: `/Users/opteron92/Downloads/Rocket Structures V2.xlsx`

The first Java implementation follows the workbook sheet structure:

- `Tube Loading`: tube area, second moment, Euler/Johnson buckling, tube axial plus bending stress.
- `Tube Loading` charts: shear-force and bending-moment diagrams are generated from nose and fin normal-force point-load approximations at the governing tube-stress simulation case.
- `Fin Flutter & Root`: NACA-style flutter speed, fin normal-force root bending stress, section modulus `C_r * t^2 / 6`.
- `Bulkhead & CR`: circular/annular plate capacity with bending and shear governing checks.
- `Fastener Strength`: tensile rods and shear fastener stress checks.
- `Coupler Thickness`: equal bending stiffness sizing.
- `Composite Properties`: rule-of-mixtures material estimate.

Intentional corrections in Java:

- Axial load is not `drag + thrust + mass`. The Java model uses the force-consistent conservative estimate `abs(thrust) + abs(drag) + abs(mass * axialAcceleration)`, with `mass * g` as a fallback when acceleration is unavailable.
- Bulkhead and centering-ring capacity do not divide by factor of safety twice. Java computes allowable stress as `strength / requiredFoS`, computes capacity from that allowable, and reports `capacity / appliedLoad`.
- Flight data is read from the selected OpenRocket simulation via `FlightDataBranch`; users do not paste an OpenRocket export table.
- Geometry for body tubes, nose cones, fin sets, bulkheads, centering rings, and couplers is extracted from the component tree where OpenRocket exposes it.
- Calculators use SI internally. Swing result details format recognized force, moment, length, area, pressure, velocity, and time values through OpenRocket `UnitGroup`.

Current validation status:

- Unit tests cover tube area/inertia, Euler and Johnson branch selection, corrected axial load, tube axial/bending stress, fin CN-alpha and CP approximation, fin flutter, fin root stress, bulkhead no-double-FoS behavior, centering-ring ring-count scaling, fastener stress, coupler sizing, composite rule-of-mixtures, and invalid geometry handling.
- Spreadsheet cell-by-cell regression is not yet implemented because the workbook is imperial and includes interactive/manual inputs. The first regression target should be a fixture that converts one workbook input row to SI and asserts the Java result for each calculator sheet.
