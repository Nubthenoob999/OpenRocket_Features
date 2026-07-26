package info.openrocket.swing.gui.simulation;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.DefaultTableModel;

import info.openrocket.core.aerodynamics.physicsaero.force.AerodynamicCoefficients;
import info.openrocket.core.aerodynamics.physicsaero.interpolation.QueryResult;
import info.openrocket.core.aerodynamics.physicsaero.interpolation.TableQueryEngine;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroMode;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroSettings;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroTableResolver;
import info.openrocket.core.aerodynamics.physicsaero.table.AerodynamicTable;
import info.openrocket.core.aerodynamics.physicsaero.table.RuntimeCorrectionData;
import info.openrocket.core.document.Simulation;
import net.miginfocom.swing.MigLayout;

/** Read-only inspector for the deterministic coefficient table used by a simulation. */
final class PhysicsAeroTableDiagnosticPanel extends JPanel {
	private static final String[] GROUP_COLUMNS = {"Group", "CA", "CN", "CY", "Cl", "Cm", "Cn"};

	private final Simulation simulation;
	private final JSpinner mach = new JSpinner(new SpinnerNumberModel(0.30, 0.0, 20.0, 0.05));
	private final JSpinner alphaDeg = new JSpinner(new SpinnerNumberModel(0.0, -90.0, 90.0, 0.5));
	private final JSpinner betaDeg = new JSpinner(new SpinnerNumberModel(0.0, -90.0, 90.0, 0.5));
	private final JSpinner poweredFraction = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 1.0, 0.05));
	private final JCheckBox applyReynolds = new JCheckBox("Apply runtime Reynolds correction");
	private final JSpinner reynolds = new JSpinner(new SpinnerNumberModel(1_000_000.0, 1.0, 1.0e12, 100_000.0));
	private final JButton query = new JButton("Query table");
	private final JButton reload = new JButton("Reload cached table");
	private final JLabel tableSummary = new JLabel("No table loaded");
	private final JLabel queryStatus = new JLabel("Build or reload a table to inspect coefficients.");
	private final ReadOnlyTableModel coefficientModel = new ReadOnlyTableModel(
			new String[] {"Result", "Meaning", "Stored table", "Runtime Reynolds"});
	private final ReadOnlyTableModel componentModel = new ReadOnlyTableModel(GROUP_COLUMNS);
	private final ReadOnlyTableModel ownerModel = new ReadOnlyTableModel(GROUP_COLUMNS);
	private final JTextArea details = new JTextArea(13, 72);
	private AerodynamicTable table;
	private DiagnosticSnapshot lastSnapshot;

	PhysicsAeroTableDiagnosticPanel(Simulation simulation) {
		super(new BorderLayout(6, 6));
		this.simulation = simulation;
		setBorder(BorderFactory.createTitledBorder("Inspect the stored table"));

		JPanel controls = new JPanel(new MigLayout("fillx, insets 4, gapx 6, gapy 4, wrap 4",
				"[][grow,fill,shrink 100][][grow,fill,shrink 100]", ""));
		controls.add(SimulationTabLayoutUtils.createBoundedWrappingText(
				"Look up a single flight condition in the table exactly the way the simulation does, "
						+ "and see which physical model produced each part of the answer.", null),
				"span 4, growx, wmin 0, wrap");
		controls.add(new JLabel("Mach:"));
		controls.add(mach);
		controls.add(new JLabel("Alpha (deg):"));
		controls.add(alphaDeg);
		controls.add(new JLabel("Beta (deg):"));
		controls.add(betaDeg);
		controls.add(new JLabel("Powered fraction:"));
		controls.add(poweredFraction);
		controls.add(applyReynolds, "span 2");
		controls.add(new JLabel("Runtime Reynolds:"));
		controls.add(reynolds);
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		buttons.add(query);
		buttons.add(reload);
		controls.add(buttons, "span 4");
		controls.add(new JLabel("Loaded table:"));
		controls.add(tableSummary, "span 3, growx");
		controls.add(new JLabel("Query status:"));
		controls.add(queryStatus, "span 3, growx");
		add(controls, BorderLayout.NORTH);

		JTabbedPane tabs = new JTabbedPane();
		tabs.addTab("Coefficient summary", tablePane(new JTable(coefficientModel)));
		tabs.addTab("Components", tablePane(new JTable(componentModel)));
		tabs.addTab("Physical owners", tablePane(new JTable(ownerModel)));
		details.setEditable(false);
		details.setLineWrap(false);
		tabs.addTab("Query details", new JScrollPane(details));
		tabs.setPreferredSize(new Dimension(480, 280));
		SimulationTabLayoutUtils.forceViewportWidth(tabs);
		add(tabs, BorderLayout.CENTER);

		reynolds.setEnabled(false);
		query.setEnabled(false);
		applyReynolds.addActionListener(event -> reynolds.setEnabled(applyReynolds.isSelected()));
		query.addActionListener(event -> queryTable());
		reload.addActionListener(event -> reloadFromCache());
	}

	void reloadFromCache() {
		PhysicsAeroSettings settings = simulation.getOptions().getPhysicsAeroSettings();
		if (settings.getGeometryHash().isBlank() || settings.getSettingsHash().isBlank()
				|| settings.getTableContentHash().isBlank()) {
			clearTable("No cached table identity; build a table first.");
			return;
		}
		PhysicsAeroSettings resolvable = settings.copy();
		if (!resolvable.isEnabled()) resolvable.setMode(PhysicsAeroMode.STRICT);
		try {
			setTable(new PhysicsAeroTableResolver().resolve(simulation.getActiveConfiguration(), resolvable));
		} catch (PhysicsAeroTableResolver.ResolutionException exception) {
			clearTable(exception.reason() + " — rebuild required");
		}
	}

	void setTable(AerodynamicTable table) {
		this.table = table;
		query.setEnabled(true);
		double[] machAxis = table.axes().mach();
		double[] alphaAxis = table.axes().alphaRad();
		double[] betaAxis = table.axes().betaRad();
		double[] poweredAxis = table.axes().poweredFraction();
		mach.setValue(clamp(0.30, machAxis[0], machAxis[machAxis.length - 1]));
		alphaDeg.setValue(Math.toDegrees(clamp(0, alphaAxis[0], alphaAxis[alphaAxis.length - 1])));
		betaDeg.setValue(Math.toDegrees(clamp(0, betaAxis[0], betaAxis[betaAxis.length - 1])));
		poweredFraction.setValue(poweredAxis[0]);
		double referenceReynolds = table.cells().get(0).runtimeCorrection().referenceReynolds();
		if (referenceReynolds > 0) reynolds.setValue(referenceReynolds);
		SimulationTabLayoutUtils.setCompactValueLabel(tableSummary, String.format(Locale.ROOT,
				"%s; %,d cells; M %.3g–%.3g; α %.2f°–%.2f°; β %.2f°–%.2f°; power %.2f–%.2f",
				table.metadata().certificationState(), table.cells().size(), machAxis[0], machAxis[machAxis.length - 1],
				Math.toDegrees(alphaAxis[0]), Math.toDegrees(alphaAxis[alphaAxis.length - 1]),
				Math.toDegrees(betaAxis[0]), Math.toDegrees(betaAxis[betaAxis.length - 1]),
				poweredAxis[0], poweredAxis[poweredAxis.length - 1]));
		queryTable();
	}

	private void clearTable(String message) {
		table = null;
		lastSnapshot = null;
		query.setEnabled(false);
		SimulationTabLayoutUtils.setCompactValueLabel(tableSummary, message);
		SimulationTabLayoutUtils.setCompactValueLabel(queryStatus, message);
		coefficientModel.setRowCount(0);
		componentModel.setRowCount(0);
		ownerModel.setRowCount(0);
		details.setText(message);
	}

	private void queryTable() {
		if (table == null) return;
		double queryMach = number(mach);
		double alpha = Math.toRadians(number(alphaDeg));
		double beta = Math.toRadians(number(betaDeg));
		double power = number(poweredFraction);
		Double runtimeReynolds = applyReynolds.isSelected() ? number(reynolds) : null;
		try {
			lastSnapshot = evaluate(table, queryMach, alpha, beta, power, runtimeReynolds);
			populate(lastSnapshot, queryMach, alpha, beta, power);
			SimulationTabLayoutUtils.setCompactValueLabel(queryStatus,
					(lastSnapshot.query().interpolated() ? "Interpolated table query" : "Direct table node")
					+ "; confidence=" + format(lastSnapshot.query().lowestConfidence())
					+ "; Reynolds=" + lastSnapshot.reynoldsStatus());
		} catch (IllegalArgumentException | IllegalStateException exception) {
			lastSnapshot = null;
			SimulationTabLayoutUtils.setCompactValueLabel(queryStatus,
					"Query failed: " + exception.getMessage());
			details.setText("Query failed\n" + exception.getClass().getSimpleName() + ": " + exception.getMessage());
		}
	}

	private void populate(DiagnosticSnapshot snapshot, double queryMach, double alpha, double beta, double power) {
		coefficientModel.setRowCount(0);
		AerodynamicCoefficients raw = snapshot.query().coefficients();
		AerodynamicCoefficients corrected = snapshot.correctedCoefficients();
		addCoefficient("CA", "Body-axis axial coefficient (integrator authority)", raw.ca(), correctedValue(corrected, 0));
		addCoefficient("CN", "Body-axis normal-force coefficient", raw.cn(), correctedValue(corrected, 1));
		addCoefficient("CY", "Body-axis side-force coefficient", raw.cy(), correctedValue(corrected, 2));
		addCoefficient("Cl", "Roll-moment coefficient", raw.cl(), correctedValue(corrected, 3));
		addCoefficient("Cm", "Pitch-moment coefficient", raw.cm(), correctedValue(corrected, 4));
		addCoefficient("Cn", "Yaw-moment coefficient", raw.cYaw(), correctedValue(corrected, 5));
		addCoefficient("CD", "Complete wind-axis drag coefficient", snapshot.rawDrag().total(),
				snapshot.correctedDrag() == null ? Double.NaN : snapshot.correctedDrag().total());
		addCoefficient("CD pressure", "Pressure/wave/interference drag residual", snapshot.rawDrag().pressure(),
				snapshot.correctedDrag() == null ? Double.NaN : snapshot.correctedDrag().pressure());
		addCoefficient("CD base", "Base-pressure drag owners", snapshot.rawDrag().base(),
				snapshot.correctedDrag() == null ? Double.NaN : snapshot.correctedDrag().base());
		addCoefficient("CD friction", "Skin-friction drag owners", snapshot.rawDrag().friction(),
				snapshot.correctedDrag() == null ? Double.NaN : snapshot.correctedDrag().friction());
		addCoefficient("Clp", "Roll-rate derivative", snapshot.query().derivatives().clp(), Double.NaN);
		addCoefficient("Cmq", "Pitch-rate derivative", snapshot.query().derivatives().cmq(), Double.NaN);
		addCoefficient("Cnr", "Yaw-rate derivative", snapshot.query().derivatives().cnr(), Double.NaN);

		populateGroups(componentModel, snapshot.query().componentTotals());
		populateGroups(ownerModel, snapshot.query().ownerTotals());
		details.setText(detailsText(table, snapshot, queryMach, alpha, beta, power));
		details.setCaretPosition(0);
	}

	private void addCoefficient(String symbol, String meaning, double raw, double corrected) {
		coefficientModel.addRow(new Object[] {symbol, meaning, format(raw), formatOptional(corrected)});
	}

	private static void populateGroups(ReadOnlyTableModel model, Map<String, AerodynamicCoefficients> groups) {
		model.setRowCount(0);
		groups.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
			AerodynamicCoefficients value = entry.getValue();
			model.addRow(new Object[] {entry.getKey(), format(value.ca()), format(value.cn()),
					format(value.cy()), format(value.cl()), format(value.cm()), format(value.cYaw())});
		});
	}

	String exportText() {
		if (lastSnapshot == null) return "tableDiagnostic=No successful query\n";
		StringBuilder output = new StringBuilder("tableDiagnostic=\n");
		output.append(details.getText()).append('\n');
		for (int row = 0; row < coefficientModel.getRowCount(); row++) {
			output.append(coefficientModel.getValueAt(row, 0)).append('=')
					.append(coefficientModel.getValueAt(row, 2));
			Object corrected = coefficientModel.getValueAt(row, 3);
			if (!"—".equals(corrected)) output.append(";runtimeRe=").append(corrected);
			output.append('\n');
		}
		appendGroups(output, "component", lastSnapshot.query().componentTotals());
		appendGroups(output, "owner", lastSnapshot.query().ownerTotals());
		return output.toString();
	}

	private static void appendGroups(StringBuilder output, String prefix,
			Map<String, AerodynamicCoefficients> groups) {
		groups.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
			AerodynamicCoefficients value = entry.getValue();
			output.append(prefix).append('.').append(entry.getKey()).append('=')
					.append("CA:").append(format(value.ca()))
					.append(",CN:").append(format(value.cn()))
					.append(",CY:").append(format(value.cy()))
					.append(",Cl:").append(format(value.cl()))
					.append(",Cm:").append(format(value.cm()))
					.append(",Cn:").append(format(value.cYaw())).append('\n');
		});
	}

	static DiagnosticSnapshot evaluate(AerodynamicTable table, double mach, double alphaRad,
			double betaRad, double poweredFraction, Double runtimeReynolds) {
		QueryResult query = new TableQueryEngine().query(table, mach, alphaRad, betaRad, poweredFraction);
		DragBreakdown rawDrag = dragBreakdown(query.coefficients(), query.ownerTotals(), alphaRad, betaRad);
		if (runtimeReynolds == null) {
			return new DiagnosticSnapshot(query, null, null, rawDrag, null, "not requested");
		}

		RuntimeCorrectionData correction = query.runtimeCorrection();
		if (!(runtimeReynolds > 0) || !(correction.referenceReynolds() > 0)) {
			return new DiagnosticSnapshot(query, null, null, rawDrag, null,
					"unavailable — valid runtime and reference Reynolds numbers are required");
		}
		double ratio = runtimeReynolds / correction.referenceReynolds();
		if (!correction.supportsRatio(ratio)) {
			return new DiagnosticSnapshot(query, null, null, rawDrag, null,
					"REYNOLDS_REBUILD_REQUIRED (ratio " + format(ratio) + " outside "
							+ format(correction.minimumRatio()) + "–" + format(correction.maximumRatio()) + ")");
		}
		double[] original = query.coefficients().toArray();
		double[] correctedValues = original.clone();
		double[] sensitivities = correction.dCoefficientDLogRe();
		double logRatio = Math.log(ratio);
		for (int index = 0; index < correctedValues.length; index++) {
			correctedValues[index] += sensitivities[index] * logRatio;
		}
		AerodynamicCoefficients corrected = AerodynamicCoefficients.fromArray(correctedValues);
		Map<String, AerodynamicCoefficients> correctedOwners = adjustGroupedTotals(
				query.ownerTotals(), original, correctedValues);
		DragBreakdown correctedDrag = dragBreakdown(corrected, correctedOwners, alphaRad, betaRad);
		return new DiagnosticSnapshot(query, corrected, correctedOwners, rawDrag, correctedDrag,
				Math.abs(logRatio) <= 1.0e-12 ? "reference Reynolds; no delta"
						: "applied with " + correction.methodId() + "; ratio=" + format(ratio));
	}

	private static Map<String, AerodynamicCoefficients> adjustGroupedTotals(
			Map<String, AerodynamicCoefficients> totals, double[] original, double[] corrected) {
		if (totals.isEmpty()) return totals;
		var keys = totals.keySet().stream().sorted().toList();
		Map<String, double[]> values = new LinkedHashMap<>();
		for (String key : keys) values.put(key, totals.get(key).toArray());
		for (int axis = 0; axis < 6; axis++) {
			double delta = corrected[axis] - original[axis];
			if (delta == 0) continue;
			double magnitude = 0;
			for (String key : keys) magnitude += Math.abs(values.get(key)[axis]);
			if (magnitude <= 1.0e-15) {
				values.get(keys.get(0))[axis] += delta;
			} else {
				for (String key : keys) {
					double[] group = values.get(key);
					group[axis] += delta * Math.abs(group[axis]) / magnitude;
				}
			}
		}
		Map<String, AerodynamicCoefficients> adjusted = new LinkedHashMap<>();
		for (String key : keys) adjusted.put(key, AerodynamicCoefficients.fromArray(values.get(key)));
		return adjusted;
	}

	private static DragBreakdown dragBreakdown(AerodynamicCoefficients coefficients,
			Map<String, AerodynamicCoefficients> owners, double alpha, double beta) {
		double total = coefficients.ca() * Math.cos(alpha) * Math.cos(beta)
				+ coefficients.cn() * Math.sin(alpha) * Math.cos(beta)
				+ coefficients.cy() * Math.sin(beta);
		double friction = ownerCaContaining(owners, "SKIN_FRICTION");
		double base = owners.entrySet().stream()
				.filter(entry -> entry.getKey().contains("BASE") && entry.getKey().contains("PRESSURE_DRAG"))
				.mapToDouble(entry -> entry.getValue().ca()).sum();
		return new DragBreakdown(total, Math.max(0, total - friction - base), base, friction);
	}

	private static double ownerCaContaining(Map<String, AerodynamicCoefficients> owners, String token) {
		return owners.entrySet().stream().filter(entry -> entry.getKey().contains(token))
				.mapToDouble(entry -> entry.getValue().ca()).sum();
	}

	private static String detailsText(AerodynamicTable table, DiagnosticSnapshot snapshot,
			double mach, double alpha, double beta, double power) {
		RuntimeCorrectionData correction = snapshot.query().runtimeCorrection();
		return "Table metadata\n"
				+ "  schema=" + table.metadata().schemaVersion() + "\n"
				+ "  certification=" + table.metadata().certificationState() + "\n"
				+ "  geometryHash=" + table.metadata().geometryHash() + "\n"
				+ "  settingsHash=" + table.metadata().settingsHash() + "\n"
				+ "  codeVersion=" + table.metadata().codeVersion() + "\n"
				+ "  registryVersion=" + table.metadata().correlationRegistryVersion() + "\n"
				+ "Query\n"
				+ "  mach=" + format(mach) + ", alphaDeg=" + format(Math.toDegrees(alpha))
				+ ", betaDeg=" + format(Math.toDegrees(beta)) + ", poweredFraction=" + format(power) + "\n"
				+ "  interpolated=" + snapshot.query().interpolated() + "\n"
				+ "  lowestConfidence=" + format(snapshot.query().lowestConfidence()) + "\n"
				+ "  diagnosticFlags=" + snapshot.query().diagnosticFlags() + "\n"
				+ "  reasonCodes=" + snapshot.query().reasonCodes() + "\n"
				+ "  validityFlags=" + snapshot.query().validityFlags() + "\n"
				+ "  methodIds=" + snapshot.query().methodIds() + "\n"
				+ "Rate derivatives (engineering only)\n"
				+ "  Clp=" + format(snapshot.query().derivatives().clp())
				+ ", Cmq=" + format(snapshot.query().derivatives().cmq())
				+ ", Cnr=" + format(snapshot.query().derivatives().cnr()) + "\n"
				+ "Runtime Reynolds correction\n"
				+ "  status=" + snapshot.reynoldsStatus() + "\n"
				+ "  referenceReynolds=" + format(correction.referenceReynolds()) + "\n"
				+ "  validRatio=" + format(correction.minimumRatio()) + "–" + format(correction.maximumRatio()) + "\n"
				+ "  methodId=" + correction.methodId() + "\n"
				+ "  topologySensitive=" + correction.topologySensitive() + "\n"
				+ "Drag convention\n"
				+ "  CD is projected from the complete CA/CN/CY body-force vector.\n"
				+ "  Pressure CD is the residual after skin-friction and base-pressure owner totals.\n";
	}

	private static JScrollPane tablePane(JTable table) {
		table.setAutoCreateRowSorter(true);
		table.setFillsViewportHeight(true);
		return new JScrollPane(table);
	}

	private static double correctedValue(AerodynamicCoefficients coefficients, int index) {
		return coefficients == null ? Double.NaN : coefficients.toArray()[index];
	}

	private static double number(JSpinner spinner) {
		return ((Number) spinner.getValue()).doubleValue();
	}

	private static double clamp(double value, double minimum, double maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	private static String format(double value) {
		return Double.isFinite(value) ? String.format(Locale.ROOT, "%.8g", value) : "—";
	}

	private static String formatOptional(double value) {
		return format(value);
	}

	record DragBreakdown(double total, double pressure, double base, double friction) { }

	record DiagnosticSnapshot(QueryResult query, AerodynamicCoefficients correctedCoefficients,
			Map<String, AerodynamicCoefficients> correctedOwners, DragBreakdown rawDrag,
			DragBreakdown correctedDrag, String reynoldsStatus) { }

	private static final class ReadOnlyTableModel extends DefaultTableModel {
		private ReadOnlyTableModel(String[] columns) {
			super(columns, 0);
		}

		@Override
		public boolean isCellEditable(int row, int column) {
			return false;
		}
	}
}
