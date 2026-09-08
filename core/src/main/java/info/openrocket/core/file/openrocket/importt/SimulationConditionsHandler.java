package info.openrocket.core.file.openrocket.importt;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.function.Consumer;

import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.file.DocumentLoadingContext;
import info.openrocket.core.file.simplesax.AbstractElementHandler;
import info.openrocket.core.file.simplesax.ElementHandler;
import info.openrocket.core.file.simplesax.PlainTextHandler;
import info.openrocket.core.models.wind.WindModelType;
import info.openrocket.core.rocketcomponent.FlightConfigurationId;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.simulation.SimulationOptions;
import info.openrocket.core.simulation.SimulationStepperMethod;
import info.openrocket.core.util.GeodeticComputationStrategy;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroMode;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroSettings;
import java.util.List;

class SimulationConditionsHandler extends AbstractElementHandler {
	static final String LEGACY_AERODYNAMICS_WARNING =
			"Legacy ROM/pathline settings were ignored. Build a Physics-Based Aerodynamics table before enabling the experimental model.";

	private final DocumentLoadingContext context;
	public FlightConfigurationId idToSet = FlightConfigurationId.ERROR_FCID;
	private final SimulationOptions options;
	private AtmosphereHandler atmosphereHandler;
	private WindHandler windHandler;
	private GravityHandler gravityHandler;
	private CsvLookupHandler dragLookupHandler;
	private CsvLookupHandler stabilityLookupHandler;
	private boolean legacyAerodynamicsEncountered;

	public SimulationConditionsHandler(Rocket rocket, DocumentLoadingContext context) {
		this.context = context;
		options = new SimulationOptions();
		// Set up default loading settings (which may differ from the new defaults)
		options.setGeodeticComputation(GeodeticComputationStrategy.FLAT);
	}

	public SimulationOptions getConditions() {
		return options;
	}

	@Override
	public ElementHandler openElement(String element, HashMap<String, String> attributes,
			WarningSet warnings) {
		if (element.equals("wind")) {
			windHandler = new WindHandler(attributes.get("model"), options, attributes);
			return windHandler;
		} else if (element.equals("atmosphere")) {
			atmosphereHandler = new AtmosphereHandler(attributes.get("model"), context);
			return atmosphereHandler;
		} else if (element.equals("gravity")) {
			gravityHandler = new GravityHandler(attributes.get("model"));
			return gravityHandler;
		} else if (element.equals("draglookup")) {
			dragLookupHandler = new CsvLookupHandler(options, List.of("cd"), true);
			return dragLookupHandler;
		} else if (element.equals("stabilitylookup")) {
			stabilityLookupHandler = new CsvLookupHandler(options, List.of("cn", "cm", "cp"), false);
			return stabilityLookupHandler;
		}
		return PlainTextHandler.INSTANCE;
	}

	@Override
	public void closeElement(String element, HashMap<String, String> attributes,
			String content, WarningSet warnings) {
		if (isLegacyAerodynamicsElement(element)) {
			legacyAerodynamicsEncountered = true;
			warnings.add(LEGACY_AERODYNAMICS_WARNING);
			return;
		}

		double d = Double.NaN;
		try {
			d = Double.parseDouble(content);
		} catch (NumberFormatException ignore) {
		}
		switch (element) {
			case "configid" -> this.idToSet = new FlightConfigurationId(content);
			case "launchrodlength" -> {
				if (Double.isNaN(d)) {
					warnings.add("Illegal launch rod length defined, ignoring.");
				} else {
					options.setLaunchRodLength(d);
				}
			}
			case "launchintowind" -> {
				options.setLaunchIntoWind(Boolean.parseBoolean(content));
			}
			case "launchrodangle" -> {
				if (Double.isNaN(d)) {
					warnings.add("Illegal launch rod angle defined, ignoring.");
				} else {
					options.setLaunchRodAngle(d * Math.PI / 180);
				}
			}
			case "launchroddirection" -> {
				if (Double.isNaN(d)) {
					warnings.add("Illegal launch rod direction defined, ignoring.");
				} else {
					options.setLaunchRodDirection(d * 2.0 * Math.PI / 360);
				}
			}

			// TODO: remove once support for OR 23.09 and prior is dropped
			case "windaverage" -> {
				if (Double.isNaN(d)) {
					warnings.add("Illegal average windspeed defined, ignoring.");
				} else {
					options.getAverageWindModel().setAverage(d);
				}
			}
			case "windturbulence" -> {
				if (Double.isNaN(d)) {
					warnings.add("Illegal wind turbulence intensity defined, ignoring.");
				} else {
					options.getAverageWindModel().setTurbulenceIntensity(d);
				}
			}
			case "winddirection" -> {
				if (Double.isNaN(d)) {
					warnings.add("Illegal wind direction defined, ignoring.");
				} else {
					options.getAverageWindModel().setDirection(d);
				}
			}

			case "wind" -> windHandler.storeSettings(options, warnings);
			case "windmodeltype" -> {
				options.setWindModelType(WindModelType.fromString(content));
			}
			case "liveweatherselected" -> options.setLiveWeatherDataSelected(Boolean.parseBoolean(content));
			case "liveweatherdate" -> options.setLiveWeatherLaunchDate(content);
			case "liveweathertime" -> options.setLiveWeatherLaunchTime(content);
			case "weathercockingcompensationenabled" ->
					options.setWeathercockingCompensationEnabled(Boolean.parseBoolean(content));
			case "airbrakesenabled" -> options.setAirbrakesEnabled(Boolean.parseBoolean(content));
			case "airbrakescfddatafilepath" -> options.setCfdDataFilePath(content);
			case "airbrakesreferencearea" -> {
				if (Double.isNaN(d)) {
					warnings.add("Illegal airbrake reference area defined, ignoring.");
				} else {
					options.setReferenceArea(d);
				}
			}
			case "airbrakesreferencelength" -> {
				if (Double.isNaN(d)) {
					warnings.add("Illegal airbrake reference length defined, ignoring.");
				} else {
					options.setReferenceLength(d);
				}
			}
			case "airbrakesmaxdeploymentrate" -> {
				if (Double.isNaN(d)) {
					warnings.add("Illegal airbrake max deployment rate defined, ignoring.");
				} else {
					options.setMaxDeploymentRate(d);
				}
			}
			case "airbrakestargetapogee" -> {
				if (Double.isNaN(d)) {
					warnings.add("Illegal airbrake target apogee defined, ignoring.");
				} else {
					options.setTargetApogee(d);
				}
			}
			case "airbrakesmaxmachfordeployment" -> {
				if (Double.isNaN(d)) {
					warnings.add("Illegal airbrake max Mach defined, ignoring.");
				} else {
					options.setMaxMachForDeployment(d);
				}
			}
			case "airbrakesalwaysopenmode" -> options.setAlwaysOpenMode(Boolean.parseBoolean(content));
			case "airbrakesalwaysopenpercentage" -> {
				if (Double.isNaN(d)) {
					warnings.add("Illegal airbrake always-open percentage defined, ignoring.");
				} else {
					options.setAlwaysOpenPercentage(d);
				}
			}
			case "airbrakesapogeetolerancemeters" -> {
				if (Double.isNaN(d)) {
					warnings.add("Illegal airbrake apogee tolerance defined, ignoring.");
				} else {
					options.setApogeeToleranceMeters(d);
				}
			}
			case "airbrakesdeployafterburnoutonly" -> options.setDeployAfterBurnoutOnly(Boolean.parseBoolean(content));
			case "airbrakesdeployafterburnoutdelays" -> {
				if (Double.isNaN(d)) {
					warnings.add("Illegal airbrake burnout delay defined, ignoring.");
				} else {
					options.setDeployAfterBurnoutDelayS(d);
				}
			}
			case "airbrakesdebugenabled" -> options.setDebugEnabled(Boolean.parseBoolean(content));
			case "airbrakesdbgalwaysopen" -> options.setDbgAlwaysOpen(Boolean.parseBoolean(content));
			case "airbrakesdbgforceddeployfrac" -> {
				if (Double.isNaN(d)) {
					warnings.add("Illegal airbrake debug deploy fraction defined, ignoring.");
				} else {
					options.setDbgForcedDeployFrac(d);
				}
			}
			case "airbrakesdbgtracepredictor" -> options.setDbgTracePredictor(Boolean.parseBoolean(content));
			case "airbrakesdbgtracecontroller" -> options.setDbgTraceController(Boolean.parseBoolean(content));
			case "airbrakesdbgwritecsv" -> options.setDbgWriteCsv(Boolean.parseBoolean(content));
			case "airbrakesdbgcsvdir" -> options.setDbgCsvDir(content);
			case "airbrakesdbgshowconsole" -> options.setDbgShowConsole(Boolean.parseBoolean(content));

			case "launchaltitude" -> {
				if (Double.isNaN(d)) {
					warnings.add("Illegal launch altitude defined, ignoring.");
				} else {
					options.setLaunchAltitude(d);
				}
			}
			case "launchlatitude" -> {
				if (Double.isNaN(d)) {
					warnings.add("Illegal launch latitude defined, ignoring.");
				} else {
					options.setLaunchLatitude(d);
				}
			}
			case "launchlongitude" -> {
				if (Double.isNaN(d)) {
					warnings.add("Illegal launch longitude.");
				} else {
					options.setLaunchLongitude(d);
				}
			}
			case "geodeticmethod" -> {
				GeodeticComputationStrategy gcs = (GeodeticComputationStrategy) DocumentConfig.findEnum(content,
						GeodeticComputationStrategy.class);
				if (gcs != null) {
					options.setGeodeticComputation(gcs);
				} else {
					warnings.add("Unknown geodetic computation method '" + content + "'");
				}
			}
			case "simulationsteppermethod" -> {
				SimulationStepperMethod stepperMethod = (SimulationStepperMethod) DocumentConfig.findEnum(content,
						SimulationStepperMethod.class);
				if (stepperMethod != null) {
					options.setSimulationStepperMethodChoice(stepperMethod);
				} else {
					warnings.add("Unknown Simulation Stepper '" + content + "'");
				}
			}
			case "physicsaeromode" -> {
				PhysicsAeroMode mode = "hybrid".equalsIgnoreCase(content.trim())
						? PhysicsAeroMode.DIAGNOSTIC_HYBRID
						: (PhysicsAeroMode) DocumentConfig.findEnum(content, PhysicsAeroMode.class);
				if (mode != null) updatePhysicsAeroSettings(settings -> settings.setMode(mode));
				else warnings.add("Unknown physics-aero mode '" + content + "', ignoring.");
			}
			case "physicsaerogeometryhash" -> updatePhysicsAeroSettings(settings -> settings.setGeometryHash(content));
			case "physicsaerosettingshash" -> updatePhysicsAeroSettings(settings -> settings.setSettingsHash(content));
			case "physicsaerotablehash" -> updatePhysicsAeroSettings(settings -> settings.setTableContentHash(content));
			case "physicsaeroforceturbulentboundarylayer" -> {
				String normalized = content.trim();
				if ("true".equalsIgnoreCase(normalized) || "false".equalsIgnoreCase(normalized)) {
					updatePhysicsAeroSettings(settings ->
							settings.setForceTurbulentBoundaryLayer(Boolean.parseBoolean(normalized)));
				} else {
					warnings.add("Illegal fully turbulent boundary-layer setting '" + content
							+ "', ignoring.");
				}
			}
			case "atmosphere" -> atmosphereHandler.storeSettings(options, warnings);
			case "gravity" -> {
				if (gravityHandler != null) {
					gravityHandler.storeSettings(options, warnings);
				}
			}
			case "timestep" -> {
				if (Double.isNaN(d) || d <= 0) {
					warnings.add("Illegal time step defined, ignoring.");
				} else {
					options.setTimeStep(d);
				}
			}
			case "maxtime" -> {
				if (Double.isNaN(d) || d <= 0) {
					warnings.add("Illegal max simulation time defined, ignoring.");
				} else {
					options.setMaxSimulationTime(d);
				}
			}
			case "recoveryspeedwarning" -> {
				if (!Double.isNaN(d) && d > 0) {
					options.setRecoverySpeedWarning(d);
				}
			}
			case "drogueLowspeedwarning" -> {
				if (!Double.isNaN(d) && d > 0) {
					options.setDrogueLowSpeedWarning(d);
				}
			}
			case "recoverydroguemainhighspeedwarning" -> {
				if (!Double.isNaN(d) && d > 0) {
					options.setRecoveryDrogueMainHighSpeedWarning(d);
				}
			}
			case "recoverydroguemainlowspeedwarning" -> {
				if (!Double.isNaN(d) && d > 0) {
					options.setRecoveryDrogueMainLowSpeedWarning(d);
				}
			}
			// draglookupcsv and stabilitylookupcsv are now handled by CsvLookupHandler
			// This case is for backward compatibility with old file format (simple text content)
			case "draglookupcsv" -> {
				// Only handle if we didn't use the CsvLookupHandler (old format)
				if (dragLookupHandler == null) {
					String trimmed = content.trim();
					if (!trimmed.isEmpty()) {
						try {
							options.setDragLookupCsvPath(Path.of(trimmed));
						} catch (RuntimeException ex) {
							warnings.add("Failed to load drag lookup CSV '" + trimmed + "', ignoring. Reason: " + ex.getMessage());
						}
					}
				}
			}
			case "stabilitylookupcsv" -> {
				// Only handle if we didn't use the CsvLookupHandler (old format)
				if (stabilityLookupHandler == null) {
					String trimmed = content.trim();
					if (!trimmed.isEmpty()) {
						try {
							options.setStabilityLookupCsvPath(Path.of(trimmed));
						} catch (RuntimeException ex) {
							warnings.add("Failed to load stability lookup CSV '" + trimmed + "', ignoring. Reason: " + ex.getMessage());
						}
					}
				}
			}
		}
	}

	private void updatePhysicsAeroSettings(Consumer<PhysicsAeroSettings> updater) {
		PhysicsAeroSettings settings = options.getPhysicsAeroSettings();
		updater.accept(settings);
		options.setPhysicsAeroSettings(settings);
	}

	@Override
	public void endHandler(String element, HashMap<String, String> attributes, String content, WarningSet warnings) {
		if (legacyAerodynamicsEncountered) {
			updatePhysicsAeroSettings(settings -> settings.setMode(PhysicsAeroMode.OFF));
		}
	}

	private static boolean isLegacyAerodynamicsElement(String element) {
		return element != null && element.startsWith("rom");
	}
}
