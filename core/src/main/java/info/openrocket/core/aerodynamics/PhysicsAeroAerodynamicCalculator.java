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
		if (mode == PhysicsAeroMode.DIAGNOSTIC_AXIAL_HYBRID) {
			angles = projectAxialIncidenceIfNeeded(angles);
		}
		double reynolds = reynoldsNumber(conditions);
		QueryCoordinates coordinates = new QueryCoordinates(conditions.getMach(), angles[0], angles[1],
				context.poweredFraction(), reynolds);
		totalQueries.incrementAndGet();
		try {
			QueryResult result = lookup.query(coordinates.mach(), coordinates.alphaRad(),
					coordinates.betaRad(), coordinates.poweredFraction());
			result = applyReynoldsCorrection(result, coordinates);
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
		if (!correction.supportsRatio(ratio)) {
			throw new PhysicsAeroQueryException(FailureReason.REYNOLDS_REBUILD_REQUIRED,
					coordinates, "runtime Reynolds ratio " + ratio + " is outside ["
							+ correction.minimumRatio() + ", " + correction.maximumRatio()
							+ "] or changes topology");
		}
		if (correction.requiresRebuild()) {
			return result;
		}
		double logRatio = Math.log(ratio);
		if (Math.abs(logRatio) <= 1.0e-12) return result;
		double[] values = result.coefficients().toArray();
		double[] original = values.clone();
		double[] derivatives = correction.dCoefficientDLogRe();
		double[] curvature = correction.dCoefficientDLogReSquared();
		double[] cubic = correction.dCoefficientDLogReCubed();
		for (int i = 0; i < values.length; i++) {
			values[i] += derivatives[i] * logRatio
					+ curvature[i] * logRatio * logRatio
					+ cubic[i] * logRatio * logRatio * logRatio;
		}
		reynoldsCorrectedQueries.incrementAndGet();
		runtimeFlags.add(PhysicsAeroRuntimeFlag.RUNTIME_REYNOLDS_CORRECTION_USED);
		return new QueryResult(AerodynamicCoefficients.fromArray(values), result.diagnosticFlags(),
				result.validityFlags(), result.methodIds(), result.interpolated(),
				correctGroupedTotals(result.componentTotals(), original, values),
				correctGroupedTotals(result.ownerTotals(), original, values), result.derivatives(),
				result.reasonCodes(), result.lowestConfidence(), correction);
	}

	private static String tableComponentId(FlightConfiguration configuration,
			RocketComponent target) {
		configuration.update();
		var active = configuration.getActiveInstances().keySet();
		List<RocketComponent> supported = new ArrayList<>();
		for (RocketComponent component : configuration.getRocket()) {
			if (active.contains(component)
					&& !"UNSUPPORTED".equals(GeometryClassifier.classify(component))) {
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
		forces.setCDaxial(coefficients.ca());
		forces.setCD(windAxisDrag(coefficients, angles[0], angles[1]));
		forces.setCN(coefficients.cn());
		forces.setCside(coefficients.cy());
		forces.setCrollForce(coefficients.cl());
		forces.setCrollDamp(rollIncrement);
		forces.setCroll(coefficients.cl() + rollIncrement);
		forces.setCm(coefficients.cm() + pitchIncrement);
		forces.setCyaw(coefficients.cYaw() + yawIncrement);
		forces.setPitchDampingMoment(-pitchIncrement);
		forces.setYawDampingMoment(-yawIncrement);
		setDragBreakdown(forces, result.ownerTotals());
		forces.setCP(centerOfPressure(coefficients, conditions, componentId));
		return forces;
	}

	private CoordinateIF centerOfPressure(AerodynamicCoefficients coefficients,
			FlightConditions conditions, String componentId) {
		var reference = table.cells().get(0).referenceState();
		LocalSlopes slopes = localTransverseSlopes(conditions, componentId);
		if (Math.abs(slopes.cNa()) <= CP_SLOPE_THRESHOLD) return Coordinate.ZERO;
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
