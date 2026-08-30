package info.openrocket.core.simulation.montecarlo;

import java.util.EnumMap;
import java.util.Random;

import info.openrocket.core.montecarlo.RandomStreamManager;

/**
 * Deterministically produces independent samples.
 * <p>
 * Each parameter and the per-flight simulation seed use separate random streams derived
 * from the master seed. Changing one parameter therefore does not shift other samples.
 */
public final class MonteCarloSampler {
	private final MonteCarloSettings settings;
	private final RandomStreamManager randomStreams;

	public MonteCarloSampler(MonteCarloSettings settings) {
		this.settings = java.util.Objects.requireNonNull(settings, "settings");
		this.randomStreams = new RandomStreamManager(settings.getSeed());
	}

	public MonteCarloSample nextSample(int runNumber) {
		if (runNumber < 1) {
			throw new IllegalArgumentException("Monte Carlo run numbers start at one");
		}

		EnumMap<MonteCarloParameter, Double> values = new EnumMap<>(MonteCarloParameter.class);
		for (MonteCarloParameter parameter : MonteCarloParameter.values()) {
			UncertaintySpec uncertainty = settings.getUncertainty(parameter);
			Random parameterStream = randomStreams.stream(
					"parameter:" + parameter.name() + ":run:" + runNumber);
			double variation = uncertainty.distribution().sample(parameterStream,
					uncertainty.spread());
			values.put(parameter, variation);
		}
		int simulationSeed = randomStreams.stream("simulation:run:" + runNumber).nextInt();
		return new MonteCarloSample(runNumber, simulationSeed, values);
	}
}
