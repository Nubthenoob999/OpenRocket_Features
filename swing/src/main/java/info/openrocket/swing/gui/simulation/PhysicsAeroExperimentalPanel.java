package info.openrocket.swing.gui.simulation;

import java.nio.file.Files;
import java.nio.file.Path;
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
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
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
import info.openrocket.core.aerodynamics.physicsaero.integration.PhysicsAeroTableService;
import info.openrocket.core.aerodynamics.physicsaero.integration.PhysicsAeroValidationGate;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroMode;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroSettings;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroTableCache;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroTableResolver;
import info.openrocket.core.aerodynamics.physicsaero.table.AerodynamicTable;
import info.openrocket.core.document.Simulation;
import net.miginfocom.swing.MigLayout;

/**
 * Aerodynamics settings and computed coefficient table for a simulation.
 */
public final class PhysicsAeroExperimentalPanel extends SimulationScrollablePanel {
	private static final long serialVersionUID = 1L;

	private static final String WALL_MODEL_ID = "ADIABATIC";
	private static final String REFINEMENT_RULE = "EXPLICIT_REGIME_HANDOFFS_AND_TRANSONIC_REFINEMENT_V1";
	private static final String FALLBACK_POLICY = "TYPED_GENERATION_FALLBACKS_ONLY";
	private static final double REFERENCE_PRESSURE_PA = 101325;
	private static final double REFERENCE_TEMPERATURE_K = 288.15;

	private static final String TILE_LAYOUT = "fillx, insets 8, gapx 8, gapy 5, wrap 2";
	private static final String TILE_COLUMNS = "[right,shrink 0][grow,fill,shrink 100]";

	private static final PhysicsAeroMode[] SELECTABLE_MODES = {
			PhysicsAeroMode.OFF,
			PhysicsAeroMode.DIAGNOSTIC_HYBRID,
			PhysicsAeroMode.STRICT
	};

	private final Simulation simulation;
	private final AtomicBoolean cancelled = new AtomicBoolean();

	private final JComboBox<PhysicsAeroMode> mode = new JComboBox<>(SELECTABLE_MODES);
	private final JCheckBox forceTurbulent = new JCheckBox("Fully turbulent");

	private final JComboBox<Fidelity> fidelity = new JComboBox<>(Fidelity.values());
	private final JLabel machAxis = new JLabel();
	private final JLabel alphaAxis = new JLabel();
	private final JLabel betaAxis = new JLabel();
	private final JLabel solvePoints = new JLabel();

	private final JButton build = new JButton("Build table");
	private final JButton cancel = new JButton("Cancel");
	private final JProgressBar progress = new JProgressBar(0, 100);
	private final JTextArea tableStatus = SimulationTabLayoutUtils.createWrappingDisplayText("");
	private final PhysicsAeroResultsPanel results = new PhysicsAeroResultsPanel();
	private int expectedCells;

	public PhysicsAeroExperimentalPanel(Simulation simulation) {
		super(new MigLayout("fillx, insets 6, gap 8 8, wrap 1", "[grow,fill]", ""));
		this.simulation = simulation;

		PhysicsAeroSettings settings = simulation.getOptions().getPhysicsAeroSettings();
		PhysicsAeroMode configuredMode = selectableMode(settings.getMode());
		mode.setSelectedItem(configuredMode);
		if (configuredMode != settings.getMode()) {
			simulation.getOptions().setPhysicsAeroMode(configuredMode);
		}
		forceTurbulent.setSelected(settings.isForceTurbulentBoundaryLayer());
		fidelity.setSelectedItem(Fidelity.STANDARD);

		JPanel columns = new JPanel(new MigLayout("fillx, insets 0, gap 8 8, wrap 2",
				"[grow,fill,shrink 50][grow,fill,shrink 50]", ""));
		columns.add(createModelTile(), "growx, wmin 0, top");
		columns.add(createDomainTile(), "growx, wmin 0, top");
		add(columns, "growx, wmin 0, wrap");

		add(createBuildTile(), "growx, wmin 0, wrap");
		SimulationTabLayoutUtils.forceViewportWidth(results);
		add(results, "growx, wmin 0, wrap");

		cancel.setEnabled(false);
		mode.addActionListener(event -> applyMode());
		forceTurbulent.addActionListener(event -> {
			simulation.getOptions().setForceTurbulentBoundaryLayer(forceTurbulent.isSelected());
			refreshCacheStatus(simulation.getOptions().getPhysicsAeroSettings());
		});
		fidelity.addActionListener(event -> refreshDomainSummary());
		build.addActionListener(event -> startBuild());
		cancel.addActionListener(event -> {
			cancelled.set(true);
			cancel.setEnabled(false);
			progress.setString("Cancelling...");
		});

		refreshModeDescription();
		refreshDomainSummary();
		refreshCacheStatus(settings);
	}

	// ------------------------------------------------------------------ layout

	private JPanel createModelTile() {
		JPanel tile = new JPanel(new MigLayout(TILE_LAYOUT, TILE_COLUMNS, ""));
		tile.setBorder(BorderFactory.createTitledBorder("Aerodynamic model"));

		tile.add(new JLabel("Method:"));
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

		tile.add(new JLabel("Boundary layer:"));
		tile.add(forceTurbulent, "growx, wmin 0");
		forceTurbulent.setToolTipText("Skip transition prediction and treat every surface as turbulent from the "
				+ "leading edge. This changes the table, so it must be rebuilt afterwards.");

		return tile;
	}

	private JPanel createDomainTile() {
		JPanel tile = new JPanel(new MigLayout(TILE_LAYOUT, TILE_COLUMNS, ""));
		tile.setBorder(BorderFactory.createTitledBorder("Table resolution"));

		tile.add(new JLabel("Grid:"));
		tile.add(fidelity, "growx, wmin 0");

		tile.add(new JLabel("Mach:"));
		tile.add(machAxis, "growx, wmin 0");
		tile.add(new JLabel("Angle of attack:"));
		tile.add(alphaAxis, "growx, wmin 0");
		tile.add(new JLabel("Sideslip:"));
		tile.add(betaAxis, "growx, wmin 0");
		tile.add(new JLabel("Table size:"));
		tile.add(solvePoints, "growx, wmin 0");
		return tile;
	}

	private JPanel createBuildTile() {
		JPanel panel = new JPanel(new MigLayout("fillx, insets 0, gapx 6, gapy 4",
				"[][][grow,fill]", ""));
		panel.add(build);
		panel.add(cancel);
		progress.setStringPainted(true);
		progress.setString("Idle");
		panel.add(progress, "growx, wmin 0, wrap");
		panel.add(tableStatus, "span, growx, wmin 0, wrap");
		return panel;
	}

	// ----------------------------------------------------------------- content

	private void applyMode() {
		PhysicsAeroMode selected = selectedMode();
		simulation.getOptions().setPhysicsAeroMode(selected);
		refreshModeDescription();
	}

	private static String modeLabel(PhysicsAeroMode value) {
		return switch (value) {
			case OFF -> "Barrowman";
			case STRICT -> "Physics-based (experimental)";
			case DIAGNOSTIC_HYBRID -> "Physics-based with fallback (experimental)";
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
		mode.setToolTipText(switch (selectedMode()) {
			case OFF -> "Use the Barrowman method.";
			case STRICT -> "Use the computed table; stop the simulation if a value is unavailable.";
			case DIAGNOSTIC_HYBRID, DIAGNOSTIC_AXIAL_HYBRID ->
					"Use the computed table with Barrowman fallback when a value is unavailable.";
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

		fidelity.setToolTipText(selected.description());
		setAxis(machAxis, mach, 2, "", "Mach nodes: ");
		setAxis(alphaAxis, degrees(alpha), 1, "\u00b0", "Angle of attack nodes: ");
		setAxis(betaAxis, degrees(beta), 1, "\u00b0", "Sideslip nodes: ");

		solvePoints.setText(String.format(Locale.ROOT, "%,d cells", expectedCells));
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
		progress.setValue(0);
		progress.setString("Starting");

		final Fidelity selected = selectedFidelity();
		final int cells = expectedCells;

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
					progress.setValue(100);
					progress.setString("Table ready");
					PhysicsAeroSettings settings = simulation.getOptions().getPhysicsAeroSettings();
					refreshCacheStatus(settings);
				} catch (Exception exception) {
					String reason = rootCause(exception).getMessage();
					progress.setString(cancelled.get() ? "Cancelled" : "Build failed");
					SimulationTabLayoutUtils.setWrappingDisplayText(tableStatus, cancelled.get()
							? "Cancelled; the previously cached table is untouched"
							: "Build failed \u2014 " + reason);
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
		if (cancelled.get()) {
			progress.setString("Cancelling...");
		} else if (value < 5) {
			progress.setString("Preparing table...");
		} else if (value < 86) {
			int solved = Math.min(cells, (int) Math.round((value - 5) / 80.0 * cells));
			progress.setString("Computing " + solved + " / " + cells);
		} else {
			progress.setString("Finishing table...");
		}
	}

	private void refreshCacheStatus(PhysicsAeroSettings settings) {
		if (settings.getGeometryHash().isBlank() || settings.getSettingsHash().isBlank()
				|| settings.getTableContentHash().isBlank()) {
			SimulationTabLayoutUtils.setWrappingDisplayText(tableStatus,
					"No computed values. Build a table to view results.");
			results.setTable(null);
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
					"Table ready: %,d cells.", table.cells().size()));
			results.setTable(table);
		} catch (PhysicsAeroTableResolver.ResolutionException exception) {
			SimulationTabLayoutUtils.setWrappingDisplayText(tableStatus,
					explain(exception));
			results.setTable(null);
		}
	}

	private static String explain(PhysicsAeroTableResolver.ResolutionException exception) {
		return switch (exception.reason()) {
			case HASH_MISMATCH -> "The airframe changed since the table was built \u2014 rebuild required";
			case TABLE_MISSING -> "The cached table file is gone \u2014 rebuild required";
			case TABLE_STALE, SCHEMA_MISMATCH -> "The cached table was built by an older version \u2014 rebuild required";
			case CHECKSUM_MISMATCH, CONTENT_HASH_MISMATCH -> "The cached table file is corrupt \u2014 rebuild required";
			case UNSUPPORTED_MULTI_STAGE_CONFIGURATION -> "Multi-stage rockets are not supported yet";
			case UNSUPPORTED_GEOMETRY -> "This airframe cannot be read by the geometry extractor";
			default -> "Table unavailable — rebuild required";
		};
	}

	private static Throwable rootCause(Throwable throwable) {
		while (throwable.getCause() != null) {
			throwable = throwable.getCause();
		}
		return throwable;
	}

	/** Preset sampling grids, all of which satisfy the artifact validation gate. */
	enum Fidelity {
		PREVIEW("Preview", "Coarse grid; fastest to build.",
				() -> sampling(
						new double[] {0, 0.3, 0.6, 0.8, 0.85, 0.9, 0.95, 1.0, 1.05, 1.2, 1.3, 1.5, 2, 3, 5, 6, 7, 8},
						new double[] {-90, -15, -5, 0, 5, 15, 90},
						new double[] {-5, 0, 5})),
		STANDARD("Standard", "Standard flight-envelope grid.",
				SamplingConfiguration::flightDomainDefaults),
		FINE("Fine", "Denser grid; slower to build.",
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
