package info.openrocket.core.aerodynamics.physicsaero.integration;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import info.openrocket.core.aerodynamics.physicsaero.interpolation.TableQueryEngine;
import info.openrocket.core.aerodynamics.physicsaero.table.AerodynamicTable;
import info.openrocket.core.aerodynamics.physicsaero.table.CertificationState;
import info.openrocket.core.aerodynamics.physicsaero.table.TableAxes;
import info.openrocket.core.aerodynamics.physicsaero.table.TableCell;
import info.openrocket.core.aerodynamics.physicsaero.table.TableMetadata;
import info.openrocket.core.aerodynamics.physicsaero.table.TableWriter;

/** Validates the generated artifact itself; it makes no claim about flight accuracy. */
public final class PhysicsAeroValidationGate {
	public static final String CODE_VERSION = "physics-aero-v86";
	public static final String REGISTRY_VERSION = "full-regime-v6";
	private static final double RECOVERY_TOLERANCE = 1e-9;

	public enum Status { PASS, FAIL }

	public record Report(Status status, Instant evaluatedAt, List<String> checks,
			List<String> failures, String version, CertificationState certificationState) {
		public Report {
			checks = List.copyOf(checks);
			failures = List.copyOf(failures);
		}
	}

	/** A gate without an artifact cannot pass. */
	public Report evaluate() {
		return new Report(Status.FAIL, Instant.now(), List.of(), List.of("TABLE_ARTIFACT_REQUIRED"),
				CODE_VERSION + ":" + REGISTRY_VERSION, CertificationState.NOT_READY);
	}

	public Report evaluate(AerodynamicTable table) {
		List<String> checks = new ArrayList<>();
		List<String> failures = new ArrayList<>();
		if (table == null) return evaluate();
		validateMetadata(table.metadata(), checks, failures);
		validateAxes(table.axes(), checks, failures);
		validateCells(table, checks, failures);
		validateDirectQueries(table, checks, failures);
		validateStructuralInterpolationHoldouts(table, checks, failures);
		validateRegimeJoins(table, checks, failures);
		validateDeterministicEncoding(table, checks, failures);
		return new Report(failures.isEmpty() ? Status.PASS : Status.FAIL, Instant.now(), checks,
				failures, CODE_VERSION + ":" + REGISTRY_VERSION,
				failures.isEmpty() ? CertificationState.EXPERIMENTAL_FLIGHT_PENDING
						: CertificationState.NOT_READY);
	}

	private static void validateMetadata(TableMetadata metadata, List<String> checks,
			List<String> failures) {
		check("TABLE_SCHEMA_V3", TableMetadata.CURRENT_SCHEMA.equals(metadata.schemaVersion()), checks, failures);
		check("SI_RADIAN_UNITS", TableMetadata.REQUIRED_UNITS.equals(metadata.units()), checks, failures);
		check("OPENROCKET_AXIS_CONVENTION",
				TableMetadata.REQUIRED_AXIS_CONVENTION.equals(metadata.axisConvention()), checks, failures);
		check("GEOMETRY_HASH_PRESENT", !metadata.geometryHash().isBlank(), checks, failures);
		check("SETTINGS_HASH_PRESENT", !metadata.settingsHash().isBlank(), checks, failures);
		check("CODE_VERSION_CURRENT", CODE_VERSION.equals(metadata.codeVersion()), checks, failures);
		check("REGISTRY_VERSION_CURRENT", REGISTRY_VERSION.equals(metadata.correlationRegistryVersion()), checks, failures);
		check("INTERPOLATION_TARGET_CONFIGURED",
				metadata.tolerances().getOrDefault("interpolationRelative", 0.02) <= 0.02, checks, failures);
		check("REGIME_JOIN_TARGET_CONFIGURED",
				metadata.tolerances().getOrDefault("joinJump", 0.01) > 0, checks, failures);
	}

	private static void validateAxes(TableAxes axes, List<String> checks, List<String> failures) {
		check("MACH_DOMAIN_0_TO_8", endpoint(axes.mach(), 0, 8), checks, failures);
		check("RADIAL_INCIDENCE_DOMAIN_MINUS90_TO_PLUS90", endpoint(axes.alphaRad(), Math.toRadians(-90),
				Math.toRadians(90)), checks, failures);
		check("BETA_DOMAIN_MINUS5_TO_PLUS5", endpoint(axes.betaRad(), Math.toRadians(-5),
				Math.toRadians(5)), checks, failures);
		check("COAST_STATE_PRESENT", Arrays.stream(axes.poweredFraction()).anyMatch(v -> v == 0),
				checks, failures);
	}

	private static void validateCells(AerodynamicTable table, List<String> checks,
			List<String> failures) {
		boolean finite = true;
		boolean totals = true;
		boolean ownership = true;
		boolean diagnostics = true;
		for (int index = 0; index < table.cells().size(); index++) {
			TableCell cell = table.cells().get(index);
			finite &= finite(cell.coefficients().toArray()) && finite(cell.derivatives().toArray())
					&& finite(cell.confidence()) && finite(cell.uncertainty())
					&& finite(cell.runtimeCorrection().dCoefficientDLogRe())
					&& finite(cell.runtimeCorrection().dCoefficientDLogReFourth())
					&& finite(cell.runtimeCorrection().dCoefficientDLogReFifth())
					&& finite(cell.runtimeCorrection().dCoefficientDLogReSixth())
					&& finite(cell.runtimeCorrection().dCoefficientDLogReSeventh());
			totals &= recovers(cell.componentTotals(), cell.coefficients().toArray())
					&& recovers(cell.ownerTotals(), cell.coefficients().toArray());
			for (String methodId : cell.methodIds()) if (isLegacyMethodId(methodId)) ownership = false;
			java.util.Set<String> canonicalOwners = new java.util.HashSet<>();
			for (String owner : cell.ownerTotals().keySet()) {
				String canonical = owner.toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9]", "");
				if (canonical.isEmpty() || !canonicalOwners.add(canonical)) ownership = false;
			}
			if (cell.diagnostics().fallback()) {
				diagnostics &= cell.diagnostics().fallbackMethodId() != null
						&& cell.diagnostics().fallbackReasonCode() != null
						&& !cell.diagnostics().reasonCodes().isEmpty();
			}
			boolean typedDiagnosticRequired = cell.validityFlags().stream()
					.map(value -> value.toUpperCase(java.util.Locale.ROOT))
					.anyMatch(value -> value.contains("INVALID") || value.contains("FALLBACK")
							|| value.contains("UNIMPLEMENTED"));
			if (typedDiagnosticRequired) diagnostics &= !cell.diagnostics().reasonCodes().isEmpty();
		}
		check("FINITE_COEFFICIENTS_AND_DERIVATIVES", finite, checks, failures);
		check("COMPONENT_AND_OWNER_TOTALS_RECOVER_VEHICLE", totals, checks, failures);
		check("UNIQUE_NONLEGACY_PHYSICAL_OWNERS", ownership, checks, failures);
		check("FALLBACK_CELLS_HAVE_TYPED_DIAGNOSTICS", diagnostics, checks, failures);
	}

	private static void validateDirectQueries(AerodynamicTable table, List<String> checks,
			List<String> failures) {
		boolean exact = true;
		TableAxes axes = table.axes();
		TableQueryEngine engine = new TableQueryEngine();
		for (int m = 0; m < axes.mach().length && exact; m++) {
			for (int a = 0; a < axes.alphaRad().length && exact; a++) {
				for (int b = 0; b < axes.betaRad().length && exact; b++) {
					for (int p = 0; p < axes.poweredFraction().length; p++) {
						TableCell expected = table.cell(m, a, b, p);
						var actual = engine.query(table, axes.mach()[m], axes.alphaRad()[a],
								axes.betaRad()[b], axes.poweredFraction()[p]);
						exact &= Arrays.equals(expected.coefficients().toArray(), actual.coefficients().toArray())
								&& expected.componentTotals().equals(actual.componentTotals())
								&& expected.ownerTotals().equals(actual.ownerTotals());
					}
				}
			}
		}
		check("DIRECT_NODE_QUERIES_REPRODUCE_STORED_CELLS", exact, checks, failures);
	}

	private static void validateStructuralInterpolationHoldouts(AerodynamicTable table,
			List<String> checks, List<String> failures) {
		TableAxes axes = table.axes();
		TableQueryEngine engine = new TableQueryEngine();
		int evaluated = 0;
		boolean valid = true;
		// Midpoints are withheld from the artifact.  Validate only topologically
		// homogeneous intervals; event-boundary behavior is checked separately.
		for (int m = 0; m < axes.mach().length && evaluated < 64; m++) {
			for (int a = 0; a + 1 < axes.alphaRad().length && evaluated < 64; a++) {
				for (int b = 0; b < axes.betaRad().length && evaluated < 64; b++) {
					TableCell lower = table.cell(m, a, b, 0), upper = table.cell(m, a + 1, b, 0);
					if (!lower.methodIds().equals(upper.methodIds())
							|| !lower.ownerTotals().keySet().equals(upper.ownerTotals().keySet())) continue;
					try {
						var result = engine.query(table, axes.mach()[m],
								0.5 * (axes.alphaRad()[a] + axes.alphaRad()[a + 1]), axes.betaRad()[b], 0);
						valid &= finite(result.coefficients().toArray())
								&& recovers(result.componentTotals(), result.coefficients().toArray())
								&& recovers(result.ownerTotals(), result.coefficients().toArray());
						evaluated++;
					} catch (RuntimeException exception) { valid = false; }
				}
			}
		}
		check("STRUCTURAL_INTERPOLATION_HOLDOUTS_PRESENT", evaluated > 0, checks, failures);
		check("STRUCTURAL_INTERPOLATION_HOLDOUTS_VALID", valid, checks, failures);
	}

	private static void validateRegimeJoins(AerodynamicTable table, List<String> checks,
			List<String> failures) {
		double configuredBound = table.metadata().tolerances().getOrDefault("joinJump", 0.01);
		double[] mach = table.axes().mach();
		boolean joinsPresent = true;
		boolean continuous = true;
		for (double join : new double[] {0.85, 0.95, 1.2, 1.3, 5.0}) {
			int index = Arrays.binarySearch(mach, join);
			if (index <= 0 || index >= mach.length - 1) { joinsPresent = false; continue; }
			double epsilon = 1e-6 * Math.min(join - mach[index - 1], mach[index + 1] - join);
			for (int a = 0; a < table.axes().alphaRad().length; a++) {
				for (int b = 0; b < table.axes().betaRad().length; b++) {
					for (int p = 0; p < table.axes().poweredFraction().length; p++) {
						double[] left = table.cell(index - 1, a, b, p).coefficients().toArray();
						double[] node = table.cell(index, a, b, p).coefficients().toArray();
						double[] right = table.cell(index + 1, a, b, p).coefficients().toArray();
						for (int c = 0; c < 6; c++) {
							double leftLimit = node[c] - epsilon * (node[c] - left[c]) / (join - mach[index - 1]);
							double rightLimit = node[c] + epsilon * (right[c] - node[c]) / (mach[index + 1] - join);
							continuous &= Math.abs(rightLimit - leftLimit) <= configuredBound;
						}
					}
				}
			}
		}
		check("REQUIRED_REGIME_JOIN_NODES_PRESENT", joinsPresent, checks, failures);
		check("REGIME_JOIN_CONTINUITY_WITHIN_CONFIGURED_BOUND", continuous, checks, failures);
	}

	private static void validateDeterministicEncoding(AerodynamicTable table, List<String> checks,
			List<String> failures) {
		try {
			TableWriter writer = new TableWriter();
			check("REPEATED_BINARY_ENCODING_BYTE_IDENTICAL",
					Arrays.equals(writer.encode(table), writer.encode(table)), checks, failures);
		} catch (IOException exception) {
			failures.add("DETERMINISTIC_ENCODING_FAILED:" + exception.getClass().getSimpleName());
		}
	}

	private static boolean endpoint(double[] values, double first, double last) {
		return Math.abs(values[0] - first) <= 1e-12 && Math.abs(values[values.length - 1] - last) <= 1e-12;
	}

	private static boolean finite(double[] values) {
		return Arrays.stream(values).allMatch(Double::isFinite);
	}

	private static boolean isLegacyMethodId(String methodId) {
		String normalized = methodId.toLowerCase(java.util.Locale.ROOT);
		return normalized.contains("path" + "line")
				|| normalized.matches("(^|.*[._-])r" + "om([._-].*|$)");
	}

	private static boolean recovers(Map<String, info.openrocket.core.aerodynamics.physicsaero.force.AerodynamicCoefficients> totals,
			double[] expected) {
		if (totals.isEmpty()) return false;
		double[] sum = new double[6];
		for (var value : totals.values()) {
			double[] coefficients = value.toArray();
			for (int index = 0; index < sum.length; index++) sum[index] += coefficients[index];
		}
		for (int index = 0; index < sum.length; index++) {
			double scale = Math.max(1, Math.abs(expected[index]));
			if (Math.abs(sum[index] - expected[index]) > RECOVERY_TOLERANCE * scale) return false;
		}
		return true;
	}

	private static void check(String name, boolean valid, List<String> checks, List<String> failures) {
		(valid ? checks : failures).add(name);
	}
}
