# ROM Improvement and Repair Plan for `ROM_dev_testing`

## Purpose

This document converts the deep research audit into a **step-by-step engineering plan** for improving and fixing the ROM in the `ROM_dev_testing` branch of `OpenRocket_Features`.

The goal is **not** to rewrite everything at once. The goal is to make the ROM:

1. **mathematically defensible**
2. **numerically stable**
3. **physically consistent**
4. **cleanly integrated with OpenRocket**
5. **validated enough to trust for trajectory work**

This plan is organized in the order that gives the highest return with the least risk.

---

## Core design decision to preserve

Do **not** immediately promote the ROM into a full aerodynamic replacement.

For now, preserve the current hybrid strategy:

- **Barrowman remains the production owner of CP, CN, Cm, and stability derivatives**
- **ROM remains focused on drag**
- **4D CN/Cm stays diagnostic/shadow-mode only until validated**

That architecture is one of the strongest parts of the current branch, because it reduces the chance of destabilizing the simulation while the drag model is still being repaired.

---

## High-level roadmap

Work in this order:

1. **Fix geometry extraction**
2. **Fix the drag-building physics**
3. **Fix continuity and trust logic**
4. **Harden interpolation and query behavior**
5. **Verify RK4/plume/event integration**
6. **Build a serious validation harness**
7. **Only then consider graduating more ROM authority**

---

# Phase A — Fix the inputs feeding the ROM

This is the highest-priority phase because bad geometry corrupts every surface the ROM builds.

## Step A1 — Refactor `RomGeometryParameters` to support multiple fin sets

### Why
The current audit found that the branch aggregates `finCount` across multiple fin sets but still relies on a single “primary” fin geometry for mean chord, span, thickness, and wet area. That can badly distort:
- fin friction drag
- fin wave drag
- induced drag behavior
- transonic drag buildup

### Files to inspect first
- `RomGeometryParameters.java`
- `DragGridEvaluator.java`
- `WaveDragModel.java`
- `InducedDragModel.java`

### What to change
Replace the single-fin-set representation with a **list of fin-set geometry records**.

### Recommended structure
```java
public record FinGeom(
    int count,
    double rootChord,
    double tipChord,
    double span,
    double thickness,
    double sweepLength,
    double wettedArea,
    String finType
) {}
```

Then add something like:
```java
private final List<FinGeom> finSets;
```

### Implementation tasks
- Walk all `FinSet` components and store each set separately.
- Do not collapse them into one global `finCount`, `finSpan`, `finThickness`, etc.
- Update drag calculations so fin contributions are summed **per fin set**.

### Acceptance criteria
- A rocket with one fin set matches old behavior within tolerance.
- A rocket with canards + rear fins now produces distinguishable geometry output.
- Fin friction and wave drag change correctly when a second fin set is added.

---

## Step A2 — Eliminate the default hard-coded fin thickness fallback where possible

### Why
The audit flagged a hard-coded default fin thickness near `0.003 m` for non-trapezoidal fin cases. Since fin wave drag scales with approximately `(t/c)^2`, this can strongly distort supersonic drag.

### What to change
- Pull true thickness from the actual fin component class whenever possible.
- If a fin type does not expose enough geometry, do **not** silently assign a “good enough” thickness without warning.

### Better fallback behavior
If thickness cannot be extracted:
- mark the geometry as partially unresolved
- log a warning
- optionally use a conservative configurable fallback
- record that fallback in diagnostics

### Acceptance criteria
- No silent 3 mm assumption unless explicitly configured.
- Geometry debug output clearly tells the user when a fallback thickness was used.

---

## Step A3 — Fix boattail base-diameter extraction

### Why
The audit found that the branch appears to use the **maximum** aft diameter among shrinking transitions when computing boattail-related base geometry. That is likely wrong. The base drag should depend on the **actual aft-most effective base**.

### Files
- `RomGeometryParameters.java`
- `BaseDragModel.java`

### What to change
Use one of these strategies:
1. choose the aft-most shrinking transition by axial location and use its aft radius
2. compute the true final exposed aft base diameter from the assembled rocket
3. if there are multiple aft transitions, use the final external aft section, not the largest aft diameter encountered

### Acceptance criteria
- A stacked boattail geometry gives the correct final base diameter.
- The extracted base area matches the rocket’s actual rear exposed area.

---

## Step A4 — Audit wetted-area accumulation

### Why
The drag builder uses wetted area heavily. If `wetArea` includes unexpected components or double-counts fins/body regions, the friction model is corrupted before interpolation even starts.

### What to do
- Verify exactly which components contribute to `getComponentWetArea()`
- Separate:
  - body wetted area
  - fin wetted area
  - optional protuberance area if needed later
- Do not mix them into one bucket unless the math truly expects that

### Acceptance criteria
- Debug output shows:
  - `bodyWetArea`
  - `finWetArea`
  - `referenceArea`
  - `baseArea`
  - `motorArea`
- Known canonical geometries reproduce expected areas by hand.

---

# Phase B — Fix the drag-building math

This phase repairs the core physics used to generate the ROM surfaces.

## Step B1 — Re-derive and repair the skin-friction drag normalization

### Why
The audit identified the current body friction drag formula as a major concern, especially the hard-coded factor `0.4`. This needs to be reconciled with the intended reference-area normalization and with OpenRocket’s documented drag framework.

### Files
- `SkinFrictionModel.java`
- `DragGridEvaluator.java`

### Immediate tasks
1. Write down exactly what the current code assumes `Cf` means.
2. Write down what area the result is normalized by.
3. Check whether the `0.4` factor is correcting:
   - a two-sided/one-sided convention
   - a body-vs-flat-plate conversion
   - a mistaken leftover constant
   - or nothing valid at all

### Recommended action
Refactor the method so the calculation is explicit:

```java
double cf = ...;                    // chosen correlation
double compressibilityCorrection = ...;
double cfCorrected = cf * compressibilityCorrection;

double bodyFormFactor = ...;
double cdBodyFriction = cfCorrected * bodyFormFactor * (bodyWetArea / referenceArea);
```

If a special normalization factor is still needed after derivation, document it with:
- derivation
- source
- units/normalization explanation
- expected calibration range

### Acceptance criteria
- The full derivation for friction drag is written in comments or docs.
- No unexplained magic coefficient remains.
- A canonical slender body gives a believable friction-drag trend across Reynolds and Mach.

---

## Step B2 — Split body friction and fin friction into clearly distinct models

### Why
The current implementation conceptually blends body and fin friction logic, but they should remain distinct because:
- body form factors differ from fin thickness corrections
- their wetted areas differ
- their compressibility and roughness handling may differ in detail

### What to do
Create separate methods:
- `cdBodyFriction(...)`
- `cdFinFriction(...)`

Then assemble:
```java
double cdFriction = cdBodyFriction(...) + cdFinFriction(...);
```

### Acceptance criteria
- Unit tests can verify body friction and fin friction independently.
- A finless rocket produces zero fin friction.
- Increasing fin thickness changes only the fin-friction term, not the body-friction term.

---

## Step B3 — Rebuild the base-drag model cleanly

### Why
The audit found:
- mismatched constants in comments vs. code
- an apparently unused variable in the subsonic base-drag model
- possible inconsistency between thesis-like logic and the current Cf-based formulation

This is a major source of uncertainty, especially near transonic flight and plume transitions.

### Files
- `BaseDragModel.java`
- `DragGridEvaluator.java`

### Decide one of two paths

#### Option 1 — Baseline-consistent path
Implement a base-drag model explicitly aligned with OpenRocket’s documented baseline approach, including plume-on reduction by subtracting motor area from base area.

#### Option 2 — Correlation-driven path
Keep the custom/base-correlation approach, but:
- cite the source
- remove dead code
- justify every coefficient
- calibrate it against references or CFD

### Do not do this
Do not keep a half-merged formula with unused pieces.

### Implementation tasks
- Remove or reinstate the unused variable in a mathematically consistent way.
- Make the transonic multiplier constant consistent between comment and code.
- Rewrite the base-drag method so each regime is clearly separated:
  - subsonic
  - transonic
  - supersonic
- Make plume-on logic explicit:
```java
effectiveBaseArea = max(0.0, baseArea - motorArea);
```

### Acceptance criteria
- `cdBasePlumeOff(M)` is continuous and explainable.
- `cdBasePlumeOn(M)` smoothly approaches the plume-off value as motor area becomes small.
- For a rocket where motor area nearly equals base area, plume-on base drag becomes near zero as expected.

---

## Step B4 — Revisit transonic peak construction

### Why
The audit flagged the current transonic peak and fineness correction as too heuristic. It likely scales too much of the total subsonic drag instead of only the physically relevant compressibility-sensitive components.

### Files
- `TransonicBlendingModel.java`
- `DragGridEvaluator.java`
- `WaveDragModel.java`
- `BaseDragModel.java`

### Better approach
Instead of applying a blanket multiplier to overall subsonic drag, separate drag components into:
- friction drag
- base drag
- wave/compressibility drag
- induced/AoA drag

Then only apply transonic amplification where physically justified.

### Suggested assembly
```java
cdSub = cdFriction + cdBaseSub + cdInducedSub;
cdTrans = cdFriction + cdBaseTrans + cdCompressibilityPeak + cdInducedTrans;
cdSup = cdFrictionSup + cdBaseSup + cdWaveSup + cdInducedSup;
```

### Acceptance criteria
- Transonic drag rise is continuous.
- It does not double count base drag or wave drag.
- The peak location and width are controllable and documented.

---

## Step B5 — Audit induced-drag and protuberance logic

### Why
The induced-drag increment and protuberance multiplier can quietly distort ROM surfaces, especially at moderate AoA.

### Files
- `InducedDragModel.java`
- `DragGridEvaluator.java`

### Tasks
- Re-derive the induced-drag relation used.
- Check whether the AoA dependence is reasonable for the rocket classes you care about.
- Confirm that the protuberance factor is not masking missing drag sources.

### Acceptance criteria
- Zero AoA produces zero induced drag increment.
- Increasing AoA produces a smooth, monotonic drag increase.
- Protuberance effects are configurable and documented.

---

# Phase C — Fix continuity, trust logic, and runtime blending

This phase keeps the ROM from introducing artificial force jumps.

## Step C1 — Make boundary-event trust continuous across burnout and apogee

### Why
The audit found the current trust reduction appears one-sided in time: trust drops before an event, then snaps back after the event. That can create discontinuities in effective Cd.

### File
- `RomAerodynamicCalculator.java`

### Replace current logic with symmetric event proximity
Use:
```java
double dt = Math.abs(simulationTimeSeconds - boundaryEventTimeSeconds);
double proximity = 1.0 - smoothStep(0.0, TRUST_WINDOW_SEC, dt);
double trust = 1.0 - (1.0 - MIN_TRUST) * proximity;
```

### Acceptance criteria
- No jump in trust exactly at burnout/apogee.
- Cd evolves continuously through regime transitions.

---

## Step C2 — Re-express plume decay using absolute event time, not repeated incremental decay

### Why
If plume decay depends on repeated substep updates, RK4 staging can bias the result depending on how many times the function is called.

### Better formulation
Track `timeSinceBurnout` directly:
```java
plumeState = burning ? 1.0 : Math.exp(-timeSinceBurnout / tau);
```

### Why this is better
It makes plume decay invariant to:
- substep count
- adaptive stepping
- method of evaluation

### Acceptance criteria
- Same physical burnout timeline gives the same plume state regardless of integrator subdivision.

---

## Step C3 — Clarify 4D alpha/beta semantics

### Why
The current 4D ROM path appears to decompose total AoA magnitude into alpha-like and beta-like components using `theta`. That can be valid, but it must be made explicit and must not be confused with OpenRocket’s other `beta` usage.

### Files
- `RomAerodynamicCalculator.java`
- 4D surface classes and docs

### What to do
Rename internal variables to something like:
- `alphaComponentDeg`
- `lateralAoAComponentDeg`

Avoid ambiguous naming like raw `beta` unless it truly means aerodynamic sideslip under a consistent convention.

### Acceptance criteria
- A new contributor can tell in 30 seconds what the 4D “beta axis” means.
- Surface-generation code and runtime query code use the same convention.

---

## Step C4 — Revisit envelope clamps and fallback thresholds

### Why
The current guardrails are useful, but they may be masking deeper model errors and can distort gradients.

### Files
- `RomAerodynamicCalculator.java`

### What to do
For each clamp/fallback:
- document why it exists
- identify whether it is temporary or fundamental
- move constants into named configuration
- add telemetry counters showing how often each guardrail triggers

### Suggested diagnostics
Track:
- percent of steps using fallback
- percent of queries clamped on Mach/Re/alpha/beta
- mean ROM confidence
- mean residual correction magnitude
- number of boundary-trust suppressions

### Acceptance criteria
- You can tell whether the ROM is actually trusted in a typical flight, or mostly being suppressed.

---

# Phase D — Harden interpolation and numerical behavior

## Step D1 — Add strict axis validation to PCHIP setup

### Why
PCHIP assumes strictly increasing axes. Do not rely on that silently.

### File
- `PchipInterpolator1D.java`

### Add checks
- non-null arrays
- same length
- minimum length >= 2
- strictly increasing x
- all finite values

### Acceptance criteria
- Bad axes fail fast with clear error messages.

---

## Step D2 — Add interpolation invariants and surface sanity tests

### Why
You want to prevent nonphysical ROM outputs before they reach the simulation.

### Add checks for
- no NaN
- no Inf
- no negative total drag
- bounded drag increase with small AoA changes
- smooth behavior near Mach-region transitions

### Acceptance criteria
- Surface generation fails loudly if it produces broken regions.

---

## Step D3 — Benchmark the grid resolution

### Why
The current grid may be adequate, but it needs proof, especially near transonic gradients.

### What to test
Perform a grid-convergence study for:
- Mach resolution
- logRe resolution
- alpha resolution
- 4D beta resolution if applicable

### Deliverable
A simple report showing:
- baseline grid
- refined grid
- maximum Cd difference
- where the difference is largest

### Acceptance criteria
- Grid density is justified by measured interpolation error, not intuition.

---

# Phase E — Verify the simulation-loop integration

## Step E1 — Trace how ROM updates are invoked inside RK4

### Why
The audit raised concern that plume decay, event timing, and trust logic may be sensitive to where in the RK4 cycle the ROM state is updated.

### Files
- `RK4SimulationStepper.java`
- `RomAerodynamicCalculator.java`

### What to check
- Is the same stage context applied consistently at k1/k2/k3/k4?
- Is plume state advanced correctly at each substep?
- Are event times interpreted consistently across substeps?
- Can fallback activate in one substep and deactivate in the next inside a single global step?

### Acceptance criteria
- The effective Cd at each substage is explainable and time-consistent.
- Substep sequencing does not create hidden bias.

---

## Step E2 — Add stage-level debug snapshots

### Why
You need visibility into what the ROM is actually doing during integration.

### Log per substep
- simulation time
- Mach
- Re_L
- AoA
- alpha component / lateral component
- queried Cd off
- queried Cd on
- plume state
- ROM confidence
- blend weight
- residual correction
- final effective Cd
- whether fallback activated

### Acceptance criteria
- One debug run makes it obvious why the ROM changed drag at a given point.

---

# Phase F — Build a validation and test harness

This is the phase that turns the repaired ROM into something you can trust.

## Step F1 — Add canonical unit tests for geometry extraction

Create test rockets for:
- simple body only
- body + one trapezoidal fin set
- body + two fin sets
- body + boattail
- body + boattail + large motor
- non-trapezoidal fin geometry

### Validate
- diameter
- reference area
- body wetted area
- fin wetted area
- base area
- motor area
- boattail aft diameter

---

## Step F2 — Add drag-component regression tests

For fixed geometry and atmosphere, verify:
- body friction only
- fin friction only
- base drag plume off
- base drag plume on
- wave drag at supersonic Mach
- induced drag versus AoA

### Purpose
This isolates bugs before they are buried inside the full ROM table.

---

## Step F3 — Add surface-continuity tests

For 3D and 4D surfaces, verify:
- continuity in Mach
- continuity in logRe
- continuity in alpha
- continuity in lateral component
- smooth plume transition behavior

### Especially test around
- Mach 0.55–0.70
- Mach 0.95–1.20
- burnout
- apogee
- edge clamping

---

## Step F4 — Add trajectory-level A/B comparisons

Compare:
1. stock Barrowman-only OpenRocket
2. current ROM branch
3. repaired ROM branch

### Evaluate
- apogee
- max Mach
- burnout velocity
- coast time
- sensitivity to wind and AoA
- stability/motion continuity

### Goal
Show that the repaired branch is:
- more realistic than stock where expected
- not less stable numerically
- not dependent on fragile guardrail suppression

---

## Step F5 — Add external validation targets

Use one or more of:
- trusted OpenRocket baseline equations
- CFD snapshots
- known drag trends from literature
- actual flight data if available
- RocketPy comparison cases if the setup is matched

### Minimum requirement
At least validate:
- subsonic Cd trend
- transonic drag rise
- plume-on to plume-off drag shift
- supersonic drag decay trend

---

# Phase G — Cleanup and preparation for future ROM authority

## Step G1 — Separate “production-trusted” from “diagnostic-only” ROM outputs in code structure

### Why
Right now the branch conceptually does this, but it should be enforced in the code structure.

### What to do
Create clear data models:
- `TrustedRomDragResult`
- `DiagnosticFourDAeroResult`

This prevents accidental future promotion of CN/Cm fields into production without validation.

---

## Step G2 — Centralize configuration constants

Move magic constants into one clearly documented config area:
- plume decay tau
- trust windows
- fallback ratio limits
- envelope clamp limits
- transonic model coefficients
- roughness defaults
- minimum/maximum domain values

### Acceptance criteria
- No critical aerodynamic constant is hidden in the middle of a method.

---

## Step G3 — Write a short technical note for the branch

Create a dev note that explains:
- what the ROM currently owns
- what it does not own
- how surfaces are built
- how trust logic works
- what is validated
- what is still experimental

This will prevent future confusion and accidental misuse.

---

# Recommended implementation order by file

## First files to fix
1. `RomGeometryParameters.java`
2. `SkinFrictionModel.java`
3. `BaseDragModel.java`
4. `DragGridEvaluator.java`
5. `RomAerodynamicCalculator.java`

## Next files
6. `WaveDragModel.java`
7. `InducedDragModel.java`
8. `PchipInterpolator1D.java`
9. 3D/4D interpolator classes
10. RK4 simulation stepper and debug hooks

---

# Concrete weekly execution plan

## Pass 1 — Geometry correctness
- Refactor multi-fin-set support
- remove hard-coded fin-thickness assumptions
- fix boattail/base geometry
- split body vs fin wetted area
- add geometry unit tests

## Pass 2 — Drag math correctness
- repair skin friction normalization
- separate body and fin friction
- cleanly rebuild base drag
- refactor transonic peak logic
- add drag-component unit tests

## Pass 3 — Runtime continuity
- fix boundary trust continuity
- convert plume decay to absolute-time form
- clarify 4D alpha/lateral-component semantics
- instrument fallback/guardrail metrics

## Pass 4 — Numerical hardening
- add PCHIP axis validation
- add surface sanity checks
- run grid-resolution study
- add continuity tests

## Pass 5 — Simulation validation
- trace RK4 substep behavior
- add stage snapshots
- run A/B trajectory comparisons
- validate against external references

---

# Definition of “good enough to trust”

The ROM should not be considered trustworthy for serious trajectory work until all of the following are true:

- geometry extraction is verified for all supported rocket layouts
- friction and base drag equations are fully explained and no longer rely on unexplained constants
- plume-on/plume-off behavior is continuous and invariant to integrator staging
- event-aware trust logic is continuous at burnout and apogee
- interpolation is tested and fails fast on bad axes/data
- surface outputs are smooth, finite, and nonnegative
- trajectory-level comparisons show stable and repeatable improvements
- guardrail activation is low enough that the ROM is actually contributing, not being constantly suppressed

---

# Stretch goals after the repair plan is complete

Only after the repaired drag ROM is validated should you consider:

1. better 4D aerodynamic surface generation
2. certified interpolation / surrogate uncertainty estimates
3. more formal reduced-order techniques
4. gradual promotion of ROM-based CN/Cm into limited-production testing
5. adaptive trust based on validated local error instead of heuristic fades

---

# Final recommendation

The best path forward is:

- **fix geometry first**
- **repair the drag math second**
- **repair continuity and simulation coupling third**
- **build validation before expanding scope**

Do **not** try to make the ROM more powerful before making it more correct.

The branch already has a strong architectural idea. The next step is to make the underlying inputs, equations, and trust logic worthy of that architecture.
