package info.openrocket.core.file.openrocket.importt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.document.Simulation.Status;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroMode;
import info.openrocket.core.file.DocumentLoadingContext;
import info.openrocket.core.file.simplesax.AbstractElementHandler;
import info.openrocket.core.file.simplesax.ElementHandler;
import info.openrocket.core.file.simplesax.PlainTextHandler;
import info.openrocket.core.rocketcomponent.FlightConfigurationId;
import info.openrocket.core.simulation.FlightData;
import info.openrocket.core.simulation.SimulationOptions;
import info.openrocket.core.simulation.extension.SimulationExtension;
import info.openrocket.core.simulation.extension.SimulationExtensionProvider;
import info.openrocket.core.simulation.extension.impl.JavaCode;
import info.openrocket.core.startup.Application;
import info.openrocket.core.util.Config;
import info.openrocket.core.util.StringUtils;

import com.google.inject.Key;

class SingleSimulationHandler extends AbstractElementHandler {
	private static final String AIRBRAKE_EXTENSION_ID = "info.openrocket.core.airbrakesplugin.AirbrakeExtension";
	private static final String LEGACY_AIRBRAKE_EXTENSION_ID = "com.airbrakesplugin.AirbrakeExtension";
	private static final String AIRBRAKE_CONFIG_PREFIX = "airbrakes.";

	private final DocumentLoadingContext context;

	private final OpenRocketDocument doc;

	private String name;

	private SimulationConditionsHandler conditionHandler;
	private ConfigHandler configHandler;
	private FlightDataHandler dataHandler;
	private boolean legacyAerodynamicsEncountered;

	private final List<SimulationExtension> extensions = new ArrayList<>();

	public SingleSimulationHandler(OpenRocketDocument doc, DocumentLoadingContext context) {
		this.doc = doc;
		this.context = context;
	}

	public OpenRocketDocument getDocument() {
		return doc;
	}

	@Override
	public ElementHandler openElement(String element, HashMap<String, String> attributes,
			WarningSet warnings) {

		if (element.equals("name") || element.equals("simulator") ||
				element.equals("calculator") || element.equals("listener") || element.equals("romdragsurface") ||
				element.equals("romdragsurface4d")) {
			return PlainTextHandler.INSTANCE;
		} else if (element.equals("conditions")) {
			conditionHandler = new SimulationConditionsHandler(doc.getRocket(), context);
			return conditionHandler;
		} else if (element.equals("extension")) {
			configHandler = new ConfigHandler();
			return configHandler;
		} else if (element.equals("flightdata")) {
			dataHandler = new FlightDataHandler(this, context);
			return dataHandler;
		} else {
			warnings.add("Unknown element '" + element + "', ignoring.");
			return null;
		}
	}

	@Override
	public void closeElement(String element, HashMap<String, String> attributes,
			String content, WarningSet warnings) {

		if (element.equals("name")) {
			name = content;
		} else if (element.equals("simulator")) {
			if (!content.trim().equals("RK4Simulator")) {
				warnings.add("Unknown simulator '" + content.trim() + "' specified, ignoring.");
			}
		} else if (element.equals("calculator")) {
			String calc = content.trim();
			if (calc.equals("RomAerodynamicCalculator") || calc.equals("PathlineROMCalculator")) {
				legacyAerodynamicsEncountered = true;
				warnings.add(SimulationConditionsHandler.LEGACY_AERODYNAMICS_WARNING);
			} else if (!calc.equals("BarrowmanCalculator") && !calc.equals("PhysicsAeroAerodynamicCalculator")) {
				warnings.add("Unknown calculator '" + content.trim() + "' specified, ignoring.");
			}
		} else if (element.equals("listener") && content.trim().length() > 0) {
			extensions.add(compatibilityExtension(content.trim()));
		} else if (element.equals("romdragsurface") || element.equals("romdragsurface4d")) {
			legacyAerodynamicsEncountered = true;
			warnings.add(SimulationConditionsHandler.LEGACY_AERODYNAMICS_WARNING);
		} else if (element.equals("extension") && !StringUtils.isEmpty(attributes.get("extensionid"))) {
			String id = attributes.get("extensionid");
			id = id.replace("net.sf.openrocket", "info.openrocket.core");
			id = id.replace("com.hprc.montecarlo", "info.openrocket.core.montecarlo");
			if (migrateAirbrakesExtension(id, configHandler.getConfig())) {
				return;
			}
			SimulationExtension extension = null;
			Set<SimulationExtensionProvider> extensionProviders = Application.getInjector()
					.getInstance(new Key<>() {
					});
			for (SimulationExtensionProvider p : extensionProviders) {
				if (p.getIds().contains(id)) {
					extension = p.getInstance(id);
				}
			}
			if (extension != null) {
				extension.setConfig(configHandler.getConfig());
				extensions.add(extension);
			} else {
				warnings.add("Simulation extension with id '" + id + "' not found.");
			}
		}

	}

	@Override
	public void endHandler(String element, HashMap<String, String> attributes,
			String content, WarningSet warnings) {

		String s = attributes.get("status");
		Simulation.Status status = (Status) DocumentConfig.findEnum(s, Simulation.Status.class);
		if (status == null) {
			warnings.add("Simulation status unknown, assuming outdated.");
			status = Simulation.Status.OUTDATED;
		}

		SimulationOptions options;
		FlightConfigurationId idToSet = FlightConfigurationId.ERROR_FCID;
		if (conditionHandler != null) {
			options = conditionHandler.getConditions();
			idToSet = conditionHandler.idToSet;
		} else {
			warnings.add("Simulation conditions not defined, using defaults.");
			options = new SimulationOptions();
		}

		if (legacyAerodynamicsEncountered) {
			options.setPhysicsAeroMode(PhysicsAeroMode.OFF);
		}

		if (name == null)
			name = "Simulation";

		// If the simulation was saved with flight data (which may just be a summary)
		// mark it as loaded from the file else as not simulated. If outdated data was
		// saved,
		// it'll be marked as outdated (creating a new status for "loaded but outdated"
		// seems
		// excessive, and the fact that it's outdated is the more important)
		FlightData data;
		if (dataHandler == null)
			data = null;
		else
			data = dataHandler.getFlightData();

		if (data == null) {
			status = Status.NOT_SIMULATED;
		} else if (status != Status.OUTDATED) {
			status = Status.LOADED;
		}

		Simulation simulation = new Simulation(doc, doc.getRocket(), status, name,
				options, extensions, data);
		simulation.setFlightConfigurationId(idToSet);

		doc.addSimulation(simulation);
	}

	/**
	 * @return the warning set associated with this simulation
	 */
	public WarningSet getWarningSet() {
		return dataHandler.getWarningSet();
	}

	private SimulationExtension compatibilityExtension(String className) {
		JavaCode extension = Application.getInjector().getInstance(JavaCode.class);
		extension.setClassName(className);
		return extension;
	}

	private boolean migrateAirbrakesExtension(String extensionId, Config config) {
		if (!AIRBRAKE_EXTENSION_ID.equals(extensionId) && !LEGACY_AIRBRAKE_EXTENSION_ID.equals(extensionId)) {
			return false;
		}
		if (conditionHandler == null) {
			return false;
		}

		SimulationOptions options = conditionHandler.getConditions();
		options.setAirbrakesEnabled(true);
		options.setCfdDataFilePath(getString(config, "cfdDataFilePath", ""));
		options.setReferenceArea(getDouble(config, "referenceArea", 0.0));
		options.setReferenceLength(getDouble(config, "referenceLength", 0.0));
		options.setMaxDeploymentRate(getDouble(config, "maxDeploymentRate", 40.0));
		options.setTargetApogee(getDouble(config, "targetApogee", 0.0));
		options.setMaxMachForDeployment(getDouble(config, "maxMachForDeployment", 1.0));
		options.setAlwaysOpenMode(getBoolean(config, "alwaysOpenMode", false));
		options.setAlwaysOpenPercentage(clamp01(getDouble(config, "alwaysOpenPercentage", 1.0)));
		options.setApogeeToleranceMeters(getDouble(config, "apogeeToleranceMeters", 5.0));
		options.setDeployAfterBurnoutOnly(getBoolean(config, "deployAfterBurnoutOnly", false));
		options.setDeployAfterBurnoutDelayS(Math.max(0.0, getDouble(config, "deployAfterBurnoutDelayS", 0.0)));
		options.setDebugEnabled(getBoolean(config, "debugEnabled", false));
		options.setDbgAlwaysOpen(getBoolean(config, "dbgAlwaysOpen", false));
		options.setDbgForcedDeployFrac(clamp01(getDouble(config, "dbgForcedDeployFrac", 1.0)));
		options.setDbgTracePredictor(getBoolean(config, "dbgTracePredictor", true));
		options.setDbgTraceController(getBoolean(config, "dbgTraceController", true));
		options.setDbgWriteCsv(getBoolean(config, "dbgWriteCsv", true));
		options.setDbgCsvDir(getString(config, "dbgCsvDir", ""));
		options.setDbgShowConsole(getBoolean(config, "dbgShowConsole", false));
		return true;
	}

	private static String getString(Config config, String key, String defaultValue) {
		return config.getString(AIRBRAKE_CONFIG_PREFIX + key, config.getString(key, defaultValue));
	}

	private static double getDouble(Config config, String key, double defaultValue) {
		return config.getDouble(AIRBRAKE_CONFIG_PREFIX + key, config.getDouble(key, defaultValue));
	}

	private static boolean getBoolean(Config config, String key, boolean defaultValue) {
		return config.getBoolean(AIRBRAKE_CONFIG_PREFIX + key, config.getBoolean(key, defaultValue));
	}

	private static double clamp01(double value) {
		if (!Double.isFinite(value)) {
			return 0.0;
		}
		return Math.max(0.0, Math.min(1.0, value));
	}

}
