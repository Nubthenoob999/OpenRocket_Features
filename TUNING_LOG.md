# Offline Physics-Aero Tuning Log

This log records only source-bounded physics changes accepted for the strict offline correlation-table path. Flight residuals are validation evidence, not per-flight calibration targets.

## 2026-08-01 - Geometry, force-frame, and source-domain corrections

**Status:** Accepted
**Code identity:** `physics-aero-v73`

Changes:

- Corrected aerodynamic moment signs and reference-frame transformations, and reconstructed incidence azimuth from the velocity vector.
- Corrected geometry-derived center of pressure, fin aspect ratio, fin count, and fin/body installation interference.
- Fixed rail-button extraction so zero axial length does not discard a finite projected protuberance area.
- Removed the body-only low-Mach placeholder; the table now uses assembled body, fin, base, skin-friction, and installation terms.
- Extended the Jorgensen/Galejs high-incidence body-force domain through the physical `+/-90 deg` symmetry boundary. Fin normal force remains held at the documented `+/-20 deg` stall/source boundary.
- Held DATCOM isolated-fin loading at its documented `+/-15 deg` incidence boundary when body upwash moves the local incidence outside the correlation domain. No DATCOM extrapolation is performed.
- Projected launch-guide axial force using the local dynamic-pressure direction and localized apogee with a bounded event listener.

Physics basis:

- USAF Stability and Control DATCOM, section 4.1.3.2 finite-wing lift-curve-slope form.
- Jorgensen/Galejs slender-body crossflow correlations for high angle of attack.
- Hoerner, *Fluid-Dynamic Drag* (1965), component drag and interference treatment: https://home.hvl.no/ansatte/gste/ftp/MarinLab_files/Litteratur/Hoerner_1965_Fluid-dynamic_drag.pdf
- NACA RM A53D02 finned-body measurements and interaction limits: https://ntrs.nasa.gov/citations/19930087740

Regression evidence:

- Geometry, fin-physics, transonic-continuity, integration-invariant, and runtime-adapter suites passed.
- No empirical per-flight multiplier or residual-fitting term was introduced.

## 2026-08-01 - Transonic and high-Mach source-boundary handling

**Status:** Accepted
**Code identity:** `physics-aero-v73`

Changes:

- Held transonic incidence corrections at their last source-supported boundary rather than extrapolating them.
- Added the NASA TR-R-100 nose-tip transonic-onset relation to the body critical-Mach estimator.
- Extended the default offline sampling envelope through Mach 8 so high-Mach flights do not query beyond the generated table.
- Added a Black Brant/high-altitude sampling point and held Reynolds corrections at the lowest directly generated density boundary instead of extrapolating into an unsupported rarefied-flow regime.
- Preserved explicit method-ownership handoffs across transonic cells; event and powered/unpowered boundaries remain non-interpolable.

Physics basis:

- NASA TR-R-100 body critical-Mach/nose-tip correlation.
- NACA TR-1307, Pitts, Nielsen, and Kaattari, wing-body-tail interference data and equations.
- NACA RM L9I30 measured transonic drag-rise shape.
- NACA RM L52E06 and NACA TN 3393 transonic/supersonic base-pressure correlations.
- NASA turbulent base-drag correlation report: https://ntrs.nasa.gov/api/citations/20030003914/downloads/20030003914.pdf

Regression evidence:

- `TransonicGeometryAndContinuityTest`, `SamplingConfigurationTest`, `FullRegimeReynoldsEnvelopeTest`, and table-contract tests passed.
- The public comparison completed all 24 single-stage flights with 100% table coverage and zero runtime fallbacks.

## 2026-08-01 - Direct Reynolds correction surface (schema v8)

**Status:** Accepted
**Old behavior:** A local derivative represented the Reynolds correction over a broad runtime interval.
**New behavior:** Seven offline, directly generated Reynolds-ratio anchors are stored per cell, including the low-ratio `0.005` boundary; runtime interpolation is bounded by those anchors.
**Code identity:** table schema v8 / `physics-aero-v73`

Physics basis:

- Prandtl-Schlichting turbulent flat-plate skin friction, `Cf = 0.455 / log10(Re)^2.58`.
- Laminar mean skin friction, `Cf = 1.328 / sqrt(Re)`.
- DATCOM compressibility correction and Van Driest II transformation, NASA TN D-6945.

Regression evidence:

- Schema-v8 deterministic round trip, checksum rejection, event ownership, Reynolds envelope, and runtime-query tests passed.
- Queries outside the generated Reynolds surface are boundary-held and explicitly diagnosed, not extrapolated.

## 2026-08-01 - Reproducible atmospheric wind realization

**Status:** Accepted
**Old behavior:** Changing `SimulationOptions.randomSeed` did not reset a pink-noise model that had already been constructed, so identical suites could produce different turbulence histories.
**New behavior:** Reseeding resets average and multilevel pink-noise realizations while preserving their mean speed, direction, and standard deviation. Each multilevel layer receives an independent deterministic child seed.

Physics/testing basis:

- This does not tune wind magnitude or flight residuals. It restores the stated Monte Carlo seed contract and makes comparisons repeatable.
- Two complete internal-corpus runs produced identical CSV SHA-256 `DCD12C3775CE0BF87DA85F0AF76C02FC509700E10EDBCB7B58CA23424F3E7ED8`.
- `PinkWindModelTest` passed, including reseed/restart equivalence.

## 2026-08-01 - Altimeter measurement-space comparison

**Status:** Accepted
**Old behavior:** Every simulated geometric apogee was compared directly with the logged altitude, even when the logger's published output was pressure-derived standard-atmosphere altitude.
**New behavior:** The harness retains geometric apogee and additionally transforms simulated pressure into the documented instrument space for EasyMini and PerfectFlite/StratoLogger data. Fluctus `dedrck-alti` remains a displacement/geometric comparison because the selected truth channel is distinct from its `baro-altitude` channel.

Manufacturer basis:

- EasyMini export documents pressure, altitude, and height above pad: https://altusmetrum.org/AltOS/doc/easymini.html
- PerfectFlite StratoLogger documents pressure-difference conversion using the U.S. Standard Atmosphere: https://www.perfectflite.com/Downloads/StratoLogger%20manual.pdf
- Fluctus documents `baro-altitude` and `dedrck-alti` as separate channels: https://silicdyne.net/resources/fluctus_fulldoc_v1_2_EN.pdf

Regression evidence:

- The bundled EasyMini pressure/height pair agrees with the implemented standard-atmosphere transfer within `0.75 m`.
- Truth-schema selection and instrument-space comparison tests passed.

## 2026-08-01 - Public flight-database validation (`physics-aero-v73`)

**Status:** Accepted as the current non-overfit plateau; goal not complete.

Results against `AidanSYu/rocket-flight-database` commit `cfbfc37`:

| Metric | Baseline OpenRocket | Strict offline table |
|---|---:|---:|
| Comparable single-stage flights | 24 | 24 |
| Mean absolute apogee error | 9.305% | 5.068% in instrument-comparable measurement space |
| Geometric-apogee MAE | n/a | 7.012% |
| Signed measurement-space bias | n/a | +1.694% |
| Flights within 5% | n/a | 15/24 |
| Flights within 10% | n/a | 20/24 |
| Runtime table coverage | n/a | 100% |
| Runtime fallbacks / abnormal endings | n/a | 0 / 0 |

Four multistage records (22, 25, 27, and 28) are reported as unsupported by the single-stage physics-aero runner rather than mis-scored. Remaining greater-than-10% residuals are flights 4, 14, 20, and 21.

The audit did not find a universal source-backed drag correction for those four residuals. In particular, flight 20 agrees with its RASAero prediction to about 1.5%, while that RASAero prediction is itself high versus flight; increasing table drag to match that flight would fit a configuration/measurement residual. Residual signs across the corpus also conflict, so a global multiplier would improve some rockets while worsening others. No such multiplier was accepted.

## 2026-08-01 - Internal club-corpus airbrake integrity gate

**Status:** Accepted gate; six comparisons blocked by missing source data.
**Production airbrake code:** Unchanged.

The harness now requires `PhaseThreeNativeAirbrakesConfigurer` to report successful configuration for every airbrake-enabled flight. A skipped or auto-disabled airbrake setup is a failed comparison, not a no-airbrake score.

Missing CFD drag surfaces:

1. `Drag Curve CDR.csv` - required by Jackpot launches 1 and 2.
2. `Drag Curve Pelicantor - Sheet1.csv` - required by DOL and Pelencator launches 1 and 4.

Current honest internal summary:

| Metric | Result |
|---|---:|
| Flights configured | 10 |
| Comparable baseline/table flights | 3 / 3 |
| Baseline MAE | 12.451% |
| Strict-table MAE (`physics-aero-v79`) | 14.105% |
| Strict-table failures due to required airbrake configuration | 6 |
| Additional truth-unavailable record | 1 |

The three valid v79 strict-table residuals are Government launch 1 `13.080%`, Government launch 2 `8.154%`, and Pelencator launch 2 `21.082%`. These are not a complete club-corpus score. The v73 values (`12.816%`, `6.796%`, and `16.603%`) are retained as historical evidence above; the currently accepted component correlations did not improve this very small subsonic subset.

Regression evidence:

- The Government launch 1 test confirms strict-table preparation preserves configured airbrakes and that deployment changes apogee.
- The missing-surface test confirms an airbrake-enabled flight cannot be silently scored with airbrakes disabled.
- The dedicated airbrake regression, schema contract, RASAero benchmark, and 67-test broad physics suite all passed.

## 2026-08-01 - RASAero geometry correlations and supersonic friction (`physics-aero-v79`)

**Status:** Accepted.

Changes:

- Added the RASAero II v1.0.2 rail-guide pair correlation for imported RASAero `Rail Guide` geometry while retaining the native NACA cylinder model for OpenRocket rail buttons.
- Added the RASAero nozzle-geometry-only power-on correction, `delta CD = -F(M) A_exit/A_reference`. Resolved thermodynamic nozzle states remain owned by the NACA RM L54D27 powered-flow model.
- Replaced the generic subsonic base term with RASAero's friction-coupled cubic-diameter correlation.
- Added RASAero virtual-fin wetted-area interference as its own friction owner.
- Applied cubic aft-diameter scaling to the transonic base-pressure correlation.
- Replaced the unresolved engineering fallback's supersonic Van Driest branch with the measured RASAero piecewise compressibility correlation. The established DATCOM subsonic factor is retained, with a named Mach 0.9--1.1 overlap. Resolved boundary-layer marches still use their wall-temperature transformations.

Physics basis:

- RASAero II v1.0.2 implementation and official user manual for rail-guide geometry, nozzle diameter ownership, hexagonal-fin geometry, and the component drag decomposition.
- Prandtl-Schlichting mean turbulent skin friction with the RASAero Mach 1.05--2 linear compressibility fit and the DATCOM `1/(1+0.144 M^2)^0.65` supersonic factor.
- RASAero/Hoerner friction-coupled base drag and virtual-fin overlap geometry.
- NACA RM L54D27 remains the authority for resolved powered-base thermodynamics.

Rejected diagnostics:

- Disabling the runtime Reynolds surface was not accepted. On flights 21, 23, and 24 it improved apogee error by only 1.4, 3.0, and 3.6 percentage points and left all three materially low; the correction was restored unchanged.
- A per-flight Qu8k drag decrement was not accepted. Independent RASAero output shows its approximately `+0.031` table-minus-RASAero CD difference at Mach 3 is distributed across base, boattail/transition, fin wave/interference, and friction terms rather than one incorrectly owned scalar.
- No correction was made for trajectory inclination: the high-altitude trajectories have vertical/total velocity ratios of approximately 0.998--0.999 at maximum dynamic pressure.

Public validation against `AidanSYu/rocket-flight-database` commit `cfbfc37`:

| Metric | `physics-aero-v73` | `physics-aero-v79` |
|---|---:|---:|
| Comparable single-stage flights | 24 | 24 |
| Instrument-comparable apogee MAE | 5.068% | 5.204% |
| Geometric-apogee MAE | 7.012% | 7.101% |
| Signed measurement-space bias | +1.694% | -1.873% |
| Flights within 5% | 15/24 | 14/24 |
| Flights within 10% | 20/24 | 21/24 |
| Runtime table coverage | 100% | 100% |
| Runtime fallbacks / abnormal endings | 0 / 0 | 0 / 0 |

The accepted source correlations trade a small MAE increase relative to v73 for better 10% coverage and substantially better high-altitude transfer. Relative to the immediately preceding v77 physics set, v79 reduces measurement-space MAE from `5.562%` to `5.204%`. Don’t Debate This moves from `-11.77%` to `-8.85%`; Proteus from `-12.94%` to `-10.94%`; Qu8k from `-20.94%` to `-17.98%`.

Regression evidence:

- The complete public replay passed with 100% strict-table coverage and no fallback or abnormal ending.
- A focused 86-test physics, table-contract, runtime, powered-flow, protuberance, and airbrake suite passed.
- A broader 325-test selection ran with all airbrake tests passing; its four failures are corpus-infrastructure issues outside this change: three absent source CSV fixtures and one Windows line-ending-sensitive pinned hash.

## Completion decision

## 2026-08-01 - Supersonic expansion and terminal-reducer correlations (`physics-aero-v81`)

**Status:** Accepted.

Changes:

- Replaced the generic compression-corner pressure integral for a conical diameter expansion with the RASAero II v1.0.2.0 empirical wave-drag correlation. The correlation is a function of Mach, expansion angle through `2 M tan(theta)`, and projected annular area.
- Added the RASAero terminal-reducer correlation as a separated-flow pressure-drag envelope for steep supersonic boattails. It uses Mach, diameter ratio, conical half-angle, and the documented implementation's `17.5 deg` limiting angle.
- Kept separate physical ownership for expansion wave drag, boattail pressure drag, and exposed-base pressure drag. No flight ID, measured apogee, or residual enters either equation.

Independent component checks:

| Mach 3 geometry | RASAero exported CD | Implemented CD |
|---|---:|---:|
| Don't Debate This fin-can expansion | 0.01591918 | 0.01591918 |
| Qu8k fin-can expansion | 0.01452094 | 0.01452094 |
| Qu8k terminal reducer | 0.02289582 | 0.02289584 |
| Proteus terminal reducer | 0.02808633 | 0.02808633 |

Public validation against `AidanSYu/rocket-flight-database` commit `cfbfc37`:

| Metric | `physics-aero-v79` | `physics-aero-v81` |
|---|---:|---:|
| Comparable single-stage flights | 24 | 24 |
| Instrument-comparable apogee MAE | 5.204% | 4.879% |
| Geometric-apogee MAE | 7.101% | 6.775% |
| Signed measurement-space bias | -1.873% | -2.113% |
| Flights within 5% | 14/24 | 14/24 |
| Flights within 10% | 21/24 | 22/24 |
| Runtime table coverage | 100% | 100% |
| Runtime fallbacks / abnormal endings | 0 / 0 | 0 / 0 |

The largest transfers are Full Metal Jacket - Black Rock 6 (`+10.06%` to `+5.96%`), Don't Debate This (`-8.85%` to `-7.63%`), and Qu8k (`-17.98%` to `-15.54%`). Proteus changes by only `0.04` percentage point and remains at `-10.91%`. Full-corpus measurement-space MAE improves by `0.325` percentage point; the 10% count improves by one flight. Current-vs-RASAero MAE moves from `5.637%` to `5.744%`, so acceptance is based on measured-flight transfer rather than circular agreement with the comparison code.

Regression evidence:

- The complete 28-row public replay passed: all 24 supported single-stage flights completed with 100% strict-table coverage, zero fallbacks, and zero abnormal endings; the same four multistage flights remain explicitly unsupported.
- Exact component-correlation anchors reproduce independent RASAero exports to within `5e-8` CD.

## Completion decision

## 2026-08-01 - Supersonic cubic base-drag envelope (`physics-aero-v82`)

**Status:** Accepted.

Changes:

- Added the RASAero II v1.0.2.0 supersonic base-pressure polynomial and cubic terminal-diameter recovery as an upper envelope on the already assembled finned-base owner.
- For terminal boattails steeper than `17.5 deg`, the correlation uses the equivalent `17.5 deg` aft diameter before applying cubic recovery. It never raises base suction when the resolved/measured closures are already lower.
- The correlation is Mach- and geometry-based. It contains no flight identity, residual, or apogee input.

Independent component checks:

- The Mach 3 envelope reproduces the RASAero exports for Qu8k (`CD_base = 0.073`) and Proteus (`CD_base = 0.068`) within their published three-decimal resolution.
- With the v81 transition/reducer correlations, Qu8k's complete Mach 3 table CD moves from approximately `0.431` toward the independent RASAero value `0.410` through separately owned, traceable terms.

Public validation against `AidanSYu/rocket-flight-database` commit `cfbfc37`:

| Metric | `physics-aero-v81` | `physics-aero-v82` |
|---|---:|---:|
| Comparable single-stage flights | 24 | 24 |
| Instrument-comparable apogee MAE | 4.879% | 4.530% |
| Geometric-apogee MAE | 6.775% | 6.424% |
| Signed measurement-space bias | -2.113% | -1.734% |
| Flights within 5% | 14/24 | 15/24 |
| Flights within 10% | 22/24 | 22/24 |
| Current-vs-RASAero apogee MAE | 5.744% | 5.426% |
| Runtime table coverage | 100% | 100% |
| Runtime fallbacks / abnormal endings | 0 / 0 | 0 / 0 |

The largest v81-to-v82 improvements are A-601 Kinsel (`3.15` percentage points), Don't Debate This (`1.62` points), and Qu8k (`2.14` points). Torrent and Full Metal Jacket - Black Rock 6 regress by only `0.07` and `0.29` points. Proteus improves slightly to `-10.81%`; Qu8k improves to `-13.40%`.

Regression evidence:

- The complete public replay passed with 100% strict-table coverage, zero fallbacks, and zero abnormal endings.
- A focused 73-test physics, ownership, table-contract, runtime, powered-flow, protuberance, inventory, and airbrake suite passed.
- The previously recorded broader-suite infrastructure limitations remain unchanged: three absent external validation CSV fixtures and one Windows line-ending-sensitive pinned fixture hash.

## 2026-08-01 - Archived Jackpot CFD surface restored

**Status:** Accepted data restoration; production airbrake code unchanged.

- Recovered `Drag Curve CDR.csv` from three byte-identical copies in the mounted HPRC team-drive archive. The archive SHA-256 is `88C0D3EF040204934695C589B9EBE76856C2194FC6E11BEA35BC26FF831AEC2D`.
- Restored the same 15-row Mach/deployment/absolute-drag table to Jackpot launches 1--3. No interpolation value or unit was inferred.
- An exhaustive repository, Git-history, team-drive, user-profile, and mounted-volume search did not find `Drag Curve Pelicantor - Sheet1.csv`. The older `Air Brakes Drag Coeff.csv` is not a valid substitute: it contains coefficient rather than absolute-force data and lacks the CFD dynamic-pressure/reference-area provenance required for a physical conversion.

Honest internal summary after restoration (`physics-aero-v81` equations are identical to v82 below Mach 1.15 for these flights):

| Metric | Result |
|---|---:|
| Flights configured | 10 |
| Comparable baseline/table flights | 6 / 6 |
| Baseline MAE | 11.414% |
| Strict-table MAE | 12.051% |
| Strict-table successes / CFD failures | 6 / 3 |
| Strict-table within 5% / 10% | 0/6 / 2/6 |
| Additional truth-unavailable record | 1 |

The three newly valid strict-table comparisons are Jackpot launch 1 EasyMini `5.301%`, Jackpot launch 2 EasyMini `10.043%`, and Jackpot launch 2 Fluctus `14.649%`. DOL, Pelencator launch 1, and Pelencator launch 4 remain hard failures because the distinct Pelencator surface is unavailable.

## Completion decision

The public corpus has reached a `4.530%` measurement-space MAE without flight-specific tuning. Two flights remain outside 10%: Proteus 6 `-10.81%` and Qu8k `-13.40%`. Further table-drag reduction is not accepted: Proteus already closely matches the independent RASAero component drag while its trajectory remains `-12.59%` versus RASAero, and Qu8k is now within `-9.51%` of the RASAero trajectory while RASAero itself is approximately `-4.3%` versus the measured flight. Forcing either measured residual through additional drag decrements would fit simulation/reference differences rather than a demonstrated aerodynamic correlation.

The complete club metric remains blocked by the missing `Drag Curve Pelicantor - Sheet1.csv`. A coefficient-only historical table cannot be converted to the required absolute-force surface without its reference area and CFD dynamic-pressure conditions.

## 2026-08-01 - Flight-input provenance and residual-owner audit

**Status:** Accepted diagnostic/test-harness improvement; no production aerodynamic or airbrake change.

- The public replay now writes `inputs.csv` beside each versioned comparison. Each selected row records the model SHA-256, stage count and strict-table eligibility, flight-configuration ID, launch guide, atmosphere, wind, boundary-layer mode, nozzle diameter, deterministic integration settings, random seed, and any manifest error.
- The internal inventory gate binds DOL, Pelencator launch 1, and Pelencator launch 4 to the exact missing source name `Drag Curve Pelicantor - Sheet1.csv`. The three ORKs retain an embedded airbrake reference area of `0.0145161 m^2` (`22.5 in^2`); the DOL and Huntsville legacy command arguments instead specify `22.3 in^2`. The source value must decide that discrepancy rather than a tuning choice.
- The original Drive placeholder was located at `H:\My Drive\Drag Curve Pelicantor.gsheet`, identifying Google Sheet `1WATYnprY_8jWP8MQmJ79q7YrgUubtzDbBItDg9K0-Xc`. Anonymous CSV export returns HTTP 401, so no surface values have been inferred or substituted while authenticated access is pending.
- Proteus 6 has conflicting RASAero provenance: the saved CDX1 contains `81,498.75 ft` while the public comparison CSV contains `86,799 ft`, a `5,300 ft` difference. The CDX1 also documents manually entered `186.7 lb` launch weight and `111 in` CG. The v82 static table is already slightly lower-drag than the independent RASAero curve across the checked Mach anchors, so the measured apogee residual is not assigned to table drag.
- Qu8k likewise has conflicting saved/public RASAero apogees (`119,683.9 ft` versus `116,254 ft`) and manually entered `314.5 lb` launch weight and `110 in` CG. Its v82 curve is close near Mach 3 but about `0.050 CD` above the independent RASAero total near Mach 1.2. This remains an ownership diagnostic for the transonic terminal-reducer/base-pressure handoff, not permission for a scalar decrement.

Regression evidence:

- Four public comparison-metric tests and four internal inventory/provenance tests pass serially.
- A focused strict replay of Proteus and Qu8k passes with 100% table coverage, zero fallbacks, and normal termination.
- No new production drag correlation was accepted because the residual evidence is presently dominated by reference-version, motor/mass/CG, and measurement provenance.
- `tools/import-pelicantor-cfd.ps1` provides the authenticated-data handoff: it accepts only finite, non-negative absolute force in newtons over at least two Mach and two deployment anchors, rejects coefficient-only or mixed deployment-unit data, requires retracted and deployed anchors, and copies unchanged bytes to the three exact corpus paths with SHA-256 verification.
- The importer validates the restored Jackpot absolute-force fixture (`14` rows, `7 x 2` anchors) and rejects the archived `Air Brakes Drag Coeff.csv` because it has no force/unit anchor. `GenericFunction2DTest` separately verifies percent-to-fraction deployment normalization, selection of force when both `CD` and force columns are present, and coefficient-only rejection.
- `Qu8kTransonicOwnershipDiagnosticTest` locks the v82 coast totals at Mach `1.15/1.20/1.30` (`0.743793000/0.726796371/0.661637959`) beside the independent RASAero anchors (`0.696/0.677/0.641`) without treating those anchors as calibration targets. At Mach 1.20 it proves that `BOATTAIL_PRESSURE_DRAG = 0.091800000`, `BODY_BASE_PRESSURE_DRAG = 0.130560000`, and `FINNED_BODY_BASE_PRESSURE_CLOSURE = 0.077193600` coexist and exactly participate in the owner sum. This narrows the next aerodynamic investigation to ownership/continuity of the existing sourced closures; it does not prove that any term is duplicated.

## 2026-08-01 - Basic Finner source-topology restriction (`physics-aero-v83`)

**Status:** Accepted transferable source-domain correction.

Physics and ownership evidence:

- RASAero II Run Test output (not the Aero Plots schema) exposes Qu8k's Mach 1.20 component row as `CD power-off = 0.677`: body friction `0.142`, nose wave `0.056`, body base `0.159`, fin friction `0.047`, fin wave `0.001`, fin interference `0.052`, fin base `0.000`, other-body/boattail wave `0.076`, and protuberance `0.144`. The nine components sum exactly to the reported total.
- The v82 table's like-for-like owners were already close for body friction (`0.13941`), fin friction (`0.04509`), fin interference (`0.04988`), fin base (`0`), and protuberance (`0.14432`). Its primary body base plus `FINNED_BODY_BASE_PRESSURE_CLOSURE` was `0.13056 + 0.07719 = 0.20775`, versus RASAero's `0.159`; this accounted for essentially the complete `+0.04980 CD` total excess.
- `FinnedBasePressureClosureModel` derives its `0.55` four-fin wake increment from the ADA636861 Basic Finner, whose measured topology is a four-fin flat cylindrical afterbody. No cited source transports that increment across a terminal boattail or an expanding fin-can sleeve. v83 therefore returns zero increment for those geometries while retaining the primary body-base, boattail-pressure, fin-edge, friction, wave, and protuberance owners unchanged. Three-fin and rounded-fin exclusions remain unchanged.
- This is a geometry/source-domain restriction, not a flight identifier, residual fit, scalar decrement, or new empirical function. Generic flat-base Basic Finner geometry still uses the published closure unchanged.

Independent A/B evidence:

| Metric | `physics-aero-v82` | `physics-aero-v83` |
|---|---:|---:|
| Comparable public flights | 24 | 24 |
| Instrument-comparable apogee MAE | 4.530% | 4.335% |
| Geometric-apogee MAE | 6.424% | 6.230% |
| Signed measurement-space bias | -1.734% | -1.539% |
| Flights within 5% | 15/24 | 15/24 |
| Flights within 10% | 22/24 | 22/24 |
| Current-vs-RASAero apogee MAE | 5.426% | 5.389% |
| Runtime coverage / fallbacks / abnormal endings | 100% / 0 / 0 | 100% / 0 / 0 |

- Only two public trajectories change: Qu8k improves from `-13.4047%` to `-10.6632%` (`+3330 ft`), and A-601 Kinsel improves from `-3.2116%` to `-1.2823%` (`+825 ft`). The other 22 supported flights are bit-for-bit unchanged in apogee; none regress.
- The internal replay is unchanged: 6 comparable strict flights, MAE `12.051460%`, 2/6 within 10%, 6 strict successes, and the same 3 explicit missing-Pelencator-CFD failures. No production airbrake behavior changed.

Rejected hypotheses:

- A candidate that transported the Basic Finner increment to a terminal boattail but capped it with the existing RASAero cubic base envelope matched the Qu8k Mach 1.20 body-base component (`0.158512` versus printed `0.159`) and total CD (`0.677555` versus `0.677`). It was rejected because the underlying wake transport remains unsupported and the selected Qu8k trajectory regressed to `-13.3397%`, almost the v82 result.
- Disabling the transported term only below the envelope's Mach-1.15 lower bound was also rejected before replay because it creates a discontinuous Mach handoff. No arbitrary blend width was introduced to hide that source gap.
- Proteus remains unchanged at `-10.8085%`. Its RASAero v1.0.1 and v1.0.2 saved apogees differ by `5300 ft`, and the current strict peak velocity is close to the v1.0.2 prediction. Without exact-version component drag or measured thrust/velocity/coast-deceleration telemetry, assigning the remaining residual to a drag owner would overfit reference-version uncertainty.

Regression and provenance evidence:

- The complete 28-row public replay passed; all 24 supported flights completed with 100% table coverage, zero fallbacks, and normal endings. Multistage IDs 22, 25, 27, and 28 remain explicitly unsupported.
- The complete 10-record internal replay passed its harness and preserved all explicit missing-data states.
- 54 affected ownership, full-regime, Reynolds, runtime, parallelism, RASAero-base, and airbrake-loader tests pass. The Qu8k component diagnostic and the eight-case Basic Finner topology unit suite pass separately.
- Restored `core/src/test/resources/physicsaero/ada636861_basic_finner_cx0.csv` as the exact eight Mach/CX0 columns from the authoritative research CSV (fixture SHA-256 `7F63BF7DD7820DF92D8D6B19421452A0692DC3C418BDD4EF2F95151E8E7A9C82`); the assembled ADA636861 whole-vehicle source gate now passes.
- Two Huntsville benchmark tests still stop before physics evaluation because the archived binary ORK is not bundled at their classpath/worktree fixture path. The archive candidate is `H:\Shared drives\HPRC\0_Archive\2025-2026\1_NASA SL 2025-2026\Airbrakes\Open Rockets\NASA_26_Huntsville_DOL_ROM.ork`, SHA-256 `3292BB970F569FA06E51870F947AB5CF5310CE6BA9BEBEFCE369A23333905777`; tests are not made dependent on a mounted user drive.
- The additional archived `Drag Curve DOL.csv` candidate (SHA-256 `DFA0D5C8378788CA8207995F44317B08C29A52D89A743B3EBCE727CDCF3D798E`) is rejected by the Pelencator importer because deployed drag is lower than retracted drag at Mach 0.55 (`134.892 N` versus `154.19904 N`). It was not imported.

## Completion decision

v83 is the best demonstrated non-overfit public plateau. Both remaining measured-apogee outliers are within 10% of their current saved RASAero trajectories, while the RASAero trajectories themselves carry material version/measurement offsets to flight truth. Further aerodynamic reduction is not justified without the discriminating telemetry listed above. The overall internal acceptance criterion remains unresolved because the authenticated Pelencator absolute-force CFD surface is still unavailable; no coefficient-only or non-monotone substitute is accepted.

## 2026-08-01 - Supplied Pelicantor surface restoration and residual-owner audit

**Status:** Accepted data restoration and diagnostics; no production aerodynamic or airbrake change.

- Validated `C:\Users\adity\Downloads\Air Brakes Drag Coeff - Sheet1.csv` as a 35-row `7 x 5` Mach/deployment surface and restored unchanged bytes to the DOL, Pelencator launch 1, and Pelencator launch 4 corpus directories. Source and all destination SHA-256 values are `87180AB361FB2F632C8BD55F0CE49891BDF667D7D67700733A95A1AA99B0E539`.
- The surface domain is Mach `0.0..0.6` and deployment fraction `0.0..1.0`. Runtime owns the non-CD `Drag` column as absolute force in newtons, ignores the adjacent `CD`, uses bilinear interpolation, and continuously clamps to domain edges. No coefficient conversion or reference-area substitution was performed.
- All nonzero rows satisfy constant `Drag/(CD*Mach^2) = 1390.7438613` within `8.43e-10` relative spread, confirming a coherent single dynamic-pressure/reference-area basis without inferring or converting that basis.
- Internal strict comparison improves from 6 comparable flights at `12.051460%` MAE to 9 at `9.019036%` MAE. All 9 comparable flights finish with 100% strict-table coverage and zero fallbacks; 3/9 are within 5% and 5/9 within 10%. The restored cases are DOL `+4.284607%`, Pelencator 1 `-1.681803%`, and Pelencator 4 `+2.896151%`.
- Pelencator 2 remains `+21.081911%`; it has airbrakes disabled and reuses the launch-1 ORK, so the Pelicantor surface cannot own that residual. Government 1 and Jackpot 2 Fluctus have near-correct peak ascent speed but approximately 10% excess time to apogee, pointing to coast/deployment-state or sensor reconstruction rather than powered drag.
- The full v83 public replay remains 24 comparable flights, `4.334909%` instrument-space MAE, `6.229812%` geometric MAE, 15/24 within 5%, 22/24 within 10%, 100% coverage, zero fallbacks, and zero abnormal endings. Proteus remains `-10.808516%`; Qu8k remains `-10.663206%`.
- Proteus and Qu8k spend only about `1.23%` and `0.29%`, respectively, of aerodynamic energy in transonic/subsonic coast. Their dominant owners are powered and Mach>=1.2 coast pressure/friction. Current static total CD is already below RASAero at Mach 1.2/2/3 for both vehicles, rejecting a further general drag reduction.
- The official Proteus comparison reports a 3201 ft/s measured peak versus 3097 ft/s predicted; current strict is about 3042 ft/s. The Qu8k flight report states a longer, smoother actual motor burn and about 92 s to apogee; current strict is 80.691 s and its saved RASAero reference is 85.930 s. These are motor/reference-trajectory discrepancies, not evidence for a new CD correlation.
- The complete plateau and exact missing measurements are documented in `reports/physics-aero-v83-pelicantor-audit-2026-08-01.md`. The public and internal MAE gates pass, but the all-flight +/-10% and requested all-flight +/-5% bands do not. No flight-specific multiplier, residual fit, arbitrary offset, guessed CFD conversion, force-aero compensation, or airbrake behavior change was accepted.

## 2026-08-01 - Hard +/-3% strict-table abort audit

**Status:** Explicit abort at the v83 non-overfit plateau; no new physics candidate accepted.

- Fresh isolated replays completed the full public and internal suites against the current dirty source snapshot. Public strict results are 24/24 comparable flights completed, 100% table coverage, zero fallbacks, zero abnormal endings, `4.334909%` instrument-space MAE, and only `10/24` within +/-3%. Internal results are 9/9 comparable strict successes, 100% query coverage, zero fallbacks or simulation failures, `9.019036%` MAE, and only `2/9` within +/-3%.
- Per-flight RASAero/measured velocity, time-to-apogee, aerodynamic-energy phase, and friction/pressure/base ownership diagnostics show mixed signs and owners. The failures cannot be closed by one transferable correlation; doing so would require residual fitting, flight-specific tuning, guessed conversions, or compensation among motor/input, sensor, airbrake, and aerodynamic owners.
- The complete abort decision, every per-flight diagnostic, reproducible commands, retained machine-readable outputs, and exact missing evidence are documented in `reports/physics-aero-v83-strict-3pct-abort-2026-08-01.md` and `reports/strict-3pct-evidence-2026-08-01/`.

## 2026-08-01 - Per-flight +/-3% feasibility audit

**Status:** Goal aborted under its explicit no-overfitting clause; no production physics or airbrake change.

- Both comparison harnesses now report a first-class per-row `within_3_percent` boolean and aggregate +/-3% counts. Focused reporting tests pass.
- A clean complete replay reports public 10/24 and internal 2/9 within +/-3%. Public/internal MAE remain `4.334909%` and `9.019036%`; strict coverage is 100%, fallbacks are zero, and endings are normal.
- Fourteen public and seven internal rows fail the hard band. The smallest required shifts range from `+1.1 ft` (Caliber Isp 05 Discovery) to `+9309.1 ft` (Qu8k) publicly and from `-16.61 m` to `-199.95 m` internally. Mixed signs reject any global drag direction.
- Caliber Isp 04 Teams 2 and 3 have identical geometry and I205 motors. Their recorded launch weights are 3.40 and 3.41 lb, temperatures 76 and 77 F, current predictions 3720.827 and 3713.086 ft, and saved RASAero predictions 3876.286 and 3875.957 ft. Their measured +/-3% intervals are disjoint by 23.78 ft. The recorded inputs therefore cannot explain both observations through a transferable geometry correlation.
- Several public barometric failures are inside +/-3% in geometric space but outside after the standard pressure-altitude transfer: Caliber Team 3, Caliber Discovery, Caliber Columbia, and Rabia Short Fin Can. Selecting direct-geometric versus barometric transfer by residual sign would be flight-specific sensor fitting.
- Proteus and Qu8k still lack the measured thrust/acceleration/reference-version evidence required to assign their residuals to aerodynamics. Current peak velocity/TTA evidence points to powered/reference inputs, and strict static CD is already below RASAero at the high-Mach anchors.
- The complete per-flight owner, minimum boundary sensitivity, and exact missing-measurement audit is `reports/physics-aero-v83-per-flight-3-percent-abort-2026-08-01.md`.
