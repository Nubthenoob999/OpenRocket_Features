# AI Agent Implementation Plan: Improving 3D and 4D ROM Lookup Tables for OpenRocket

## Objective

Implement a physics-first reduced-order modeling pipeline that upgrades OpenRocket aerodynamic lookup tables from simple low-dimensional drag estimates into structured, regime-aware 3D and 4D aerodynamic ROMs for rockets.

The target outputs are:

- **3D ROM lookup tables** for axisymmetric rockets where sideslip can be collapsed into an effective incidence variable:
  - `Cd(M, Re, alpha_total)` or `Cd(M, Re, alpha)`
- **4D ROM lookup tables** for non-axisymmetric or fin-orientation-sensitive rockets:
  - `Cd(M, Re, alpha, beta)`

The ROM must be:

- fast enough for Monte Carlo and repeated simulation calls
- grounded in aerodynamic first principles
- extensible to sparse CFD, wind-tunnel, or flight-test correction data later
- exportable in a form OpenRocket can query efficiently

---

## Why this framework will make the ROM better

This framework improves the ROM because it replaces a single monolithic drag fit with a **physics-structured, regime-aware, compressed surrogate architecture**.

### Core reasons it improves the ROM

1. **It bakes in real aerodynamic structure before any compression**
   - The source PDFs emphasize a drag build-up based on friction, pressure/form, wave, base, and induced drag rather than an opaque fit.
   - That means the ROM starts from physically meaningful variation with Mach, Reynolds number, geometry, angle of attack, and sideslip.
   - Result: better extrapolation, fewer non-physical artifacts, and more stable predictions outside sparse calibration data.

2. **It resolves the transonic region explicitly**
   - The transonic drag rise is one of the strongest nonlinear features in rocket aerodynamics.
   - Regime-specific splitting and blending prevent a single global surrogate from smoothing over or misplacing the drag rise.
   - Result: better `Cd(M)` accuracy where trajectory sensitivity is highest.

3. **It reduces dimensional waste**
   - For axisymmetric vehicles, the PDFs note that beta can often be collapsed using `alpha_total = sqrt(alpha^2 + beta^2)`.
   - Active subspaces can further show that only 1–2 dominant combinations of variables drive most of the variance.
   - Result: smaller tables, less overfitting, faster evaluation, and better conditioning.

4. **It preserves speed without discarding fidelity**
   - The physics-first source states that analytical table generation over a structured grid is cheap, and tensor compression can reduce the full table to a few KB with sub-microsecond queries.
   - Result: the ROM remains usable inside Monte Carlo loops and repeated OpenRocket calls.

5. **It supports correction rather than replacement**
   - Sparse CFD or flight data can be added later through multi-fidelity co-kriging or discrepancy modeling instead of rebuilding the system from scratch.
   - Result: the ROM improves incrementally as higher-fidelity data appears.

6. **It provides uncertainty and validation hooks**
   - UQ, holdout testing, extrapolation detection, and sensitivity analysis are explicitly part of the framework.
   - Result: the ROM is not just fast, but auditable and trustworthy.

---

## Source-backed design principles

This implementation plan is derived from two provided PDFs:

1. **Reduced Order Models for Rocket Aerodynamic Drag Tables: A Physics-First ROM Framework**
2. **Flexible Deep-Research Plan for an Unspecified Prompt With a ROM Aerodynamics Focus**

The sources jointly support these design choices:

- use a **physics-first drag decomposition** as the baseline generator
- generate a structured 3D or 4D coefficient tensor over `(M, Re, alpha, beta)`
- compress the table using **Tucker** or **CP** tensor decomposition
- optionally fit **Gaussian Process / Kriging** models with the physics model used as the prior mean
- apply **active subspaces** to reduce effective input dimension
- use **multi-fidelity correction** when sparse CFD or test data becomes available
- add **PCE / GP variance** for uncertainty quantification
- maintain a clear pipeline of **intake -> equations -> data generation -> compression -> validation -> deployment**

---

## Deliverables the agent must produce

The agent must produce all of the following:

1. `equation_register.md`
   - all aerodynamic equations used
   - regime validity ranges
   - variable definitions
   - assumptions and simplifications

2. `rom_dataset_schema.md`
   - exact axes for 3D and 4D tables
   - units
   - reference geometry definitions
   - normalization conventions
   - metadata fields

3. `rom_training_pipeline.py`
   - baseline physics table generator
   - optional CFD/test-data ingestion
   - tensor decomposition / surrogate fitting
   - validation metrics
   - export logic

4. `rom_validation_report.md`
   - holdout metrics
   - regime-sliced errors
   - transonic-specific diagnostics
   - no-extrapolation boundaries

5. `rom_model_card.md`
   - supported geometry class
   - supported Mach/Re/alpha/beta ranges
   - expected error bands
   - known failure modes
   - integration target details for OpenRocket

6. `openrocket_integration_spec.md`
   - Java-side calling pattern
   - lookup-table query interface
   - interpolation rules
   - fallback behavior

---

## Agent execution order

The agent must execute the work in the following order.

### Phase 1: Intake and scoping

Determine:

- whether the geometry is **axisymmetric-only** or truly **beta-sensitive**
- whether the desired output is only `Cd`, or also `CL`, `Cm`, `Cn`, `Cl`, `CP`
- the required ranges for:
  - Mach
  - Reynolds number
  - angle of attack
  - sideslip
  - optional roll rate / body rate terms
- acceptable runtime per query
- acceptable storage footprint
- validation targets
- whether gradients are needed for control or optimization

### Decision rule: 3D vs 4D

Use **3D** if all of the following are true:

- the rocket is effectively axisymmetric in drag response
- fin orientation relative to the wind plane is not modeled explicitly
- sideslip dependence can be collapsed through:
  - `alpha_total = sqrt(alpha^2 + beta^2)`

Use **4D** if any of the following are true:

- fin-orientation effects matter
- deployables, rail buttons, asymmetries, or protuberances create directional drag dependence
- beta-dependent aerodynamic anisotropy is required for fidelity
- the lookup table will later be extended to side force or moment coefficients

---

## Phase 2: Build the physics-first aerodynamic backbone

The agent must implement a component build-up model before any ROM compression.

### Required drag decomposition

Use:

`Cd_total = Cd_friction + Cd_pressure + Cd_wave + Cd_base + Cd_induced + Cd_excrescences`

For finned rockets, treat body and fin friction separately and include body-fin interference factors where appropriate.

### Minimum physics modules

#### 1. Skin-friction module
Implement:

- laminar `Cf`
- turbulent Prandtl-Schlichting `Cf`
- transition-corrected `Cf`
- compressibility correction
- body and fin form factors

This improves the ROM because Reynolds-number variation is forced to follow known boundary-layer trends instead of arbitrary fitted behavior.

#### 2. Wave-drag module
Implement:

- transonic drag rise function
- supersonic slender-body / area-rule inspired wave-drag behavior
- geometry-sensitive nose and area-distribution effects

This improves the ROM because Mach dependence becomes physically shaped, especially through transonic and supersonic transitions.

#### 3. Base-drag module
Implement:

- subsonic base-drag correlation
- Mach-dependent base-drag scaling
- boattail / aft-body geometry dependence where available

This improves the ROM because base drag is frequently one of the dominant error sources in rocket drag models.

#### 4. Pressure / induced-drag / AoA module
Implement:

- small-angle slender-body normal force relations
- Allen-Perkins style crossflow contribution for moderate to high alpha
- induced drag from normal force
- body-axis to wind-axis conversion where needed

This improves the ROM because angle effects stop being treated as an ad hoc penalty and instead follow known aerodynamic structure.

#### 5. Compressibility module
Implement:

- subsonic compressibility correction
- near-critical/transonic behavior
- supersonic shock-related relations where needed for pressure-based terms

This improves the ROM because the model can handle regime changes without unphysical continuity breaks.

---

## Phase 3: Define parameter grids and generate raw lookup tables

### 3D table definition

For axisymmetric rockets, define:

- `M`
- `Re`
- `alpha_total` or `alpha`

Recommended stored tensor:

`T3[i, j, k] = Cd(M_i, Re_j, alpha_k)`

### 4D table definition

For beta-sensitive rockets, define:

- `M`
- `Re`
- `alpha`
- `beta`

Recommended stored tensor:

`T4[i, j, k, l] = Cd(M_i, Re_j, alpha_k, beta_l)`

### Grid design rules

The source material recommends adaptive density where nonlinear behavior is strongest. The agent must:

- densify Mach samples in `0.8 <= M <= 1.2`
- log-space Reynolds number
- sample alpha more densely at low angle and around onset of crossflow effects
- sample beta symmetrically around zero
- use structured grids first, then refine adaptively

### Mandatory adaptive refinement zones

Use at minimum:

- **3x density** in transonic Mach range
- **2x density** near transition Reynolds numbers
- **2x density** at moderate and high alpha where crossflow begins to dominate

This improves the ROM because grid resolution is spent where the aerodynamic response curvature is largest, not wasted in smooth regions.

---

## Phase 4: Apply regime-specific splitting and blending

Do **not** fit one global model over the entire Mach range without regime logic.

Partition the space into:

- subsonic
- transonic
- supersonic

Build separate submodels or separate tensor blocks for each regime.

Blend them with smooth weighting functions such as:

- sigmoid blending
- Hermite `C1` or `C2` blending

### Why this improves the ROM

- transonic drag rise is too sharp for a single smooth global surrogate
- splitting reduces surrogate bias in the most trajectory-sensitive region
- blending preserves continuity for OpenRocket integration

---

## Phase 5: Compress the raw tables into a ROM

### Primary recommendation: Tucker decomposition

Use Tucker decomposition first for the lookup-table ROM.

Apply to either `T3` or `T4`:

- `T ≈ G x1 U_M x2 U_Re x3 U_alpha [x4 U_beta]`

Use HOOI or a comparable algorithm.

#### Why Tucker should be the first choice

- well suited to structured, gridded coefficient tensors
- high compression ratio
- fast evaluation
- interpretable mode structure
- straightforward error monitoring via reconstruction error

### Secondary option: CP decomposition

Use CP when storage size is prioritized even more strongly than robustness.

#### Why CP can help

- even lower storage than Tucker in some cases
- simpler factorized evaluation form

#### Why CP is secondary

- often more sensitive to rank choice and conditioning
- can be less stable in strongly nonlinear or weakly separable regions

### Compression acceptance criteria

Require:

- low reconstruction error on held-out table points
- no spurious oscillation introduced near transonic rise
- no sign or monotonicity violations in obviously constrained regions
- stable interpolation between stored knots

---

## Phase 6: Add surrogate layers where they improve performance

### Option A: Gaussian Process / Kriging surrogate

Use a GP surrogate with input vector:

- `x = (M, log10(Re), alpha[, beta])`

Use the **physics ROM as the prior mean**, not a zero mean.

#### Why this improves the ROM

- the physics prior handles global structure
- the GP only needs to learn residual corrections
- predictive variance provides an epistemic uncertainty estimate
- sparse high-fidelity correction becomes feasible

Use this when:

- you have sparse CFD, wind-tunnel, or flight data
- uncertainty estimates are valuable
- the table size is still moderate

### Option B: Polynomial Chaos Expansion (PCE)

Use PCE if the main goal is:

- uncertainty propagation
- Sobol sensitivity decomposition
- extremely cheap repeated evaluation

#### Why this improves the ROM

- exposes parameter importance directly
- supports Monte Carlo acceleration
- gives a compact analytic surrogate if the response is sufficiently smooth

### Option C: PINN / operator-learning correction

Only use this after the previous methods are working.

Use a neural correction model if one or more of the following is true:

- differentiability is required
- the aerodynamic response has complicated residual structure
- multi-output extension is planned
- enough high-fidelity data exists

#### Why this improves the ROM

- it can learn structured residuals that tensor models may miss
- symmetry, positivity, monotonicity, and asymptotic constraints can be encoded in the loss

#### Why it is not first-line

- higher training complexity
- higher verification burden
- greater risk of hidden failure modes

---

## Phase 7: Use active subspaces to reduce effective dimension

Compute gradient-based active subspaces on the physics-generated table or surrogate.

### Goals

- determine whether `Cd` really depends on all nominal input dimensions
- identify dominant combined directions in `(M, Re, alpha, beta)`
- reduce table or surrogate input dimension when justified by eigenvalue gaps

### Expected outcome

The source material suggests that Mach will usually dominate, followed by angle of attack, with Reynolds number and beta often contributing less.

### Why this improves the ROM

- reduces unnecessary input dimension
- improves surrogate conditioning
- lowers memory
- increases robustness of interpolation and fitting

### Mandatory rule

Do not reduce dimension blindly. Only collapse dimensions if:

- the active-subspace eigenvalue spectrum shows a clear gap
- validation error remains acceptable after reduction

---

## Phase 8: Add multi-fidelity correction when better data becomes available

When CFD, wind-tunnel, or flight data exists, do not replace the baseline ROM outright.

Use a discrepancy model of the form:

- `Z_high(x) = rho * Z_low(x) + delta(x)`

where:

- `Z_low` is the physics ROM
- `delta(x)` is a learned discrepancy term

### Why this improves the ROM

- uses sparse expensive data efficiently
- avoids discarding the physics model
- reduces the amount of high-fidelity data needed
- preserves fast evaluation while improving accuracy

### Fidelity hierarchy to support

1. semi-empirical physics model
2. DATCOM / panel-style estimates if available
3. RANS CFD
4. wind-tunnel or flight-test data

The agent must make the architecture modular so new fidelity levels can be attached later.

---

## Phase 9: Validation and acceptance testing

The ROM is not accepted until it passes all validation layers.

### A. Table reconstruction validation

Measure:

- relative `L2` error
- max pointwise error
- regime-wise error by Mach band
- reconstruction quality near transonic rise
- interpolation consistency

### B. Physics consistency tests

Check for:

- `Cd >= 0`
- expected Reynolds trend in friction-dominated regimes
- symmetry in beta for axisymmetric cases
- continuity across regime boundaries
- reasonable asymptotic high-Mach behavior

### C. External validation

Use at least one external dataset that is **not used in training**.

Prioritize datasets noted by the source material, such as:

- NACA / NASA rocket-like zero-lift drag datasets
- sounding-rocket aerodynamic datasets
- projectile or standard validation bodies where applicable

### D. Trajectory-level validation

Propagate ROM differences into trajectory outputs:

- apogee
- max velocity
- coast profile
- descent timing
- landing dispersion, if used in Monte Carlo

This matters because a lookup table can have a modest coefficient error but still produce large trajectory error in the most sensitive regimes.

---

## Phase 10: Uncertainty quantification

The agent must attach uncertainty estimates to the ROM even if only approximate at first.

### Minimum uncertainty categories

- atmospheric uncertainty
- Reynolds uncertainty via surface roughness or transition behavior
- model-form uncertainty in base drag and transonic drag rise
- interpolation / compression error
- sparse-data correction uncertainty if GP or co-kriging is used

### Recommended methods

- GP predictive variance
- PCE for propagated uncertainty and Sobol indices
- held-out error envelopes by regime
- explicit extrapolation flags outside validated ranges

### Why this improves the ROM

- makes the table usable in dispersion studies instead of only deterministic runs
- reveals where more CFD or test data is worth collecting
- prevents overconfidence in poorly constrained regions

---

## Phase 11: OpenRocket integration requirements

The final ROM must be exportable and callable from OpenRocket.

### OpenRocket-side requirements

The agent must design a query interface with:

- input:
  - Mach
  - Reynolds number
  - alpha
  - beta
  - optional geometry/configuration identifier
- output:
  - `Cd`
  - optionally other coefficients later

### Query behavior rules

1. clamp or flag values outside validated domain
2. use smooth interpolation inside the validated domain
3. preserve regime blending continuity
4. avoid expensive online recomputation
5. allow fallback to baseline OpenRocket drag if the ROM is unavailable or out of domain

### Serialization options

Support one of:

- compressed tensor artifact
- spline-ready factor matrices and core tensor
- GP model package
- hybrid artifact with tensor baseline plus correction residual model

### Why this improves the ROM in OpenRocket specifically

- OpenRocket needs repeated fast evaluations during simulation
- compressed ROM artifacts meet that runtime constraint
- explicit domain boundaries prevent silent misuse
- integration-ready serialization makes the work operational, not just theoretical

---

## Recommended implementation stack

### Python-side model development

Use:

- `numpy`
- `scipy`
- `pandas`
- `tensorly`
- `scikit-learn`
- `smt` for multi-fidelity kriging if needed
- `SALib` or equivalent for sensitivity analysis
- optional:
  - `jax` or `autograd` for gradients
  - `pytorch` for PINN or neural correction layers

### Java/OpenRocket-side deployment

Implement:

- a ROM loader
- a fast query evaluator
- interpolation routines
- simulation hook / listener integration
- model metadata validator

---

## Mandatory implementation decisions

The agent must enforce the following decisions unless evidence shows otherwise.

### Decision 1
Start from a **physics-first analytical generator**, not a black-box regression model.

### Decision 2
Support **both 3D and 4D tables**, with the 3D path treated as the default for axisymmetric rockets.

### Decision 3
Use **regime-aware submodels** rather than a single global fit.

### Decision 4
Use **Tucker decomposition first**, then compare against GP and CP as alternatives or complements.

### Decision 5
Use **active-subspace analysis** to justify dimensional reduction rather than assuming all dimensions matter equally.

### Decision 6
Use **multi-fidelity correction** instead of discarding the baseline model once CFD or test data appears.

### Decision 7
Treat **validation and UQ** as required outputs, not optional extras.

---

## What success looks like

The ROM implementation is successful when it demonstrates all of the following:

1. A raw physics-generated 3D or 4D coefficient table exists and is reproducible.
2. The table is compressed into a fast ROM without materially distorting important aerodynamic structure.
3. Transonic drag rise is represented accurately and smoothly.
4. Axisymmetric cases are reduced cleanly to 3D when justified.
5. Beta-dependent cases are preserved in 4D when required.
6. Validation identifies the model's accurate and inaccurate regions explicitly.
7. The artifact can be queried efficiently from OpenRocket.
8. The system can accept future CFD or test-data corrections without redesign.

---

## Priority order for development

Implement in this order:

1. physics-based raw table generator
2. 3D axisymmetric ROM path
3. 4D beta-sensitive ROM path
4. regime splitting and blending
5. Tucker compression
6. validation suite
7. OpenRocket integration artifact
8. active-subspace analysis
9. GP / multi-fidelity correction layer
10. PCE / Sobol UQ
11. PINN or neural correction, only if needed

---

## Non-negotiable cautions

The agent must avoid the following failure modes:

- fitting a global smooth model across transonic drag rise with no regime splitting
- treating beta as independent when the geometry is actually axisymmetric and reducible
- collapsing beta when true directional asymmetry exists
- trusting compression error alone without trajectory-level validation
- extrapolating beyond trained Mach/Re/alpha/beta bounds without warning
- using a neural model before the physics baseline and validation pipeline are working

---

## Final recommendation

The best implementation path is:

**physics-first aerodynamic table generation -> regime-aware 3D/4D tensor construction -> Tucker compression -> validation -> OpenRocket deployment -> active-subspace reduction -> optional GP multi-fidelity correction**

That path is the best balance of:

- aerodynamic interpretability
- speed
- compression
- modularity
- future extensibility
- compatibility with OpenRocket simulation loops

It will make the ROM better because it improves the lookup table in all three areas that matter most:

- **fidelity** through physics-aware structure and targeted correction
- **efficiency** through tensor compression and dimensional reduction
- **trustworthiness** through validation, uncertainty estimates, and explicit domain limits

---

## Short implementation checklist

- [ ] classify the rocket as 3D-reducible or true 4D
- [ ] implement friction, wave, base, pressure, and induced drag modules
- [ ] generate structured `(M, Re, alpha[, beta])` tables
- [ ] densify transonic and transition regions
- [ ] split by Mach regime and blend smoothly
- [ ] compress with Tucker
- [ ] compare against CP and GP if useful
- [ ] run holdout and regime-sliced validation
- [ ] compute trajectory-level sensitivity to ROM error
- [ ] export OpenRocket-ready artifact
- [ ] add multi-fidelity correction hooks
- [ ] attach UQ and model-card metadata
