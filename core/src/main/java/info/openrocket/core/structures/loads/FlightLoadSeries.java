package info.openrocket.core.structures.loads;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class FlightLoadSeries {
	private static final FlightLoadCase EMPTY_CASE = new FlightLoadCase(Double.NaN, Double.NaN, Double.NaN, Double.NaN,
			Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
			Double.NaN);

	private final List<FlightLoadCase> cases;

	public FlightLoadSeries(List<FlightLoadCase> cases) {
		this.cases = Collections.unmodifiableList(new ArrayList<>(cases));
	}

	public List<FlightLoadCase> getCases() {
		return cases;
	}

	public boolean isEmpty() {
		return cases.isEmpty();
	}

	public FlightLoadCase getMaxDynamicPressureCase() {
		return maxBy(loadCase -> loadCase.getDynamicPressure());
	}

	public FlightLoadCase getMaxVelocityCase() {
		return maxBy(loadCase -> Math.abs(loadCase.getVelocity()));
	}

	public FlightLoadCase getMaxAxialLoadCase() {
		return maxBy(AxialLoadModel::conservativeAxialLoad);
	}

	public FlightLoadCase getWorstTubeStressCase() {
		return maxBy(loadCase -> Math.max(loadCase.getDynamicPressure(), 0.0) +
				AxialLoadModel.conservativeAxialLoad(loadCase));
	}

	private FlightLoadCase maxBy(ValueExtractor extractor) {
		return cases.stream()
				.filter(loadCase -> Double.isFinite(extractor.get(loadCase)))
				.max(Comparator.comparingDouble(extractor::get))
				.orElse(EMPTY_CASE);
	}

	private interface ValueExtractor {
		double get(FlightLoadCase loadCase);
	}
}
