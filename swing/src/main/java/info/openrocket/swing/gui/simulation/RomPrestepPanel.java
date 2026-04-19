package info.openrocket.swing.gui.simulation;

import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.JTable;

import net.miginfocom.swing.MigLayout;

import info.openrocket.core.aerodynamics.AerodynamicForces;
import info.openrocket.core.aerodynamics.FlightConditions;
import info.openrocket.core.aerodynamics.RomAerodynamicCalculator;
import info.openrocket.core.aerodynamics.rom.RomFallbackMode;
import info.openrocket.core.aerodynamics.rom.RomMode;
import info.openrocket.core.aerodynamics.rom.RomResult;
import info.openrocket.core.aerodynamics.rom.RomSettings;
import info.openrocket.core.aerodynamics.rom.control.AerodynamicConfidence;
import info.openrocket.core.aerodynamics.rom.flow.FlowRegime;
import info.openrocket.core.aerodynamics.rom.flow.FlowState;
import info.openrocket.core.aerodynamics.rom.flow.RegimeSelector;
import info.openrocket.core.aerodynamics.rom.geometry.FinGeometry;
import info.openrocket.core.aerodynamics.rom.geometry.GeometryFeatureExtractor;
import info.openrocket.core.aerodynamics.rom.geometry.GeometryFeatures;
import info.openrocket.core.aerodynamics.rom.geometry.PathlineSeed;
import info.openrocket.core.aerodynamics.rom.geometry.PathlineSeeder;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.models.atmosphere.AtmosphericConditions;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.simulation.SimulationConditions;

class RomPrestepPanel extends SimulationScrollablePanel {

	private static final long serialVersionUID = 2770487667033110266L;

	private final Simulation simulation;
	private final GeometryFeatureExtractor geometryFeatureExtractor = new GeometryFeatureExtractor();
	private final PathlineSeeder pathlineSeeder = new PathlineSeeder();
	private final RegimeSelector regimeSelector = new RegimeSelector();

	// ---- Geometry snapshot labels ----
	private final JLabel geometryHashValue    = new JLabel("-");
	private final JLabel bodyLengthValue      = new JLabel("-");
	private final JLabel maxDiameterValue     = new JLabel("-");
	private final JLabel finCountValue        = new JLabel("-");
	private final JLabel shoulderValue        = new JLabel("-");
	private final JLabel romStatusValue       = new JLabel("-");
	private final JLabel modeValue            = new JLabel("-");
	private final JLabel fallbackValue        = new JLabel("-");

	// ---- Compute inputs ----
	private final JSpinner machSpinner   = new JSpinner(new SpinnerNumberModel(0.80, 0.0, 8.0, 0.05));
	private final JSpinner aoaSpinner    = new JSpinner(new SpinnerNumberModel(5.0, -30.0, 30.0, 0.5));
	private final JSpinner thetaSpinner  = new JSpinner(new SpinnerNumberModel(0.0, -180.0, 180.0, 5.0));
	private final JSpinner plumeSpinner  = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 1.0, 0.05));

	// ---- Pathline count controls (write-through to RomSettings) ----
	private final JSpinner bodyPathlineSpinner = new JSpinner(new SpinnerNumberModel(12, 2, 64, 1));
	private final JSpinner finPathlineSpinner  = new JSpinner(new SpinnerNumberModel(3, 1, 16, 1));
	private final JButton applyDesignDefaultsButton = new JButton("Apply design defaults");
	private final JLabel defaultHintLabel = new JLabel("-");

	// ---- Seed table ----
	private final DefaultTableModel seedTableModel = new DefaultTableModel(
			new Object[]{"Family", "Component", "x (m)", "Area weight"}, 0) {
		private static final long serialVersionUID = 1L;
		@Override public boolean isCellEditable(int r, int c) { return false; }
	};
	private final JLabel seedCountLabel = new JLabel("–");

	// ---- Results ----
	private final JTextArea resultArea = createTextArea(10);
	private final JLabel regimeLabel      = new JLabel("–");
	private final JLabel confidenceLabel  = new JLabel("–");
	private final JLabel fallbackPctLabel = new JLabel("–");

	// Suppress feedback loop when loading settings into spinners
	private boolean loadingSettings = false;
	private boolean designDefaultsInitialized = false;

	RomPrestepPanel(Simulation simulation) {
		super(new MigLayout("fillx, insets 8, gap 8 8, wrap 2", "[grow,fill][grow,fill]", ""));
		this.simulation = simulation;
		defaultHintLabel.setFont(defaultHintLabel.getFont().deriveFont(Font.ITALIC, 11f));
		defaultHintLabel.setToolTipText("Uses rocket geometry characteristics to suggest practical ROM seed counts.");

		add(buildStatusAndGeometryPanel(), "span 2, growx, wrap");
		add(buildPathlinePanel(),          "growx, top");
		add(buildSeedTablePanel(),         "grow, top");
		add(buildComputePanel(),           "span 2, growx, wrap");

		wireEvents();
		refreshFromModel();
	}

	// ─── panel builders ───────────────────────────────────────────────────────

	private JPanel buildStatusAndGeometryPanel() {
		JPanel p = new JPanel(new MigLayout("fillx, insets 0, gap 12 0", "[grow,fill][grow,fill]", ""));

		// Left: ROM config status
		JPanel status = new JPanel(new MigLayout("fillx, insets 6, gapx 8, gapy 5", "[right][grow]", ""));
		status.setBorder(BorderFactory.createTitledBorder("ROM status"));
		status.add(createInfoButton("ROM status",
				"Enabled: whether the pathline reduced-order model is active for this simulation.\n"
						+ "Mode: seed-density preset (Standard, Conservative, or Diagnostic).\n"
						+ "Fallback: behavior when confidence drops in harsh flow conditions."), "span 2, right, wrap");
		status.add(new JLabel("Enabled:"));  status.add(romStatusValue, "wrap");
		status.add(new JLabel("Mode:"));     status.add(modeValue, "wrap");
		status.add(new JLabel("Fallback:")); status.add(fallbackValue, "wrap");
		p.add(status, "grow, top");

		// Right: geometry snapshot
		JPanel geo = new JPanel(new MigLayout("fillx, insets 6, gapx 8, gapy 5",
				"[right][grow][right][grow]", ""));
		geo.setBorder(BorderFactory.createTitledBorder("Geometry snapshot"));
		geo.add(createInfoButton("Geometry snapshot",
				"Geometry hash: stable signature used to detect design changes.\n"
						+ "Body length and max diameter define fineness and flow scaling.\n"
						+ "Fin count and shoulder/boattail transitions drive seed budgeting."), "span 4, right, wrap");
		geo.add(new JLabel("Geometry hash:"));    geo.add(geometryHashValue);
		geo.add(new JLabel("Body length:"));      geo.add(bodyLengthValue, "wrap");
		geo.add(new JLabel("Max diameter:"));     geo.add(maxDiameterValue);
		geo.add(new JLabel("Total fins:"));       geo.add(finCountValue, "wrap");
		geo.add(new JLabel("Shoulders / boattails:")); geo.add(shoulderValue, "span 3, wrap");
		p.add(geo, "grow, top");

		return p;
	}

	private JPanel buildPathlinePanel() {
		JPanel p = new JPanel(new MigLayout("fillx, insets 6, gapx 8, gapy 6",
				"[right][grow][right][grow]", ""));
		p.setBorder(BorderFactory.createTitledBorder("Pathline configuration"));
		p.add(createInfoButton("Pathline configuration",
				"Body meridian pathlines: axial seed lines over body surfaces.\n"
						+ "Fin surface pathlines: seed lines per fin set for vortex/separation behavior.\n"
						+ "Higher counts improve fidelity but increase runtime.\n"
						+ "Mach/AoA/theta/plume define the current ROM evaluation condition."), "span 4, right, wrap");

		p.add(new JLabel("Body meridian pathlines:"));
		p.add(bodyPathlineSpinner, "growx");
		p.add(new JLabel("Fin surface pathlines:"));
		p.add(finPathlineSpinner, "growx, wrap");

		applyDesignDefaultsButton.setToolTipText("Set pathline counts from current rocket geometry characteristics.");
		p.add(applyDesignDefaultsButton, "span 2");
		p.add(defaultHintLabel, "span 2, growx, wrap");

		p.add(new JLabel("Mach:"));
		p.add(machSpinner, "growx");
		p.add(new JLabel("AoA (deg):"));
		p.add(aoaSpinner, "growx, wrap");

		p.add(new JLabel("Roll plane θ (deg):"));
		p.add(thetaSpinner, "growx");
		p.add(new JLabel("Plume state:"));
		p.add(plumeSpinner, "growx, wrap");

		return p;
	}

	private JPanel buildSeedTablePanel() {
		JPanel p = new JPanel(new MigLayout("fill, insets 6, gapy 4", "[grow,fill]", "[][grow]"));
		p.setBorder(BorderFactory.createTitledBorder("Pathline seed plan"));
		p.add(createInfoButton("Pathline seed plan",
				"Shows where ROM seeds are placed before solving.\n"
						+ "Family identifies body, fin, or aft-body placeholder seeds.\n"
						+ "Area weight indicates each seed's influence in blended coefficients."), "right, wrap");
		p.add(seedCountLabel, "wrap");
		JTable table = new JTable(seedTableModel);
		table.setFillsViewportHeight(true);
		p.add(new JScrollPane(table), "grow, hmin 120");
		return p;
	}

	private JPanel buildComputePanel() {
		JPanel p = new JPanel(new MigLayout("fillx, insets 6, gapx 8, gapy 6",
				"[][grow][][grow][][grow]", ""));
		p.setBorder(BorderFactory.createTitledBorder("ROM compute"));
		p.add(createInfoButton("ROM compute",
				"Compute ROM evaluates coefficients at the selected flight condition.\n"
						+ "Regime and confidence summarize trust in the current solution.\n"
						+ "Fallback percentage shows Barrowman blend applied to stabilize output."), "span 7, right, wrap");

		// Prominent compute button
		JButton computeBtn = new JButton("Compute ROM");
		computeBtn.setFont(computeBtn.getFont().deriveFont(Font.BOLD, 13f));
		computeBtn.setBackground(new Color(60, 120, 200));
		computeBtn.setForeground(Color.WHITE);
		computeBtn.setOpaque(true);
		computeBtn.addActionListener(e -> runCompute());
		p.add(computeBtn, "h 32!, w 160!");

		// Quick-read status line
		p.add(new JLabel("Regime:"));    p.add(regimeLabel, "growx");
		p.add(new JLabel("Confidence:")); p.add(confidenceLabel, "growx");
		p.add(new JLabel("Fallback:"));   p.add(fallbackPctLabel, "growx, wrap");

		// Results area
		p.add(new JScrollPane(resultArea), "span 7, growx, h 200!");

		return p;
	}

	// ─── wiring ───────────────────────────────────────────────────────────────

	private void wireEvents() {
		// Refresh geometry snapshot when simulation options change
		simulation.getOptions().addChangeListener(
				e -> SwingUtilities.invokeLater(this::refreshFromModel));

		// Pathline count spinners write through to RomSettings immediately
		bodyPathlineSpinner.addChangeListener(e -> {
			if (loadingSettings) return;
			applyPathlineCounts();
		});
		finPathlineSpinner.addChangeListener(e -> {
			if (loadingSettings) return;
			applyPathlineCounts();
		});

		applyDesignDefaultsButton.addActionListener(e -> applyDesignDefaults());

		// Seed preview refreshes when pathline counts or Mach/AoA change
		ChangeListener previewListener = e -> refreshSeedPreview();
		machSpinner.addChangeListener(previewListener);
		aoaSpinner.addChangeListener(previewListener);
		bodyPathlineSpinner.addChangeListener(previewListener);
		finPathlineSpinner.addChangeListener(previewListener);
	}

	private void applyPathlineCounts() {
		RomSettings settings = simulation.getOptions().getRomSettings().copy();
		settings.setBodyMeridianSeedCount((int) spinnerValue(bodyPathlineSpinner));
		settings.setFinSurfaceSeedCount((int) spinnerValue(finPathlineSpinner));
		simulation.getOptions().setRomSettings(settings);
		designDefaultsInitialized = true;
		GeometryFeatures geometry = geometryFeatureExtractor.extract(activeConfiguration());
		RecommendedPathlineCounts recommendation = recommendPathlineCounts(geometry);
		updateDesignDefaultHint(settings, recommendation);
		refreshSeedPreview();
	}

	private void applyDesignDefaults() {
		GeometryFeatures geometry = geometryFeatureExtractor.extract(activeConfiguration());
		RecommendedPathlineCounts recommendation = recommendPathlineCounts(geometry);

		loadingSettings = true;
		bodyPathlineSpinner.setValue(recommendation.bodySeedCount);
		finPathlineSpinner.setValue(recommendation.finSeedCount);
		loadingSettings = false;

		designDefaultsInitialized = true;
		applyPathlineCounts();
	}

	// ─── refresh / compute ────────────────────────────────────────────────────

	private void refreshFromModel() {
		RomSettings settings = simulation.getOptions().getRomSettings();
		GeometryFeatures geometry = geometryFeatureExtractor.extract(activeConfiguration());
		RecommendedPathlineCounts recommendation = recommendPathlineCounts(geometry);
		if (!designDefaultsInitialized && shouldInitializeDesignDefaults(settings, recommendation)) {
			RomSettings updated = settings.copy();
			updated.setBodyMeridianSeedCount(recommendation.bodySeedCount);
			updated.setFinSurfaceSeedCount(recommendation.finSeedCount);
			simulation.getOptions().setRomSettings(updated);
			settings = updated;
			designDefaultsInitialized = true;
		}

		// Status
		romStatusValue.setText(settings.isEnabled() ? "Enabled" : "Disabled");
		modeValue.setText(formatMode(settings.getMode()));
		fallbackValue.setText(formatFallback(settings.getFallbackMode()));

		// Geometry — total fin count is sum of finCount across all fin sets
		int totalFins = geometry.getFins().stream().mapToInt(FinGeometry::getFinCount).sum();
		int finSets   = geometry.getFins().size();
		finCountValue.setText(totalFins + (finSets > 0
				? String.format("  (%d set%s)", finSets, finSets == 1 ? "" : "s") : ""));
		geometryHashValue.setText(shortHash(geometry.getGeometryHash()));
		bodyLengthValue.setText(formatMeters(geometry.getBodyLength()));
		maxDiameterValue.setText(formatMeters(geometry.getMaxDiameter()));
		shoulderValue.setText(geometry.getShoulderCount() + " / " + geometry.getBoattailCount());

		// Load pathline counts without triggering write-back
		loadingSettings = true;
		bodyPathlineSpinner.setValue(settings.getBodyMeridianSeedCount());
		finPathlineSpinner.setValue(settings.getFinSurfaceSeedCount());
		loadingSettings = false;

		updateDesignDefaultHint(settings, recommendation);

		refreshSeedPreview();

		if (resultArea.getText().isBlank()) {
			resultArea.setText("Press \"Compute ROM\" to evaluate aerodynamic coefficients at the current conditions.");
		}
	}

	private void refreshSeedPreview() {
		RomSettings settings = simulation.getOptions().getRomSettings();
		GeometryFeatures geometry = geometryFeatureExtractor.extract(activeConfiguration());
		List<PathlineSeed> seeds = pathlineSeeder.createSeeds(geometry, settings);

		int totalFins = geometry.getFins().stream().mapToInt(FinGeometry::getFinCount).sum();
		seedCountLabel.setText(String.format(Locale.ROOT,
				"%d total seeds  (body: %d  |  fin per set: %d  |  %d individual fins)",
				seeds.size(),
				settings.getBodyMeridianSeedCount(),
				settings.getFinSurfaceSeedCount(),
				totalFins));

		seedTableModel.setRowCount(0);
		for (PathlineSeed seed : seeds) {
			seedTableModel.addRow(new Object[]{
					formatSeedFamily(seed),
					seed.getComponentName(),
					String.format(Locale.ROOT, "%.3f", seed.getX()),
					String.format(Locale.ROOT, "%.4f", seed.getAreaWeight())
			});
		}
	}

	private void runCompute() {
		FlightConfiguration configuration = activeConfiguration();
		SimulationConditions prepared = simulation.getOptions().toSimulationConditions();
		RomAerodynamicCalculator calculator = prepared.getRomAerodynamicCalculator();

		FlightConditions conditions = new FlightConditions(configuration);
		conditions.setAtmosphericConditions(new AtmosphericConditions(
				simulation.getOptions().getLaunchTemperature(),
				simulation.getOptions().getLaunchPressure(),
				simulation.getOptions().getLaunchRelativeHumidity()));
		conditions.setMach(spinnerValue(machSpinner));
		conditions.setAOA(Math.toRadians(spinnerValue(aoaSpinner)));
		conditions.setTheta(Math.toRadians(spinnerValue(thetaSpinner)));
		calculator.setPlumeState(spinnerValue(plumeSpinner));

		WarningSet warnings = new WarningSet();
		AerodynamicForces forces = prepared.getAerodynamicCalculator()
				.getAerodynamicForces(configuration, conditions, warnings);

		if (!calculator.isEnabled()) {
			showDisabledResult(forces);
			return;
		}

		RomResult result = calculator.getLastResult();
		if (result == null) {
			resultArea.setText("ROM returned no result. Check that the rocket has valid geometry.");
			return;
		}

		// Update quick-read labels
		regimeLabel.setText(formatRegime(result.getRegime()));
		double conf = result.getConfidence().getOverallScore();
		confidenceLabel.setText(String.format(Locale.ROOT, "%.1f%%", conf * 100.0));
		confidenceLabel.setForeground(conf > 0.75 ? new Color(0, 150, 0)
				: conf > 0.45 ? new Color(180, 100, 0) : Color.RED);
		fallbackPctLabel.setText(String.format(Locale.ROOT, "%.0f%% Barrowman",
				result.getFallbackWeight() * 100.0));

		// Build detailed result text
		StringBuilder sb = new StringBuilder();
		sb.append(String.format(Locale.ROOT, "%-22s %s%n", "Regime:", formatRegime(result.getRegime())));
		sb.append(String.format(Locale.ROOT, "%-22s M=%.3f  AoA=%.1f°  θ=%.1f°  plume=%.2f%n",
				"Conditions:",
				spinnerValue(machSpinner), spinnerValue(aoaSpinner),
				spinnerValue(thetaSpinner), spinnerValue(plumeSpinner)));
		sb.append("\n");
		sb.append(String.format(Locale.ROOT, "%-22s %.4f%n", "CD (total):",  result.getBlendedForces().getCD()));
		sb.append(String.format(Locale.ROOT, "  %-20s %.4f%n", "pressure:", result.getBlendedForces().getPressureCD()));
		sb.append(String.format(Locale.ROOT, "  %-20s %.4f%n", "friction:",  result.getBlendedForces().getFrictionCD()));
		sb.append(String.format(Locale.ROOT, "  %-20s %.4f%n", "base:",      result.getBlendedForces().getBaseCD()));
		sb.append(String.format(Locale.ROOT, "%-22s %.4f%n", "CN:",          result.getBlendedForces().getCN()));
		sb.append(String.format(Locale.ROOT, "%-22s %.4f%n", "Cm:",          result.getBlendedForces().getCm()));
		sb.append(String.format(Locale.ROOT, "%-22s %.4f m%n", "CP x:",      result.getBlendedForces().getCP().getX()));
		sb.append("\n");
		sb.append(String.format(Locale.ROOT, "%-22s %.3f  (%.0f%% Barrowman)%n",
				"Confidence:", conf, result.getFallbackWeight() * 100.0));
		sb.append(String.format(Locale.ROOT, "%-22s %.3f%n", "Separation fraction:", result.getSeparationFraction()));
		sb.append(String.format(Locale.ROOT, "%-22s %d%n", "Active seeds:", result.getSeeds().size()));

		if (!result.getConfidence().getReasons().isEmpty()) {
			sb.append("\nConfidence flags:\n");
			for (String reason : result.getConfidence().getReasons()) {
				sb.append("  • ").append(reason).append("\n");
			}
		}
		if (!warnings.isEmpty()) {
			sb.append("\nWarnings:\n");
			sb.append(warnings).append("\n");
		}
		if (!result.getNotes().isBlank()) {
			sb.append("\nNotes:\n").append(result.getNotes()).append("\n");
		}

		// Side-by-side ROM vs Barrowman
		sb.append("\n── ROM vs Barrowman comparison ──────────────────────────\n");
		sb.append(String.format(Locale.ROOT, "  %-18s  ROM: %.4f   Barrowman: %.4f%n",
				"CD:", result.getRomForces().getCD(), forces.getCD()));
		sb.append(String.format(Locale.ROOT, "  %-18s  ROM: %.4f   Barrowman: %.4f%n",
				"CN:", result.getRomForces().getCN(), forces.getCN()));
		sb.append(String.format(Locale.ROOT, "  %-18s  ROM: %.4f   Barrowman: %.4f%n",
				"Cm:", result.getRomForces().getCm(), forces.getCm()));

		resultArea.setText(sb.toString());
		resultArea.setCaretPosition(0);
	}

	private void showDisabledResult(AerodynamicForces forces) {
		regimeLabel.setText("–");
		confidenceLabel.setText("–");
		fallbackPctLabel.setText("100% Barrowman");

		resultArea.setText(String.format(Locale.ROOT,
				"ROM is disabled — showing Barrowman reference only.%n%n"
				+ "  CD = %.4f%n  CN = %.4f%n  Cm = %.4f%n  CP = %.4f m%n%n"
				+ "Enable the ROM in Simulation Options to use the reduced-order solver.",
				forces.getCD(), forces.getCN(), forces.getCm(),
				forces.getCP() != null ? forces.getCP().getX() : 0.0));
		resultArea.setCaretPosition(0);
	}

	// ─── helpers ─────────────────────────────────────────────────────────────

	private FlightConfiguration activeConfiguration() {
		return simulation.getRocket().getFlightConfiguration(simulation.getFlightConfigurationId());
	}

	private static JTextArea createTextArea(int rows) {
		JTextArea area = new JTextArea(rows, 60);
		area.setEditable(false);
		area.setLineWrap(false);
		area.setWrapStyleWord(false);
		area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		return area;
	}

	private static String formatMeters(double v) {
		return String.format(Locale.ROOT, "%.4f m", v);
	}

	private static String shortHash(String hash) {
		if (hash == null || hash.isBlank()) return "–";
		return hash.length() <= 12 ? hash : hash.substring(0, 12);
	}

	private static String formatMode(RomMode mode) {
		if (mode == null) return "Standard";
		String raw = mode.name().toLowerCase(Locale.ROOT);
		return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
	}

	private static String formatFallback(RomFallbackMode m) {
		if (m == null) return "Blend";
		return switch (m) {
			case BLEND         -> "Blend";
			case BARROWMAN_ONLY -> "Barrowman only";
			case FORCE_ROM      -> "Force ROM";
		};
	}

	private static String formatRegime(FlowRegime r) {
		if (r == null) return "–";
		return switch (r) {
			case SUBSONIC         -> "Subsonic";
			case TRANSONIC        -> "Transonic";
			case SUPERSONIC       -> "Supersonic";
			case HYPERSONIC_LEANING -> "Hypersonic leaning";
		};
	}

	private static String formatSeedFamily(PathlineSeed seed) {
		return switch (seed.getFamily()) {
			case BODY_MERIDIAN       -> "Body";
			case FIN_SURFACE         -> "Fin";
			case AFT_BODY_PLACEHOLDER -> "Aft body";
		};
	}

	private static JButton createInfoButton(String title, String body) {
		JButton infoButton = new JButton("(i)");
		infoButton.setFocusable(false);
		infoButton.setMargin(new Insets(1, 4, 1, 4));
		infoButton.setToolTipText("Show " + title + " details");
		infoButton.addActionListener(e -> showInfoMenu(infoButton, title, body));
		return infoButton;
	}

	private static void showInfoMenu(JButton owner, String title, String body) {
		JPopupMenu menu = new JPopupMenu();
		JPanel content = new JPanel(new MigLayout("insets 8, gapy 4, wrap 1", "[grow,fill]", ""));

		JLabel header = new JLabel(title);
		header.setFont(header.getFont().deriveFont(Font.BOLD));

		JTextArea bodyArea = new JTextArea(body);
		bodyArea.setEditable(false);
		bodyArea.setFocusable(false);
		bodyArea.setOpaque(false);
		bodyArea.setLineWrap(true);
		bodyArea.setWrapStyleWord(true);
		bodyArea.setColumns(44);

		content.add(header, "growx");
		content.add(bodyArea, "growx");
		menu.add(content);
		menu.show(owner, 0, owner.getHeight());
	}

	private void updateDesignDefaultHint(RomSettings settings, RecommendedPathlineCounts recommendation) {
		defaultHintLabel.setText(String.format(Locale.ROOT,
				"<html>Design suggestion: body %d, fin %d per set (fineness %.1f, fins %d). Current: %d / %d.</html>",
				recommendation.bodySeedCount,
				recommendation.finSeedCount,
				recommendation.finenessRatio,
				recommendation.totalFins,
				settings.getBodyMeridianSeedCount(),
				settings.getFinSurfaceSeedCount()));
	}

	private static boolean shouldInitializeDesignDefaults(RomSettings settings, RecommendedPathlineCounts recommendation) {
		RomMode mode = settings.getMode() != null ? settings.getMode() : RomMode.STANDARD;
		boolean usesModeDefaults = settings.getBodyMeridianSeedCount() == mode.getBodySeedCount()
				&& settings.getFinSurfaceSeedCount() == mode.getFinSeedCount();
		boolean recommendationDiffers = settings.getBodyMeridianSeedCount() != recommendation.bodySeedCount
				|| settings.getFinSurfaceSeedCount() != recommendation.finSeedCount;
		return usesModeDefaults && recommendationDiffers;
	}

	private static RecommendedPathlineCounts recommendPathlineCounts(GeometryFeatures geometry) {
		double maxDiameter = Math.max(1e-3, geometry.getMaxDiameter());
		double finenessRatio = geometry.getBodyLength() / maxDiameter;
		int totalFins = geometry.getFins().stream().mapToInt(FinGeometry::getFinCount).sum();
		int finSets = geometry.getFins().size();
		int contourComplexity = geometry.getShoulderCount() + geometry.getBoattailCount()
				+ Math.min(4, geometry.getSlopeChangeCount() / 4);

		double averageSpanToChord = geometry.getFins().stream()
				.mapToDouble(fin -> {
					double avgChord = 0.5 * (fin.getRootChord() + fin.getTipChord());
					return fin.getSpan() / Math.max(1e-3, avgChord);
				})
				.average()
				.orElse(0.0);

		int bodySeedCount = 8
				+ (int) Math.round(Math.min(18.0, finenessRatio * 0.7))
				+ contourComplexity
				+ (totalFins >= 4 ? 1 : 0);
		bodySeedCount = clampInt(bodySeedCount, 8, 40);

		int finSeedCount = 1;
		if (totalFins > 0) {
			finSeedCount = 2
					+ (int) Math.round(Math.min(3.0, averageSpanToChord))
					+ (finSets > 1 ? 1 : 0)
					+ (totalFins >= 4 ? 1 : 0);
		}
		finSeedCount = clampInt(finSeedCount, 1, 12);

		return new RecommendedPathlineCounts(bodySeedCount, finSeedCount, finenessRatio, totalFins);
	}

	private static int clampInt(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static final class RecommendedPathlineCounts {
		private final int bodySeedCount;
		private final int finSeedCount;
		private final double finenessRatio;
		private final int totalFins;

		private RecommendedPathlineCounts(int bodySeedCount, int finSeedCount, double finenessRatio, int totalFins) {
			this.bodySeedCount = bodySeedCount;
			this.finSeedCount = finSeedCount;
			this.finenessRatio = finenessRatio;
			this.totalFins = totalFins;
		}
	}

	private static double spinnerValue(JSpinner s) {
		Object v = s.getValue();
		return v instanceof Number n ? n.doubleValue() : 0.0;
	}
}
