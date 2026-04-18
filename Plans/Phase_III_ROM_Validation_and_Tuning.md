# Phase III - Validation, Tuning, Benchmarking, and Release Hardening

## Purpose

Phase III is where the new ROM earns trust. By this point the runtime solver already exists and runs inside OpenRocket. This phase proves what it gets right, identifies where it is still weak, tunes the parts that are legitimately tunable, and formalizes release-quality acceptance criteria.

This phase is not about inventing a different solver. It is about validating and sharpening the solver built in Phase II.

---

## Phase III mission statement

**Demonstrate that the runtime ROM is accurate enough, fast enough, and honest enough to replace or sit alongside the current aerodynamic path in OpenRocket.**

That means:
- verification against known analytical behavior,
- benchmarking against canonical aerodynamic datasets,
- optional comparison to CFD and literature anchors,
- optional comparison to user flight data and altimeter-derived trajectory behavior,
- tuning only the modules that should be tuned,
- and locking down runtime confidence and fallback behavior.

---

## Phase III must honor the project rules

- CFD is **optional for validation**, not required for computation.
- The solver must remain usable without CFD-derived training data.
- Any calibration overlays must remain **corrections to the physics**, not a replacement of the physics.
- The ROM must remain simulation-safe on a normal machine with 16 GB RAM.
- Any tuned behavior must be documented and bounded.
- Any region of weak validity must remain visible to the user.

---

## What Phase III includes

### Included
- unit, integration, regression, and benchmark expansion
- canonical geometry validation
- optional CFD comparison campaign
- optional flight-data comparison campaign
- performance hardening
- confidence calibration
- fallback-threshold tuning
- release gating
- documentation of validity envelope
- optional bounded correction overlays for known weak regimes

### Explicitly not included
- replacing the solver architecture
- requiring a CFD database to run
- adding black-box neural surrogates into the default runtime path
- silently hiding weak regimes behind opaque tables

---

## Validation philosophy

Phase III validation should be layered.

### Layer A - Mathematical / closure verification
Prove the closures behave as expected in canonical simplified cases.

### Layer B - Canonical aerodynamic benchmark validation
Compare against classic literature geometries and known trends.

### Layer C - Full-rocket validation
Compare with complete rocket shapes across Mach and angle sweeps.

### Layer D - Trajectory relevance validation
Compare predicted drag history and apogee-relevant results against actual flight or trusted high-fidelity references.

### Layer E - Runtime and robustness validation
Show the solver remains practical, stable, and well behaved inside OpenRocket.

---

## Phase III implementation passes

## Pass 3.1 - Expand the verification suite

### Goal
Move from development tests to release-grade verification.

### Required expansions
- more closure tests
- more regime-boundary tests
- more sign-convention tests
- edge-case numerical-failure tests
- more cache-consistency tests
- more fallback continuity tests

### Required additions
- tests for transonic smoothness
- tests for ignition / burnout coefficient continuity
- tests for angle-of-attack confidence degradation
- tests for coefficient positivity / boundedness where applicable

### Acceptance criteria
- CI catches numerical regressions immediately
- major branches have direct tests
- failure modes are tested, not just normal modes

---

## Pass 3.2 - Canonical geometry benchmark campaign

### Goal
Validate the solver on canonical bodies before claiming performance on arbitrary rockets.

### Minimum canonical families
- cone-cylinder
- tangent-ogive cylinder
- secant-ogive or representative slender forebody
- finned basic-finner style geometry
- boattailed body
- optional blunt-body representative for modified Newtonian checks

### Minimum sweep axes
- Mach sweep across subsonic, transonic, and supersonic bands
- small to moderate angle-of-attack sweep
- powered vs coast for relevant base/plume cases

### Quantities to compare
- `C_A`
- `C_N`
- `C_m`
- `C_N_alpha`
- center of pressure or effective CP trend
- local `Cp` trend where reference data exists
- base pressure or base drag where reference data exists

### Acceptance criteria
The exact thresholds can be adjusted, but the program should aim for:
- attached subsonic / supersonic `C_A` errors in the single-digit to low-teens percent range
- transonic errors clearly smaller than the legacy baseline
- CP errors reduced meaningfully relative to the current stock model
- no solver-breakdown warnings in nominal canonical cases

---

## Pass 3.3 - Optional CFD anchor and comparison study

### Goal
Use a **small** amount of CFD only where it is scientifically justified:
- transonic bridge quality,
- boattail/shock-induced separation behavior,
- base/plume correction realism,
- fin-body junction corrections.

### Allowed CFD use in this project
- validation
- calibration of bounded residual corrections
- confirming expected trends
- informing confidence penalties

### Not allowed
- turning the runtime solver into a CFD-trained black box
- requiring CFD data for normal simulation use
- replacing broad physics modules with dense interpolated lookup tables

### Recommended bounded CFD study structure
Use a small, deliberately chosen set of anchor cases, for example:
- cone-cylinder across transonic and supersonic points
- tangent-ogive finned body across transonic and supersonic points
- boattailed body with powered / unpowered comparison
- one or two moderate-AoA checks

### Deliverables
- comparison plots
- residual plots
- identified bias patterns
- recommendation on whether a compact correction overlay is worth adding

### Acceptance criteria
- any correction added remains low-dimensional and physically interpretable
- out-of-sample behavior remains bounded
- default physics path still runs with no CFD present

---

## Pass 3.4 - Optional bounded correction overlays

### Goal
Improve known weak spots without degenerating back into a lookup-table ROM.

### Allowed overlay types
- transonic drag-rise residual multiplier
- base-pressure anchor correction curve
- boattail separation plateau correction
- fin-body junction axial increment correction
- plume recompression factor correction

### Overlay design rules
- store residuals relative to physics prediction, not absolute coefficients
- keep dimensionality low
- use monotone interpolation where possible
- disable gracefully outside trusted bounds
- expose confidence reduction when outside calibrated space

### Acceptance criteria
- overlay improves benchmark accuracy in its target region
- overlay does not degrade behavior outside its target region
- overlay can be turned off for pure-physics mode

---

## Pass 3.5 - Flight-data and apogee relevance validation

### Goal
Show that the new ROM improves the quantities that matter to users, not just coefficient plots.

### Recommended comparison outputs
- apogee
- max Mach
- velocity history during ascent and coast
- coast deceleration trend
- time-to-apogee
- sensitivity under nonzero angle or wind if data permits

### Required methodology
- hold thrust curve, mass properties, and atmosphere assumptions as consistent as possible
- compare old and new ROM against the same flight cases
- log which differences are aerodynamic and which are likely propulsion/atmosphere driven

### Acceptance criteria
- new ROM should show reduced bias or reduced spread on apogee-relevant cases
- transonic-sensitive flights should benefit the most
- diagnostics should show whether errors are still aerodynamic or are now dominated by motor / mass / atmosphere uncertainty

### Important rule
Do not overclaim from sparse flight data. Use it as trajectory relevance evidence, not as the only truth source.

---

## Pass 3.6 - Confidence and fallback calibration

### Goal
Tune the confidence meter and fallback thresholds so they reflect reality.

### Required tasks
- compare low-confidence flags against known weak benchmark regions
- compare fallback activation against solver instability or known low-fidelity situations
- adjust sigmoid centers / widths or equivalent threshold logic
- document confidence interpretation for users

### Desired behavior
- high confidence in benign supported cases
- reduced confidence near Mach 1
- reduced confidence for large AoA or large separation fraction
- strong fallback when the solver is clearly outside its assumptions

### Acceptance criteria
- confidence trends are interpretable and reproducible
- fallback does not trigger too aggressively in supported regimes
- fallback does trigger before gross nonsense appears

---

## Pass 3.7 - Performance hardening and memory cleanup

### Goal
Make sure the solver meets the real computational budget, not just the conceptual one.

### Required tasks
- benchmark cold and warm calls
- benchmark complete trajectories
- inspect allocation hot spots
- reduce GC pressure in inner loops
- verify cache hit behavior
- profile UI responsiveness during simulation

### Acceptance criteria
- Balanced mode is practical on a 16 GB machine
- no obvious memory leaks
- repeated trajectory runs remain stable in runtime and memory use
- debug and export tooling can be disabled cleanly for normal use

---

## Pass 3.8 - Release documentation and validity envelope

### Goal
Explain clearly where the ROM is strong, marginal, and unsupported.

### Required documentation
- supported Mach and AoA envelope
- known weak regimes
- description of fallback logic
- explanation of confidence meter
- performance expectations by preset
- explanation that CFD is optional and not required for runtime
- developer note on which closures are implemented and why

### Required user-facing statements
- what the ROM computes directly
- what is handled empirically
- when users should trust it most
- when users should expect degraded confidence

### Acceptance criteria
- documentation matches actual code behavior
- documentation does not claim more than the solver can support
- advanced users can trace which modules contribute to which behaviors

---

## Required benchmark outputs for Phase III

At minimum, every validation run should be able to produce:
- coefficient vs Mach plots
- coefficient vs AoA plots
- local pressure trend comparisons where available
- center-of-pressure trend plots
- confidence vs Mach / AoA plots
- fallback activation plots
- runtime vs preset plots
- old-vs-new apogee comparison summaries where flight data exists

---

## Required regression gating before release

A release candidate should not ship unless all of the following are satisfied:

1. **No catastrophic numerical failures** in supported benchmark cases.
2. **No silent invalid states** in the runtime path.
3. **Fallback continuity** is verified.
4. **Performance budget** is met in the advertised default preset.
5. **Transonic behavior** is smoother and more believable than the legacy path.
6. **Subsonic and supersonic attached-flow cases** show improvement or at least no unacceptable regression.
7. **Documentation and warnings** accurately reflect the real validity envelope.

---

## What may be tuned in Phase III

These are legitimate tuning targets:
- transonic hump magnitude
- base-drag curve parameters
- plume multiplier smoothing and magnitude
- confidence thresholds
- fallback hysteresis
- bounded residual overlay coefficients
- pathline-count preset defaults
- local refinement triggers

These are **not** tuning targets:
- arbitrary coefficient fudge factors that mask broken physics
- user-hidden apogee hacks
- tuning that destroys low-Mach Barrowman consistency without reason
- tuning that only fits one rocket and harms generality

---

## Recommended Phase III benchmark hierarchy

### Tier 1 - pure math / closure tests
Cheap, always run in CI.

### Tier 2 - canonical geometry tests
Moderate cost, nightly or release-candidate runs.

### Tier 3 - full-rocket regression suite
Slower, run on release candidates and nightly if affordable.

### Tier 4 - optional CFD / flight comparison campaign
Not necessary for every CI run, but necessary for milestone acceptance.

---

## Phase III risk register

### Risk 1 - Overfitting via bounded corrections
Mitigation:
- use residual corrections only
- keep overlays low-dimensional
- compare pure-physics and corrected results side by side

### Risk 2 - Confidence meter becomes arbitrary
Mitigation:
- tune against actual benchmark failure regions
- document thresholds explicitly

### Risk 3 - Benchmark suite becomes too expensive
Mitigation:
- tier the validation suite
- separate CI-fast, nightly, and milestone runs

### Risk 4 - Flight data is noisy and misleading
Mitigation:
- use flight data as one evidence layer only
- keep atmospheric and motor uncertainties visible

### Risk 5 - Performance regressions sneak in during tuning
Mitigation:
- keep performance benchmarks as release gates
- benchmark both cold and warm paths

---

## Final exit criteria for Phase III

Phase III is complete when all of the following are true:

1. The ROM has a documented and demonstrated validity envelope.
2. Benchmarks show meaningful improvement over the legacy path in the intended use cases.
3. Confidence and fallback logic reflect actual solver weakness rather than guesswork.
4. Performance in default mode is acceptable on the target hardware class.
5. Any optional corrections remain bounded, interpretable, and nonessential to runtime operation.
6. The code, documentation, diagnostics, and validation suite are strong enough for long-term maintenance.

---

## Final release recommendation

At the end of Phase III, the project should be shipped in one of the following clearly labeled modes:

### Recommended default shipping mode
**Pure physics runtime ROM with calibrated safeguards**
- runtime uses analytic/pathline/integral methods only
- CFD not required
- bounded tuned curves or residuals only where justified
- fallback always available

### Optional advanced shipping mode
**Validation-enhanced mode**
- same runtime physics
- optional bounded correction overlays enabled
- stronger transonic / base / plume accuracy where validated
- confidence reflects whether the case is inside the validated envelope

### Not recommended as the default
- dense coefficient-table replacement
- mandatory CFD-anchored runtime inference
- opaque black-box surrogate default solver

---

## Final Phase III gate

**The ROM is ready only when it is both better and more honest.**
Better means improved aerodynamic and trajectory relevance.
More honest means it clearly signals its weak regions, degrades gracefully, and does not pretend to know what it does not know.