# Structures Tool Validation Notes

Workbook inspected: `Rocket Structures V4.xlsx` from the team Google Drive Structures folder.

The first Java implementation follows the workbook sheet structure:

- `Tube Loading`: tube area, second moment, Euler/Johnson buckling, tube axial plus bending stress.
- `Tube Loading` charts: shear-force and bending-moment diagrams are generated from nose and fin normal-force point-load approximations at the governing tube-stress simulation case.
- `Fin Flutter & Root`: NACA-style flutter speed, fin normal-force root bending stress, section modulus `C_r * t^2 / 6`.
- `Bulkhead & CR`: circular/annular plate capacity with bending and shear governing checks.
- `Fastener Strength`: tensile rods and shear fastener stress checks.
- `Coupler Thickness`: equal bending stiffness sizing.
- `Composite Properties`: rule-of-mixtures material estimate.

Intentional corrections in Java:

- The workbook's `drag + thrust + mass` axial envelope is retained in dimensionally valid form as `abs(thrust) + abs(drag) + mass * g`. The Java checks select the maximum positive-thrust sample so a recovery-deployment drag spike is not reused as a powered-ascent compression load. `mass * axialAcceleration` is not added because it is the response to those applied forces, not another independent force.
- Bulkhead and centering-ring capacity use the workbook's Roark `L6`, `L9`, `C4`, `C7`, `alphaM`, and `alphaQ` coefficient chain. Java computes raw bending and transverse-shear capacities, reports the physical `capacity / appliedLoad` factor of safety, and compares it once with `requiredFoS`. The workbook's shear-capacity cell divides by the required FoS twice; Java corrects that defect.
- The plate calculators accept an explicit transverse-shear allowable, as the workbook does. Until that property is persisted by the core material schema, assigned component materials fall back to `0.6 * tensile` with a visible warning.
- Contiguous same-name, same-material bulkhead layers on one parent are analysed as one bonded stack. This matches the Huntsville model's two-slice representation of each physical sandwich bulkhead and prevents the full load from being applied independently to each slice. The result carries a bond-interface warning.
- Flight data is read from the selected OpenRocket simulation via `FlightDataBranch`; users do not paste an OpenRocket export table.
- Geometry for body tubes, nose cones, fin sets, bulkheads, centering rings, and couplers is extracted from the component tree where OpenRocket exposes it.
- Calculators use SI internally. Swing result details format recognized force, moment, length, area, pressure, velocity, and time values through OpenRocket `UnitGroup`.

Current validation status:

- Unit tests cover tube area/inertia, Euler and Johnson branch selection, the powered-ascent axial envelope, tube axial/bending stress, fin CN-alpha and CP approximation, the SI form of NACA TN 4197 equation 18, fin root stress, exact V4 bulkhead/centering-ring Roark coefficients and design capacities, no-double-FoS behavior, centering-ring ring-count scaling, fastener stress, coupler sizing, composite rule-of-mixtures, and invalid geometry handling.
- SI regression fixtures reproduce the V4 workbook's 4.024-inch Blue Tube
  Euler/Johnson case, equal-stiffness coupler result, and S-glass/System 2001
  constituent rule-of-mixtures result.  The NASA Huntsville `.ork` fixture also
  loads its saved simulation samples and verifies that every component selected
  by default returns a finite factor of safety. It also verifies that the saved
  15.61 kN recovery-event drag sample is excluded from the powered-ascent plate
  load and that any genuine sub-unity plate FoS is reported as `FAIL`, not
  clamped or hidden.
