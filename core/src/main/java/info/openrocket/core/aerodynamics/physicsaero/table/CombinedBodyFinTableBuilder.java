package info.openrocket.core.aerodynamics.physicsaero.table;

import java.util.*;
import info.openrocket.core.aerodynamics.physicsaero.api.*;
import info.openrocket.core.aerodynamics.physicsaero.body.*;
import info.openrocket.core.aerodynamics.physicsaero.diagnostics.*;
import info.openrocket.core.aerodynamics.physicsaero.fin.*;
import info.openrocket.core.aerodynamics.physicsaero.flow.*;
import info.openrocket.core.aerodynamics.physicsaero.force.*;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.ThermodynamicModel;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroComponent;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;
import info.openrocket.core.aerodynamics.physicsaero.interaction.BodyFinInterferenceSolver;
import info.openrocket.core.aerodynamics.physicsaero.boundarylayer.*;
import info.openrocket.core.aerodynamics.physicsaero.coupling.FinnedBasePressureClosureModel;
import info.openrocket.core.aerodynamics.physicsaero.coupling.FinnedBodyBasePressureInteractionModel;
import info.openrocket.core.aerodynamics.physicsaero.coupling.OneWayViscousCoupling;
import info.openrocket.core.aerodynamics.physicsaero.flow.ShockEvent;
import info.openrocket.core.aerodynamics.physicsaero.thermal.WallThermalBoundary;
import info.openrocket.core.aerodynamics.physicsaero.transition.TransitionMode;
import info.openrocket.core.util.Coordinate;

/** Populates direct Mach/alpha/beta six-axis cells from Phase 2 body drag plus Phase 3 individual fins. */
public final class CombinedBodyFinTableBuilder {
	private static final double SUPERSONIC_INCIDENCE_LIMIT_RAD = Math.toRadians(15);
	private final boolean pnkEnabled;
	private final boolean phaseFiveEnabled;
	public CombinedBodyFinTableBuilder() { this(false, false); }
	/** PNK is opt-in so the isolated-fin validation gate remains enforceable. */
	public CombinedBodyFinTableBuilder(boolean pnkEnabled) { this(pnkEnabled, false); }
	/** Phase-5 coupling is explicit so Phase-1-to-3 pressure validation remains independently reproducible. */
	public CombinedBodyFinTableBuilder(boolean pnkEnabled, boolean phaseFiveEnabled) { this.pnkEnabled = pnkEnabled; this.phaseFiveEnabled = phaseFiveEnabled; }
	public AerodynamicTable build(AeroGeometry geometry, double[] machAxis, double[] alphaAxis, double[] betaAxis,
			AtmosphereState atmosphere, ThermodynamicModel model, TableMetadata metadata) {
		TableAxes axes = new TableAxes(machAxis, alphaAxis, betaAxis); List<TableCell> cells = new ArrayList<>();
		Map<Double, AxisymmetricBodyResult> bodies = new HashMap<>();
		for (double mach : machAxis) for (double alpha : alphaAxis) for (double beta : betaAxis) {
			AxisymmetricBodyResult body = bodies.computeIfAbsent(mach, value -> new AxisymmetricBodySolver().evaluate(geometry,
					FlowCondition.fromAngles(value, 0, 0, atmosphere, model, false, geometry.geometryHash())));
			double incidence = Math.atan(Math.hypot(Math.tan(alpha), Math.tan(beta)));
			boolean incidenceBoundaryHold = incidence > SUPERSONIC_INCIDENCE_LIMIT_RAD;
			double evaluationScale = incidenceBoundaryHold
					? SUPERSONIC_INCIDENCE_LIMIT_RAD / incidence : 1;
			FlowCondition flow = FlowCondition.fromAngles(mach, alpha * evaluationScale,
					beta * evaluationScale, atmosphere, model, false, geometry.geometryHash());
			FinResult fins = new SupersonicFinSolver().evaluate(geometry, flow, body.edgeStateHistory()); ContributionLedger ledger = new ContributionLedger();
			body.contributions().forEach(ledger::add); fins.contributions().forEach(ledger::add);
			new BodyIncidenceLoadModel().evaluate(geometry, flow).forEach(ledger::add);
			boolean finnedBodyBaseInteraction = addFinnedBodyBaseInteraction(ledger, geometry, flow);
			boolean rasaeroBaseEnvelope = applyRasaeroBaseEnvelope(ledger, geometry, flow);
			if (pnkEnabled) new BodyFinInterferenceSolver().evaluate(geometry, mach, fins.contributions()).forEach(ledger::add);
			ReferenceState reference = new ReferenceState(flow.dynamicPressurePa(), geometry.references().referenceAreaM2(),
					geometry.references().referenceLengthM(), geometry.references().momentOriginM());
			EngineeringSkinFrictionCorrelation.Result engineeringFriction =
					new EngineeringSkinFrictionCorrelation().evaluate(geometry, flow);
			boolean engineeringFinFriction = addEngineeringFinFriction(
					ledger, geometry, reference, engineeringFriction);
			String bodyComponentId = body.segments().isEmpty()
					? "axisymmetric-body" : body.segments().get(0).componentId();
			List<String> phaseFiveMessages = new ArrayList<>(); boolean phaseFiveInvalid = false;
			boolean prescribedBodyTransition = false, prescribedBodyWallTemperature = false;
			boolean fullyTurbulentBody = forceFullyTurbulentBody(geometry);
			boolean fullyTurbulentEngineeringBody = false;
			if (phaseFiveEnabled) {
				try {
					double roughness = geometry.components().stream().filter(c -> c.axisymmetricProfile() != null).mapToDouble(c -> c.roughnessM()).max().orElse(0);
					OptionalDouble transitionReynolds = prescribedTransitionReynolds(geometry, flow.mach());
					WallThermalBoundary wallMode = bodyWallMode(geometry);
					prescribedBodyTransition = transitionReynolds.isPresent() && !fullyTurbulentBody;
					prescribedBodyWallTemperature = wallMode != WallThermalBoundary.ADIABATIC;
					if (fullyTurbulentBody && wallMode == WallThermalBoundary.ADIABATIC) {
						addFullyTurbulentEngineeringBodyFriction(
								ledger, geometry, reference, engineeringFriction, bodyComponentId);
						fullyTurbulentEngineeringBody = true;
						phaseFiveMessages.add("FULLY_TURBULENT_AVERAGE_FLAT_PLATE_BODY_SKIN_FRICTION:"
								+ EngineeringSkinFrictionCorrelation.METHOD_ID);
						if (transitionReynolds.isPresent()) {
							phaseFiveMessages.add("FULLY_TURBULENT_OVERRIDES_PRESCRIBED_TRANSITION");
						}
					} else {
						BoundaryLayerConfiguration defaults = BoundaryLayerConfiguration.defaults();
						BoundaryLayerConfiguration blConfig = new BoundaryLayerConfiguration(
								fullyTurbulentBody ? TransitionMode.FULLY_TURBULENT
										: prescribedBodyTransition ? TransitionMode.USER_TRIPPED
												: defaults.transitionMode(),
								defaults.turbulencePercent(), defaults.transitionBlendLengthM(),
								defaults.intermittencyExponent(), wallMode, defaults.gamma(), defaults.prandtl(),
								defaults.minimumVelocityMS(), defaults.totalStateRelativeTolerance(),
								defaults.skinFrictionCompressibilityMode());
						SurfaceTrackBuilder trackBuilder = new SurfaceTrackBuilder();
						SurfaceTrack track = trackBuilder.fromAxisymmetricHistory(bodyComponentId,
								"axisymmetric-body-stack", body.edgeStateHistory(), roughness, blConfig);
						if (prescribedBodyWallTemperature) {
							track = trackBuilder.withPrescribedWallTemperature(track,
									x -> prescribedWallTemperatureK(geometry, x));
							phaseFiveMessages.add("PRESCRIBED_BODY_WALL_TEMPERATURE:" + wallMode);
						}
						if (transitionReynolds.isPresent() && !fullyTurbulentBody) {
							track = trackBuilder.withForcedTransitionReynolds(track, transitionReynolds.getAsDouble(),
									flow.atmosphere().densityKgM3(), flow.velocityBody().length(),
									flow.atmosphere().dynamicViscosityPaS());
							phaseFiveMessages.add("PRESCRIBED_BODY_TRANSITION_REYNOLDS:"
									+ transitionReynolds.getAsDouble());
						} else if (transitionReynolds.isPresent()) {
							phaseFiveMessages.add("FULLY_TURBULENT_OVERRIDES_PRESCRIBED_TRANSITION");
						}
						BoundaryLayerResult bl = new BoundaryLayerMarcher().march(
								track, blConfig, reference.momentOriginM());
						List<ShockEvent> shocks = body.edgeStateHistory().events().stream()
								.filter(ShockEvent.class::isInstance).map(ShockEvent.class::cast).toList();
						OneWayViscousCoupling.Result coupled = new OneWayViscousCoupling().apply(
								bl.history(), shocks,
								geometry.references().maximumBodyDiameterM(),
								geometry.references().vehicleLengthM(), reference,
								reference.momentOriginM(),
								flow.thermodynamics().gamma(flow.atmosphere().temperatureK()), .72);
						coupled.contributions().forEach(ledger::add);
						for (var interaction : coupled.interactions()) {
							phaseFiveMessages.add("SWBLI:" + interaction.interactionId() + ':'
									+ interaction.classification() + ":risk=" + interaction.thermalRisk()
									+ ":confidence=" + interaction.confidence());
						}
						for (var wake : coupled.wakes()) {
							phaseFiveMessages.add("WAKE:" + wake.originComponent() + ':'
									+ wake.startM() + ':' + wake.endM() + ':' + wake.validity());
						}
						phaseFiveMessages.addAll(coupled.diagnostics());
					}
				} catch (BoundaryLayerException | IllegalArgumentException ex) {
					phaseFiveInvalid = true;
					phaseFiveMessages.add("PHASE5_INVALID:" + ex.getClass().getSimpleName() + ':' + ex.getMessage());
					addEngineeringBodyFriction(ledger, geometry, reference, engineeringFriction, bodyComponentId);
					phaseFiveMessages.add("ENGINEERING_BODY_SKIN_FRICTION_FALLBACK:"
							+ EngineeringSkinFrictionCorrelation.METHOD_ID);
				}
			}
			List<ForceContribution> all = ledger.entries();
			boolean pressureFallback = all.stream().anyMatch(c -> c.methodId().value().equals(DatcomFinLiftModel.METHOD_ID));
			boolean bodyFrictionFallback = phaseFiveEnabled && phaseFiveInvalid;
			boolean fallback = pressureFallback || bodyFrictionFallback;
			List<String> validity = new ArrayList<>(); validity.add("INDIVIDUAL_FIN_3D");
			if (incidenceBoundaryHold) {
				validity.add("SUPERSONIC_INCIDENCE_15DEG_BOUNDARY_HOLD");
				validity.add("HIGH_INCIDENCE_CELL_RESERVED_FOR_SUBSONIC_JORGENSEN_DOMAIN");
			}
			if (alpha != 0 || beta != 0) validity.add("BODY_NONZERO_INCIDENCE_OWNED");
			validity.add(pnkEnabled ? "PNK_ENABLED_AFTER_ISOLATED_VALIDATION_GATE" : "PNK_DISABLED_ISOLATED_VALIDATION_GATE");
			if (engineeringFinFriction) validity.add("ENGINEERING_FIN_SKIN_FRICTION");
			if (finnedBodyBaseInteraction) validity.add("FINNED_BODY_BASE_PRESSURE_INTERACTION");
			if (rasaeroBaseEnvelope) validity.add("RASAERO_CUBIC_BASE_DRAG_ENVELOPE");
			if (phaseFiveEnabled) {
				validity.add(phaseFiveInvalid ? "PHASE5_VISCOUS_COUPLING_INVALID"
						: fullyTurbulentEngineeringBody
								? "FULLY_TURBULENT_ENGINEERING_BODY_SKIN_FRICTION"
								: "PHASE5_ONE_WAY_VISCOUS_COUPLING");
				validity.add(phaseFiveInvalid ? "ENGINEERING_BODY_SKIN_FRICTION_FALLBACK"
						: fullyTurbulentEngineeringBody
								? "AVERAGE_FLAT_PLATE_BODY_SKIN_FRICTION"
								: "RESOLVED_BODY_SKIN_FRICTION");
				if (!phaseFiveInvalid && prescribedBodyTransition) validity.add("PRESCRIBED_BODY_TRANSITION");
				if (!phaseFiveInvalid && fullyTurbulentBody) validity.add("FULLY_TURBULENT_BODY_BOUNDARY_LAYER");
				if (!phaseFiveInvalid && prescribedBodyWallTemperature) validity.add("PRESCRIBED_BODY_WALL_TEMPERATURE");
			}
			String fallbackMethod = bodyFrictionFallback ? EngineeringSkinFrictionCorrelation.METHOD_ID
					: pressureFallback ? DatcomFinLiftModel.METHOD_ID : null;
			String fallbackReason = bodyFrictionFallback ? "PHASE5_VISCOUS_COUPLING_INVALID"
					: pressureFallback ? "LOCAL_PRESSURE_INVALID" : null;
			Set<FailureReason> reasonCodes = new LinkedHashSet<>();
			if (!pnkEnabled) reasonCodes.add(FailureReason.PNK_INTERFERENCE_DISABLED);
			if (engineeringFinFriction) reasonCodes.add(FailureReason.ENGINEERING_SKIN_FRICTION);
			if (bodyFrictionFallback) reasonCodes.add(FailureReason.VISCOUS_COUPLING_FALLBACK);
			if (prescribedBodyTransition) reasonCodes.add(FailureReason.PRESCRIBED_TRANSITION);
			if (prescribedBodyWallTemperature) reasonCodes.add(FailureReason.PRESCRIBED_WALL_TEMPERATURE);
			if (pressureFallback || fins.contributions().stream()
					.anyMatch(value -> value.methodId().value().contains("PROFILE_DRAG_FALLBACK"))) {
				reasonCodes.add(FailureReason.FIN_PROFILE_FALLBACK);
			}
			cells.add(new TableCell(CoefficientAssembler.assemble(ledger, reference), grouped(all, reference, true), grouped(all, reference, false),
					all.stream().map(c -> c.methodId().value()).distinct().sorted().toList(), new double[] {.75,.75,.75,.7,.7,.7},
					new double[] {.2,.2,.2,.25,.25,.25}, validity, reference,
					new CellDiagnostics(phaseFiveInvalid ? Set.of(DiagnosticFlag.DIRECT_GENERATION, DiagnosticFlag.SEPARATION, DiagnosticFlag.FALLBACK_USED) :
							fallback ? Set.of(DiagnosticFlag.DIRECT_GENERATION, DiagnosticFlag.FALLBACK_USED) : Set.of(DiagnosticFlag.DIRECT_GENERATION),
							reasonCodes, fallback, fallbackMethod, fallbackReason, phaseFiveMessages), true));
		}
		return new AerodynamicTable(axes, cells, metadata);
	}

	private static boolean addFinnedBodyBaseInteraction(ContributionLedger ledger,
			AeroGeometry geometry, FlowCondition flow) {
		double bodyBaseArea = geometry.references().exposedBaseAreaM2();
		if (!(bodyBaseArea > 0)) return false;
		double terminalBodyEnd = geometry.components().stream()
				.filter(component ->
						component.axisymmetricProfile() != null)
				.mapToDouble(AeroComponent::axialEndM).max()
				.orElseThrow(() -> new IllegalArgumentException(
						"missing terminal axisymmetric body"));
		List<FinnedBodyBasePressureInteractionModel.FinSet> finSets = new ArrayList<>();
		for (AeroComponent component : geometry.components()) {
			if (component.finGeometry() == null) continue;
			double meanChord = component.finGeometry().planformAreaM2()
					/ component.finGeometry().spanM();
			double thicknessRatio = component.localReferences().getOrDefault("thicknessRatio",
					component.localReferences().getOrDefault("thicknessM", 0.0) / meanChord);
			double baseGap = Math.abs(terminalBodyEnd
					- component.axialEndM());
			finSets.add(new FinnedBodyBasePressureInteractionModel.FinSet(
					component.finGeometry().count(), component.finGeometry().planformAreaM2(),
					thicknessRatio, baseGap));
		}
		if (finSets.isEmpty()) return false;
		double primaryCp = new HartTn3393SupersonicBasePressureCorrelation()
				.basePressureCoefficient(flow.mach(),
						flow.thermodynamics().gamma(flow.atmosphere().temperatureK()));
		FinnedBodyBasePressureInteractionModel.Result interaction =
				new FinnedBodyBasePressureInteractionModel().evaluate(
						new FinnedBodyBasePressureInteractionModel.BaseState(flow.mach(),
								flow.powered(), primaryCp, bodyBaseArea,
								geometry.references().maximumBodyDiameterM()), finSets);
		if (!(interaction.pressureCoefficientIncrement() < 0)) return false;

		List<ForceContribution> primaries = ledger.entries().stream()
				.filter(contribution -> contribution.owner().term() == PhysicalTerm.BASE_PRESSURE_DRAG
						|| contribution.owner().term() == PhysicalTerm.FIN_BASE_PRESSURE_DRAG)
				.toList();
		for (ForceContribution primary : primaries) {
			double pressureArea = primary.forceBodyN().x
					/ (flow.dynamicPressurePa() * -primaryCp);
			if (!(pressureArea > 0)) continue;
			double forceIncrement = -interaction.pressureCoefficientIncrement()
					* flow.dynamicPressurePa() * pressureArea;
			List<String> flags = new ArrayList<>(interaction.validityFlags());
			flags.add("ACTUAL_PRESSURE_LOADED_BASE_AREA");
			ledger.add(new ForceContribution(primary.componentId(),
					new PhysicalOwner(primary.owner().term(), OwnershipMode.MODIFIES,
							primary.regionId(), primary.methodId()),
					new MethodId(FinnedBodyBasePressureInteractionModel.METHOD_ID),
					new Coordinate(forceIncrement, 0, 0), new Coordinate(),
					primary.applicationPointM(), primary.regionId(), flags,
					interaction.confidence(), interaction.uncertainty(), null));
		}

		double currentPressureMagnitude = -primaryCp
				- interaction.pressureCoefficientIncrement();
		FinnedBasePressureClosureModel.Result closure =
				new FinnedBasePressureClosureModel().evaluate(
						geometry, flow.mach(), currentPressureMagnitude);
		if (!(closure.pressureMagnitudeIncrement() > 0)) return true;
		for (ForceContribution primary : primaries) {
			double pressureArea = primary.forceBodyN().x
					/ (flow.dynamicPressurePa() * -primaryCp);
			if (!(pressureArea > 0)) continue;
			double forceIncrement = closure.pressureMagnitudeIncrement()
					* flow.dynamicPressurePa() * pressureArea;
			List<String> flags = new ArrayList<>(closure.validityFlags());
			flags.add("EXISTING_A53D02_INCREMENT_SUBTRACTED_FROM_TARGET");
			ledger.add(new ForceContribution(primary.componentId(),
					new PhysicalOwner(primary.owner().term(), OwnershipMode.MODIFIES,
							primary.regionId(), primary.methodId()),
					new MethodId(FinnedBasePressureClosureModel.METHOD_ID),
					new Coordinate(forceIncrement, 0, 0), new Coordinate(),
					primary.applicationPointM(), primary.regionId(), flags,
					closure.confidence(), closure.uncertainty(), null));
		}
		return true;
	}

	private static boolean applyRasaeroBaseEnvelope(ContributionLedger ledger,
			AeroGeometry geometry, FlowCondition flow) {
		double targetCd = new RasaeroSupersonicBaseDragEnvelope()
				.dragCoefficient(geometry, flow.mach());
		if (!Double.isFinite(targetCd)) return false;
		List<ForceContribution> base = ledger.entries().stream()
				.filter(contribution -> contribution.owner().term()
						== PhysicalTerm.BASE_PRESSURE_DRAG)
				.toList();
		if (base.isEmpty()) return false;
		double currentForce = base.stream()
				.mapToDouble(contribution -> contribution.forceBodyN().x).sum();
		double targetForce = targetCd * flow.dynamicPressurePa()
				* geometry.references().referenceAreaM2();
		if (!(currentForce > targetForce) || !(targetForce >= 0)) return false;
		ForceContribution primary = base.get(0);
		ledger.add(new ForceContribution(primary.componentId(),
				new PhysicalOwner(PhysicalTerm.BASE_PRESSURE_DRAG,
						OwnershipMode.MODIFIES, primary.regionId(), primary.methodId()),
				new MethodId(RasaeroSupersonicBaseDragEnvelope.METHOD_ID),
				new Coordinate(targetForce - currentForce, 0, 0), new Coordinate(),
				primary.applicationPointM(), primary.regionId(),
				List.of("EMPIRICAL_SUPERSONIC_BASE_DRAG_UPPER_ENVELOPE",
						"CUBIC_TERMINAL_DIAMETER_RECOVERY"),
				0.35, 0.30, null));
		return true;
	}

	private static OptionalDouble prescribedTransitionReynolds(AeroGeometry geometry, double mach) {
		double transitionReynolds = Double.POSITIVE_INFINITY;
		for (AeroComponent component : geometry.components()) if (component.axisymmetricProfile() != null
				&& component.localReferences().containsKey("transitionReynolds")) {
			double value = component.localReferences().get("transitionReynolds");
			double minimumMach = component.localReferences().getOrDefault("transitionMachMinimum", 0.0);
			if (!(value > 0) || !Double.isFinite(value) || minimumMach < 0 || !Double.isFinite(minimumMach)) {
				throw new IllegalArgumentException("invalid prescribed body transition");
			}
			if (mach >= minimumMach) transitionReynolds = Math.min(transitionReynolds, value);
		}
		return Double.isFinite(transitionReynolds)
				? OptionalDouble.of(transitionReynolds) : OptionalDouble.empty();
	}

	private static boolean forceFullyTurbulentBody(AeroGeometry geometry) {
		boolean forced = false;
		for (AeroComponent component : geometry.components()) {
			if (component.axisymmetricProfile() == null) continue;
			double value = component.localReferences().getOrDefault("forceFullyTurbulent", 0.0);
			if (value != 0.0 && value != 1.0) {
				throw new IllegalArgumentException("invalid fully turbulent boundary-layer metadata");
			}
			forced |= value == 1.0;
		}
		return forced;
	}

	private static WallThermalBoundary bodyWallMode(AeroGeometry geometry) {
		Set<WallThermalBoundary> modes = new LinkedHashSet<>();
		for (AeroComponent component : geometry.components()) if (component.axisymmetricProfile() != null) {
			try {
				modes.add(WallThermalBoundary.valueOf(component.wallTemperatureModelId().trim().toUpperCase(Locale.ROOT)));
			} catch (RuntimeException ex) {
				throw new IllegalArgumentException("unsupported body wall-temperature model: "
						+ component.wallTemperatureModelId(), ex);
			}
		}
		if (modes.size() != 1) throw new IllegalArgumentException("mixed body wall-temperature models are unsupported");
		return modes.iterator().next();
	}

	private static double prescribedWallTemperatureK(AeroGeometry geometry, double xM) {
		AeroComponent nearest = geometry.components().stream()
				.filter(component -> component.axisymmetricProfile() != null)
				.min(Comparator.comparingDouble(component -> axialDistance(component, xM)))
				.orElseThrow(() -> new IllegalArgumentException("missing axisymmetric body"));
		double temperature = nearest.localReferences().getOrDefault("wallTemperatureK", Double.NaN);
		if (!(temperature > 0) || !Double.isFinite(temperature)) {
			throw new IllegalArgumentException("missing prescribed wall temperature for " + nearest.id());
		}
		return temperature;
	}

	private static double axialDistance(AeroComponent component, double xM) {
		if (xM < component.axialStartM()) return component.axialStartM() - xM;
		if (xM > component.axialEndM()) return xM - component.axialEndM();
		return 0;
	}

	private static boolean addEngineeringFinFriction(ContributionLedger ledger, AeroGeometry geometry,
			ReferenceState reference, EngineeringSkinFrictionCorrelation.Result friction) {
		List<AeroComponent> finSets = geometry.components().stream()
				.filter(component -> component.finGeometry() != null && component.wettedAreaM2() > 0).toList();
		double totalWettedArea = finSets.stream().mapToDouble(AeroComponent::wettedAreaM2).sum();
		if (!(friction.finCd() > 0) || !(totalWettedArea > 0)) return false;
		for (var component : finSets) {
			double areaFraction = component.wettedAreaM2() / totalWettedArea;
			double cd = friction.finCd() * areaFraction;
			double force = reference.dynamicPressurePa() * reference.referenceAreaM2() * cd;
			String region = component.id() + ":engineering-fin-wetted-surface";
			ledger.add(new ForceContribution(component.id(),
					new PhysicalOwner(PhysicalTerm.SKIN_FRICTION, OwnershipMode.REPLACES, region, null),
					new MethodId(EngineeringSkinFrictionCorrelation.METHOD_ID), new Coordinate(force, 0, 0),
					new Coordinate(), new Coordinate(0.5 * (component.axialStartM() + component.axialEndM()), 0, 0),
					region, List.of("ENGINEERING_FIN_SKIN_FRICTION", "ACTUAL_FIN_WETTED_AREA", "AXIAL_DRAG_ONLY"),
					0.65, 0.25, null));
			double interferenceCd = friction.finInterferenceCd() * areaFraction;
			if (interferenceCd > 0) {
				double interferenceForce = reference.dynamicPressurePa()
						* reference.referenceAreaM2() * interferenceCd;
				ledger.add(new ForceContribution(component.id(),
						new PhysicalOwner(PhysicalTerm.BODY_FIN_INTERFERENCE_DRAG,
								OwnershipMode.MODIFIES, region,
								new MethodId(EngineeringSkinFrictionCorrelation.METHOD_ID)),
						new MethodId(EngineeringSkinFrictionCorrelation.FIN_INTERFERENCE_METHOD_ID),
						new Coordinate(interferenceForce, 0, 0), new Coordinate(),
						new Coordinate(0.5 * (component.axialStartM()
								+ component.axialEndM()), 0, 0),
						region,
						List.of("RASAERO_VIRTUAL_FIN_WETTED_AREA",
								"FIN_BODY_INTERFERENCE_SKIN_FRICTION",
								"AXIAL_DRAG_ONLY"),
						0.75, 0.20, null));
			}
		}
		return true;
	}

	private static void addEngineeringBodyFriction(ContributionLedger ledger, AeroGeometry geometry,
			ReferenceState reference, EngineeringSkinFrictionCorrelation.Result friction, String componentId) {
		addEngineeringBodyFriction(ledger, geometry, reference, friction, componentId,
				List.of("ENGINEERING_BODY_SKIN_FRICTION_FALLBACK", "PHASE5_VISCOUS_COUPLING_INVALID",
						"ACTUAL_BODY_WETTED_AREA", "AXIAL_DRAG_ONLY"),
				0.55, 0.35, "PHASE5_VISCOUS_COUPLING_INVALID");
	}

	private static void addFullyTurbulentEngineeringBodyFriction(ContributionLedger ledger,
			AeroGeometry geometry, ReferenceState reference,
			EngineeringSkinFrictionCorrelation.Result friction, String componentId) {
		addEngineeringBodyFriction(ledger, geometry, reference, friction, componentId,
				List.of("FULLY_TURBULENT_AVERAGE_FLAT_PLATE",
						"ACTUAL_BODY_WETTED_AREA", "AXIAL_DRAG_ONLY"),
				0.75, 0.20, null);
	}

	private static void addEngineeringBodyFriction(ContributionLedger ledger, AeroGeometry geometry,
			ReferenceState reference, EngineeringSkinFrictionCorrelation.Result friction, String componentId,
			List<String> validityFlags, double confidence, double uncertainty,
			String fallbackReason) {
		if (!(friction.bodyCd() > 0)) return;
		double wettedArea = 0;
		double weightedX = 0;
		for (var component : geometry.components()) if (component.axisymmetricProfile() != null) {
			wettedArea += component.wettedAreaM2();
			weightedX += component.wettedAreaM2() * 0.5 * (component.axialStartM() + component.axialEndM());
		}
		double applicationX = wettedArea > 0 ? weightedX / wettedArea
				: 0.5 * geometry.references().vehicleLengthM();
		double force = reference.dynamicPressurePa() * reference.referenceAreaM2() * friction.bodyCd();
		String region = "axisymmetric-body-stack";
		ledger.add(new ForceContribution(componentId,
				new PhysicalOwner(PhysicalTerm.SKIN_FRICTION, OwnershipMode.REPLACES, region, null),
				new MethodId(EngineeringSkinFrictionCorrelation.METHOD_ID), new Coordinate(force, 0, 0),
				new Coordinate(), new Coordinate(applicationX, 0, 0), region,
				validityFlags, confidence, uncertainty, fallbackReason));
	}

	private static Map<String, AerodynamicCoefficients> grouped(List<ForceContribution> values, ReferenceState reference, boolean component) {
		Map<String, List<ForceContribution>> groups = new TreeMap<>();
		for (ForceContribution c : values) groups.computeIfAbsent(component ? c.componentId() : c.owner().term().name(), key -> new ArrayList<>()).add(c);
		Map<String, AerodynamicCoefficients> result = new TreeMap<>();
		groups.forEach((key, contributions) -> {
			ContributionLedger ledger = new ContributionLedger();
			List<ForceContribution> ordered = contributions.stream()
					.sorted(Comparator.comparingInt(value -> ownershipRank(value.owner().mode())))
					.toList();
			int groupedIndex = 0;
			for (ForceContribution contribution : ordered) {
				if (!component && contribution.owner().mode() != info.openrocket.core.aerodynamics.physicsaero.api.OwnershipMode.REPLACES
						&& contribution.owner().mode() != info.openrocket.core.aerodynamics.physicsaero.api.OwnershipMode.DIAGNOSTIC_ONLY) {
					String groupedRegion = contribution.regionId() + ":grouped-modifier:" + groupedIndex++;
					var owner = new info.openrocket.core.aerodynamics.physicsaero.api.PhysicalOwner(contribution.owner().term(),
							info.openrocket.core.aerodynamics.physicsaero.api.OwnershipMode.REPLACES, groupedRegion, null);
					ledger.add(new ForceContribution(contribution.componentId(), owner, contribution.methodId(), contribution.forceBodyN(),
							contribution.intrinsicMomentBodyNm(), contribution.applicationPointM(), groupedRegion,
							contribution.validityFlags(), contribution.confidence(), contribution.uncertainty(), contribution.fallbackReason()));
				} else ledger.add(contribution);
			}
			result.put(key, CoefficientAssembler.assemble(ledger, reference));
		});
		return result;
	}

	private static int ownershipRank(OwnershipMode mode) {
		return switch (mode) {
			case REPLACES -> 0;
			case MODIFIES, RESIDUAL -> 1;
			case DIAGNOSTIC_ONLY -> 2;
		};
	}
}
