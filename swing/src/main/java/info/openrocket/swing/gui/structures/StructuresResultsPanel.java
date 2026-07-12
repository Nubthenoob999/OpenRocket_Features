package info.openrocket.swing.gui.structures;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;

import info.openrocket.core.structures.StructuresReport;
import info.openrocket.core.structures.StructuresResult;
import info.openrocket.core.unit.UnitGroup;

public final class StructuresResultsPanel extends JPanel {
	private final ResultTableModel model = new ResultTableModel();
	private final JTextArea details = new JTextArea(8, 40);

	public StructuresResultsPanel() {
		super(new BorderLayout(6, 6));
		JTable table = new JTable(model);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.getSelectionModel().addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting()) {
				updateDetails(table.getSelectedRow());
			}
		});
		details.setEditable(false);
		add(new JScrollPane(table), BorderLayout.CENTER);
		add(new JScrollPane(details), BorderLayout.SOUTH);
	}

	public void setReport(StructuresReport report) {
		model.setResults(report == null ? new ArrayList<>() : report.getResults());
		details.setText("");
	}

	private void updateDetails(int row) {
		StructuresResult result = model.getResult(row);
		if (result == null) {
			details.setText("");
			return;
		}
		StringBuilder builder = new StringBuilder();
		builder.append(result.getComponentName()).append(" - ").append(result.getAnalysisType()).append('\n');
		builder.append("Governing case: ").append(result.getGoverningCaseDescription()).append('\n');
		for (Map.Entry<String, Double> entry : result.getValues().entrySet()) {
			builder.append(entry.getKey()).append(": ").append(format(entry.getKey(), entry.getValue())).append('\n');
		}
		for (String warning : result.getWarnings()) {
			builder.append("Warning: ").append(warning).append('\n');
		}
		details.setText(builder.toString());
		details.setCaretPosition(0);
	}

	private static String format(String key, double value) {
		if (!Double.isFinite(value)) {
			return "";
		}
		if (key.endsWith("_N")) {
			return UnitGroup.UNITS_FORCE.toStringUnit(value);
		}
		if (key.endsWith("_Nm")) {
			return UnitGroup.UNITS_MOMENT.toStringUnit(value);
		}
		if (key.endsWith("_mps")) {
			return UnitGroup.UNITS_VELOCITY.toStringUnit(value);
		}
		if (key.endsWith("_m") || key.endsWith("_s")) {
			return key.endsWith("_s") ? UnitGroup.UNITS_LONG_TIME.toStringUnit(value) : UnitGroup.UNITS_LENGTH.toStringUnit(value);
		}
		if (key.endsWith("_m2")) {
			return UnitGroup.UNITS_AREA.toStringUnit(value);
		}
		if (key.endsWith("_Pa")) {
			return UnitGroup.UNITS_PRESSURE.toStringUnit(value);
		}
		return String.format("%.5g", value);
	}

	private static final class ResultTableModel extends AbstractTableModel {
		private final String[] columns = { "Component", "Analysis", "FoS", "Status", "Governing Case" };
		private final List<StructuresResult> results = new ArrayList<>();

		void setResults(List<StructuresResult> newResults) {
			results.clear();
			results.addAll(newResults);
			fireTableDataChanged();
		}

		StructuresResult getResult(int row) {
			if (row < 0 || row >= results.size()) {
				return null;
			}
			return results.get(row);
		}

		@Override
		public int getRowCount() {
			return results.size();
		}

		@Override
		public int getColumnCount() {
			return columns.length;
		}

		@Override
		public String getColumnName(int column) {
			return columns[column];
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			StructuresResult result = results.get(rowIndex);
			switch (columnIndex) {
			case 0:
				return result.getComponentName();
			case 1:
				return result.getAnalysisType();
			case 2:
				return Double.isFinite(result.getFactorOfSafety()) ? String.format("%.2f", result.getFactorOfSafety()) : "";
			case 3:
				return result.getStatus().toString();
			case 4:
				return result.getGoverningCaseDescription();
			default:
				return "";
			}
		}
	}
}
