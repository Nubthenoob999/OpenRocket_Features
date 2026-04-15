package info.openrocket.core.tuning;

import java.util.ArrayList;
import java.util.List;

public class PhaseTwoDatasetConfig {
	private static final double DEFAULT_WEATHERCOCKING_STABILITY_MIN_CALIBERS = 2.0;
	private static final double DEFAULT_WEATHERCOCKING_STABILITY_MASS_RATIO_MIN = 1.0;

	private String name;
	private String flightId;
	private String referenceCsv;
	private String truthCsv;
	private String candidateCsv;
	private String orkPath;
	private boolean airbrakeEnabled;
	private boolean weathercockingEnabled;
	private double weathercockingStabilityMinCalibers = DEFAULT_WEATHERCOCKING_STABILITY_MIN_CALIBERS;
	private double weathercockingStabilityMassRatioMin = DEFAULT_WEATHERCOCKING_STABILITY_MASS_RATIO_MIN;
	private double weathercockingCdGain;
	private Double apogeeTargetFtOverride;
	private boolean manualApogeeOnly;
	private PluginConfig plugin = new PluginConfig();

	public String getName() {
		return name;
	}

	public String getFlightId() {
		return flightId;
	}

	public String getReferenceCsv() {
		return referenceCsv;
	}

	public String getTruthCsv() {
		return truthCsv;
	}

	public String getCandidateCsv() {
		return candidateCsv;
	}

	public String getOrkPath() {
		return orkPath;
	}

	public boolean isAirbrakeEnabled() {
		return airbrakeEnabled;
	}

	public boolean isWeathercockingEnabled() {
		return weathercockingEnabled;
	}

	public double getWeathercockingStabilityMinCalibers() {
		if (!Double.isFinite(weathercockingStabilityMinCalibers) || weathercockingStabilityMinCalibers <= 0.0) {
			return DEFAULT_WEATHERCOCKING_STABILITY_MIN_CALIBERS;
		}
		return weathercockingStabilityMinCalibers;
	}

	public double getWeathercockingStabilityMassRatioMin() {
		if (!Double.isFinite(weathercockingStabilityMassRatioMin) || weathercockingStabilityMassRatioMin <= 0.0) {
			return DEFAULT_WEATHERCOCKING_STABILITY_MASS_RATIO_MIN;
		}
		return weathercockingStabilityMassRatioMin;
	}

	public double getWeathercockingCdGain() {
		return weathercockingCdGain;
	}

	public Double getApogeeTargetFtOverride() {
		if (apogeeTargetFtOverride == null || !Double.isFinite(apogeeTargetFtOverride)) {
			return null;
		}
		return apogeeTargetFtOverride;
	}

	public boolean isManualApogeeOnly() {
		return manualApogeeOnly;
	}

	public PluginConfig getPlugin() {
		return plugin;
	}

	public static class PluginConfig {
		private boolean enabled;
		private List<String> arguments = new ArrayList<>();
		private String argumentsFile;

		public boolean isEnabled() {
			return enabled;
		}

		public List<String> getArguments() {
			return arguments;
		}

		public String getArgumentsFile() {
			return argumentsFile;
		}
	}
}
