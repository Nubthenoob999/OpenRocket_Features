package info.openrocket.core.aerodynamics.physicsaero.config;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import info.openrocket.core.aerodynamics.physicsaero.flow.PoweredFlowState;

/** Canonical, machine-independent fingerprint of every table-generation choice. */
public final class PhysicsAeroSettingsFingerprint {
	public static final String FINGERPRINT_SCHEMA = "physics-aero-settings/3";

	public record Input(SamplingConfiguration sampling, List<String> refinementRules,
			PhysicsConfiguration physics, NumericalTolerances tolerances,
			String generationFallbackPolicy, String codeVersion,
			String correlationRegistryVersion, PoweredFlowState[] poweredStates) {
		public Input {
			if (sampling == null || physics == null || tolerances == null) {
				throw new IllegalArgumentException("settings fingerprint configuration required");
			}
			refinementRules = List.copyOf(refinementRules == null ? List.of() : refinementRules);
			generationFallbackPolicy = required(generationFallbackPolicy);
			codeVersion = required(codeVersion);
			correlationRegistryVersion = required(correlationRegistryVersion);
			poweredStates = poweredStates == null ? new PoweredFlowState[0] : poweredStates.clone();
			boolean includesPowered = java.util.Arrays.stream(poweredStates)
					.anyMatch(PoweredFlowState::powered);
			if (physics.powered() != includesPowered) {
				throw new IllegalArgumentException("powered variant and propulsion inputs disagree");
			}
			if (includesPowered && (poweredStates.length < 2 || poweredStates[0].poweredFraction() != 0)) {
				throw new IllegalArgumentException("powered tables must include coast state first");
			}
		}

		@Override public PoweredFlowState[] poweredStates() { return poweredStates.clone(); }
	}

	private PhysicsAeroSettingsFingerprint() { }

	public static String hash(Input input) {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (DataOutputStream out = new DataOutputStream(bytes)) {
				write(out, FINGERPRINT_SCHEMA);
				writeAxis(out, input.sampling().mach());
				writeAxis(out, input.sampling().alphaRad());
				writeAxis(out, input.sampling().betaRad());
				List<String> refinements = new ArrayList<>(input.refinementRules());
				refinements.sort(String::compareTo);
				writeStrings(out, refinements);
				write(out, input.physics().wallTemperatureModel());
				out.writeLong(bits(input.physics().roughnessM()));
				out.writeBoolean(input.physics().powered());
				out.writeBoolean(input.physics().forceTurbulentBoundaryLayer());
				List<String> methods = input.physics().enabledMethods().stream()
						.map(Object::toString).sorted().toList();
				writeStrings(out, methods);
				NumericalTolerances t = input.tolerances();
				for (double value : new double[] {t.angleConsistencyRad(), t.rootResidual(), t.geometryM(),
						t.interpolation(), t.cornerAngleThresholdRad(), t.cornerPressureSignificance()}) {
					out.writeLong(bits(value));
				}
				out.writeInt(t.maximumRootIterations());
				write(out, input.generationFallbackPolicy());
				write(out, input.codeVersion());
				write(out, input.correlationRegistryVersion());
				PoweredFlowState[] powered = input.poweredStates();
				out.writeInt(powered.length);
				for (PoweredFlowState state : powered) {
					for (double value : new double[] {state.poweredFraction(), state.thrustN(),
							state.nozzleExitMach(), state.nozzleExitAreaM2(), state.nozzleExitPressurePa(),
							state.exhaustGamma(), state.ambientPressurePa()}) out.writeLong(bits(value));
					write(out, state.resolution().name());
				}
			}
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
		} catch (IOException exception) {
			throw new IllegalStateException(exception);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 unavailable", exception);
		}
	}

	private static void writeAxis(DataOutputStream out, double[] values) throws IOException {
		out.writeInt(values.length);
		for (double value : values) out.writeLong(bits(value));
	}

	private static void writeStrings(DataOutputStream out, List<String> values) throws IOException {
		out.writeInt(values.size());
		for (String value : values) write(out, value);
	}

	private static void write(DataOutputStream out, String value) throws IOException {
		byte[] encoded = required(value).getBytes(StandardCharsets.UTF_8);
		out.writeInt(encoded.length);
		out.write(encoded);
	}

	private static long bits(double value) {
		return Double.doubleToLongBits(value == 0 ? 0 : value);
	}

	private static String required(String value) {
		if (value == null || value.isBlank()) throw new IllegalArgumentException("blank fingerprint value");
		return value.trim();
	}
}
