package info.openrocket.swing.gui.simulation;

import java.awt.Font;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;

import info.openrocket.core.aerodynamics.physicsaero.config.NumericalTolerances;
import info.openrocket.core.aerodynamics.physicsaero.config.PhysicsAeroSettingsFingerprint;
import info.openrocket.core.aerodynamics.physicsaero.config.PhysicsConfiguration;
import info.openrocket.core.aerodynamics.physicsaero.config.SamplingConfiguration;
import info.openrocket.core.aerodynamics.physicsaero.flow.AtmosphereState;
import info.openrocket.core.aerodynamics.physicsaero.flow.PoweredFlowState;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.PerfectGasAir;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;
import info.openrocket.core.aerodynamics.physicsaero.geometry.GeometryExtractor;
import info.openrocket.core.aerodynamics.physicsaero.integration.FullRegimeTableBuilder;
import info.openrocket.core.aerodynamics.physicsaero.integration.PhysicsAeroTableService;
import info.openrocket.core.aerodynamics.physicsaero.integration.PhysicsAeroValidationGate;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroMode;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroRuntimeReport;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroSettings;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroTableCache;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroTableResolver;
import info.openrocket.core.aerodynamics.physicsaero.table.AerodynamicTable;
import info.openrocket.core.document.Simulation;
import net.miginfocom.swing.MigLayout;

/**
 * Aerodynamics tab: builds, caches and explains the pre-solved coefficient
 * table that replaces Barrowman during a simulation.
 */
public final class PhysicsAeroExperimentalPanel extends SimulationScrollablePanel {
	private static final long serialVersionUID = 1L;

	private static final String DISPLAY_NAME = "Physics-Based Aerodynamics (Experimental)";
	private static final String WALL_MODEL_ID = "ADIABATIC";
	private static final String REFINEMENT_RULE = "EXPLICIT_REGIME_HANDOFFS_AND_TRANSONIC_REFINEMENT_V1";
	private static final String FALLBACK_POLICY = "TYPED_GENERATION_FALLBACKS_ONLY";
	private static final double REFERENCE_PRESSURE_PA = 101325;
	private static final double REFERENCE_TEMPERATURE_K = 288.15;

	private static final String TILE_LAYOUT = "fillx, insets 8, gapx 8, gapy 5, wrap 2";
	private static final String TILE_COLUMNS = "[right,shrink 0][grow,fill,shrink 100]";

	private static final String MARK_PENDING = "\u25cb";
	private static final String MARK_ACTIVE = "\u25b6";
	private static final String MARK_DONE = "\u2714";
	private static final String MARK_FAILED = "\u2716";
	private static final PhysicsAeroMode[] SELECTABLE_MODES = {
			PhysicsAeroMode.OFF,
			PhysicsAeroMode.DIAGNOSTIC_HYBRID,
			PhysicsAeroMode.STRICT
	};

	private final Simulation simulation;
	private final AtomicBoolean cancelled = new AtomicBoolean();

	private final JComboBox<PhysicsAeroMode> mode = new JComboBox<>(SELECTABLE_MODES);
	private final JTextArea modeDescription =
			SimulationTabLayoutUtils.createWrappingDisplayText("");
	private final JCheckBox forceTurbulent = new JCheckBox("Force a fully turbulent boundary layer");

	private final JComboBox<Fidelity> fidelity = new JComboBox<>(Fidelity.values());
	private final JTextArea fidelityDescription =
			SimulationTabLayoutUtils.createWrappingDisplayText("");
	private final JLabel machAxis = new JLabel();
	private final JLabel alphaAxis = new JLabel();
	private final JLabel betaAxis = new JLabel();
	private final JTextArea solvePoints = SimulationTabLayoutUtils.createWrappingDisplayText("");

	private final JButton build = new JButton("Build table");
	private final JButton cancel = new JButton("Cancel");
	private final JButton export = new JButton("Export report...");
	private final JProgressBar progress = new JProgressBar(0, 100);
	private final List<StageRow> stages = new ArrayList<>();

	private final JTextArea tableStatus = SimulationTabLayoutUtils.createWrappingDisplayText("Not resolved");
	private final JTextArea tableDomain = SimulationTabLayoutUtils.createWrappingDisplayText("");
	private final JLabel certification = new JLabel("NOT_READY");
	private final JLabel geometryHash = new JLabel("\u2014");
	private final JLabel settingsHash = new JLabel("\u2014");
	private final JLabel contentHash = new JLabel("\u2014");
	private final JLabel cacheLocation = new JLabel("\u2014");
	private final JTextArea runtime = SimulationTabLayoutUtils.createWrappingDisplayText(
			"No simulation has used this table yet");

	private final JTextArea diagnostics = new JTextArea(8, 40);
	private final PhysicsAeroTableDiagnosticPanel tableDiagnostic;
	private Path buildReport;
	private int expectedCells;

	public PhysicsAeroExperimentalPanel(Simulation simulation) {
		super(new MigLayout("fillx, insets 6, gap 8 8, wrap 1", "[grow,fill]", ""));
		this.simulation = simulation;
		this.tableDiagnostic = new PhysicsAeroTableDiagnosticPanel(simulation);

		PhysicsAeroSettings settings = simulation.getOptions().getPhysicsAeroSettings();
		PhysicsAeroMode configuredMode = selectableMode(settings.getMode());
		mode.setSelectedItem(configuredMode);
		if (configuredMode != settings.getMode()) {
			simulation.getOptions().setPhysicsAeroMode(configuredMode);
		}
		forceTurbulent.setSelected(settings.isForceTurbulentBoundaryLayer());
		fidelity.setSelectedItem(Fidelity.STANDARD);

		add(createOverview(), "growx, wmin 0, wrap");

		JPanel columns = new JPanel(new MigLayout("fillx, insets 0, gap 8 8, wrap 2",
				"[grow,fill,shrink 50][grow,fill,shrink 50]", ""));
		columns.add(createModelTile(), "growx, wmin 0, top");
		columns.add(createDomainTile(), "growx, wmin 0, top");
		add(columns, "growx, wmin 0, wrap");

		add(createBuildTile(), "growx, wmin 0, wrap");
		add(createStatusTile(), "growx, wmin 0, wrap");

		SimulationTabLayoutUtils.forceViewportWidth(tableDiagnostic);
		add(tableDiagnostic, "growx, wmin 0, wrap");
		add(createActivityTile(), "growx, wmin 0, wrap");

		cancel.setEnabled(false);
		mode.addActionListener(event -> applyMode());
		forceTurbulent.addActionListener(event -> {
			simulation.getOptions().setForceTurbulentBoundaryLayer(forceTurbulent.isSelected());
			append(forceTurbulent.isSelected()
					? "Boundary layer forced turbulent everywhere; transition prediction is bypassed."
					: "Boundary layer transition will be predicted per surface.");
			refreshDomainSummary();
		});
		fidelity.addActionListener(event -> refreshDomainSummary());
		build.addActionListener(event -> startBuild());
		cancel.addActionListener(event -> {
			cancelled.set(true);
			append("Cancellation requested; the last valid table will remain unchanged.");
		});
		export.addActionListener(event -> exportReport());

		refreshModeDescription();
		refreshDomainSummary();
		refreshIdentity(settings);
		refreshCacheStatus(settings);
		tableDiagnostic.reloadFromCache();
		refreshRuntimeReport();
		resetStages();
	}

	// ------------------------------------------------------------------ layout

	private JPanel createOverview() {
		JPanel tile = new JPanel(new MigLayout("fillx, insets 8, gapy 5, wrap 1", "[grow,fill]", ""));
		tile.setBorder(BorderFactory.createTitledBorder(DISPLAY_NAME));
		tile.add(SimulationTabLayoutUtils.createBoundedWrappingText(
				"Instead of evaluating Barrowman equations while the rocket flies, this method solves the "
						+ "aerodynamics once over the whole flight envelope and stores the result as a coefficient "
						+ "table on disk. The simulation then only looks values up, so every run of the same rocket "
						+ "sees exactly the same aerodynamics.",
				null), "growx, wmin 0, wrap");
		tile.add(SimulationTabLayoutUtils.createBoundedWrappingText(
				"The table is keyed to this airframe. Change a component and the stored table no longer matches, "
						+ "so it has to be built again. Experimental: flight validation is still pending and only "
						+ "single-stage configurations are supported.",
				null), "growx, wmin 0, wrap");
		return tile;
	}

	private JPanel createModelTile() {
		JPanel tile = new JPanel(new MigLayout(TILE_LAYOUT, TILE_COLUMNS, ""));
		tile.setBorder(BorderFactory.createTitledBorder("1 \u00b7 How the simulation uses the table"));

		tile.add(new JLabel("Mode:"));
		mode.setRenderer(new DefaultListCellRenderer() {
			private static final long serialVersionUID = 1L;

			@Override
			public java.awt.Component getListCellRendererComponent(JList<?> list, Object item,
					int index, boolean selected, boolean focused) {
				super.getListCellRendererComponent(list, item, index, selected, focused);
				if (item instanceof PhysicsAeroMode value) {
					setText(modeLabel(value));
				}
				return this;
			}
		});
		tile.add(mode, "growx, wmin 0");
		tile.add(modeDescription, "skip 1, growx, wmin 0, wrap");

		tile.add(new JLabel("Boundary layer:"));
		tile.add(forceTurbulent, "growx, wmin 0");
		forceTurbulent.setToolTipText("Skip transition prediction and treat every surface as turbulent from the "
				+ "leading edge. This changes the table, so it must be rebuilt afterwards.");

		tile.add(new JLabel("Wall model:"));
		tile.add(value("Adiabatic (no heat flux into the skin)"), "growx, wmin 0");

		tile.add(new JLabel("Surface roughness:"));
		tile.add(value("Taken from each component's finish setting"), "growx, wmin 0");
		return tile;
	}

	private JPanel createDomainTile() {
		JPanel tile = new JPanel(new MigLayout(TILE_LAYOUT, TILE_COLUMNS, ""));
		tile.setBorder(BorderFactory.createTitledBorder("2 \u00b7 What gets solved"));

		tile.add(new JLabel("Grid:"));
		tile.add(fidelity, "growx, wmin 0");
		tile.add(fidelityDescription, "skip 1, growx, wmin 0, wrap");

		tile.add(new JLabel("Mach:"));
		tile.add(machAxis, "growx, wmin 0");
		tile.add(new JLabel("Angle of attack:"));
		tile.add(alphaAxis, "growx, wmin 0");
		tile.add(new JLabel("Sideslip:"));
		tile.add(betaAxis, "growx, wmin 0");
		tile.add(new JLabel("Solver work:"));
		tile.add(solvePoints, "growx, wmin 0");
		tile.add(new JLabel("Reference air:"));
		tile.add(value(String.format(Locale.ROOT, "Sea-level ISA \u2014 %.0f Pa, %.2f K",
				REFERENCE_PRESSURE_PA, REFERENCE_TEMPERATURE_K)), "growx, wmin 0");
		tile.add(new JLabel("Altitude:"));
		tile.add(value("Handled at run time by a Reynolds-number correction stored with each cell"),
				"growx, wmin 0");
		return tile;
	}

	private JPanel createBuildTile() {
		JPanel tile = new JPanel(new MigLayout("fillx, insets 8, gapy 5, wrap 1", "[grow,fill]", ""));
		tile.setBorder(BorderFactory.createTitledBorder("3 \u00b7 Build"));

		JPanel buttons = new JPanel(new MigLayout("insets 0, gapx 6", "", ""));
		buttons.add(build);
		buttons.add(cancel);
		buttons.add(export);
		tile.add(buttons, "growx, wmin 0, wrap");

		progress.setStringPainted(true);
		progress.setString("Idle");
		tile.add(progress, "growx, wmin 0, wrap");

		JPanel stagePanel = new JPanel(new MigLayout("fillx, insets 0, gapx 6, gapy 2, wrap 2",
				"[pref!][grow,fill]", ""));
		addStage(stagePanel, "Read the airframe geometry and fingerprint the settings");
		addStage(stagePanel, "Pre-solve supersonic Reynolds stencils");
		addStage(stagePanel, "Solve every Mach / alpha / sideslip cell");
		addStage(stagePanel, "Check the artifact and write it to the cache");
		tile.add(stagePanel, "growx, wmin 0, wrap");
		return tile;
	}

	private JPanel createStatusTile() {
		JPanel tile = new JPanel(new MigLayout(TILE_LAYOUT, TILE_COLUMNS, ""));
		tile.setBorder(BorderFactory.createTitledBorder("Stored table"));

		tile.add(new JLabel("Status:"));
		tile.add(tableStatus, "growx, wmin 0");
		tile.add(new JLabel("Covers:"));
		tile.add(tableDomain, "growx, wmin 0");
		tile.add(new JLabel("Certification:"));
		tile.add(certification, "growx, wmin 0");
		tile.add(new JLabel("Cache file:"));
		tile.add(cacheLocation, "growx, wmin 0");
		tile.add(new JLabel("Last simulation:"));
		tile.add(runtime, "growx, wmin 0");

		JPanel identity = new JPanel(new MigLayout("fillx, insets 0, gapx 8, gapy 3, wrap 2",
				TILE_COLUMNS, ""));
		identity.add(new JLabel("Geometry hash:"));
		identity.add(geometryHash, "growx, wmin 0");
		identity.add(new JLabel("Settings hash:"));
		identity.add(settingsHash, "growx, wmin 0");
		identity.add(new JLabel("Content hash:"));
		identity.add(contentHash, "growx, wmin 0");
		identity.setToolTipText("A simulation only runs when all three hashes still match the rocket, "
				+ "the settings above and the file in the cache.");
		tile.add(identity, "span 2, growx, wmin 0, gaptop 4, wrap");
		return tile;
	}

	private JPanel createActivityTile() {
		JPanel tile = new JPanel(new MigLayout("fillx, insets 8, wrap 1", "[grow,fill]", ""));
		tile.setBorder(BorderFactory.createTitledBorder("Activity log"));
		diagnostics.setEditable(false);
		diagnostics.setLineWrap(true);
		diagnostics.setWrapStyleWord(true);
		diagnostics.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
		JScrollPane scroll = SimulationTabLayoutUtils.createContainedScrollPane(diagnostics);
		tile.add(scroll, "growx, wmin 0, hmin 90lp, hmax 220lp, top");
		return tile;
	}

	private void addStage(JPanel parent, String description) {
		StageRow row = new StageRow(description);
		stages.add(row);
		parent.add(row.marker, "aligny top");
		parent.add(row.label, "growx, wmin 0");
	}

	private static JComponent value(String text) {
		return SimulationTabLayoutUtils.createBoundedWrappingText(text, null);
	}

	// ----------------------------------------------------------------- content

	private void applyMode() {
		PhysicsAeroMode selected = selectedMode();
		simulation.getOptions().setPhysicsAeroMode(selected);
		refreshModeDescription();
		append("Aerodynamic mode set to " + selected + ".");
	}

	private static String modeLabel(PhysicsAeroMode value) {
		return switch (value) {
			case OFF -> "No table";
			case STRICT -> "Table only";
			case DIAGNOSTIC_HYBRID -> "Table with Barrowman fallback (diagnostic)";
			case DIAGNOSTIC_AXIAL_HYBRID -> "Table drag only (diagnostic)";
		};
	}

	private static PhysicsAeroMode selectableMode(PhysicsAeroMode configuredMode) {
		return configuredMode == PhysicsAeroMode.DIAGNOSTIC_AXIAL_HYBRID
				? PhysicsAeroMode.DIAGNOSTIC_HYBRID
				: configuredMode;
	}

	private PhysicsAeroMode selectedMode() {
		PhysicsAeroMode selected = (PhysicsAeroMode) mode.getSelectedItem();
		return selected == null ? PhysicsAeroMode.OFF : selected;
	}

	private void refreshModeDescription() {
		SimulationTabLayoutUtils.setWrappingDisplayText(modeDescription, switch (selectedMode()) {
			case OFF -> "The table is ignored and the simulation uses the built-in Barrowman method. "
					+ "You can still build a table here to inspect it.";
			case STRICT -> "Every force and moment comes from the table. If a value cannot be looked up "
					+ "the simulation stops instead of silently switching methods.";
			case DIAGNOSTIC_HYBRID -> "The table is tried first and Barrowman fills any gap. Each substitution "
					+ "is counted and reported, so use this to compare methods rather than for final numbers.";
			case DIAGNOSTIC_AXIAL_HYBRID -> "Only axial force (drag) comes from the table. Normal force, "
					+ "stability and centre of pressure stay on Barrowman, which isolates drag differences.";
		});
	}

	private Fidelity selectedFidelity() {
		Fidelity selected = (Fidelity) fidelity.getSelectedItem();
		return selected == null ? Fidelity.STANDARD : selected;
	}

	private void refreshDomainSummary() {
		Fidelity selected = selectedFidelity();
		SamplingConfiguration sampling = selected.sampling();
		double[] mach = sampling.mach();
		double[] alpha = sampling.alphaRad();
		double[] beta = sampling.betaRad();
		expectedCells = mach.length * alpha.length * beta.length * poweredStatesFor(simulation).length;

		SimulationTabLayoutUtils.setWrappingDisplayText(fidelityDescription, selected.description());
		setAxis(machAxis, mach, 2, "", "Mach nodes: ");
		setAxis(alphaAxis, degrees(alpha), 1, "\u00b0", "Angle of attack nodes: ");
		setAxis(betaAxis, degrees(beta), 1, "\u00b0", "Sideslip nodes: ");

		long stencils = Arrays.stream(mach)
				.filter(m -> m >= FullRegimeTableBuilder.SUPERSONIC_STENCIL_MIN_MACH
						&& m <= FullRegimeTableBuilder.SUPERSONIC_STENCIL_MAX_MACH)
				.count();
		SimulationTabLayoutUtils.setWrappingDisplayText(solvePoints, String.format(Locale.ROOT,
				"%,d cells across explicit coast and powered states, plus %d supersonic Mach nodes "
						+ "pre-solved at 7-9 Reynolds anchors each. "
						+ "The anchors are what let the stored table be corrected for air density at "
						+ "altitude without rebuilding it.",
				expectedCells, stencils));

		Path root = cacheRoot();
		SimulationTabLayoutUtils.setCompactValueLabel(cacheLocation, root.toString());
	}

	private static void setAxis(JLabel label, double[] values, int decimals, String unit, String tooltipPrefix) {
		String format = "%." + decimals + "f";
		String text = String.format(Locale.ROOT, "%d points, " + format + unit + " to " + format + unit,
				values.length, values[0], values[values.length - 1]);
		label.setText(text);
		StringBuilder all = new StringBuilder("<html>").append(tooltipPrefix).append("<br>");
		for (int index = 0; index < values.length; index++) {
			if (index > 0) {
				all.append(", ");
			}
			all.append(String.format(Locale.ROOT, format, values[index]));
		}
		label.setToolTipText(all.append("</html>").toString());
	}

	private static double[] degrees(double[] radians) {
		return Arrays.stream(radians).map(Math::toDegrees).toArray();
	}

	private static Path cacheRoot() {
		return Path.of(System.getProperty("user.home"), ".openrocket", "physics-aero");
	}

	// ------------------------------------------------------------------- build

	private void startBuild() {
		cancelled.set(false);
		build.setEnabled(false);
		cancel.setEnabled(true);
		fidelity.setEnabled(false);
		forceTurbulent.setEnabled(false);
		resetStages();
		progress.setValue(0);
		progress.setString("Starting");

		final Fidelity selected = selectedFidelity();
		final int cells = expectedCells;
		append("Building a " + selected.label() + " table: " + cells + " cells over Mach "
				+ "0-8. This runs in the background and the current runtime mode is unchanged.");
		setStage(0, MARK_ACTIVE, null);

		new SwingWorker<PhysicsAeroTableService.Result, Integer>() {
			@Override
			protected PhysicsAeroTableService.Result doInBackground() throws Exception {
				SamplingConfiguration sampling = selected.sampling();
				boolean turbulent = simulation.getOptions().isForceTurbulentBoundaryLayer();
				PoweredFlowState[] poweredStates = poweredStatesFor(simulation);
				String settingsFingerprint = PhysicsAeroSettingsFingerprint.hash(
						new PhysicsAeroSettingsFingerprint.Input(sampling,
								List.of(REFINEMENT_RULE),
								new PhysicsConfiguration(PhysicsConfiguration.allRegisteredMethods(),
										WALL_MODEL_ID, 0, true, turbulent),
								NumericalTolerances.defaults(),
								FALLBACK_POLICY,
								PhysicsAeroValidationGate.CODE_VERSION,
								PhysicsAeroValidationGate.REGISTRY_VERSION, poweredStates));
				AeroGeometry geometry = new GeometryExtractor().extractWithComponentRoughness(
						simulation.getActiveConfiguration(), WALL_MODEL_ID, settingsFingerprint, turbulent);

				Path root = cacheRoot();
				Files.createDirectories(root);
				PhysicsAeroTableCache.Key key = new PhysicsAeroTableCache.Key(
						geometry.geometryHash(), settingsFingerprint, PhysicsAeroValidationGate.CODE_VERSION,
						PhysicsAeroValidationGate.REGISTRY_VERSION);
				Path table = new PhysicsAeroTableCache(root).tablePath(key);
				Path report = Path.of(table.toString().replaceFirst("\\.aero$", ".json"));

				PerfectGasAir air = new PerfectGasAir();
				AtmosphereState atmosphere = new AtmosphereState(REFERENCE_PRESSURE_PA, REFERENCE_TEMPERATURE_K,
						REFERENCE_PRESSURE_PA / (air.gasConstant() * REFERENCE_TEMPERATURE_K),
						air.viscosity(REFERENCE_TEMPERATURE_K));
				PhysicsAeroTableService.Request request = new PhysicsAeroTableService.Request(
						geometry, sampling.mach(), sampling.alphaRad(), sampling.betaRad(), poweredStates,
						atmosphere, air,
						settingsFingerprint, table, report);
				return new PhysicsAeroTableService().build(request, cancelled, this::publishProgress);
			}

			private void publishProgress(int value) {
				publish(value);
			}

			@Override
			protected void process(List<Integer> values) {
				onProgress(values.get(values.size() - 1), cells);
			}

			@Override
			protected void done() {
				try {
					PhysicsAeroTableService.Result result = get();
					simulation.getOptions().setPhysicsAeroTableIdentity(result.table(), result.tableHash());
					buildReport = result.manifestFile();
					markAllStagesDone();
					progress.setValue(100);
					progress.setString("Table ready");
					certification.setText(result.validation().certificationState().name());
					PhysicsAeroSettings settings = simulation.getOptions().getPhysicsAeroSettings();
					refreshIdentity(settings);
					refreshCacheStatus(settings);
					SimulationTabLayoutUtils.setCompactValueLabel(cacheLocation, result.tableFile().toString());
					tableDiagnostic.setTable(result.table());
					append("Built and cached " + result.table().cells().size() + " cells at "
							+ result.tableFile());
					append("Artifact checks passed: " + result.validation().checks().size()
							+ "; certification " + result.validation().certificationState() + ".");
				} catch (Exception exception) {
					failCurrentStage();
					String reason = rootCause(exception).getMessage();
					progress.setString(cancelled.get() ? "Cancelled" : "Build failed");
					SimulationTabLayoutUtils.setWrappingDisplayText(tableStatus, cancelled.get()
							? "Cancelled; the previously cached table is untouched"
							: "Build failed \u2014 " + reason);
					append((cancelled.get() ? "Build cancelled: " : "Build failed: ") + reason);
				} finally {
					build.setEnabled(true);
					cancel.setEnabled(false);
					fidelity.setEnabled(true);
					forceTurbulent.setEnabled(true);
				}
			}
		}.execute();
	}

	/**
	 * Defines the complete runtime powered axis used by the GUI table builder.
	 * A measured/imported nozzle diameter enables the RASAero nozzle-geometry
	 * correlation.  Otherwise the powered endpoint is deliberately identical to
	 * coast and is flagged POWERED_INCREMENT_UNMODELED by the table builder; no
	 * nozzle dimension or powered drag increment is inferred.
	 */
	static PoweredFlowState[] poweredStatesFor(Simulation simulation) {
		double nozzleDiameterM = simulation.getOptions().getNozzleExitDiameterForStage(0);
		PoweredFlowState powered = Double.isFinite(nozzleDiameterM) && nozzleDiameterM > 0
				? PoweredFlowState.nozzleGeometryOnly(1,
						Math.PI * nozzleDiameterM * nozzleDiameterM / 4, REFERENCE_PRESSURE_PA)
				: PoweredFlowState.unmodeledPoweredBaseline(REFERENCE_PRESSURE_PA);
		return new PoweredFlowState[] {
				PoweredFlowState.coast(REFERENCE_PRESSURE_PA),
				powered
		};
	}

	/**
	 * The service reports 1-4 while supersonic stencils are pre-solved, 5-85
	 * across the cell sweep, 90 once the artifact is validated and 100 after it
	 * is written, which is enough to name the stage the build is actually in.
	 */
	private void onProgress(int value, int cells) {
		progress.setValue(value);
		if (value < 5) {
			setStage(0, MARK_DONE, null);
			setStage(1, MARK_ACTIVE, "pre-solving Reynolds anchors");
			progress.setString("Pre-solving supersonic stencils");
		} else if (value < 86) {
			setStage(0, MARK_DONE, null);
			setStage(1, MARK_DONE, null);
			int solved = Math.min(cells, (int) Math.round((value - 5) / 80.0 * cells));
			setStage(2, MARK_ACTIVE, "cell " + solved + " of " + cells);
			progress.setString("Solving cells " + solved + " / " + cells);
		} else {
			setStage(0, MARK_DONE, null);
			setStage(1, MARK_DONE, null);
			setStage(2, MARK_DONE, null);
			setStage(3, MARK_ACTIVE, value >= 95 ? "writing cache" : "checking the artifact");
			progress.setString(value >= 95 ? "Writing cache" : "Validating table");
		}
	}

	private void resetStages() {
		for (StageRow stage : stages) {
			stage.set(MARK_PENDING, null);
		}
	}

	private void setStage(int index, String marker, String detail) {
		if (index < stages.size()) {
			stages.get(index).set(marker, detail);
		}
	}

	private void markAllStagesDone() {
		for (StageRow stage : stages) {
			stage.set(MARK_DONE, null);
		}
	}

	private void failCurrentStage() {
		for (StageRow stage : stages) {
			if (!MARK_DONE.equals(stage.marker.getText())) {
				stage.set(MARK_FAILED, null);
				return;
			}
		}
	}

	// ------------------------------------------------------------------ status

	private void refreshIdentity(PhysicsAeroSettings settings) {
		SimulationTabLayoutUtils.setCompactValueLabel(geometryHash, display(settings.getGeometryHash()));
		SimulationTabLayoutUtils.setCompactValueLabel(settingsHash, display(settings.getSettingsHash()));
		SimulationTabLayoutUtils.setCompactValueLabel(contentHash, display(settings.getTableContentHash()));
	}

	private void refreshCacheStatus(PhysicsAeroSettings settings) {
		if (settings.getGeometryHash().isBlank() || settings.getSettingsHash().isBlank()
				|| settings.getTableContentHash().isBlank()) {
			SimulationTabLayoutUtils.setWrappingDisplayText(tableStatus,
					"No table has been built for this rocket yet");
			SimulationTabLayoutUtils.setWrappingDisplayText(tableDomain, "");
			return;
		}
		PhysicsAeroSettings resolvable = settings.copy();
		if (!resolvable.isEnabled()) {
			resolvable.setMode(PhysicsAeroMode.STRICT);
		}
		try {
			AerodynamicTable table = new PhysicsAeroTableResolver().resolve(
					simulation.getActiveConfiguration(), resolvable);
			SimulationTabLayoutUtils.setWrappingDisplayText(tableStatus, String.format(Locale.ROOT,
					"Cached and verified \u2014 %,d cells ready to use", table.cells().size()));
			SimulationTabLayoutUtils.setWrappingDisplayText(tableDomain, describeDomain(table));
			certification.setText(table.metadata().certificationState().name());
		} catch (PhysicsAeroTableResolver.ResolutionException exception) {
			SimulationTabLayoutUtils.setWrappingDisplayText(tableStatus,
					explain(exception) + " (" + exception.getMessage() + ")");
			SimulationTabLayoutUtils.setWrappingDisplayText(tableDomain, "");
		}
	}

	private static String describeDomain(AerodynamicTable table) {
		double[] mach = table.axes().mach();
		double[] alpha = degrees(table.axes().alphaRad());
		double[] beta = degrees(table.axes().betaRad());
		return String.format(Locale.ROOT,
				"Mach %.2f-%.2f (%d), alpha %.1f\u00b0 to %.1f\u00b0 (%d), sideslip %.1f\u00b0 to %.1f\u00b0 (%d)",
				mach[0], mach[mach.length - 1], mach.length,
				alpha[0], alpha[alpha.length - 1], alpha.length,
				beta[0], beta[beta.length - 1], beta.length);
	}

	private static String explain(PhysicsAeroTableResolver.ResolutionException exception) {
		return switch (exception.reason()) {
			case HASH_MISMATCH -> "The airframe changed since the table was built \u2014 rebuild required";
			case TABLE_MISSING -> "The cached table file is gone \u2014 rebuild required";
			case TABLE_STALE, SCHEMA_MISMATCH -> "The cached table was built by an older version \u2014 rebuild required";
			case CHECKSUM_MISMATCH, CONTENT_HASH_MISMATCH -> "The cached table file is corrupt \u2014 rebuild required";
			case UNSUPPORTED_MULTI_STAGE_CONFIGURATION -> "Multi-stage rockets are not supported yet";
			case UNSUPPORTED_GEOMETRY -> "This airframe cannot be read by the geometry extractor";
			default -> exception.reason() + " \u2014 rebuild required";
		};
	}

	private void refreshRuntimeReport() {
		PhysicsAeroRuntimeReport report = simulation.getPhysicsAeroRuntimeReport();
		if (report.totalQueries() == 0) {
			return;
		}
		SimulationTabLayoutUtils.setWrappingDisplayText(runtime, String.format(Locale.ROOT,
				"%,d of %,d lookups answered by the table; %,d fell back; flags=%s",
				report.successfulTableQueries(), report.totalQueries(), report.fallbackCount(),
				report.runtimeFlags()));
	}

	private void exportReport() {
		JFileChooser chooser = new JFileChooser();
		chooser.setSelectedFile(new java.io.File("physics-aero-report.txt"));
		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		try {
			StringBuilder report = new StringBuilder();
			report.append(DISPLAY_NAME).append('\n');
			report.append("grid=").append(selectedFidelity().label()).append('\n');
			report.append("tableIdentity=")
					.append(simulation.getOptions().getPhysicsAeroSettings()).append('\n');
			report.append("runtime=").append(simulation.getPhysicsAeroRuntimeReport()).append('\n');
			report.append(tableDiagnostic.exportText());
			if (buildReport != null && Files.isRegularFile(buildReport)) {
				report.append("buildReport=\n").append(Files.readString(buildReport));
			}
			Files.writeString(chooser.getSelectedFile().toPath(), report.toString());
			append("Exported report: " + chooser.getSelectedFile());
		} catch (IOException exception) {
			append("Export failed: " + exception.getMessage());
		}
	}

	private static String display(String value) {
		return value == null || value.isBlank() ? "\u2014" : value;
	}

	private void append(String text) {
		diagnostics.append(text + System.lineSeparator());
		diagnostics.setCaretPosition(diagnostics.getDocument().getLength());
	}

	private static Throwable rootCause(Throwable throwable) {
		while (throwable.getCause() != null) {
			throwable = throwable.getCause();
		}
		return throwable;
	}

	// ------------------------------------------------------------------ pieces

	private static final class StageRow {
		private final JLabel marker = new JLabel(MARK_PENDING);
		private final JLabel label = new JLabel();
		private final String description;

		private StageRow(String description) {
			this.description = description;
			label.setText(description);
			Font base = marker.getFont();
			marker.setFont(base.deriveFont(Font.BOLD));
		}

		private void set(String state, String detail) {
			marker.setText(state);
			label.setText(detail == null ? description : description + " \u2014 " + detail);
		}
	}

	/** Preset sampling grids, all of which satisfy the artifact validation gate. */
	enum Fidelity {
		PREVIEW("Preview", "Coarse grid for a quick look at the drag curve. Fewest solves, "
				+ "least accurate between nodes.",
				() -> sampling(
						new double[] {0, 0.3, 0.6, 0.8, 0.85, 0.9, 0.95, 1.0, 1.05, 1.2, 1.3, 1.5, 2, 3, 5, 6, 7, 8},
						new double[] {-90, -15, -5, 0, 5, 15, 90},
						new double[] {-5, 0, 5})),
		STANDARD("Standard", "The validated flight-envelope grid, with extra nodes placed on every "
				+ "regime handoff. Use this unless you have a reason not to.",
				SamplingConfiguration::flightDomainDefaults),
		FINE("Fine", "Denser transonic and angle-of-attack coverage for smoother interpolation. "
				+ "Several times slower to build.",
				() -> sampling(
						new double[] {0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.75, 0.8, 0.85, 0.875, 0.9, 0.925,
								0.95, 0.975, 1.0, 1.025, 1.05, 1.1, 1.15, 1.2, 1.25, 1.3, 1.4, 1.5, 1.75, 2,
								2.5, 3, 3.5, 4, 4.5, 5, 5.5, 6, 6.5, 7, 8},
						new double[] {-90, -75, -60, -45, -15, -12, -10, -7.5, -5, -2.5, 0,
								2.5, 5, 7.5, 10, 12, 15, 45, 60, 75, 90},
						new double[] {-5, -2.5, 0, 2.5, 5}));

		private final String label;
		private final String description;
		private final Supplier<SamplingConfiguration> factory;

		Fidelity(String label, String description, Supplier<SamplingConfiguration> factory) {
			this.label = label;
			this.description = description;
			this.factory = factory;
		}

		private static SamplingConfiguration sampling(double[] mach, double[] alphaDeg, double[] betaDeg) {
			return new SamplingConfiguration(mach,
					Arrays.stream(alphaDeg).map(Math::toRadians).toArray(),
					Arrays.stream(betaDeg).map(Math::toRadians).toArray());
		}

		String label() {
			return label;
		}

		String description() {
			return description;
		}

		SamplingConfiguration sampling() {
			return factory.get();
		}

		@Override
		public String toString() {
			return label;
		}
	}
}
