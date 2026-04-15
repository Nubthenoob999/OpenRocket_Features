package info.openrocket.core.file.openrocket.importt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import com.airbrakesplugin.AirbrakeExtension;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.document.Simulation.Status;
import info.openrocket.core.aerodynamics.rom.DragGridEvaluator;
import info.openrocket.core.aerodynamics.rom.DragSurface;
import info.openrocket.core.aerodynamics.rom.DragSurfaceSerializer;
import info.openrocket.core.aerodynamics.rom.RomGeometryParameters;
import info.openrocket.core.aerodynamics.rom.RomSurfaceMode;
import info.openrocket.core.aerodynamics.rom.adapter.SurfaceAdapter;
import info.openrocket.core.aerodynamics.rom.core.eval.AeroGridEvaluator4D;
import info.openrocket.core.aerodynamics.rom.core.io.AeroSurfaceSerializer;
import info.openrocket.core.aerodynamics.rom.core.surface.AeroSurface4D;
import info.openrocket.core.aerodynamics.rom.RomSurfaceHashUtil;
import info.openrocket.core.file.DocumentLoadingContext;
import info.openrocket.core.file.simplesax.AbstractElementHandler;
import info.openrocket.core.file.simplesax.ElementHandler;
import info.openrocket.core.file.simplesax.PlainTextHandler;
import info.openrocket.core.rocketcomponent.FlightConfigurationId;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.simulation.FlightData;
import info.openrocket.core.simulation.SimulationOptions;
import info.openrocket.core.simulation.extension.SimulationExtension;
import info.openrocket.core.simulation.extension.SimulationExtensionProvider;
import info.openrocket.core.simulation.extension.impl.JavaCode;
import info.openrocket.core.startup.Application;
import info.openrocket.core.util.StringUtils;

import com.google.inject.Key;

class SingleSimulationHandler extends AbstractElementHandler {
	private final DocumentLoadingContext context;

	private final OpenRocketDocument doc;

	private String name;

	private SimulationConditionsHandler conditionHandler;
	private ConfigHandler configHandler;
	private FlightDataHandler dataHandler;
	private DragSurface romDragSurface;
	private AeroSurface4D romDragSurface4D;
	private String romGeometryHash;
	private String romGeometryHash4D;
	private double romLooRmse;
	private long romBuildTimestamp;
	private long romBuildTimestamp4D;
	private int romFinCount4D;
	private boolean romDragSurfacePayloadPresent;
	private boolean romDragSurface4DPayloadPresent;

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
			if (!calc.equals("BarrowmanCalculator") && !calc.equals("RomAerodynamicCalculator")) {
				warnings.add("Unknown calculator '" + content.trim() + "' specified, ignoring.");
			}
		} else if (element.equals("listener") && content.trim().length() > 0) {
			extensions.add(compatibilityExtension(content.trim()));
		} else if (element.equals("romdragsurface")) {
			String payload = content != null ? content.trim() : "";
			if (!payload.isEmpty()) {
				romDragSurfacePayloadPresent = true;
				romGeometryHash = attributes.get("geometryhash");
				try {
					romLooRmse = Double.parseDouble(attributes.getOrDefault("looRmse", "0"));
				} catch (RuntimeException ex) {
					romLooRmse = 0.0;
				}
				try {
					romBuildTimestamp = Long.parseLong(attributes.getOrDefault("builttimestamp", "0"));
				} catch (RuntimeException ex) {
					romBuildTimestamp = 0L;
				}
				try {
					romDragSurface = DragSurfaceSerializer.deserializeFromBase64Gzip(
							payload,
							romGeometryHash,
							romLooRmse,
							romBuildTimestamp);
				} catch (IllegalStateException ex) {
					warnings.add("Failed to parse romdragsurface, ignoring. Reason: " + ex.getMessage());
				}
			}
		} else if (element.equals("romdragsurface4d")) {
			String payload = content != null ? content.trim() : "";
			if (!payload.isEmpty()) {
				romDragSurface4DPayloadPresent = true;
				romGeometryHash4D = attributes.get("geometryhash");
				try {
					romBuildTimestamp4D = Long.parseLong(attributes.getOrDefault("builttimestamp", "0"));
				} catch (RuntimeException ex) {
					romBuildTimestamp4D = 0L;
				}
				try {
					romFinCount4D = Integer.parseInt(attributes.getOrDefault("fincount", "0"));
				} catch (RuntimeException ex) {
					romFinCount4D = 0;
				}
				try {
					romDragSurface4D = AeroSurfaceSerializer.deserialize(
							payload.getBytes(java.nio.charset.StandardCharsets.UTF_8),
							romGeometryHash4D,
							romBuildTimestamp4D,
							romFinCount4D);
				} catch (RuntimeException | java.io.IOException ex) {
					warnings.add("Failed to parse romdragsurface4d, ignoring. Reason: " + ex.getMessage());
				}
			}
		} else if (element.equals("extension") && !StringUtils.isEmpty(attributes.get("extensionid"))) {
			String id = attributes.get("extensionid");
			id = id.replace("net.sf.openrocket", "info.openrocket.core");
			id = id.replace("com.hprc.montecarlo", "info.openrocket.core.montecarlo");
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
				if (!migrateAirbrakesExtension(extension)) {
					extensions.add(extension);
				}
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

		RomGeometryParameters geometryParameters = resolveGeometryParameters(idToSet);
		String expectedHash = geometryParameters.geometryHash();
		boolean shouldInferRomMode = conditionHandler == null || !conditionHandler.wasRomSurfaceModeSpecified();

		if (romDragSurfacePayloadPresent) {
			if (romDragSurface != null && RomSurfaceHashUtil.matchesGeometry(romGeometryHash, expectedHash)) {
				options.setRomDragSurface(romDragSurface);
				if (shouldInferRomMode) {
					options.setRomSurfaceMode(RomSurfaceMode.THREE_D);
				}
			} else {
				warnings.add("Ignoring romdragsurface due to geometry hash mismatch; rebuilding for current geometry.");
				rebuildRomDragSurface(options, geometryParameters, shouldInferRomMode, warnings);
			}
		}

		if (romDragSurface4DPayloadPresent) {
			if (romDragSurface4D != null && RomSurfaceHashUtil.matchesGeometry(romGeometryHash4D, expectedHash)) {
				options.setRomAeroSurface4D(romDragSurface4D);
				if (options.getRomDragSurface() == null) {
					options.setRomDragSurface(SurfaceAdapter.toBetaZeroDragSurface(romDragSurface4D));
				}
				if (shouldInferRomMode) {
					options.setRomSurfaceMode(RomSurfaceMode.FOUR_D);
				}
			} else {
				warnings.add("Ignoring romdragsurface4d due to geometry hash mismatch; rebuilding for current geometry.");
				rebuildRomAeroSurface4D(options, geometryParameters, shouldInferRomMode, warnings);
			}
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

	private boolean migrateAirbrakesExtension(SimulationExtension extension) {
		if (!(extension instanceof AirbrakeExtension airbrakeExtension)) {
			return false;
		}
		if (conditionHandler == null) {
			return false;
		}

		SimulationOptions options = conditionHandler.getConditions();
		options.setAirbrakesEnabled(true);
		options.setCfdDataFilePath(airbrakeExtension.getCfdDataFilePath());
		options.setReferenceArea(airbrakeExtension.getReferenceArea());
		options.setReferenceLength(airbrakeExtension.getReferenceLength());
		options.setMaxDeploymentRate(airbrakeExtension.getMaxDeploymentRate());
		options.setTargetApogee(airbrakeExtension.getTargetApogee());
		options.setMaxMachForDeployment(airbrakeExtension.getMaxMachForDeployment());
		options.setAlwaysOpenMode(airbrakeExtension.isAlwaysOpenMode());
		options.setAlwaysOpenPercentage(airbrakeExtension.getAlwaysOpenPercentage());
		options.setApogeeToleranceMeters(airbrakeExtension.getApogeeToleranceMeters());
		options.setDeployAfterBurnoutOnly(airbrakeExtension.isDeployAfterBurnoutOnly());
		options.setDeployAfterBurnoutDelayS(airbrakeExtension.getDeployAfterBurnoutDelayS());
		options.setDebugEnabled(airbrakeExtension.isDebugEnabled());
		options.setDbgAlwaysOpen(airbrakeExtension.isDbgAlwaysOpen());
		options.setDbgForcedDeployFrac(airbrakeExtension.getDbgForcedDeployFrac());
		options.setDbgTracePredictor(airbrakeExtension.isDbgTracePredictor());
		options.setDbgTraceController(airbrakeExtension.isDbgTraceController());
		options.setDbgWriteCsv(airbrakeExtension.isDbgWriteCsv());
		options.setDbgCsvDir(airbrakeExtension.getDbgCsvDir());
		options.setDbgShowConsole(airbrakeExtension.isDbgShowConsole());
		return true;
	}

	private RomGeometryParameters resolveGeometryParameters(FlightConfigurationId idToSet) {
		if (idToSet != null && !idToSet.hasError()) {
			FlightConfiguration config = doc.getRocket().getFlightConfiguration(idToSet);
			return RomGeometryParameters.fromRocket(config);
		}
		return RomGeometryParameters.fromRocket(doc.getRocket().getSelectedConfiguration());
	}

	private void rebuildRomDragSurface(SimulationOptions options,
			RomGeometryParameters geometryParameters,
			boolean shouldInferRomMode,
			WarningSet warnings) {
		try {
			DragSurface rebuilt = DragGridEvaluator.evaluate(geometryParameters, null);
			options.setRomDragSurface(rebuilt);
			if (shouldInferRomMode) {
				options.setRomSurfaceMode(RomSurfaceMode.THREE_D);
			}
			warnings.add("Rebuilt romdragsurface for current geometry.");
		} catch (RuntimeException ex) {
			warnings.add("Failed to rebuild romdragsurface for current geometry. Reason: " + ex.getMessage());
		}
	}

	private void rebuildRomAeroSurface4D(SimulationOptions options,
			RomGeometryParameters geometryParameters,
			boolean shouldInferRomMode,
			WarningSet warnings) {
		try {
			AeroSurface4D rebuilt = AeroGridEvaluator4D.evaluate(
					geometryParameters.toRomGeometryInput(),
					geometryParameters.geometryHash(RomSurfaceMode.FOUR_D),
					(AeroGridEvaluator4D.ProgressListener) null);
			options.setRomAeroSurface4D(rebuilt);
			if (options.getRomDragSurface() == null) {
				options.setRomDragSurface(SurfaceAdapter.toBetaZeroDragSurface(rebuilt));
			}
			if (shouldInferRomMode) {
				options.setRomSurfaceMode(RomSurfaceMode.FOUR_D);
			}
			warnings.add("Rebuilt romdragsurface4d for current geometry.");
		} catch (RuntimeException ex) {
			warnings.add("Failed to rebuild romdragsurface4d for current geometry. Reason: " + ex.getMessage());
		}
	}

}
