package info.openrocket.core.tuning;

import java.util.ArrayList;
import java.util.List;

public class PhaseTwoRunConfig {
	private double sampleRateHz = 20.0;
	private String interpolationMode = InterpolationMode.CUBIC_HERMITE.name();
	private String telemetryInterpolationMode;
	// Legacy field retained for older JSON files. Phase III now runs native airbrakes in-process.
	private String pluginJarPath;
	// Legacy field retained for older JSON files. No external airbrakes process is started anymore.
	private int pluginTimeoutSeconds = 120;
	private List<PhaseTwoDatasetConfig> datasets = new ArrayList<>();

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

	public String getPluginJarPath() {
		return pluginJarPath;
	}

	public int getPluginTimeoutSeconds() {
		return pluginTimeoutSeconds;
	}

	public List<PhaseTwoDatasetConfig> getDatasets() {
		return datasets;
	}
}
