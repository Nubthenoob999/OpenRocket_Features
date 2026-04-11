# AI Agent Implementation Spec  
## RocketPy-Inspired ROM for Accurate Full-Ascent Aerodynamics with Compressibility and Isentropic Flow Integration

---

## 1. Purpose

This document defines exactly how an AI coding agent should implement a reduced-order aerodynamic model (ROM) for rocket trajectory simulation, inspired by RocketPy’s aerodynamic architecture but extended for higher-fidelity whole-ascent prediction.

The implementation must prioritize:

1. **Accurate drag prediction across the entire ascent profile**
2. **Smooth behavior from low subsonic through transonic and into supersonic flight**
3. **Use of Mach, Reynolds number, angle of attack, and sideslip where available**
4. **Integration of isentropic compressible-flow equations for any rocket flying above Mach 0.7**
5. **Stable, physically consistent interpolation and extrapolation**
6. **A code structure that can operate with or without CFD tables**

The model should not assume CFD is required. It must be useful using physics-based equations alone, while allowing higher-fidelity multidimensional tables to be plugged in later.

---

## 2. Core Design Philosophy

RocketPy’s aerodynamic architecture separates different aerodynamic effects rather than forcing everything into one single universal drag table.

The agent should follow the same idea, but make it more rigorous for ascent-profile accuracy.

### 2.1 Baseline strategy

Implement aerodynamics in layered form:

- **Layer 1:** Baseline axial body drag model
- **Layer 2:** Compressibility correction model for Mach >= 0.7
- **Layer 3:** Reynolds-number correction
- **Layer 4:** Angle-of-attack and sideslip corrections
- **Layer 5:** Optional control-surface or air-brake drag additions
- **Layer 6:** Optional multidimensional ROM lookup if tabulated data exists

This structure allows the simulation to remain stable even when only partial data is available.

---

## 3. High-Level Requirements

The agent must implement a system capable of evaluating aerodynamic loads during each simulation step using the current flight state.

At minimum, the aerodynamic solver must use:

- altitude
- atmospheric density
- atmospheric temperature
- atmospheric pressure
- dynamic viscosity
- local wind
- rocket velocity
- body angular rates
- rocket orientation
- body reference area
- body reference length
- current motor state
- current control-device state
- center of mass location
- center of pressure location

From these, compute:

- freestream speed
- Mach number
- Reynolds number
- angle of attack
- sideslip angle
- dynamic pressure
- compressible corrected pressure and temperature ratios
- drag coefficient
- optional lift and side-force coefficients
- aerodynamic force vector
- aerodynamic moment vector

---

## 4. Main Implementation Goal

The goal is **not** to blindly clone RocketPy.

The goal is to implement a **better ROM framework** that preserves RocketPy’s strengths:

- simple baseline aerodynamic modeling
- clean modular coefficient evaluation
- table-driven or function-driven coefficient support
- force and moment conversion from coefficients
- dynamic trajectory integration

but improves on it by adding:

- full-ascent profile calibration logic
- explicit transonic handling
- isentropic-flow thermodynamic state reconstruction
- smoother compressibility transition beginning at Mach 0.7
- support for 3D and 4D drag tables

---

## 5. Architecture to Implement

---

## 5.1 Primary modules

The AI agent should implement the following modules or classes:

### `AtmosphereModel`
Responsible for:
- pressure
- temperature
- density
- speed of sound
- dynamic viscosity
- optional wind model

### `FlightState`
Holds:
- position
- velocity
- angular velocity
- orientation
- time
- mass
- motor thrust state
- control-device deployment state

### `FlowState`
Derived from `FlightState` and `AtmosphereModel`, contains:
- local freestream velocity vector
- local freestream speed
- Mach number
- Reynolds number
- angle of attack
- sideslip angle
- dynamic pressure
- stagnation pressure
- stagnation temperature
- static-to-total ratios
- compressibility correction terms

### `AerodynamicModel`
Master evaluator that returns:
- drag coefficient
- lift coefficient
- side-force coefficient
- moment coefficients
- aerodynamic force vector
- aerodynamic moment vector

### `BaselineDragModel`
Handles:
- power-off axial drag
- power-on axial drag
- optional bluff-body/base-drag logic
- low-Mach body drag baseline

### `CompressibilityModel`
Handles:
- onset of compressibility effects at Mach >= 0.7
- isentropic-flow state calculations
- compressibility corrections to drag
- transonic drag-rise blending
- supersonic continuation

### `ReynoldsCorrectionModel`
Handles:
- viscous drag scaling
- skin-friction updates
- characteristic-length handling
- table or equation-based Re corrections

### `AttitudeCorrectionModel`
Handles:
- angle-of-attack drag rise
- sideslip drag rise
- lift generation if desired
- normal-force and pitching-moment corrections

### `ROMTableEvaluator`
Handles:
- interpolation of 1D, 2D, 3D, and 4D tables
- robust out-of-bounds handling
- smooth extrapolation
- optional fallback to physics-based model

### `AeroLoadAssembler`
Converts coefficients into:
- body-frame forces
- body-frame moments
- CP-offset-induced moments

### `AscentValidationMonitor`
Tracks:
- drag history
- Mach history
- Reynolds history
- AoA history
- dynamic pressure history
- acceleration history
- altitude error vs reference
- burnout velocity error
- apogee sensitivity to drag model

---

## 6. What the Agent Must Reproduce from RocketPy

RocketPy effectively uses several strategies worth preserving.

### 6.1 Separate powered and unpowered drag
The agent must include separate drag paths for:
- **power-on**
- **power-off**

Reason:
Base drag and plume-flow interaction can differ strongly between powered ascent and coast.

### 6.2 Evaluate aerodynamic state every time step
The agent must recompute aerodynamic state at every integration step using the updated velocity, altitude, atmospheric state, and rocket attitude.

### 6.3 Convert coefficients to loads using physics
Never use coefficients alone.

Always compute:
- dynamic pressure \( q = \frac{1}{2} \rho V^2 \)
- force = coefficient × dynamic pressure × reference area
- moment = moment coefficient × dynamic pressure × reference area × reference length

### 6.4 Modular table support
The aerodynamic model must accept:
- equations only
- lookup tables only
- equation + table blending
- missing-data fallback logic

---

## 7. Full-Ascent Accuracy Requirements

This is one of the most important sections.

The model must be tuned for **whole ascent profile accuracy**, not just apogee accuracy.

That means the code must not only reproduce final altitude but also match the full evolution of the ascent.

The agent must explicitly implement diagnostics for:

- altitude vs time
- velocity vs time
- Mach vs time
- drag force vs time
- acceleration vs time
- dynamic pressure vs time
- burnout conditions
- maximum-q timing and magnitude
- transonic timing
- sensitivity of apogee to local drag errors

### 7.1 Why this matters
A drag model can match apogee while still being wrong during most of the ascent.

Examples:
- underpredicting drag during transonic flight
- overpredicting drag early in low-speed ascent
- compensating errors between burn phase and coast phase

The agent must avoid these false positives.

### 7.2 Accuracy objective
The model should be designed so that calibration targets prioritize:

1. ascent velocity history
2. dynamic pressure history
3. Mach transition timing
4. burnout altitude and burnout velocity
5. apogee

Do **not** tune only for final altitude.

---

## 8. Compressibility and Isentropic Flow Requirements

This is mandatory for rockets going above Mach 0.7.

The agent must activate a compressibility-aware aerodynamic branch beginning at:

\[
M \ge 0.7
\]

Do not wait for Mach 1.0.

### 8.1 Why start at Mach 0.7
Compressibility effects begin influencing pressure distribution and drag before sonic conditions are reached.

For slender rockets, significant drag rise and pressure effects can appear in the high subsonic regime, especially near:

- Mach 0.7
- Mach 0.8
- Mach 0.9
- Mach 1.0 to 1.2

So the code must introduce a smooth transition beginning at Mach 0.7.

---

## 9. Isentropic Flow Equations to Implement

The agent must implement standard isentropic relations for perfect-gas flow.

Assume:
- \( \gamma = 1.4 \) unless atmosphere model specifies otherwise
- \( R = 287.05 \, \text{J/(kg·K)} \) for air unless otherwise defined

Given static state \( P, T, \rho \) and Mach number \( M \):

### 9.1 Speed of sound
\[
a = \sqrt{\gamma R T}
\]

### 9.2 Stagnation temperature
\[
T_0 = T \left(1 + \frac{\gamma - 1}{2} M^2 \right)
\]

### 9.3 Stagnation pressure
\[
P_0 = P \left(1 + \frac{\gamma - 1}{2} M^2 \right)^{\frac{\gamma}{\gamma - 1}}
\]

### 9.4 Stagnation density
\[
\rho_0 = \rho \left(1 + \frac{\gamma - 1}{2} M^2 \right)^{\frac{1}{\gamma - 1}}
\]

### 9.5 Static-to-total temperature ratio
\[
\frac{T}{T_0} = \left(1 + \frac{\gamma - 1}{2} M^2 \right)^{-1}
\]

### 9.6 Static-to-total pressure ratio
\[
\frac{P}{P_0} = \left(1 + \frac{\gamma - 1}{2} M^2 \right)^{-\frac{\gamma}{\gamma - 1}}
\]

### 9.7 Static-to-total density ratio
\[
\frac{\rho}{\rho_0} = \left(1 + \frac{\gamma - 1}{2} M^2 \right)^{-\frac{1}{\gamma - 1}}
\]

---

## 10. How Isentropic Equations Must Be Used

The agent must not just compute these quantities and ignore them.

They must actively influence aerodynamic evaluation.

### 10.1 Required uses
Use isentropic relations to support:

- compressibility-aware dynamic pressure interpretation
- transonic drag blending
- pressure-based drag scaling
- estimation of pressure coefficient trends
- normalization for ROM inputs if needed
- consistent comparison between low-speed and high-speed aerodynamic states

### 10.2 Practical rule
For Mach < 0.7:
- use incompressible or low-compressibility baseline

For Mach >= 0.7:
- compute isentropic state quantities
- apply compressibility correction terms
- activate transonic drag-rise model
- smoothly transition to supersonic drag logic

---

## 11. Drag Model Formulation

The total drag coefficient should be decomposed as:

\[
C_D = C_{D,\text{base}} + \Delta C_{D,\text{comp}} + \Delta C_{D,\text{Re}} + \Delta C_{D,\alpha\beta} + \Delta C_{D,\text{devices}} + \Delta C_{D,\text{ROM residual}}
\]

Where:

- \( C_{D,\text{base}} \): baseline body drag
- \( \Delta C_{D,\text{comp}} \): compressibility and transonic correction
- \( \Delta C_{D,\text{Re}} \): Reynolds correction
- \( \Delta C_{D,\alpha\beta} \): AoA and sideslip correction
- \( \Delta C_{D,\text{devices}} \): air-brakes or other surfaces
- \( \Delta C_{D,\text{ROM residual}} \): residual correction from table/model discrepancy

This structure is important because it lets the model work even if some parts are unavailable.

---

## 12. Baseline Body Drag Model

The baseline body drag model must work without CFD.

It should include:

- skin-friction drag
- pressure drag
- base drag
- optional wave-drag onset term

### 12.1 Skin-friction drag
Use standard flat-plate or slender-body approximations with Reynolds-number dependence.

At minimum, include a turbulent skin-friction formulation and optionally laminar logic.

Example forms the agent may use:
- laminar:
\[
C_f = \frac{1.328}{\sqrt{Re}}
\]
- turbulent:
\[
C_f = \frac{0.074}{Re^{1/5}}
\]

The exact implementation may include compressibility-corrected or more advanced forms later.

### 12.2 Pressure drag
Use geometry-based pressure drag approximations for nose, body, and aft geometry.

### 12.3 Base drag
Implement separate power-on and power-off logic.

Powered ascent generally reduces effective base drag compared with coast.

### 12.4 Wave drag onset
Near transonic conditions, begin adding wave-drag-like behavior or drag-rise correction.

---

## 13. Reynolds Number Computation

The agent must compute Reynolds number every step using:

\[
Re = \frac{\rho V L_{\text{ref}}}{\mu}
\]

Where:
- \( \rho \): local density
- \( V \): local freestream speed
- \( L_{\text{ref}} \): characteristic length
- \( \mu \): dynamic viscosity

### 13.1 Characteristic length rule
Use:
- body diameter for axial drag as default
- local surface reference length for fins or aerodynamic surfaces

### 13.2 Whole-ascent requirement
Reynolds number must evolve continuously through ascent as:
- density changes
- temperature changes
- velocity changes
- viscosity changes

Do not hold Re constant.

---

## 14. Angle of Attack and Sideslip

The agent must compute local aerodynamic angles from the body-frame freestream vector.

Recommended definitions:

\[
\alpha = \tan^{-1}\left(\frac{V_y}{V_z}\right)
\]

\[
\beta = \tan^{-1}\left(\frac{V_x}{V_z}\right)
\]

Use a consistent body-axis convention across the codebase.

### 14.1 Why AoA matters for drag
Even small AoA can significantly increase drag during ascent, especially in:

- wind
- weathercocking
- control-surface deployment
- transonic flight

The model must include an AoA drag-rise term even if only approximate.

Example structure:
\[
\Delta C_{D,\alpha\beta} = k_\alpha \alpha^2 + k_\beta \beta^2
\]

This can later be replaced by table data.

---

## 15. Compressibility Correction Strategy

This is mandatory.

The agent must implement a compressibility correction model with three regions:

### Region 1: Low subsonic
\[
M < 0.7
\]
Use baseline model with small or no compressibility correction.

### Region 2: High subsonic / transonic onset
\[
0.7 \le M \le 1.2
\]
Use smooth drag-rise blending.

This region must be handled carefully because drag changes rapidly here.

### Region 3: Supersonic continuation
\[
M > 1.2
\]
Use supersonic continuation model that avoids discontinuity after transonic peak.

---

## 16. Recommended Transonic Drag-Rise Implementation

The agent should implement a smooth drag-rise function rather than a sharp discontinuity.

Example concept:

\[
\Delta C_{D,\text{comp}} = A \cdot S(M; M_1, M_2)
\]

where \(S\) is a smooth sigmoid, spline, or blending function activating between \(M_1 = 0.7\) and \(M_2 \approx 1.2\).

Possible implementation options:
- cubic Hermite blend
- logistic sigmoid
- smoothstep function
- spline through reference anchor points

### 16.1 Required properties
The drag-rise model must be:
- continuous
- differentiable if possible
- monotonic up to transonic peak if physics suggests it
- stable under time integration
- non-oscillatory

### 16.2 Do not do this
Do **not** implement:
- piecewise hard jumps
- discontinuous Cd spikes
- abrupt drag changes at Mach 1.0

That will hurt solver stability and damage whole-ascent accuracy.

---

## 17. Multidimensional ROM Table Support

The agent must support the following lookup types:

### 17.1 1D
\[
C_D = f(M)
\]

### 17.2 2D
\[
C_D = f(M, Re)
\]
or
\[
C_D = f(\text{deployment}, M)
\]

### 17.3 3D
\[
C_D = f(M, Re, \alpha)
\]

### 17.4 4D
\[
C_D = f(M, Re, \alpha, \beta)
\]

The evaluation system must support any subset of these.

---

## 18. Interpolation Rules

The agent must implement robust multidimensional interpolation.

### 18.1 Required interpolation options
Support at least:
- linear interpolation
- nearest-neighbor fallback
- optional radial basis fallback for sparse data

### 18.2 Default recommendation
Use:
- linear interpolation inside the table domain
- controlled smooth fallback outside domain
- physics-based fallback when far outside domain

### 18.3 Extrapolation rule
Do not allow unbounded extrapolation.

Out-of-bounds behavior should be one of:

1. clamp to nearest valid boundary
2. blend toward physics model
3. use smooth bounded extrapolation with warnings

Best recommendation:
- **blend to physics-based model outside trusted domain**

---

## 19. Table Confidence and Fallback Logic

Every table must carry metadata:
- valid Mach range
- valid Reynolds range
- valid alpha range
- valid beta range
- source type
- confidence score
- interpolation method
- extrapolation policy

When query point is outside domain:
- compute physics-based fallback
- blend with nearest valid table prediction
- log a warning or confidence reduction

This is critical for robust ascent simulation.

---

## 20. Force and Moment Assembly

After coefficients are evaluated, compute aerodynamic forces and moments.

### 20.1 Dynamic pressure
\[
q = \frac{1}{2} \rho V^2
\]

### 20.2 Force from drag
\[
D = C_D \, q \, A_{\text{ref}}
\]

### 20.3 Force from lift
\[
L = C_L \, q \, A_{\text{ref}}
\]

### 20.4 Side force
\[
Y = C_Y \, q \, A_{\text{ref}}
\]

### 20.5 Moment
\[
M = C_m \, q \, A_{\text{ref}} \, L_{\text{ref}}
\]

Then:
- rotate aerodynamic-frame forces into body frame
- compute moment contribution from CP-CG offset:
\[
\mathbf{M}_{\text{offset}} = \mathbf{r}_{CP-CG} \times \mathbf{F}
\]
- add intrinsic aerodynamic moments

---

## 21. Powered vs Coast Ascent Logic

The agent must separately evaluate drag during:

### 21.1 Powered ascent
Use:
- power-on drag baseline
- reduced base drag if appropriate
- plume interaction correction if modeled
- active transonic compressibility branch if Mach >= 0.7

### 21.2 Coast ascent
Use:
- power-off drag baseline
- increased base drag if appropriate
- same Reynolds and compressibility evaluation
- same attitude corrections

This split is important for whole-ascent fidelity.

---

## 22. Ascent-Profile Calibration Strategy

The agent must include a calibration mode that compares the modeled ascent profile against reference data.

Reference data may include:
- flight logs
- CFD points
- wind-tunnel points
- higher-fidelity simulation data
- expected textbook values

### 22.1 Calibration targets
The calibration routine must score agreement in:
- altitude vs time
- velocity vs time
- Mach vs time
- dynamic pressure vs time
- acceleration vs time
- burnout altitude
- burnout velocity
- apogee

### 22.2 Objective function idea
Use weighted error:

\[
J = w_1 E_h + w_2 E_v + w_3 E_M + w_4 E_q + w_5 E_{a} + w_6 E_{burnout} + w_7 E_{apogee}
\]

Where each \(E\) is a normalized error metric.

### 22.3 Important rule
Choose weights so that apogee does **not** dominate the objective.

The whole point is full-ascent accuracy.

---

## 23. Recommended Coding Workflow for the Agent

### Phase 1: Core atmospheric and flow-state engine
Implement:
- atmosphere evaluation
- speed of sound
- dynamic viscosity
- freestream velocity
- Mach
- Reynolds
- AoA
- sideslip
- dynamic pressure
- isentropic quantities

### Phase 2: Baseline physics-only drag model
Implement:
- power-on drag
- power-off drag
- skin-friction model
- pressure drag
- base drag
- AoA correction
- Reynolds correction

### Phase 3: Compressibility and transonic model
Implement:
- Mach >= 0.7 transition
- isentropic thermodynamic relations
- transonic drag-rise blending
- supersonic continuation

### Phase 4: Table evaluator
Implement:
- 1D, 2D, 3D, 4D interpolation
- out-of-bounds detection
- fallback blending to physics model

### Phase 5: Force/moment assembly
Implement:
- coefficient-to-load conversion
- frame transforms
- CP-offset moments

### Phase 6: Validation and diagnostics
Implement:
- ascent history recorder
- comparison plots
- residual/error tracker
- trust-domain warnings

---

## 24. Pseudocode the Agent Should Follow

```python
def evaluate_aerodynamics(flight_state, atmosphere, rocket, aero_tables=None):
    # 1. Atmospheric state
    atm = atmosphere.evaluate(flight_state.altitude)

    # 2. Relative freestream
    V_rel_body = compute_body_frame_relative_wind(
        flight_state.velocity,
        flight_state.angular_velocity,
        flight_state.orientation,
        atm.wind,
        rocket.reference_points
    )

    V = norm(V_rel_body)
    rho = atm.density
    T = atm.temperature
    P = atm.pressure
    mu = atm.dynamic_viscosity
    gamma = atm.gamma
    R = atm.gas_constant

    # 3. Primary flow properties
    a = sqrt(gamma * R * T)
    M = V / a if a > 0 else 0.0
    Re = rho * V * rocket.reference_length / mu if mu > 0 else 0.0
    alpha = atan2(V_rel_body[1], V_rel_body[2])
    beta  = atan2(V_rel_body[0], V_rel_body[2])
    q = 0.5 * rho * V**2

    # 4. Isentropic properties for Mach >= 0.7
    if M >= 0.7:
        T0 = T * (1 + 0.5 * (gamma - 1) * M**2)
        P0 = P * (1 + 0.5 * (gamma - 1) * M**2)**(gamma / (gamma - 1))
        rho0 = rho * (1 + 0.5 * (gamma - 1) * M**2)**(1 / (gamma - 1))
    else:
        T0, P0, rho0 = T, P, rho

    # 5. Baseline drag
    Cd_base = evaluate_baseline_drag(
        mach=M,
        reynolds=Re,
        alpha=alpha,
        beta=beta,
        powered=flight_state.motor_is_burning
    )

    # 6. Compressibility correction
    dCd_comp = evaluate_compressibility_correction(
        mach=M,
        static_pressure=P,
        static_temperature=T,
        stagnation_pressure=P0,
        stagnation_temperature=T0
    )

    # 7. AoA/sideslip correction
    dCd_att = evaluate_attitude_drag(alpha, beta)

    # 8. Optional device drag
    dCd_dev = evaluate_device_drag(flight_state, M)

    # 9. Optional multidimensional ROM residual
    dCd_rom = 0.0
    if aero_tables is not None:
        dCd_rom = evaluate_rom_residual_or_override(
            mach=M,
            reynolds=Re,
            alpha=alpha,
            beta=beta,
            tables=aero_tables,
            baseline=Cd_base + dCd_comp + dCd_att + dCd_dev
        )

    # 10. Total drag coefficient
    Cd = Cd_base + dCd_comp + dCd_att + dCd_dev + dCd_rom

    # 11. Convert to force
    D = Cd * q * rocket.reference_area
    F_aero_body = drag_vector_from_relative_wind(D, V_rel_body)

    # 12. Optional lift/side forces
    # Compute here if model supports them

    # 13. Moment from CP offset
    r_cp_cg = rocket.cp_position - rocket.cg_position
    M_aero_body = cross(r_cp_cg, F_aero_body)

    return {
        "Mach": M,
        "Re": Re,
        "alpha": alpha,
        "beta": beta,
        "q": q,
        "Cd": Cd,
        "F_aero_body": F_aero_body,
        "M_aero_body": M_aero_body,
        "P0": P0,
        "T0": T0,
        "rho0": rho0
    }