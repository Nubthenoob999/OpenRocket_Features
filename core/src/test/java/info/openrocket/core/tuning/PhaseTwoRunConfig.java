package info.openrocket.core.tuning;

import java.util.ArrayList;
import java.util.List;

public class PhaseTwoRunConfig {
	private double sampleRateHz = 20.0;
	private String interpolationMode = InterpolationMode.CUBIC_HERMITE.name();
	private String pluginJarPath;
	private int pluginTimeoutSeconds = 120;
	private List<PhaseTwoDatasetConfig> datasets = new ArrayList<>();

	public double getSampleRateHz() {
		return sampleRateHz;
	}

	public InterpolationMode getInterpolationMode() {
		return InterpolationMode.valueOf(interpolationMode);
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