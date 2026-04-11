# Agent Plan: ROM Drag Prediction Pipeline — Test Implementation
> Source: "Validating Reduced-Order Models for Rocket Drag: Achieving ≤1% Fidelity Against RANS
> Through Physics-Constrained Surrogates and Regime-Stratified Validation"
> Combined with: Multi-Regime Analysis (Doc 1), First-Principles Math Framework (Doc 2),
> and RANS-ROM Pipeline (Doc 3).

---

## Purpose

This plan directs an AI coding agent to implement a complete, self-contained test suite for the
five-stage ROM validation pipeline described in the research. Because OpenFOAM CFD cannot be
executed directly, the agent will:

1. Generate synthetic "CFD-equivalent" training data using the first-principles semi-empirical
   drag models derived in Documents 1 and 2 (Barrowman, Hoerner, Braeunig, Kármán-Moore,
   Van Driest II). This acts as the ground-truth oracle.
2. Train a Gaussian Process surrogate (ROM) on subsets of that data.
3. Validate the ROM against held-out oracle data with regime-stratified error metrics.
4. Benchmark ROM accuracy against OpenRocket-equivalent Barrowman baseline predictions.
5. Integrate the ROM into a simplified RK4 coast-phase trajectory solver and propagate errors
   to apogee, validating the δh/h ≈ −(δCd/Cd) × f_drag sensitivity relationship.

All code must be Python 3.10+, use only open-source libraries (numpy, scipy, scikit-learn,
matplotlib, h5py, pandas), and be structured for easy extension to real OpenFOAM data.

---

## Repository Structure

The agent must create the following file tree under a single working directory `rocket_rom/`:

```
rocket_rom/
├── plan.md                        ← this file (copy here too)
├── README.md
├── requirements.txt
│
├── physics/
│   ├── __init__.py
│   ├── atmosphere.py              ← US Standard Atmosphere 1976
│   ├── skin_friction.py           ← Blasius, Schlichting, Van Driest II, roughness
│   ├── base_drag.py               ← Hoerner, Braeunig transonic multiplier, plume model
│   ├── wave_drag.py               ← Kármán-Moore integral, Sears-Haack, Ackeret
│   ├── nose_drag.py               ← shape-specific subsonic/transonic/supersonic
│   ├── fin_drag.py                ← friction + pressure + Ackeret wave drag
│   ├── drag_buildup.py            ← unified Cd(M, Re, alpha) component buildup
│   └── transonic_blend.py         ← sigmoid blending functions across regime boundaries
│
├── data/
│   ├── __init__.py
│   ├── oracle.py                  ← generate synthetic CFD dataset via drag_buildup
│   ├── sampling.py                ← LHS, Sobol, adaptive sampling (pyDOE2 / scipy)
│   ├── benchmarks.py              ← digitized NACA TN-4201 reference Cd values
│   └── hdf5_store.py              ← read/write HDF5 aerodynamic databases
│
├── surrogate/
│   ├── __init__.py
│   ├── gp_model.py                ← Kriging with anisotropic Matérn 5/2 kernel (scikit-learn)
│   ├── kernels.py                 ← custom anisotropic Matérn 5/2 with physics trend mean
│   ├── pce_model.py               ← polynomial chaos expansion surrogate (optional alt)
│   ├── interpolation.py           ← Fritsch-Carlson monotone PCHIP for lookup tables
│   └── lookup_table.py            ← dense grid export and fast trilinear/tricubic query
│
├── validation/
│   ├── __init__.py
│   ├── error_metrics.py           ← L2, L_inf, MAPE, RMSE; regime-stratified reporting
│   ├── cross_validation.py        ← LOO-CV (analytical GP formula), k-fold, holdout
│   ├── gci.py                     ← Grid Convergence Index (Richardson extrapolation)
│   ├── gradient_check.py          ← verify dCd/dM consistency at transonic points
│   └── physics_checks.py          ← Cd > 0, monotone drag rise, correct supersonic decay
│
├── trajectory/
│   ├── __init__.py
│   ├── rk4_solver.py              ← RK4 ODE integrator for coast-phase trajectory
│   ├── sensitivity.py             ← δh/h ≈ −(δCd/Cd)×f_drag analytical + numerical
│   └── monte_carlo.py             ← MC uncertainty propagation using GP posterior variance
│
├── benchmarks/
│   ├── __init__.py
│   ├── barrowman_baseline.py      ← OpenRocket-equivalent Barrowman Cd estimator
│   └── rasaero_comparison.py      ← RASAero II accuracy reference data (literature values)
│
└── tests/
    ├── __init__.py
    ├── test_physics.py
    ├── test_surrogate.py
    ├── test_validation.py
    ├── test_trajectory.py
    └── run_all_tests.py           ← master test runner with report generation
```

---

## Stage 0: Foundation (implement first, all other stages depend on this)

### File: `requirements.txt`

```
numpy>=1.24
scipy>=1.11
scikit-learn>=1.3
matplotlib>=3.7
h5py>=3.9
pandas>=2.0
pyDOE2>=1.3
SALib>=1.4
pytest>=7.4
```

### File: `physics/atmosphere.py`

Implement the US Standard Atmosphere 1976 model. Provide functions:
- `density(altitude_m)` → kg/m³
- `temperature(altitude_m)` → K
- `pressure(altitude_m)` → Pa
- `dynamic_viscosity(T_K)` → Pa·s  [Sutherland's law: μ = μ_ref(T/T_ref)^(3/2)(T_ref+S)/(T+S),
  μ_ref=1.716e-5, T_ref=273.15, S=110.4]
- `speed_of_sound(altitude_m)` → m/s  [a = sqrt(γRT), γ=1.4, R=287.058]
- `mach_number(velocity_ms, altitude_m)` → dimensionless
- `reynolds_number(velocity_ms, altitude_m, L_ref_m)` → dimensionless

Use the standard layered atmosphere: troposphere (0–11 km, lapse 6.5 K/km),
tropopause (11–20 km, isothermal 216.65 K), stratosphere layers per ICAO Doc 7488.

**Test**: at sea level, ρ=1.225 kg/m³, T=288.15 K, a=340.29 m/s. At 11 km, T=216.65 K.

---

### File: `physics/skin_friction.py`

Implement ALL of the following, each as a standalone function with docstring:

1. `blasius_laminar_cf(Re_x)` → local Cf  [0.664/sqrt(Re_x)]
2. `blasius_laminar_CF(Re_L)` → average CF  [1.328/sqrt(Re_L)]
3. `schlichting_turbulent_CF(Re_L)` → average CF  [0.455/(log10(Re_L))^2.58]
4. `prandtl_schlichting_CF(Re_L, Re_crit=5e5)` → with transition correction
   [0.455/(log10(Re_L))^2.58 − A/Re_L, where A=1700 for Re_crit=5e5]
5. `karman_schoenherr_CF(Re_L)` → implicit, solve iteratively [0.242/sqrt(CF) = log10(Re_L·CF)]
6. `rough_surface_CF(L_m, k_s_m)` → fully rough limit [1/(1.89+1.62*log10(L/k_s))^2.5]
7. `van_driest_II(CF_incomp, M_e, T_w_over_T_e=1.0, gamma=1.4, Pr=0.72)` → compressible CF
   Implement the full Van Driest II transformation as described in Section 3 of Doc 2:
   - Compute recovery factor r=0.88 (turbulent)
   - Compute m, F, A, B, α, β, F_c, F_θ, F_x per the equations
   - Transform Re_x_bar = F_x * Re_L, solve Kármán-Schoenherr at Re_x_bar, scale by 1/F_c
8. `eckert_reference_temperature(M_e, T_e_K, T_w_K=None)` → T* in K
   [T*/T_e = 0.5 + 0.5*(T_w/T_e) + 0.22*r*(γ-1)/2 * M_e²]
9. `effective_CF_mixed(Re_L, x_tr_fraction, M=0.0)` → blended laminar+turbulent
   [CF_eff = x_tr * CF_lam + (1-x_tr) * CF_turb, using compressibility-corrected values]
10. `form_factor_body(fineness_ratio)` → FF = 1 + 1.5*(d/l)^1.5 + 50*(d/l)^3
11. `cd_friction_total(Re_L, M, fineness_ratio, S_wet_over_Aref, k_s_m=6.4e-6, T_w_ratio=1.0)`
    → total friction Cd referenced to frontal area, using Van Driest II and form factor

**Tests**:
- Blasius at Re_L=1e6: CF ≈ 1.328e-3
- Schlichting at Re_L=1e7: CF ≈ 2.93e-3
- Rough surface, L=1.5m, k_s=6.4e-6m: verify against Braeunig reference value
- Van Driest II at M=2.0, adiabatic wall: CF_comp/CF_incomp ≈ 0.55–0.65

---

### File: `physics/base_drag.py`

Implement:

1. `hoerner_base_cd(CF_body, base_to_ref_area_ratio=1.0)`
   → 0.029/sqrt(CF_body) × (A_base/A_ref)
   Physical note: inverse relation means lower skin friction → higher base drag.

2. `braeunig_subsonic_base_cd(CF_body, Lo_over_d, db_over_d=1.0)`
   → Cd_base = Kb * (db/d)^n / sqrt(CF_body)
   where Kb = 0.0274*arctan(Lo/d) + 0.0116 + 1
   and   n  = 3.6542*(Lo/d)^(-0.2733)
   Lo = length aft of maximum body diameter

3. `braeunig_transonic_multiplier(M)`
   → For 0.6 < M < 1.0:  fb = 1.0 + 215.8*(M-0.6)^6.0
   → For 1.0 < M < 2.0:  fb = 2.0881*(M-1)^3 - 3.7938*(M-1)^2 + 1.4618*(M-1) + 1.8839
   → For M > 2.0:         fb = 1.8839 / M  (approximate 1/M decay, calibrate to match M=2.0)
   → For M < 0.6:         fb = 1.0
   Return 1.0 outside valid range with a warning.

4. `base_drag_plume_correction(Cd_base_off, A_exit_over_A_base)`
   → Simplified Brazzel: Cd_base_on = Cd_base_off * (1 - A_exit/A_base)
   Clamp to [0, Cd_base_off]. Physical range A_exit/A_base ∈ [0, 1].

5. `base_drag_burnout_transient(t, t_burnout, Cd_base_off, Cd_base_on, tau=0.2)`
   → Cd_base(t) = Cd_base_off - (Cd_base_off - Cd_base_on)*exp(-(t-t_burnout)/tau) for t > t_burnout
   → Cd_base_on for t ≤ t_burnout

6. `base_drag_total(M, Re_L, fineness_ratio, S_wet_over_Aref, k_s_m=6.4e-6,
                    Lo_over_d=0.0, plume_on=False, A_exit_over_A_base=0.0)`
   → Orchestrator: compute CF via skin_friction module, compute base Cd_off,
     apply transonic multiplier, apply plume correction if powered.

**Tests**:
- Hoerner at CF=0.003: Cd_base ≈ 0.529
- Transonic multiplier at M=0.95: verify value ≈ 1.8–2.0
- Burnout transient: Cd_base approaches Cd_base_off exponentially, tau=0.2s

---

### File: `physics/wave_drag.py`

Implement:

1. `karman_moore_wave_cd(area_distribution_fn, L_m, d_max_m, M, N_points=200)`
   → Numerically evaluate the Kármán-Moore double integral:
   D_wave = -(ρV²/2π) ∫∫ S''(ξ1) S''(ξ2) ln|ξ2-ξ1| dξ1 dξ2
   Use numerical second derivative of area_distribution_fn (callable: x → S(x) in m²).
   Regularize the log singularity at ξ1=ξ2 using the Hadamard finite-part prescription
   (exclude diagonal with small ε offset). Reference Sref = π*(d_max_m/2)².
   Only valid for M > 1.2 (supersonic linearized theory). Return 0 for M < 1.0.

2. `sears_haack_wave_cd(S_max_m2, L_m)`
   → Theoretical minimum: (9π/2) * (S_max/L²)²  [referenced to S_max]
   Convert to frontal-area reference.

3. `ogive_area_distribution(x, L_nose, d_base)`
   → Cross-sectional area S(x) for a tangent ogive nose cone.
   radius r(x) = sqrt(rho² - (L-x)²) + y_nose where rho = (L²+R²)/(2R)
   Return S(x) = pi*r(x)².

4. `von_karman_area_distribution(x, L_nose, d_base)`
   → Von Kármán (Haack series) nose: minimizes wave drag for given L, d.
   θ(x) = arccos(1 - 2x/L)
   r(x) = (d_base/2) * sqrt(θ - sin(2θ)/2) / sqrt(π)
   Return S(x) = π*r(x)².

5. `ackeret_fin_wave_cd(t_over_c, M)`
   → Ackeret thin airfoil supersonic wave drag: Cd_wave = 4*(t/c)² / sqrt(M²-1)
   Only valid M > 1.0. Return 0 for M ≤ 1.0.

6. `braeunig_drag_divergence_mach(L_nose_over_d)`
   → M_DD = -0.0156*(L_N/d)² + 0.136*(L_N/d) + 0.6817
   Valid only for L_N/d < 6.0. Warn outside range.

**Tests**:
- Sears-Haack for L=1.5m, d=0.1m: compute and compare to known analytic value
- Von Kármán area distribution: verify S(0)=0, S(L)=π*(d/2)², smooth derivative
- Ackeret at t/c=0.05, M=2.0: Cd_wave = 4*(0.05²)/sqrt(3) ≈ 0.00577

---

### File: `physics/nose_drag.py`

Implement:

1. `nose_subsonic_cd(M, nose_shape='ogive', L_nose_over_d=3.0)`
   → For well-shaped noses (ogive, Von Kármán), return ~0 subsonic pressure drag.
   Apply Prandtl-Glauert correction only: 0/sqrt(1-M²) ≈ 0.
   Returns essentially 0 for M < M_cr.

2. `nose_transonic_cd(M, nose_shape='ogive', L_nose_over_d=3.0)`
   → Lookup table interpolation (Braeunig/OpenRocket Appendix B style).
   Encode tabulated Cd values for standard nose shapes at M = 0.8, 0.9, 1.0, 1.1, 1.2, 1.5, 2.0, 3.0.
   Use scipy PchipInterpolator (Fritsch-Carlson monotone). Reference values (ogive):
   M:    [0.8,  0.9,  1.0,  1.05, 1.1,  1.2,  1.5,  2.0,  3.0]
   Cd:   [0.01, 0.06, 0.18, 0.22, 0.24, 0.22, 0.15, 0.10, 0.06]
   (normalized to frontal area; tune to match NACA TN-4201 order of magnitude)

3. `nose_supersonic_cd(M, L_nose_over_d=3.0, nose_shape='von_karman')`
   → Use Kármán-Moore integral via wave_drag module for M > 1.2.
   Cache results for standard geometries.

4. `nose_cd_total(M, Re_L, L_nose_over_d=3.0, nose_shape='ogive')`
   → Orchestrator: select regime and blend using transonic_blend.sigmoid_blend.

---

### File: `physics/fin_drag.py`

Implement for a set of N fins of given geometry:

1. `fin_friction_cd(Re_L, M, t_over_c, A_wet_fin_over_Aref, N_fins=4)`
   → CF * (1 + 2*t/c) * A_wet_fin/Aref  [compressibility-corrected CF from skin_friction module]

2. `fin_wave_cd_supersonic(M, t_over_c, A_planform_over_Aref, N_fins=4)`
   → Ackeret: 4*(t/c)²/sqrt(M²-1) * A_planform/Aref * N_fins
   Returns 0 for M ≤ 1.0.

3. `fin_interference_factor(N_fins=4)`
   → Kf ≈ 1.04 for typical configurations (literature value, not geometry-dependent here)

4. `fin_cd_total(Re_L, M, t_over_c, A_wet_fin_over_Aref, A_planform_over_Aref, N_fins=4)`
   → Sum friction + wave + apply interference factor.

---

### File: `physics/transonic_blend.py`

Implement smooth blending functions:

1. `sigmoid_weight(M, M_center, k=30.0)`
   → 1 / (1 + exp(-k*(M - M_center)))

2. `regime_weights(M, M1=0.8, M2=1.2, k=30.0)`
   → Returns (w_sub, w_trans, w_sup) as a tuple.
   w_sub  = 1 - sigmoid(M, M1, k)
   w_sup  = sigmoid(M, M2, k)
   w_trans = 1 - w_sub - w_sup
   All weights ∈ [0,1], sum to 1.

3. `blend_cd(M, Cd_sub, Cd_trans, Cd_sup, M1=0.8, M2=1.2, k=30.0)`
   → weighted sum using regime_weights

**Tests**: Verify weights sum to 1.0 everywhere, smooth at M=0.8 and M=1.2.

---

### File: `physics/drag_buildup.py`

The master physics oracle. Implement a `RocketGeometry` dataclass and the main function:

```python
@dataclass
class RocketGeometry:
    """All geometric parameters for a HPR-class rocket."""
    body_length_m: float       # total body length
    body_diameter_m: float     # max body diameter
    nose_length_m: float       # nose cone length
    nose_shape: str            # 'ogive', 'von_karman', 'conical', 'parabolic'
    base_diameter_m: float     # base diameter (may differ from body if boattail)
    afterbody_length_m: float  # length aft of max diameter (Lo for base drag)
    fin_count: int             # number of fins
    fin_thickness_m: float     # fin max thickness
    fin_root_chord_m: float    # fin root chord
    fin_span_m: float          # fin semi-span
    surface_roughness_m: float = 6.4e-6  # paint roughness ~0.25 mil
    nozzle_exit_diameter_m: float = 0.0  # 0 = no motor / coast
```

```python
def cd_total(geometry: RocketGeometry, M: float, Re_L: float,
             alpha_deg: float = 0.0, plume_on: bool = False) -> dict:
    """
    Full component buildup: returns dict with keys
    'Cd_friction', 'Cd_base', 'Cd_wave', 'Cd_nose', 'Cd_fin', 'Cd_induced', 'Cd_total'
    """
```

Internal logic:
- Compute derived geometry: fineness_ratio, L_nose/d, S_wet body, fin wetted area, planform area
- Call each physics submodule
- Apply transonic blending
- Induced drag: Cd_induced = 0.0 for alpha=0; for alpha>0: Cd_induced ≈ k_alpha * alpha_rad²
  where k_alpha = 1.1 * fin_count * (fin_span/body_diameter)  (simplified)
- Sum components
- Return full dict for diagnostic purposes

Also implement:
```python
def cd_scalar(geometry, M, Re_L, alpha_deg=0.0, plume_on=False) -> float:
    """Returns only Cd_total (for surrogate training)."""
```

**Reference geometry** (use throughout all tests as default):
```python
HPR_REFERENCE = RocketGeometry(
    body_length_m=1.5, body_diameter_m=0.1, nose_length_m=0.3,
    nose_shape='ogive', base_diameter_m=0.1, afterbody_length_m=0.0,
    fin_count=4, fin_thickness_m=0.003, fin_root_chord_m=0.15,
    fin_span_m=0.05, surface_roughness_m=6.4e-6, nozzle_exit_diameter_m=0.0
)
```

Expected output (cross-check against Doc 1 error budget):
- Subsonic M=0.3, Re=5e5: Cd_total ≈ 0.45–0.60
- Transonic M=1.0: Cd_total ≈ 0.7–1.0 (peak)
- Supersonic M=2.0: Cd_total ≈ 0.30–0.45

---

## Stage 1: Data Generation

### File: `data/oracle.py`

```python
def generate_oracle_dataset(geometry: RocketGeometry,
                             M_range=(0.1, 5.0), M_n=80,
                             Re_range=(1e5, 1e8), Re_n=20,
                             alpha_range=(0.0, 15.0), alpha_n=8,
                             transonic_oversample=True) -> pd.DataFrame
```

- Generate a dense regular grid using `np.logspace` for Re, `np.linspace` for M and alpha.
- If `transonic_oversample=True`: add extra M points with ΔM=0.025 in [0.75, 1.35].
- For each (M, Re, alpha) point: compute altitude h from Re (using reference atmosphere with
  known velocity to back-calculate h — use sea-level ρ,µ unless Re implies altitude), then
  call `cd_scalar()`.
- Return a DataFrame with columns: ['M', 'Re', 'alpha_deg', 'Cd', 'regime']
  where 'regime' ∈ {'subsonic', 'transonic', 'supersonic'}.
- Save to HDF5 via `data/hdf5_store.py`.

```python
def regime_label(M: float) -> str:
    """Returns 'subsonic', 'transonic', or 'supersonic'."""
    if M < 0.8: return 'subsonic'
    elif M <= 1.2: return 'transonic'
    else: return 'supersonic'
```

### File: `data/sampling.py`

```python
def lhs_sample(n_samples: int, bounds: dict, random_state=42) -> pd.DataFrame:
    """
    Latin Hypercube Sample over parameter bounds.
    bounds = {'M': (0.1, 5.0), 'Re': (1e5, 1e8), 'alpha_deg': (0.0, 15.0)}
    Re is sampled in log10 space then exponentiated.
    """

def sobol_sample(n_samples: int, bounds: dict) -> pd.DataFrame:
    """Sobol quasi-random sequence via scipy.stats.qmc.Sobol."""

def adaptive_sample(existing_df: pd.DataFrame,
                    gp_model,
                    bounds: dict,
                    n_new: int,
                    criterion: str = 'variance') -> pd.DataFrame:
    """
    Add n_new samples where GP posterior variance is largest.
    criterion='variance': pure exploration (GP variance)
    criterion='ei': Expected Improvement (exploration + exploitation)
    Returns DataFrame of new (M, Re, alpha_deg) points to evaluate.
    """
```

### File: `data/benchmarks.py`

Encode digitized reference Cd values from NACA TN-4201 and Doc 1/2 literature for the
HPR_REFERENCE geometry (approximate, sufficient for bias checks):

```python
NACA_TN4201_REFERENCE = {
    # (M, Re) → Cd_total (approximate, from literature digitization)
    (0.6, 5e6): 0.38, (0.8, 5e6): 0.40, (1.0, 5e6): 0.72,
    (1.2, 5e6): 0.60, (1.5, 5e6): 0.48, (2.0, 5e6): 0.38,
    (0.6, 2e7): 0.35, (1.0, 2e7): 0.68, (2.0, 2e7): 0.34,
}
```

Also encode the KTH study finding (OpenRocket overestimates Cd by 12–73% vs RANS/CFD)
as a fractional error range for comparison in validation reports.

### File: `data/hdf5_store.py`

```python
def save_dataset(df: pd.DataFrame, filepath: str, metadata: dict) -> None:
    """Save parameter+coefficient DataFrame to HDF5 with metadata."""
    # Structure: /parameters/{M, Re, alpha_deg}, /coefficients/{Cd}, /metadata

def load_dataset(filepath: str) -> Tuple[pd.DataFrame, dict]:
    """Load from HDF5, return (DataFrame, metadata_dict)."""

def save_lookup_table(grid_M, grid_Re, grid_alpha, Cd_grid,
                      variance_grid, filepath: str) -> None:
    """Save dense evaluation grid for fast trajectory lookup."""

def load_lookup_table(filepath: str) -> dict:
    """Return dict with grid arrays and Cd/variance arrays."""
```

---

## Stage 2: Surrogate Construction

### File: `surrogate/kernels.py`

Implement a custom scikit-learn-compatible kernel:

```python
class AnisotropicMatern52(sklearn.gaussian_process.kernels.Kernel):
    """
    Matérn 5/2 kernel with separate length scales per dimension.
    k(r) = σ²(1 + √5·r/ℓ + 5r²/(3ℓ²)) exp(−√5·r/ℓ)
    where r = sqrt(Σ_d (x_d - x'_d)² / ℓ_d²) [anisotropic Euclidean distance]

    Parameters: amplitude σ², length_scales [ℓ_M, ℓ_Re_log10, ℓ_alpha]
    Note: Re input should be transformed to log10(Re) before passing to kernel.
    """
```

Also implement:
```python
class PhysicsInformedMean:
    """
    Callable mean function using Barrowman semi-empirical predictions.
    GP is built atop: y = ŷ_barrowman(M, Re, alpha) + GP_zero_mean(M, Re, alpha)
    This improves extrapolation behavior outside training envelope.
    """
    def __call__(self, X: np.ndarray) -> np.ndarray:
        """X columns: [M, log10(Re), alpha_deg]. Returns Cd predictions."""
```

### File: `surrogate/gp_model.py`

```python
class RocketDragGP:
    """
    Gaussian Process ROM for rocket drag coefficient.
    Wraps scikit-learn GaussianProcessRegressor with:
    - Anisotropic Matérn 5/2 kernel
    - Physics-informed mean (Barrowman baseline)
    - Log10(Re) input transformation
    - Hyperparameter optimization via marginal likelihood
    """

    def __init__(self, noise_level=1e-8, n_restarts=10):
        ...

    def fit(self, M: np.ndarray, Re: np.ndarray, alpha: np.ndarray,
            Cd: np.ndarray) -> 'RocketDragGP':
        """Train GP. Returns self for chaining."""

    def predict(self, M: np.ndarray, Re: np.ndarray,
                alpha: np.ndarray, return_std=True):
        """Returns (Cd_pred, sigma) or just Cd_pred if return_std=False."""

    def loo_cv_errors(self) -> np.ndarray:
        """
        Analytical LOO-CV using Sherman-Morrison-Woodbury formula.
        ε_LOO,i = (K⁻¹y)_i / (K⁻¹)_ii
        Returns array of shape (N_train,) with LOO residuals.
        """

    def posterior_variance(self, M: np.ndarray, Re: np.ndarray,
                           alpha: np.ndarray) -> np.ndarray:
        """Return GP posterior variance at query points (for UQ)."""

    def export_lookup_table(self, M_grid, Re_grid, alpha_grid,
                            filepath: str) -> None:
        """Evaluate on dense grid, save to HDF5 via hdf5_store."""
```

### File: `surrogate/interpolation.py`

```python
def fritsch_carlson_1d(x: np.ndarray, y: np.ndarray) -> scipy.interpolate.PchipInterpolator:
    """
    Wrapper around scipy PchipInterpolator (which implements Fritsch-Carlson).
    Guarantees monotonicity preservation near transonic peak.
    """

def monotone_pchip_2d(x1: np.ndarray, x2: np.ndarray,
                       Z: np.ndarray) -> callable:
    """
    2D monotone interpolation via nested PCHIP (Fritsch-Carlson in each dimension).
    Returns callable(x1_new, x2_new) -> Z_new.
    """

def validate_monotonicity(M_array: np.ndarray, Cd_array: np.ndarray,
                           M_rise_start=0.8, M_rise_end=1.15) -> bool:
    """
    Check that Cd is monotonically increasing in the drag rise region.
    Returns True if monotone, False with warning if violated.
    """
```

### File: `surrogate/lookup_table.py`

```python
class CdLookupTable:
    """
    Fast 3D lookup table: Cd(M, Re, alpha) with Fritsch-Carlson interpolation.
    Evaluation latency target: < 1 µs per query.
    """
    def __init__(self, filepath: str):
        """Load from HDF5 file."""

    def query(self, M: float, Re: float, alpha_deg: float) -> float:
        """Single point query with clipping to table bounds."""

    def query_batch(self, M: np.ndarray, Re: np.ndarray,
                    alpha_deg: np.ndarray) -> np.ndarray:
        """Vectorized batch query."""

    def is_extrapolating(self, M, Re, alpha_deg) -> bool:
        """True if query point is outside training envelope."""
```

---

## Stage 3: Validation

### File: `validation/error_metrics.py`

```python
def compute_errors(Cd_pred: np.ndarray, Cd_true: np.ndarray,
                   regimes: np.ndarray = None) -> dict:
    """
    Compute comprehensive error metrics. Returns dict with:
    - 'L2': RMSE
    - 'L_inf': max absolute error
    - 'MAPE': mean absolute percentage error
    - 'NRMSE': normalized RMSE (by range)
    - 'R2': coefficient of determination
    - 'bias': mean signed error
    - Per-regime metrics if regimes array provided:
      {'subsonic': {...}, 'transonic': {...}, 'supersonic': {...}}
    """

def regime_stratified_report(Cd_pred, Cd_true, M_array, print_report=True) -> dict:
    """
    Acceptance criteria check (per validation paper Section 7):
    - Subsonic MAPE < 2%
    - Transonic MAPE < 3%
    - Supersonic MAPE < 2–3%
    - Global L∞ relative < 5%
    Prints PASS/FAIL for each criterion. Returns dict.
    """
```

### File: `validation/cross_validation.py`

```python
def loo_cv_analytical(gp_model: RocketDragGP) -> dict:
    """
    Compute LOO-CV errors using the analytical GP formula (no retraining).
    ε_LOO,i = (K⁻¹y)_i / (K⁻¹)_ii
    σ²_LOO,i = 1 / (K⁻¹)_ii
    Returns dict: {'errors': array, 'variances': array, 'RMSE': float, 'MAPE': float}
    """

def k_fold_cv(geometry, M_arr, Re_arr, alpha_arr, Cd_arr, k=5) -> dict:
    """
    k-fold cross-validation: re-train GP on k-1 folds, test on held-out fold.
    Returns dict with per-fold metrics and aggregated statistics.
    """

def holdout_test(geometry, df_train, df_test) -> dict:
    """
    Train GP on df_train, evaluate on df_test (never seen during training).
    Required for final acceptance report.
    """

def train_test_split_stratified(df: pd.DataFrame,
                                 test_fraction=0.2,
                                 random_state=42) -> Tuple[pd.DataFrame, pd.DataFrame]:
    """
    Stratified split preserving regime proportions (80% train / 20% test).
    """
```

### File: `validation/gci.py`

```python
def grid_convergence_index(f_fine, f_medium, f_coarse,
                            r=2.0, F_s=1.25) -> dict:
    """
    Richardson extrapolation + GCI for CFD grid quality.
    Steps per Roache (NIST methodology):
    1. p = ln[(f3-f2)/(f2-f1)] / ln(r)         observed order
    2. f_extrap = f1 + (f1-f2)/(r^p - 1)        continuum limit
    3. GCI_fine = F_s * |ε| / (r^p - 1)         percentage
    4. Asymptotic range check: GCI23/(r^p * GCI12) ≈ 1.0
    Returns dict: {'p': order, 'f_extrap': value, 'GCI_fine': %, 'GCI_medium': %,
                   'asymptotic_ratio': value, 'in_asymptotic_range': bool}
    """

def gci_demo() -> None:
    """
    Simulate three 'grids' by adding gaussian noise scaled to expected
    discretization error (proportional to h^2). Verify GCI < 1% is achievable.
    Uses synthetic Cd values at M=0.5, 1.0, 2.0 as three-grid sequences.
    """
```

### File: `validation/gradient_check.py`

```python
def compute_dCd_dM(gp_model, M_array, Re, alpha_deg, dM=0.01) -> np.ndarray:
    """Finite difference ∂Cd/∂M from GP predictions."""

def compute_dCd_dM_oracle(geometry, M_array, Re, alpha_deg, dM=0.01) -> np.ndarray:
    """Finite difference ∂Cd/∂M from physics oracle."""

def gradient_consistency_check(gp_model, geometry, M_test_points,
                                Re_test, alpha_test=0.0,
                                tolerance=0.10) -> dict:
    """
    Per validation paper Section 7, Stage 3 acceptance criterion:
    |∂Cd/∂M_ROM - ∂Cd/∂M_CFD| / |∂Cd/∂M_CFD| < 10% at all test points.
    Focus on transonic points M ∈ [0.8, 1.3].
    Returns dict: {'M_points': array, 'grad_error_pct': array,
                   'max_error': float, 'passed': bool}
    """

def plot_gradient_comparison(gp_model, geometry, filepath='gradient_check.png'):
    """Plot oracle vs ROM dCd/dM across full Mach range."""
```

### File: `validation/physics_checks.py`

```python
def physics_check_suite(gp_model, geometry, M_grid, Re_ref=1e6) -> dict:
    """
    All physical consistency checks from validation paper Section 7:
    1. Cd > 0 everywhere in parameter space
    2. dCd/dM > 0 in drag rise region (M = M_crit to M_peak ≈ 1.0–1.2)
    3. Cd decreases monotonically for M > 1.5 (supersonic decay)
    4. Cd approaches correct subsonic asymptote as M → 0
    5. Cd increases with alpha² at small alpha (≤ 10°)
    6. GP posterior variance σ² > 0 everywhere (positive definiteness check)
    Returns dict: {check_name: {'passed': bool, 'details': str}}
    """
```

---

## Stage 4: Trajectory Integration

### File: `trajectory/rk4_solver.py`

```python
def coast_phase_rk4(v0_ms: float, h0_m: float,
                    gamma_rad: float,
                    geometry: RocketGeometry,
                    cd_function: callable,
                    dt: float = 0.01,
                    max_time_s: float = 300.0) -> dict:
    """
    RK4 integrator for coast-phase trajectory (no thrust).
    State vector: [v, h] (speed, altitude).
    ODE: m dv/dt = -½ρv²Cd(M,Re,α)A - mg·sin(γ)
         dh/dt  = v·sin(γ)
    cd_function: callable(M, Re, alpha_deg=0.0) -> float
    Returns dict: {'t': array, 'v': array, 'h': array, 'Cd': array,
                   'M': array, 'Re': array, 'apogee_m': float, 't_apogee': float}
    """

def coast_analytical(v0_ms: float, m_kg: float,
                     Cd: float, A_ref_m2: float,
                     rho_kgm3: float = 1.225) -> dict:
    """
    Analytical coast altitude (constant Cd, constant ρ, vertical flight):
    Δh = [m/(ρCdA)] * ln(1 + ρCdAv₀²/(2mg))
    Returns dict: {'delta_h_m': float, 'beta': float, 'f_drag': float}
    """

def stability_check(geometry: RocketGeometry, v_range=(10, 500),
                    rho=1.225) -> dict:
    """
    Verify RK4 stability: eigenvalue λ = -2kv/m should give max stable dt >> 0.01s.
    Returns max stable dt and confirms dt=0.01s is stable.
    Per Doc 2 Section 10: max Δt > 27 seconds for typical HPR parameters.
    """
```

### File: `trajectory/sensitivity.py`

```python
def drag_loss_fraction(v0_ms: float, m_kg: float,
                       Cd: float, A_ref_m2: float,
                       rho: float = 1.225) -> float:
    """
    f_drag = 1 - (1/β) * ln(1+β)   where β = ρCdAv₀²/(2mg)
    """

def apogee_sensitivity(v0_ms: float, m_kg: float,
                       Cd: float, A_ref_m2: float,
                       rho: float = 1.225) -> dict:
    """
    Compute: δh/h ≈ −(δCd/Cd) × f_drag
    Returns dict: {'f_drag': float, 'sensitivity': float,
                   'cd_error_for_1pct_h': float}
    The key relationship from Doc 1/2/4:
    For HPR with f_drag ≈ 0.20–0.35, a 5% Cd error → 1–1.75% apogee error.
    """

def error_budget_table(geometry: RocketGeometry,
                       rom_cd_error_pct: float,
                       v0_ms: float,
                       m_kg: float) -> pd.DataFrame:
    """
    Reproduce the error budget table from Doc 1 Section 6 + Doc 4 Section 1:
    Sources: base drag, surface roughness, AoA, atmosphere, thrust, transonic, ROM
    Columns: source, Cd_error_pct, apogee_error_pct
    """
```

### File: `trajectory/monte_carlo.py`

```python
def monte_carlo_trajectory(v0_ms: float,
                           h0_m: float,
                           geometry: RocketGeometry,
                           gp_model: RocketDragGP,
                           n_samples: int = 1000,
                           cd_noise_sigma_frac: float = 0.02) -> dict:
    """
    Monte Carlo uncertainty propagation using GP posterior variance.
    For each sample:
    - Draw Cd perturbation from GP posterior: Cd_i ~ N(Cd_mean, σ²_GP)
    - Run coast_analytical (fast) or RK4 (accurate)
    - Collect apogee samples
    Returns dict: {'apogee_samples': array, 'mean_m': float, 'std_m': float,
                   'sigma_over_mu_pct': float, 'bias_vs_oracle_pct': float,
                   'acceptance': bool}  ← acceptance: σ/µ < 1%, bias < 0.5%
    """

def plot_monte_carlo_distribution(mc_result: dict,
                                  oracle_apogee_m: float,
                                  filepath: str = 'mc_distribution.png') -> None:
    """Histogram of apogee samples with oracle reference line."""
```

---

## Stage 5: Benchmarks and Comparison

### File: `benchmarks/barrowman_baseline.py`

Implement a simplified OpenRocket-equivalent Barrowman estimator for Cd(M):

```python
def barrowman_cd(M: float, Re_L: float,
                 fineness_ratio: float = 15.0,
                 nose_LN_over_d: float = 3.0,
                 base_area_fraction: float = 1.0) -> float:
    """
    Simplified Barrowman/Niskanen Cd estimation (subsonic + supersonic, no transonic).
    For transonic, linear interpolation between M=0.8 and M=1.2 anchor points.
    Expected bias: 12–73% over-prediction vs CFD (KTH 2024).
    """
    # Skin friction: fully turbulent Schlichting, OpenRocket compressibility correction
    # Cf_comp = Cf / (1 + 0.2044*M²) for subsonic
    # Cf_comp = Cf / (1 + 0.15*M²)^0.58 for supersonic
    # Base drag: Cd_base = (0.12 + 0.13*M²) for M<1; 0.25/M for M>1 [Barrowman original]
    # Transonic: linear interpolation
```

```python
def compare_barrowman_vs_oracle(geometry: RocketGeometry,
                                M_array: np.ndarray,
                                Re: float = 1e6) -> pd.DataFrame:
    """
    Side-by-side: Barrowman Cd vs oracle physics Cd.
    Returns DataFrame with columns: M, Cd_barrowman, Cd_oracle, error_pct
    Expected: Barrowman errors 12–73% in transonic, systematic bias.
    """

def compare_barrowman_vs_rom(geometry, gp_model, M_array, Re=1e6) -> pd.DataFrame:
    """Three-way comparison: Barrowman, ROM, oracle."""
```

---

## Stage 6: Test Suite

### File: `tests/test_physics.py`

Tests for every function in the `physics/` module. Key assertions:

```python
def test_atmosphere_sea_level():
    assert abs(atmosphere.density(0) - 1.225) < 0.01

def test_blasius_laminar():
    assert abs(skin_friction.blasius_laminar_CF(1e6) - 1.328e-3) < 1e-5

def test_transonic_multiplier_continuity():
    """Verify no discontinuity at M=0.6, M=1.0, M=2.0 boundaries."""
    M_vals = np.linspace(0.55, 2.5, 500)
    fb = np.array([base_drag.braeunig_transonic_multiplier(m) for m in M_vals])
    # Check max gradient doesn't produce discontinuities
    assert np.all(np.abs(np.diff(fb)) < 1.0)

def test_drag_buildup_reference():
    """Reference geometry Cd values within expected physical bounds."""
    Cd = drag_buildup.cd_scalar(HPR_REFERENCE, M=0.5, Re_L=5e5)
    assert 0.3 < Cd < 0.8

def test_sears_haack_minimum():
    """ROM Cd at any M should exceed Sears-Haack wave drag minimum."""
    ...

def test_sigmoid_weights_sum_to_one():
    ...
```

### File: `tests/test_surrogate.py`

```python
def test_gp_loo_cv_formula():
    """Analytical LOO must match manual LOO (retrain N times) to within 0.1%."""

def test_gp_positive_variance():
    """GP posterior variance σ² > 0 everywhere in parameter space."""

def test_gp_accuracy_subsonic():
    """Train on 100 LHS points, test on 30 holdout: subsonic MAPE < 2%."""

def test_gp_accuracy_transonic():
    """With transonic oversampling: transonic MAPE < 3%."""

def test_gp_accuracy_supersonic():
    """Supersonic MAPE < 2%."""

def test_lookup_table_latency():
    """Batch query of 10,000 points completes in < 100ms."""

def test_monotone_interpolation():
    """Fritsch-Carlson PCHIP preserves monotonicity in drag rise."""

def test_gp_extrapolation_warns():
    """Query outside training envelope returns increasing variance and triggers warning."""
```

### File: `tests/test_validation.py`

```python
def test_gci_asymptotic_range():
    """Three synthetic 'grids' with 2nd-order convergence → GCI < 1%, ratio ≈ 1.0."""

def test_gradient_consistency_passes_acceptance():
    """With 200 training samples: gradient error < 10% at transonic points."""

def test_physics_checks_all_pass():
    """All 6 physics invariants hold for trained GP model."""

def test_error_budget_magnitude():
    """For f_drag=0.25: 4% Cd error → ~1% apogee error."""

def test_regime_stratified_acceptance():
    """Full pipeline acceptance criteria per paper Section 7."""
```

### File: `tests/test_trajectory.py`

```python
def test_rk4_stability():
    """Confirm max stable dt >> 0.01s for reference HPR parameters."""

def test_analytical_vs_rk4_agreement():
    """Constant-Cd analytical vs RK4 agree to < 0.1% on apogee."""

def test_sensitivity_relationship():
    """Verify δh/h ≈ −(δCd/Cd)×f_drag numerically to < 5% relative error."""
    # Perturb Cd by +5%, run two trajectories, check apogee ratio

def test_monte_carlo_acceptance():
    """1000 MC samples: σ/µ < 1%, bias vs oracle < 0.5%."""

def test_barrowman_systematic_error():
    """
    Barrowman vs oracle errors should be > 10% in transonic regime,
    confirming the KTH study finding (12–73% over-prediction).
    """
```

### File: `tests/run_all_tests.py`

```python
"""
Master test runner that:
1. Runs pytest on all test files
2. Generates a validation report: validation_report.html
3. Produces key plots:
   - Cd(M) comparison: Oracle vs ROM vs Barrowman
   - Error map: relative error in (M, Re) space
   - dCd/dM: gradient consistency
   - MC apogee distribution
   - Error budget bar chart
4. Prints final pipeline acceptance summary table
"""
```

Acceptance summary table format:
```
╔══════════════════════════════════════════════════╗
║     ROM VALIDATION PIPELINE — FINAL SUMMARY     ║
╠══════════════════════════╦══════════╦═══════════╣
║ Criterion                ║ Value    ║ Status    ║
╠══════════════════════════╬══════════╬═══════════╣
║ GCI (grid quality)       ║ X.XX%    ║ PASS/FAIL ║
║ LOO-CV RMSE              ║ X.XXX    ║ PASS/FAIL ║
║ Subsonic MAPE            ║ X.XX%    ║ PASS/FAIL ║
║ Transonic MAPE           ║ X.XX%    ║ PASS/FAIL ║
║ Supersonic MAPE          ║ X.XX%    ║ PASS/FAIL ║
║ Global L∞ relative       ║ X.XX%    ║ PASS/FAIL ║
║ Gradient consistency     ║ XX.X%    ║ PASS/FAIL ║
║ Physics checks (6/6)     ║ X/6      ║ PASS/FAIL ║
║ MC apogee σ/µ            ║ X.XX%    ║ PASS/FAIL ║
║ MC apogee bias           ║ X.XX%    ║ PASS/FAIL ║
╚══════════════════════════╩══════════╩═══════════╝
```

---

## Implementation Order

The agent must follow this strict dependency order:

```
1. requirements.txt + README.md
2. physics/atmosphere.py                       (no deps)
3. physics/skin_friction.py                    (no deps)
4. physics/base_drag.py                        (← skin_friction)
5. physics/wave_drag.py                        (no deps)
6. physics/nose_drag.py                        (← wave_drag, transonic_blend)
7. physics/fin_drag.py                         (← skin_friction, wave_drag)
8. physics/transonic_blend.py                  (no deps)
9. physics/drag_buildup.py                     (← ALL physics modules)
10. tests/test_physics.py                      (verify Stage 0 before proceeding)
11. data/hdf5_store.py                         (← h5py)
12. data/sampling.py                           (← pyDOE2, scipy)
13. data/oracle.py                             (← drag_buildup, sampling, hdf5_store)
14. data/benchmarks.py                         (← literature values)
15. surrogate/kernels.py                       (← scikit-learn)
16. surrogate/gp_model.py                      (← kernels, benchmarks for mean fn)
17. surrogate/interpolation.py                 (← scipy)
18. surrogate/lookup_table.py                  (← interpolation, hdf5_store)
19. tests/test_surrogate.py                    (verify Stage 2)
20. validation/error_metrics.py                (← numpy, pandas)
21. validation/cross_validation.py             (← gp_model, error_metrics)
22. validation/gci.py                          (← numpy)
23. validation/gradient_check.py               (← gp_model, drag_buildup)
24. validation/physics_checks.py               (← gp_model, drag_buildup)
25. tests/test_validation.py
26. trajectory/rk4_solver.py                   (← atmosphere, drag_buildup)
27. trajectory/sensitivity.py                  (← rk4_solver)
28. trajectory/monte_carlo.py                  (← gp_model, rk4_solver)
29. benchmarks/barrowman_baseline.py           (← skin_friction, atmosphere)
30. tests/test_trajectory.py
31. tests/run_all_tests.py                     (← ALL modules)
```

---

## Critical Implementation Notes

### Numerical regularization for Kármán-Moore integral
The double integral has a logarithmic singularity at ξ1=ξ2. Use Gaussian quadrature
on off-diagonal sub-regions with ε=1e-6 exclusion zone around the diagonal.
Alternatively: integrate by parts twice to regularize analytically, then use Gaussian
quadrature on the resulting non-singular kernel.

### GP conditioning
The covariance matrix K can become ill-conditioned for large N or when points cluster
in the transonic regime. Always add a nugget: K_y = K + σ²_noise * I where σ²_noise ≥ 1e-8.
Monitor condition number; warn if cond(K) > 1e12.

### Re transformation
Always transform Re to log10(Re) before passing to the GP kernel. This accounts for
the logarithmic dependence of skin friction on Reynolds number and improves length
scale estimation in the Re dimension.

### Transonic sampling discipline
The most important design decision. In `oracle.py`, when generating training data,
always use ΔM = 0.025 in M ∈ [0.75, 1.35] (24 extra points) and ΔM = 0.1 elsewhere.
Without this, GP will smooth the transonic peak and underpredict C_D_max by 5–15%.

### Burnout discontinuity
The `base_drag_burnout_transient` function uses τ=0.2s. In the RK4 trajectory solver,
this transient must be captured by ensuring dt ≤ τ/10 = 0.02s around the burnout event.
Use adaptive dt if implementing the full powered+coast simulation.

### Extrapolation guard
The `CdLookupTable.query` method must clip M to [M_min, M_max], Re to [Re_min, Re_max],
and alpha to [0, 15°] of the training envelope. Log a warning when extrapolating.
Never let a GP extrapolation return a negative Cd.

---

## Deliverable Checks

Before marking the implementation complete, the agent must verify:

- [ ] All pytest tests pass with zero failures
- [ ] `run_all_tests.py` produces the acceptance summary table
- [ ] Cd(M) plot shows physically correct shape: flat subsonic, sharp transonic peak, smooth
      supersonic decay — with oracle, ROM, and Barrowman lines
- [ ] ROM achieves MAPE < 2% subsonic, < 3% transonic, < 2% supersonic on holdout set
- [ ] LOO-CV RMSE < 0.005 in absolute Cd units
- [ ] Gradient check: max error < 10% at transonic test points
- [ ] MC trajectory: σ/µ < 1%, bias < 0.5%
- [ ] Barrowman shows > 10% over-prediction error in transonic regime (confirming KTH finding)
- [ ] δh/h ≈ −(δCd/Cd) × f_drag verified numerically to < 5% relative error
- [ ] All six physics invariant checks pass
- [ ] GCI demo confirms < 1% numerical uncertainty is achievable

---

## Key Physical Constants (use throughout)

```python
GAMMA = 1.4              # specific heat ratio for air
R_AIR = 287.058          # J/(kg·K), specific gas constant for air
PR_AIR = 0.72            # Prandtl number for air
G = 9.80665              # m/s², standard gravity
MU_REF = 1.716e-5        # Pa·s, Sutherland reference viscosity
T_REF_SUTH = 273.15      # K, Sutherland reference temperature
S_SUTH = 110.4           # K, Sutherland constant
RECOVERY_FACTOR_TURB = 0.88   # turbulent flat plate
```
