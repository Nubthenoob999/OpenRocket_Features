package info.openrocket.core.tuning;

import java.util.ArrayList;
import java.util.List;

public class PhaseThreeBatchRunConfig {
	private double sampleRateHz = 20.0;
	private String interpolationMode = InterpolationMode.CUBIC_HERMITE.name();
	private String telemetryInterpolationMode;
	private List<PhaseThreeBatchDatasetConfig> datasets = new ArrayList<>();

	public double getSampleRateHz() {
		return sampleRateHz;
	}

	public InterpolationMode getTelemetryInterpolationMode() {
		String configured = telemetryInterpolationMode;
		if (configured == null || configured.isBlank()) {
			configured = interpolationMode;
		}
		return InterpolationMode.valueOf(configured);
	}

	public InterpolationMode getInterpolationMode() {
		return getTelemetryInterpolationMode();
	}

	public List<PhaseThreeBatchDatasetConfig> getDatasets() {
		return datasets;
	}
}
