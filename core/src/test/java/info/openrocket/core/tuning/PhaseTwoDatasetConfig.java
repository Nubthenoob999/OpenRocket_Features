package info.openrocket.core.tuning;

import java.util.ArrayList;
import java.util.List;

public class PhaseTwoDatasetConfig {
	private String name;
	private String referenceCsv;
	private String candidateCsv;
	private String orkPath;
	private boolean airbrakeEnabled;
	private PluginConfig plugin = new PluginConfig();

	public String getName() {
		return name;
	}

	public String getReferenceCsv() {
		return referenceCsv;
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