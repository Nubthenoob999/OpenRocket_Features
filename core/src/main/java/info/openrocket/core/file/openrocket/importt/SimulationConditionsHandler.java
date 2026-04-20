package info.openrocket.core.file.openrocket.importt;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.function.Consumer;

import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.file.DocumentLoadingContext;
import info.openrocket.core.file.simplesax.AbstractElementHandler;
import info.openrocket.core.file.simplesax.ElementHandler;
import info.openrocket.core.file.simplesax.PlainTextHandler;
import info.openrocket.core.aerodynamics.rom.RomFallbackMode;
import info.openrocket.core.aerodynamics.rom.RomMode;
import info.openrocket.core.aerodynamics.rom.RomSettings;
import info.openrocket.core.models.wind.WindModelType;
import info.openrocket.core.rocketcomponent.FlightConfigurationId;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.simulation.SimulationOptions;
import info.openrocket.core.util.GeodeticComputationStrategy;
import info.openrocket.core.simulation.SimulationStepperMethod;
import info.openrocket.core.aerodynamics.rom.RomSurfaceMode;
import java.util.List;

class SimulationConditionsHandler extends AbstractElementHandler {
	private final DocumentLoadingContext context;
	public FlightConfigurationId idToSet = FlightConfigurationId.ERROR_FCID;
	private final SimulationOptions options;
	private AtmosphereHandler atmosphereHandler;
	private WindHandler windHandler;
	private GravityHandler gravityHandler;
	private CsvLookupHandler dragLookupHandler;
	private CsvLookupHandler stabilityLookupHandler;
	private boolean romSurfaceModeSpecified;

	public SimulationConditionsHandler(Rocket rocket, DocumentLoadingContext context) {
		this.context = context;
		options = new SimulationOptions();
		// Set up default loading settings (which may differ from the new defaults)
		options.setGeodeticComputation(GeodeticComputationStrategy.FLAT);
	}

	public SimulationOptions getConditions() {
		return options;
	}

	public boolean wasRomSurfaceModeSpecified() {
		return romSurfaceModeSpecified;
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

		double d = Double.NaN;
		try {
			d = Double.parseDouble(content);
		} catch (NumberFormatException ignore) {
		}
		final double parsedValue = d;

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
			case "romsurfacemode" -> {
				options.setRomSurfaceMode(RomSurfaceMode.fromStorageValue(content));
				romSurfaceModeSpecified = true;
			}
			case "romenabled" -> updateRomSettings(settings -> settings.setEnabled(Boolean.parseBoolean(content)));
			case "rommode" -> {
				RomMode romMode = (RomMode) DocumentConfig.findEnum(content, RomMode.class);
				if (romMode != null) {
					updateRomSettings(settings -> settings.setMode(romMode));
				} else {
					warnings.add("Unknown ROM mode '" + content + "', ignoring.");
				}
			}
			case "romfallbackmode" -> {
				RomFallbackMode fallbackMode = (RomFallbackMode) DocumentConfig.findEnum(content, RomFallbackMode.class);
				if (fallbackMode != null) {
					updateRomSettings(settings -> settings.setFallbackMode(fallbackMode));
				} else {
					warnings.add("Unknown ROM fallback mode '" + content + "', ignoring.");
				}
			}
			case "romdiagnosticsenabled" -> updateRomSettings(
					settings -> settings.setDiagnosticsEnabled(Boolean.parseBoolean(content)));
			case "rombodymeridianseedcount" -> {
				if (Double.isNaN(parsedValue)) {
					warnings.add("Illegal ROM body meridian seed count defined, ignoring.");
				} else {
					updateRomSettings(settings -> settings.setBodyMeridianSeedCount((int) Math.round(parsedValue)));
				}
			}
			case "romfinsurfaceseedcount" -> {
				if (Double.isNaN(parsedValue)) {
					warnings.add("Illegal ROM fin surface seed count defined, ignoring.");
				} else {
					updateRomSettings(settings -> settings.setFinSurfaceSeedCount((int) Math.round(parsedValue)));
				}
			}
			case "romtransonicbandhalfwidth" -> {
				if (Double.isNaN(parsedValue)) {
					warnings.add("Illegal ROM transonic band half-width defined, ignoring.");
				} else {
					updateRomSettings(settings -> settings.setTransonicBandHalfWidth(parsedValue));
				}
			}
			case "romhighangledeg" -> {
				if (Double.isNaN(parsedValue)) {
					warnings.add("Illegal ROM high-angle threshold defined, ignoring.");
				} else {
					updateRomSettings(settings -> settings.setHighAngleDeg(parsedValue));
				}
			}
			case "rommaxtrustedseparationfraction" -> {
				if (Double.isNaN(parsedValue)) {
					warnings.add("Illegal ROM separation fraction defined, ignoring.");
				} else {
					updateRomSettings(settings -> settings.setMaxTrustedSeparationFraction(parsedValue));
				}
			}
			case "romprestepmach" -> {
				if (Double.isNaN(parsedValue)) {
					warnings.add("Illegal ROM pre-step Mach defined, ignoring.");
				} else {
					updateRomSettings(settings -> settings.setPrestepMach(parsedValue));
				}
			}
			case "romprestepaoadeg" -> {
				if (Double.isNaN(parsedValue)) {
					warnings.add("Illegal ROM pre-step angle of attack defined, ignoring.");
				} else {
					updateRomSettings(settings -> settings.setPrestepAngleOfAttackDeg(parsedValue));
				}
			}
			case "romprestepthetadeg" -> {
				if (Double.isNaN(parsedValue)) {
					warnings.add("Illegal ROM pre-step theta defined, ignoring.");
				} else {
					updateRomSettings(settings -> settings.setPrestepThetaDeg(parsedValue));
				}
			}
			case "romprestepplumestate" -> {
				if (Double.isNaN(parsedValue)) {
					warnings.add("Illegal ROM pre-step plume state defined, ignoring.");
				} else {
					updateRomSettings(settings -> settings.setPrestepPlumeState(parsedValue));
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

	private void updateRomSettings(Consumer<RomSettings> updater) {
		RomSettings settings = options.getRomSettings();
		updater.accept(settings);
		options.setRomSettings(settings);
	}
}
