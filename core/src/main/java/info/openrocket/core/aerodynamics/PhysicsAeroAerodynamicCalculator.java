package info.openrocket.core.aerodynamics;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import info.openrocket.core.aerodynamics.physicsaero.diagnostics.FailureReason;
import info.openrocket.core.aerodynamics.physicsaero.force.AerodynamicCoefficients;
import info.openrocket.core.aerodynamics.physicsaero.geometry.GeometryClassifier;
import info.openrocket.core.aerodynamics.physicsaero.interpolation.QueryResult;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroFailureOccurrence;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroMode;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroQueryException;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroQueryException.QueryCoordinates;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroRuntimeFlag;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroRuntimeReport;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroTableCalculator;
import info.openrocket.core.aerodynamics.physicsaero.table.AerodynamicTable;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.util.Coordinate;
import info.openrocket.core.util.CoordinateIF;
import info.openrocket.core.util.ModID;

/** Opt-in adapter from a validated offline table to OpenRocket simulation forces. */
public final class PhysicsAeroAerodynamicCalculator extends AbstractAerodynamicCalculator
		implements SimulationAwareAerodynamicCalculator {
	private static final double CP_SLOPE_THRESHOLD = 1.0e-8;
	private static final double LOW_CONFIDENCE_THRESHOLD = 0.5;
	private static final double LOW_DENSITY_BOUNDARY_HOLD_RATIO = 0.005;
	private static final double MAXIMUM_HELD_SOURCE_BOUNDARY_RATIO = 0.01;
	private static final String LOW_DENSITY_BOUNDARY_HOLD_METHOD =
			"LOW_DENSITY_REYNOLDS_SOURCE_BOUNDARY_HOLD_V1";
	private static final Set<FailureReason> HYBRID_QUERY_FAILURES = EnumSet.of(
			FailureReason.OUT_OF_DOMAIN,
			FailureReason.INTERPOLATION_EVENT_BOUNDARY,
			FailureReason.POWERED_STATE_UNAVAILABLE,
			FailureReason.REYNOLDS_REBUILD_REQUIRED,
			FailureReason.LOW_CONFIDENCE_CELL);

	private final AerodynamicTable table;
	private final PhysicsAeroTableCalculator lookup;
	private final AerodynamicCalculator fallback;
	private final PhysicsAeroMode mode;
	private final String contentHash;
	private final AtomicLong totalQueries = new AtomicLong();
	private final AtomicLong successfulQueries = new AtomicLong();
	private final AtomicLong interpolatedQueries = new AtomicLong();
	private final AtomicLong reynoldsCorrectedQueries = new AtomicLong();
	private final AtomicLong fallbackCount = new AtomicLong();
	private final Map<FailureReason, AtomicLong> failureCounts = new ConcurrentHashMap<>();
	private final Map<FailureReason, PhysicsAeroFailureOccurrence> firstOccurrences = new ConcurrentHashMap<>();
	private final Set<PhysicsAeroRuntimeFlag> runtimeFlags = ConcurrentHashMap.newKeySet();
	private volatile double lowestConfidence = Double.NaN;
	private volatile SimulationAerodynamicsContext context;
	private final ModID modId = new ModID();

	public PhysicsAeroAerodynamicCalculator(AerodynamicTable table, String geometryHash,
			String settingsHash, String contentHash, PhysicsAeroMode mode, AerodynamicCalculator fallback) {
		this.table = java.util.Objects.requireNonNull(table, "table");
		this.lookup = new PhysicsAeroTableCalculator(table, geometryHash, settingsHash);
		this.mode = mode == null ? PhysicsAeroMode.STRICT : mode;
		if (this.mode == PhysicsAeroMode.OFF) throw new IllegalArgumentException("physics-aero adapter cannot use OFF mode");
		if (isHybrid(this.mode) && fallback == null) {
			throw new IllegalArgumentException("diagnostic hybrid requires an explicit fallback calculator");
		}
		if (this.mode == PhysicsAeroMode.STRICT && fallback != null) {
			throw new IllegalArgumentException("strict physics-aero must not have a fallback calculator");
		}
		this.fallback = fallback;
		this.contentHash = contentHash == null ? "" : contentHash;
		this.context = SimulationAerodynamicsContext.coast(0, 101325);
		runtimeFlags.add(PhysicsAeroRuntimeFlag.SINGLE_STAGE_ONLY);
		runtimeFlags.add(PhysicsAeroRuntimeFlag.RATE_DERIVATIVES_ENGINEERING_ONLY);
		if (!"FLIGHT_VALIDATED".equals(table.metadata().validationStatus())) {
			runtimeFlags.add(PhysicsAeroRuntimeFlag.FLIGHT_VALIDATION_PENDING);
		}
	}

	/** Source-compatible constructor for tests which do not persist content identity. */
	public PhysicsAeroAerodynamicCalculator(AerodynamicTable table, String geometryHash,
			String settingsHash, PhysicsAeroMode mode, AerodynamicCalculator fallback) {
		this(table, geometryHash, settingsHash, "", mode, fallback);
	}

	public boolean isEnabled() { return true; }
	public long getFallbackCount() { return fallbackCount.get(); }
	public AerodynamicTable getTable() { return table; }

	public PhysicsAeroRuntimeReport getRuntimeReport() {
		Map<FailureReason, Long> counts = new EnumMap<>(FailureReason.class);
		failureCounts.forEach((reason, count) -> counts.put(reason, count.get()));
		return new PhysicsAeroRuntimeReport(true, table.metadata().schemaVersion(),
				table.metadata().geometryHash(), table.metadata().settingsHash(), contentHash,
				table.metadata().validationStatus(), totalQueries.get(), successfulQueries.get(),
				interpolatedQueries.get(), reynoldsCorrectedQueries.get(), fallbackCount.get(),
				counts, lowestConfidence, runtimeFlags, firstOccurrences);
	}

	@Override
	public void setSimulationAerodynamicsContext(SimulationAerodynamicsContext value) {
		context = java.util.Objects.requireNonNull(value, "context");
	}

	@Override
	public double getStallAngle() {
		double[] axis = table.axes().alphaRad();
		return Math.max(Math.abs(axis[0]), Math.abs(axis[axis.length - 1]));
	}

	@Override
	public CoordinateIF getCP(FlightConfiguration configuration, FlightConditions conditions,
			WarningSet warnings) {
		QueryResult result = queryOrFallback(conditions, warnings);
		if (result == null) return fallback.getCP(configuration, conditions, warnings);
		if (mode == PhysicsAeroMode.DIAGNOSTIC_AXIAL_HYBRID) {
			return fallback.getCP(configuration, conditions, warnings);
		}
		return centerOfPressure(result.coefficients(), conditions, null);
	}

	@Override
	public AerodynamicForces getAerodynamicForces(FlightConfiguration configuration,
			FlightConditions conditions, WarningSet warnings) {
		QueryResult result = queryOrFallback(conditions, warnings);
		if (result == null) return fallback.getAerodynamicForces(configuration, conditions, warnings);
		AerodynamicForces physics = forces(configuration.getRocket(), result.coefficients(),
				result, conditions, null, true);
		if (mode != PhysicsAeroMode.DIAGNOSTIC_AXIAL_HYBRID) return physics;
		AerodynamicForces established = fallback.getAerodynamicForces(configuration, conditions, warnings);
		copyDrag(physics, established);
		runtimeFlags.add(PhysicsAeroRuntimeFlag.AXIAL_ONLY_HYBRID_USED);
		return established;
	}

	@Override
	public Map<RocketComponent, AerodynamicForces> getForceAnalysis(FlightConfiguration configuration,
			FlightConditions conditions, WarningSet warnings) {
		QueryResult result = queryOrFallback(conditions, warnings);
		if (result == null) return fallback.getForceAnalysis(configuration, conditions, warnings);
		if (mode == PhysicsAeroMode.DIAGNOSTIC_AXIAL_HYBRID) {
			Map<RocketComponent, AerodynamicForces> output =
					new LinkedHashMap<>(fallback.getForceAnalysis(configuration, conditions, warnings));
			for (Map.Entry<RocketComponent, AerodynamicForces> entry : output.entrySet()) {
				RocketComponent component = entry.getKey();
				String componentId = component == configuration.getRocket()
						? null : tableComponentId(configuration, component);
				AerodynamicCoefficients coefficients = componentId == null
						? result.coefficients() : result.componentTotals().get(componentId);
				if (coefficients == null) continue;
				copyDrag(forces(component, coefficients, result, conditions,
						componentId, componentId == null), entry.getValue());
			}
			runtimeFlags.add(PhysicsAeroRuntimeFlag.AXIAL_ONLY_HYBRID_USED);
			return output;
		}
		Map<RocketComponent, AerodynamicForces> output = new LinkedHashMap<>();
		for (RocketComponent component : configuration.getRocket()) {
			String componentId = tableComponentId(configuration, component);
			if (componentId == null) continue;
			AerodynamicCoefficients coefficients = result.componentTotals().get(componentId);
			if (coefficients != null) {
				output.put(component, forces(component, coefficients, result, conditions,
						componentId, false));
			}
		}
		output.put(configuration.getRocket(),
				forces(configuration.getRocket(), result.coefficients(), result, conditions, null, true));
		return output;
	}

	private QueryResult queryOrFallback(FlightConditions conditions, WarningSet warnings) {
		double[] angles = bodyAngles(conditions);
		IncidenceProjection incidenceProjection = IncidenceProjection.none(angles);
		if (mode == PhysicsAeroMode.DIAGNOSTIC_AXIAL_HYBRID) {
			angles = projectAxialIncidenceIfNeeded(angles);
			incidenceProjection = IncidenceProjection.none(angles);
		} else {
			incidenceProjection = incidenceProjection(angles);
		}
		double reynolds = reynoldsNumber(conditions);
		QueryCoordinates coordinates = new QueryCoordinates(conditions.getMach(), angles[0], angles[1],
				context.poweredFraction(), reynolds);
		totalQueries.incrementAndGet();
		try {
			boolean launchGuideProjection = !context.launchGuideCleared();
			QueryResult result = lookup.query(coordinates.mach(),
					launchGuideProjection ? 0 : incidenceProjection.queryAlphaRad(),
					launchGuideProjection ? 0 : incidenceProjection.queryBetaRad(),
					coordinates.poweredFraction());
			result = applyReynoldsCorrection(result, coordinates);
			if (launchGuideProjection) {
				result = projectOntoLaunchGuide(result, angles);
				runtimeFlags.add(PhysicsAeroRuntimeFlag.LAUNCH_GUIDE_AXIAL_TABLE_PROJECTION);
			} else if (incidenceProjection.projected()) {
				result = rotateIncidence(result, incidenceProjection);
				runtimeFlags.add(PhysicsAeroRuntimeFlag.INCIDENCE_AZIMUTH_RECONSTRUCTION);
			}
			successfulQueries.incrementAndGet();
			runtimeFlags.add(PhysicsAeroRuntimeFlag.TABLE_QUERY_USED);
			if (result.interpolated()) interpolatedQueries.incrementAndGet();
			if (result.validityFlags().contains("POWERED_INCREMENT_UNMODELED")) {
				runtimeFlags.add(PhysicsAeroRuntimeFlag.POWERED_INCREMENT_UNMODELED);
			}
			if (result.reasonCodes().contains(FailureReason.INTERPOLATION_EVENT_BOUNDARY)) {
				runtimeFlags.add(PhysicsAeroRuntimeFlag.INTERPOLATION_EVENT_BOUNDARY);
			}
			updateLowestConfidence(result.lowestConfidence());
			if (Double.isFinite(result.lowestConfidence()) && result.lowestConfidence() < LOW_CONFIDENCE_THRESHOLD) {
				runtimeFlags.add(PhysicsAeroRuntimeFlag.LOW_CONFIDENCE_CELL_USED);
			}
			return result;
		} catch (IllegalArgumentException | IllegalStateException exception) {
			PhysicsAeroQueryException typed = translateQueryFailure(exception, coordinates);
			if (typed == null) throw exception;
			recordFailure(typed);
			if (!isHybrid(mode)
					|| !HYBRID_QUERY_FAILURES.contains(typed.reason())) throw typed;
			fallbackCount.incrementAndGet();
			runtimeFlags.add(PhysicsAeroRuntimeFlag.DIAGNOSTIC_FALLBACK_USED);
			if (typed.reason() == FailureReason.POWERED_STATE_UNAVAILABLE) {
				runtimeFlags.add(PhysicsAeroRuntimeFlag.POWERED_STATE_FALLBACK);
			}
			if (warnings != null) warnings.add("Physics-based aerodynamics used diagnostic Barrowman fallback: "
					+ typed.getMessage());
			return null;
		}
	}

	/**
	 * While the guide constrains the vehicle, its reaction owns every transverse
	 * load and all aerodynamic moments.  The only aerodynamic degree of freedom
	 * is guide-parallel translation.  Query zero-incidence CA from the offline
	 * table and scale it by q_axial/q = (V_axial/V)^2.
	 */
	private static QueryResult projectOntoLaunchGuide(QueryResult result, double[] angles) {
		double axialVelocityFraction = Math.cos(angles[0]) * Math.cos(angles[1]);
		double axialPressureFraction = axialVelocityFraction * axialVelocityFraction;
		AerodynamicCoefficients coefficients = launchGuideAxial(
				result.coefficients(), axialPressureFraction);
		Map<String, AerodynamicCoefficients> components = new LinkedHashMap<>();
		result.componentTotals().forEach((key, value) -> components.put(key,
				launchGuideAxial(value, axialPressureFraction)));
		Map<String, AerodynamicCoefficients> owners = new LinkedHashMap<>();
		result.ownerTotals().forEach((key, value) -> owners.put(key,
				launchGuideAxial(value, axialPressureFraction)));
		List<String> validity = new ArrayList<>(result.validityFlags());
		validity.add("LAUNCH_GUIDE_TRANSVERSE_REACTION_OWNED");
		validity.add("LAUNCH_GUIDE_AXIAL_DYNAMIC_PRESSURE_PROJECTION");
		List<String> methods = new ArrayList<>(result.methodIds());
		methods.add("LAUNCH_GUIDE_Q_AXIAL_OVER_Q_PROJECTION_V1");
		return new QueryResult(coefficients, result.diagnosticFlags(), validity, methods,
				result.interpolated(), components, owners,
				info.openrocket.core.aerodynamics.physicsaero.force.AerodynamicDerivatives.zero(),
				result.reasonCodes(), result.lowestConfidence(), result.runtimeCorrection());
	}

	private static AerodynamicCoefficients launchGuideAxial(
			AerodynamicCoefficients source, double axialPressureFraction) {
		return new AerodynamicCoefficients(source.ca() * axialPressureFraction,
				0, 0, 0, 0, 0);
	}

	/**
	 * The offline builder evaluates axisymmetric-body and installed-fin closures
	 * using one total-incidence magnitude, then resolves the resulting normal
	 * load into signed alpha and beta components in every Mach branch.
	 * Consequently a state that only
	 * exceeds the rectangular beta sampling edge can be reconstructed exactly
	 * from the beta=0 slice, provided its total incidence remains inside the
	 * validated alpha envelope.  This is a coordinate transformation of the
	 * same correlations, not extrapolation or coefficient clamping.
	 */
	private IncidenceProjection incidenceProjection(double[] angles) {
		double[] alphaAxis = table.axes().alphaRad();
		double[] betaAxis = table.axes().betaRad();
		boolean outside = angles[0] < alphaAxis[0] || angles[0] > alphaAxis[alphaAxis.length - 1]
				|| angles[1] < betaAxis[0] || angles[1] > betaAxis[betaAxis.length - 1];
		if (!outside) return IncidenceProjection.none(angles);
		double incidence = Math.atan(Math.hypot(Math.tan(angles[0]), Math.tan(angles[1])));
		if (!(incidence > 0) || incidence > alphaAxis[alphaAxis.length - 1]
				|| 0 < betaAxis[0] || 0 > betaAxis[betaAxis.length - 1]) {
			return IncidenceProjection.none(angles);
		}
		return new IncidenceProjection(angles[0], angles[1], incidence, 0,
				angles[0] / incidence, angles[1] / incidence, true);
	}

	private static QueryResult rotateIncidence(QueryResult result,
			IncidenceProjection projection) {
		AerodynamicCoefficients coefficients = rotateIncidenceCoefficients(
				result.coefficients(), projection);
		Map<String, AerodynamicCoefficients> components = new LinkedHashMap<>();
		result.componentTotals().forEach((key, value) -> components.put(key,
				rotateIncidenceCoefficients(value, projection)));
		Map<String, AerodynamicCoefficients> owners = new LinkedHashMap<>();
		result.ownerTotals().forEach((key, value) -> owners.put(key,
				rotateIncidenceCoefficients(value, projection)));
		List<String> validity = new ArrayList<>(result.validityFlags());
		if (!validity.contains("TOTAL_INCIDENCE_AZIMUTH_RECONSTRUCTION_EXACT")) {
			validity.add("TOTAL_INCIDENCE_AZIMUTH_RECONSTRUCTION_EXACT");
		}
		List<String> methods = new ArrayList<>(result.methodIds());
		if (!methods.contains("TOTAL_INCIDENCE_AZIMUTH_RECONSTRUCTION_V1")) {
			methods.add("TOTAL_INCIDENCE_AZIMUTH_RECONSTRUCTION_V1");
		}
		return new QueryResult(coefficients, result.diagnosticFlags(), validity, methods,
				result.interpolated(), components, owners, result.derivatives(),
				result.reasonCodes(), result.lowestConfidence(), result.runtimeCorrection());
	}

	private static AerodynamicCoefficients rotateIncidenceCoefficients(
			AerodynamicCoefficients source, IncidenceProjection projection) {
		double alphaWeight = projection.alphaWeight();
		double betaWeight = projection.betaWeight();
		return new AerodynamicCoefficients(source.ca(), source.cn() * alphaWeight,
				source.cn() * betaWeight, source.cl(), source.cm() * alphaWeight,
				-source.cm() * betaWeight);
	}

	private double[] projectAxialIncidenceIfNeeded(double[] angles) {
		double[] alphaAxis = table.axes().alphaRad();
		double[] betaAxis = table.axes().betaRad();
		boolean outsideAlpha = angles[0] < alphaAxis[0]
				|| angles[0] > alphaAxis[alphaAxis.length - 1];
		boolean outsideBeta = angles[1] < betaAxis[0]
				|| angles[1] > betaAxis[betaAxis.length - 1];
		if (!outsideAlpha && !outsideBeta) return angles;
		double incidence = Math.atan(Math.hypot(Math.tan(angles[0]),
				Math.tan(angles[1])));
		if (incidence > alphaAxis[alphaAxis.length - 1]
				|| 0 < betaAxis[0] || 0 > betaAxis[betaAxis.length - 1]) {
			return angles;
		}
		runtimeFlags.add(PhysicsAeroRuntimeFlag.AXIAL_INCIDENCE_SYMMETRY_PROJECTION);
		return new double[] {incidence, 0};
	}

	private QueryResult applyReynoldsCorrection(QueryResult result, QueryCoordinates coordinates) {
		// Reynolds number is irrelevant at effectively zero airspeed/dynamic pressure.
		if (!(coordinates.reynoldsNumber() > 1)) return result;
		var correction = result.runtimeCorrection();
		if (!(correction.referenceReynolds() > 0)) {
			throw new PhysicsAeroQueryException(FailureReason.REYNOLDS_REBUILD_REQUIRED,
					coordinates, "cell has no valid reference Reynolds number");
		}
		double ratio = coordinates.reynoldsNumber() / correction.referenceReynolds();
		boolean lowDensityBoundaryHold = !correction.requiresRebuild()
				&& ratio < correction.minimumRatio()
				&& ratio <= LOW_DENSITY_BOUNDARY_HOLD_RATIO
				&& correction.minimumRatio() <= MAXIMUM_HELD_SOURCE_BOUNDARY_RATIO;
		double evaluationRatio = lowDensityBoundaryHold
				? correction.minimumRatio() : ratio;
		if (!correction.supportsRatio(evaluationRatio)) {
			throw new PhysicsAeroQueryException(FailureReason.REYNOLDS_REBUILD_REQUIRED,
					coordinates, "runtime Reynolds ratio " + ratio + " is outside ["
							+ correction.minimumRatio() + ", " + correction.maximumRatio()
							+ "] or changes topology");
		}
		if (correction.requiresRebuild()) {
			return result;
		}
		if (Math.abs(Math.log(evaluationRatio)) <= 1.0e-12) return result;
		double[] values = result.coefficients().toArray();
		double[] original = values.clone();
		for (int i = 0; i < values.length; i++) {
			values[i] += correction.coefficientDelta(evaluationRatio, i);
		}
		reynoldsCorrectedQueries.incrementAndGet();
		runtimeFlags.add(PhysicsAeroRuntimeFlag.RUNTIME_REYNOLDS_CORRECTION_USED);
		List<String> validityFlags = result.validityFlags();
		List<String> methodIds = result.methodIds();
		if (lowDensityBoundaryHold) {
			runtimeFlags.add(PhysicsAeroRuntimeFlag.LOW_DENSITY_REYNOLDS_SOURCE_BOUNDARY_HOLD);
			validityFlags = new ArrayList<>(validityFlags);
			validityFlags.add("LOW_DENSITY_REYNOLDS_SOURCE_BOUNDARY_HOLD");
			methodIds = new ArrayList<>(methodIds);
			methodIds.add(LOW_DENSITY_BOUNDARY_HOLD_METHOD);
		}
		return new QueryResult(AerodynamicCoefficients.fromArray(values), result.diagnosticFlags(),
				validityFlags, methodIds, result.interpolated(),
				correctGroupedTotals(result.componentTotals(), original, values),
				correctGroupedTotals(result.ownerTotals(), original, values), result.derivatives(),
				result.reasonCodes(), result.lowestConfidence(), correction);
	}

	static String tableComponentId(FlightConfiguration configuration,
			RocketComponent target) {
		configuration.update();
		var active = configuration.getActiveInstances().keySet();
		List<RocketComponent> supported = new ArrayList<>();
		for (RocketComponent component : configuration.getRocket()) {
			if (active.contains(component)
					&& !"UNSUPPORTED".equals(GeometryClassifier.classify(component))
					&& !(component instanceof info.openrocket.core.rocketcomponent.ExternalComponent
							&& !(component.getLength() > 0))) {
				supported.add(component);
			}
		}
		supported.sort(Comparator.comparingDouble(
				component -> component.getComponentLocations()[0].getX()));
		int order = supported.indexOf(target);
		return order < 0 ? null
				: String.format(Locale.ROOT, "aero-component-%04d", order);
	}

	private static Map<String, AerodynamicCoefficients> correctGroupedTotals(
			Map<String, AerodynamicCoefficients> totals, double[] original, double[] corrected) {
		if (totals.isEmpty()) return totals;
		java.util.List<String> keys = totals.keySet().stream().sorted().toList();
		Map<String, double[]> values = new LinkedHashMap<>();
		for (String key : keys) values.put(key, totals.get(key).toArray());
		for (int axis = 0; axis < 6; axis++) {
			double delta = corrected[axis] - original[axis];
			if (delta == 0) continue;
			double magnitude = 0;
			for (String key : keys) magnitude += Math.abs(values.get(key)[axis]);
			if (magnitude <= 1.0e-15) {
				values.get(keys.get(0))[axis] += delta;
			} else {
				for (String key : keys) {
					double[] group = values.get(key);
					group[axis] += delta * Math.abs(group[axis]) / magnitude;
				}
			}
		}
		Map<String, AerodynamicCoefficients> adjusted = new LinkedHashMap<>();
		for (String key : keys) adjusted.put(key, AerodynamicCoefficients.fromArray(values.get(key)));
		return adjusted;
	}

	private PhysicsAeroQueryException translateQueryFailure(RuntimeException exception,
			QueryCoordinates coordinates) {
		if (exception instanceof PhysicsAeroQueryException typed) return typed;
		String message = exception.getMessage() == null ? "" : exception.getMessage();
		String normalized = message.toUpperCase(java.util.Locale.ROOT);
		FailureReason reason;
		if (context.powered() && maximum(table.axes().poweredFraction()) == 0
				&& normalized.contains("OUT_OF_DOMAIN")) {
			reason = FailureReason.POWERED_STATE_UNAVAILABLE;
		} else if (normalized.contains("OUT_OF_DOMAIN")) {
			reason = FailureReason.OUT_OF_DOMAIN;
		} else if (normalized.contains("EVENT") || normalized.contains("TOPOLOGY")
				|| normalized.contains("OWNERSHIP")) {
			reason = FailureReason.INTERPOLATION_EVENT_BOUNDARY;
		} else if (normalized.contains("REYNOLDS_REBUILD_REQUIRED")) {
			reason = FailureReason.REYNOLDS_REBUILD_REQUIRED;
		} else {
			return null;
		}
		return new PhysicsAeroQueryException(reason, coordinates, message, exception);
	}

	private void recordFailure(PhysicsAeroQueryException failure) {
		failureCounts.computeIfAbsent(failure.reason(), ignored -> new AtomicLong()).incrementAndGet();
		firstOccurrences.putIfAbsent(failure.reason(), new PhysicsAeroFailureOccurrence(
				context.timeSeconds(), failure.coordinates(), failure.getMessage()));
		switch (failure.reason()) {
			case OUT_OF_DOMAIN -> runtimeFlags.add(PhysicsAeroRuntimeFlag.OUT_OF_DOMAIN);
			case INTERPOLATION_EVENT_BOUNDARY -> runtimeFlags.add(PhysicsAeroRuntimeFlag.INTERPOLATION_EVENT_BOUNDARY);
			case REYNOLDS_REBUILD_REQUIRED -> runtimeFlags.add(PhysicsAeroRuntimeFlag.REYNOLDS_REBUILD_REQUIRED);
			default -> { }
		}
	}

	private synchronized void updateLowestConfidence(double value) {
		if (Double.isNaN(value)) return;
		if (Double.isNaN(lowestConfidence) || value < lowestConfidence) lowestConfidence = value;
	}

	private static boolean isHybrid(PhysicsAeroMode mode) {
		return mode == PhysicsAeroMode.DIAGNOSTIC_HYBRID
				|| mode == PhysicsAeroMode.DIAGNOSTIC_AXIAL_HYBRID;
	}

	private static void copyDrag(AerodynamicForces source, AerodynamicForces target) {
		target.setCDaxial(source.getCDaxial());
		target.setCD(source.getCD());
		target.setFrictionCD(source.getFrictionCD());
		target.setBaseCD(source.getBaseCD());
		target.setPressureCD(source.getPressureCD());
		target.setOverrideCD(source.getOverrideCD());
	}

	private AerodynamicForces forces(RocketComponent component, AerodynamicCoefficients coefficients,
			QueryResult result, FlightConditions conditions, String componentId, boolean vehicleTotal) {
		AerodynamicForces forces = new AerodynamicForces();
		forces.zero();
		forces.setComponent(component);
		double velocity = Math.max(1e-9, conditions.getVelocity());
		double rateScale = conditions.getRefLength() / (2 * velocity);
		double rollIncrement = vehicleTotal
				? result.derivatives().clp() * conditions.getRollRate() * rateScale : 0;
		double pitchIncrement = vehicleTotal
				? result.derivatives().cmq() * conditions.getPitchRate() * rateScale : 0;
		double yawIncrement = vehicleTotal
				? result.derivatives().cnr() * conditions.getYawRate() * rateScale : 0;
		double[] angles = bodyAngles(conditions);
		double theta = conditions.getTheta();
		double cosTheta = Math.cos(theta);
		double sinTheta = Math.sin(theta);
		/*
		 * Table CN/CY and Cm/Cyaw are fixed body-axis components.  OpenRocket's
		 * stepper subsequently rotates its normal/side and pitch/yaw pair by
		 * FlightConditions.theta.  Resolve the table vectors back into that
		 * incidence-aligned frame here so the stepper performs the rotation once,
		 * rather than double-rotating a body-axis vector.  For an axisymmetric
		 * load this yields CN=sqrt(CN_body^2+CY_body^2), Cside=0 and the same
		 * restoring moment in every azimuth.
		 */
		double openRocketNormal = coefficients.cn() * cosTheta
				+ coefficients.cy() * sinTheta;
		double openRocketSide = coefficients.cy() * cosTheta
				- coefficients.cn() * sinTheta;
		double openRocketStaticPitch = -coefficients.cm() * cosTheta
				+ coefficients.cYaw() * sinTheta;
		double openRocketStaticYaw = coefficients.cYaw() * cosTheta
				+ coefficients.cm() * sinTheta;
		forces.setCDaxial(coefficients.ca());
		forces.setCD(windAxisDrag(coefficients, angles[0], angles[1]));
		forces.setCN(openRocketNormal);
		forces.setCside(openRocketSide);
		forces.setCrollForce(coefficients.cl());
		forces.setCrollDamp(rollIncrement);
		forces.setCroll(coefficients.cl() + rollIncrement);
		// The table stores physical body-axis torque from r x F.  For a positive
		// normal force acting aft of the moment origin this is a negative y torque.
		// OpenRocket, however, defines Cm as CN * xCP / L and applies its own
		// -CN * xCG / L shift in the integrator.  Convert only the static pitch
		// moment at this boundary; cmq is already a damping derivative in the
		// OpenRocket convention and must retain its sign.
		forces.setCm(openRocketStaticPitch + pitchIncrement);
		forces.setCyaw(openRocketStaticYaw + yawIncrement);
		forces.setPitchDampingMoment(-pitchIncrement);
		forces.setYawDampingMoment(-yawIncrement);
		setDragBreakdown(forces, result.ownerTotals());
		forces.setCP(centerOfPressure(coefficients, conditions, componentId));
		return forces;
	}

	private CoordinateIF centerOfPressure(AerodynamicCoefficients coefficients,
			FlightConditions conditions, String componentId) {
		var reference = table.cells().get(0).referenceState();
		double[] angles = bodyAngles(conditions);
		IncidenceProjection projection = incidenceProjection(angles);
		if (projection.projected()) {
			double slope;
			double cpX;
			if (Math.abs(angles[0]) > CP_SLOPE_THRESHOLD
					&& Math.abs(coefficients.cn()) > CP_SLOPE_THRESHOLD) {
				slope = coefficients.cn() / angles[0];
				cpX = reference.momentOriginM().x
						- coefficients.cm() * reference.referenceLengthM() / coefficients.cn();
			} else if (Math.abs(angles[1]) > CP_SLOPE_THRESHOLD
					&& Math.abs(coefficients.cy()) > CP_SLOPE_THRESHOLD) {
				slope = coefficients.cy() / angles[1];
				cpX = reference.momentOriginM().x
						+ coefficients.cYaw() * reference.referenceLengthM() / coefficients.cy();
			} else {
				return Coordinate.ZERO;
			}
			return Double.isFinite(cpX + slope) ? new Coordinate(cpX, 0, 0, slope)
					: Coordinate.ZERO;
		}
		LocalSlopes slopes = localTransverseSlopes(conditions, componentId);
		if (Math.abs(slopes.cNa()) <= CP_SLOPE_THRESHOLD) return Coordinate.ZERO;
		// Local table Cm-alpha remains a physical torque slope, so its aft-CP
		// reconstruction retains the physical r x F sign convention.
		double cpX = reference.momentOriginM().x
				- slopes.cmAlpha() * reference.referenceLengthM() / slopes.cNa();
		return Double.isFinite(cpX) ? new Coordinate(cpX, 0, 0, slopes.cNa()) : Coordinate.ZERO;
	}

	private LocalSlopes localTransverseSlopes(FlightConditions conditions, String componentId) {
		double[] angles = bodyAngles(conditions);
		double[] axis = table.axes().alphaRad();
		double alpha = angles[0];
		if (axis.length < 2) return LocalSlopes.UNDEFINED;
		int nearest = 0;
		for (int i = 1; i < axis.length; i++) {
			if (Math.abs(axis[i] - alpha) < Math.abs(axis[nearest] - alpha)) nearest = i;
		}
		int other = nearest == 0 ? 1 : nearest == axis.length - 1 ? axis.length - 2
				: (alpha >= axis[nearest] ? nearest + 1 : nearest - 1);
		double firstAlpha = axis[nearest], secondAlpha = axis[other];
		if (firstAlpha == secondAlpha) return LocalSlopes.UNDEFINED;
		try {
			QueryResult first = lookup.query(conditions.getMach(), firstAlpha, angles[1], context.poweredFraction());
			QueryResult second = lookup.query(conditions.getMach(), secondAlpha, angles[1], context.poweredFraction());
			AerodynamicCoefficients firstCoefficients = componentId == null ? first.coefficients()
					: first.componentTotals().get(componentId);
			AerodynamicCoefficients secondCoefficients = componentId == null ? second.coefficients()
					: second.componentTotals().get(componentId);
			if (firstCoefficients == null || secondCoefficients == null) return LocalSlopes.UNDEFINED;
			double delta = secondAlpha - firstAlpha;
			return new LocalSlopes((secondCoefficients.cn() - firstCoefficients.cn()) / delta,
					(secondCoefficients.cm() - firstCoefficients.cm()) / delta);
		} catch (IllegalArgumentException | IllegalStateException ignored) {
			return LocalSlopes.UNDEFINED;
		}
	}

	private record LocalSlopes(double cNa, double cmAlpha) {
		private static final LocalSlopes UNDEFINED = new LocalSlopes(0, 0);
	}

	private record IncidenceProjection(double rawAlphaRad, double rawBetaRad,
			double queryAlphaRad, double queryBetaRad, double alphaWeight,
			double betaWeight, boolean projected) {
		private static IncidenceProjection none(double[] angles) {
			return new IncidenceProjection(angles[0], angles[1], angles[0], angles[1],
					1, 0, false);
		}
	}

	private static double windAxisDrag(AerodynamicCoefficients coefficients, double alpha, double beta) {
		return coefficients.ca() * Math.cos(alpha) * Math.cos(beta)
				+ coefficients.cn() * Math.sin(alpha) * Math.cos(beta)
				+ coefficients.cy() * Math.sin(beta);
	}

	private static void setDragBreakdown(AerodynamicForces forces,
			Map<String, AerodynamicCoefficients> owners) {
		double friction = ownerCaContaining(owners, "SKIN_FRICTION");
		double base = owners.entrySet().stream()
				.filter(entry -> entry.getKey().contains("BASE")
						&& entry.getKey().contains("PRESSURE_DRAG"))
				.mapToDouble(entry -> entry.getValue().ca()).sum();
		double pressure = Math.max(0, forces.getCD() - friction - base);
		forces.setFrictionCD(friction);
		forces.setBaseCD(base);
		forces.setPressureCD(pressure);
		forces.setOverrideCD(0);
	}

	private static double ownerCaContaining(Map<String, AerodynamicCoefficients> owners, String token) {
		return owners.entrySet().stream().filter(entry -> entry.getKey().contains(token))
				.mapToDouble(entry -> entry.getValue().ca()).sum();
	}

	private static double reynoldsNumber(FlightConditions conditions) {
		double viscosity = conditions.getAtmosphericConditions().getKinematicViscosity();
		return viscosity > 0 ? conditions.getVelocity() * conditions.getRefLength() / viscosity : Double.NaN;
	}

	private static double maximum(double[] values) {
		double result = -Double.MAX_VALUE;
		for (double value : values) result = Math.max(result, value);
		return result;
	}

	public static double[] bodyAngles(FlightConditions conditions) {
		double alpha = conditions.getAOA() * Math.cos(conditions.getTheta());
		double beta = conditions.getAOA() * Math.sin(conditions.getTheta());
		return new double[] { alpha, beta };
	}

	@Override
	public AerodynamicCalculator newInstance() {
		return new PhysicsAeroAerodynamicCalculator(table, table.metadata().geometryHash(),
				table.metadata().settingsHash(), contentHash, mode,
				fallback == null ? null : fallback.newInstance());
	}

	@Override
	public void checkGeometry(FlightConfiguration configuration, RocketComponent component,
			WarningSet warnings) {
		if (fallback != null) fallback.checkGeometry(configuration, component, warnings);
	}

	@Override
	public ModID getModID() { return modId; }
}
