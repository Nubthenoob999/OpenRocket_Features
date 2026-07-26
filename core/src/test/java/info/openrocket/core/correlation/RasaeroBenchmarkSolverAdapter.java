package info.openrocket.core.correlation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;

import info.openrocket.core.aerodynamics.physicsaero.body.AxisymmetricBodySolver;
import info.openrocket.core.aerodynamics.physicsaero.flow.AtmosphereState;
import info.openrocket.core.aerodynamics.physicsaero.flow.FlowCondition;
import info.openrocket.core.aerodynamics.physicsaero.force.CenterOfPressureDiagnostic;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;
import info.openrocket.core.aerodynamics.physicsaero.flow.PoweredFlowState;
import info.openrocket.core.aerodynamics.physicsaero.powered.PoweredBaseFlowModel;
import info.openrocket.core.aerodynamics.physicsaero.diagnostics.FailureReason;
import info.openrocket.core.aerodynamics.physicsaero.integration.FullRegimeTableBuilder;
import info.openrocket.core.aerodynamics.physicsaero.table.TableCell;
import info.openrocket.core.aerodynamics.physicsaero.table.TableMetadata;
import info.openrocket.core.correlation.RasaeroBenchmarkData.BenchmarkCase;

/** Maps benchmark quantities to production table outputs without consulting expected values. */
final class RasaeroBenchmarkSolverAdapter {
	static final String POWERED_UNSUPPORTED = "POWERED_BASE_MODEL_NOT_IMPLEMENTED";
	static final String BODY_INCIDENCE_UNOWNED = "BODY_NONZERO_INCIDENCE_UNOWNED_PHASE3";
	static final String HIGH_MACH_LIMITATION =
			"GENERIC_GEOMETRY_AND_REYNOLDS_INDEPENDENT_HIGH_MACH_CLOSURE";

	Prediction predictCa(AeroGeometry geometry, double mach, double alphaRad,
			AtmosphereState atmosphere, List<String> fixtureLimitations) {
		return tablePrediction(geometry, mach, alphaRad, atmosphere, fixtureLimitations, false);
	}

	Prediction predictCpPercent(AeroGeometry geometry, double mach, double alphaRad,
			AtmosphereState atmosphere, List<String> fixtureLimitations) {
		return tablePrediction(geometry, mach, alphaRad, atmosphere, fixtureLimitations, true);
	}

	Prediction probePowered(BenchmarkCase benchmark, AeroGeometry geometry, double mach, AtmosphereState atmosphere) {
		try {
			double exitMach = geometry.components().stream()
					.mapToDouble(component -> component.localReferences().getOrDefault("jetExitMach", 0.0))
					.max().orElse(0);
			PoweredFlowState state = new PoweredFlowState(1, 1, exitMach,
					geometry.references().exposedBaseAreaM2(), atmosphere.pressurePa(), 1.25,
					atmosphere.pressurePa());
			var result = new PoweredBaseFlowModel().evaluate(geometry, mach, state);
			double actual = switch (benchmark.expectedName()) {
				case "delta_CD_power_on_minus_power_off" -> result.totalDeltaCd();
				case "delta_CD_base" -> result.baseDeltaCd();
				case "delta_CD_boattail_estimated" -> result.boattailDeltaCd();
				case "delta_CD_unaccounted" -> result.plumeAndInstallationDeltaCd();
				default -> throw new IllegalArgumentException("UNSUPPORTED_POWERED_BENCHMARK_QUANTITY");
			};
			return new Prediction(OptionalDouble.of(actual), null, true, result.methodIds(),
					result.validityFlags(), Set.of(), "POWERED_FLOW_OWNER", List.of());
		} catch (IllegalArgumentException exception) {
			return Prediction.unsupported(message(exception), List.of(), List.of(),
					"POWERED_FLOW_OWNER:BUILD_REJECTED", List.of());
		}
	}

	private Prediction tablePrediction(AeroGeometry geometry, double mach, double alphaRad,
			AtmosphereState atmosphere, List<String> fixtureLimitations, boolean cp) {
		List<String> limitations = new ArrayList<>(fixtureLimitations);
		Set<FailureReason> fixtureReasons = fixtureLimitations.contains(
				RasaeroBenchmarkFixtures.ARCAS_GEOMETRY_LIMITATION)
				? Set.of(FailureReason.GEOMETRY_FEATURE_UNREPRESENTED) : Set.of();
		try {
			TableCell cell = new FullRegimeTableBuilder().build(geometry, new double[] {mach},
					new double[] {alphaRad}, new double[] {0}, atmosphere,
					RasaeroBenchmarkFixtures.AIR, metadata(geometry)).cell(0, 0, 0);
			double actual;
			if (cp) {
				OptionalDouble pitchX = CenterOfPressureDiagnostic.derive(cell.coefficients(),
						cell.referenceState(), 1e-12).pitchXM();
				if (pitchX.isEmpty()) {
					return Prediction.unsupported("CENTER_OF_PRESSURE_UNDEFINED", cell.methodIds(),
							cell.validityFlags(), ownership(cell), limitations);
				}
				actual = 100 * pitchX.getAsDouble() / geometry.references().vehicleLengthM();
			} else {
				actual = cell.coefficients().ca();
			}
			if (!Double.isFinite(actual)
					|| !Arrays.stream(cell.coefficients().toArray()).allMatch(Double::isFinite)) {
				return Prediction.unsupported("NONFINITE_PRODUCTION_OUTPUT", cell.methodIds(),
						cell.validityFlags(), ownership(cell), limitations);
			}
			Set<FailureReason> reasons = new java.util.LinkedHashSet<>(cell.diagnostics().reasonCodes());
			reasons.addAll(fixtureReasons);
			return new Prediction(OptionalDouble.of(actual), null, cell.directlyGenerated(),
					cell.methodIds(), cell.validityFlags(), reasons,
					ownership(cell), List.copyOf(limitations));
		} catch (IllegalArgumentException | IllegalStateException exception) {
			return Prediction.unsupported(message(exception), List.of(), List.of(),
					"FULL_REGIME_OWNER:BUILD_REJECTED", limitations);
		}
	}

	private static TableMetadata metadata(AeroGeometry geometry) {
		return new TableMetadata(TableMetadata.CURRENT_SCHEMA, geometry.geometryHash(),
				"rasaero-correlation-benchmark", "test", "physics-aero-production", "SI;radians",
				"OPENROCKET_BODY_AXES_V1", Instant.parse("2026-01-01T00:00:00Z"), Map.of(),
				Map.of(), "PUBLISHED_SOURCE_TOLERANCE");
	}

	private static String ownership(TableCell cell) {
		return cell.ownerTotals().entrySet().stream().sorted(Map.Entry.comparingByKey())
				.map(entry -> entry.getKey() + "{ca=" + entry.getValue().ca()
						+ ",cn=" + entry.getValue().cn() + ",cm=" + entry.getValue().cm() + '}')
				.collect(java.util.stream.Collectors.joining(","));
	}

	private static String message(RuntimeException exception) {
		return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
	}

	record Prediction(OptionalDouble actual, String unsupportedReason, boolean directlyGenerated,
			List<String> methodIds, List<String> validityFlags, Set<FailureReason> reasonCodes, String ownership,
			List<String> limitations) {
		Prediction {
			methodIds = methodIds.stream().sorted(Comparator.naturalOrder()).toList();
			validityFlags = validityFlags.stream().sorted(Comparator.naturalOrder()).toList();
			reasonCodes = Set.copyOf(reasonCodes);
			limitations = List.copyOf(limitations);
		}

		static Prediction unsupported(String reason, List<String> methods, List<String> validity,
				String ownership, List<String> limitations) {
			return new Prediction(OptionalDouble.empty(), reason, false, methods, validity,
					typedUnsupportedReason(reason), ownership,
					limitations);
		}

		private static Set<FailureReason> typedUnsupportedReason(String reason) {
			if (reason == null) return Set.of(FailureReason.INVALID_STATE);
			if (reason.contains("DOMAIN_EXCEEDED")) return Set.of(FailureReason.MACH_DOMAIN_EXCEEDED);
			if (reason.contains("POWERED")) return Set.of(FailureReason.POWERED_FLOW_UNIMPLEMENTED);
			if (reason.contains("NONFINITE")) return Set.of(FailureReason.NUMERICAL_FAILURE);
			return Set.of(FailureReason.INVALID_STATE);
		}

		boolean unsupported() {
			return unsupportedReason != null;
		}

		boolean bodyIncidenceUnowned() {
			return validityFlags.contains(BODY_INCIDENCE_UNOWNED);
		}
	}
}
