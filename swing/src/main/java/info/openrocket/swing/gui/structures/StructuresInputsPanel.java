package info.openrocket.swing.gui.structures;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

import info.openrocket.core.structures.geometry.BulkheadGeometry;
import info.openrocket.core.structures.geometry.CenteringRingGeometry;
import info.openrocket.core.structures.geometry.FinGeometryStructural;
import info.openrocket.core.structures.geometry.NoseConeGeometryStructural;
import info.openrocket.core.structures.geometry.StructuralComponentExtractor.ExtractedStructuralComponent;
import info.openrocket.core.structures.geometry.TubeGeometry;
import info.openrocket.core.structures.loads.FlightLoadCase;
import info.openrocket.core.structures.loads.FlightLoadSeries;
import info.openrocket.core.structures.materials.StructuralMaterial;
import info.openrocket.core.unit.UnitGroup;

public final class StructuresInputsPanel extends JPanel {
	private final InputTableModel inputModel = new InputTableModel();
	private final InputTableModel materialModel = new InputTableModel();

	public StructuresInputsPanel() {
		super(new BorderLayout());
		JTabbedPane tabs = new JTabbedPane();
		tabs.addTab("Inputs", new JScrollPane(new JTable(inputModel)));
		tabs.addTab("Component Materials", new JScrollPane(new JTable(materialModel)));
		add(tabs, BorderLayout.CENTER);
	}

	public void updateInputs(List<ExtractedStructuralComponent> components, FlightLoadSeries loadSeries) {
		List<InputRow> inputRows = new ArrayList<>();
		List<InputRow> materialRows = new ArrayList<>();
		TubeGeometry tube = findFirst(components, TubeGeometry.class);
		FinGeometryStructural fin = findFirst(components, FinGeometryStructural.class);
		BulkheadGeometry bulkhead = findFirst(components, BulkheadGeometry.class);
		CenteringRingGeometry ring = findFirst(components, CenteringRingGeometry.class);
		NoseConeGeometryStructural nose = findFirst(components, NoseConeGeometryStructural.class);
		FlightLoadCase loadCase = loadSeries == null || loadSeries.isEmpty() ? null : loadSeries.getWorstTubeStressCase();

		addTubeRows(inputRows, tube);
		addFinRows(inputRows, fin);
		addBulkheadRows(inputRows, bulkhead);
		addCenteringRingRows(inputRows, ring);
		addFlightRows(inputRows, loadCase);
		addNoseRows(inputRows, nose, fin);
		addFoSRows(inputRows);
		addMaterialRows(materialRows, components);

		inputModel.setRows(inputRows);
		materialModel.setRows(materialRows);
	}

	private static void addTubeRows(List<InputRow> rows, TubeGeometry tube) {
		rows.add(row("Airframe / Tube Geometry", "Airframe OD", tube == null ? "" :
				UnitGroup.UNITS_LENGTH.toStringUnit(tube.getOuterDiameter()), "Body tube"));
		rows.add(row("Airframe / Tube Geometry", "Tube wall thickness", tube == null ? "" :
				UnitGroup.UNITS_LENGTH.toStringUnit(tube.getWallThickness()), "Body tube"));
		rows.add(row("Airframe / Tube Geometry", "Airframe ID", tube == null ? "" :
				UnitGroup.UNITS_LENGTH.toStringUnit(tube.getInnerDiameter()), "Body tube"));
		rows.add(row("Airframe / Tube Geometry", "Unsupported column length L", tube == null ? "" :
				UnitGroup.UNITS_LENGTH.toStringUnit(tube.getUnsupportedLength()), "Default: tube length"));
		rows.add(row("Airframe / Tube Geometry", "End condition factor K", "1.0", "Default"));
	}

	private static void addFinRows(List<InputRow> rows, FinGeometryStructural fin) {
		rows.add(row("Fin Geometry", "b_semi - semi-span", fin == null ? "" :
				UnitGroup.UNITS_LENGTH.toStringUnit(fin.getSemiSpan()), "Fin set"));
		rows.add(row("Fin Geometry", "C_r - root chord", fin == null ? "" :
				UnitGroup.UNITS_LENGTH.toStringUnit(fin.getRootChord()), "Fin set"));
		rows.add(row("Fin Geometry", "C_t - tip chord", fin == null ? "" :
				UnitGroup.UNITS_LENGTH.toStringUnit(fin.getTipChord()), "Fin set"));
		rows.add(row("Fin Geometry", "Sweep", fin == null ? "" :
				UnitGroup.UNITS_LENGTH.toStringUnit(fin.getSweep()), "Fin set"));
		rows.add(row("Fin Geometry", "t_preset - fin thickness", fin == null ? "" :
				UnitGroup.UNITS_LENGTH.toStringUnit(fin.getThickness()), "Fin set"));
		rows.add(row("Fin Geometry", "N_fins", fin == null ? "" : Integer.toString(fin.getFinCount()), "Fin set"));
	}

	private static void addBulkheadRows(List<InputRow> rows, BulkheadGeometry bulkhead) {
		rows.add(row("Bulkhead", "r_0 - bolt circle inner radius", "Manual input required", "Not stored on bulkhead"));
		rows.add(row("Bulkhead", "a - plate outer radius", bulkhead == null ? "" :
				UnitGroup.UNITS_LENGTH.toStringUnit(bulkhead.getOuterRadius()), "Bulkhead"));
		rows.add(row("Bulkhead", "b - inner radius", "0", "Bulkhead"));
		rows.add(row("Bulkhead", "t - plate thickness", bulkhead == null ? "" :
				UnitGroup.UNITS_LENGTH.toStringUnit(bulkhead.getThickness()), "Bulkhead length"));
	}

	private static void addCenteringRingRows(List<InputRow> rows, CenteringRingGeometry ring) {
		rows.add(row("Centering Ring", "r_0 - motor tube radius", ring == null ? "" :
				UnitGroup.UNITS_LENGTH.toStringUnit(ring.getInnerRadius()), "Centering ring"));
		rows.add(row("Centering Ring", "a - airframe ID/2", ring == null ? "" :
				UnitGroup.UNITS_LENGTH.toStringUnit(ring.getOuterRadius()), "Centering ring"));
		rows.add(row("Centering Ring", "b - motor tube radius", ring == null ? "" :
				UnitGroup.UNITS_LENGTH.toStringUnit(ring.getInnerRadius()), "Centering ring"));
		rows.add(row("Centering Ring", "t - plate thickness", ring == null ? "" :
				UnitGroup.UNITS_LENGTH.toStringUnit(ring.getThickness()), "Centering ring length"));
		rows.add(row("Centering Ring", "N_CR - number of centering rings", ring == null ? "" :
				Integer.toString(ring.getNumberOfRings()), "Centering ring instances"));
	}

	private static void addFlightRows(List<InputRow> rows, FlightLoadCase loadCase) {
		rows.add(row("Flight / Aero Conditions", "alpha", loadCase == null || !Double.isFinite(loadCase.getAngleOfAttack()) ?
				"0 rad" : UnitGroup.UNITS_ANGLE.toStringUnit(loadCase.getAngleOfAttack()), "Selected simulation"));
	}

	private static void addNoseRows(List<InputRow> rows, NoseConeGeometryStructural nose, FinGeometryStructural fin) {
		rows.add(row("Nosecone / Reference Geometry", "Nosecone length", nose == null ? "" :
				UnitGroup.UNITS_LENGTH.toStringUnit(nose.getLength()), "Nose cone"));
		rows.add(row("Nosecone / Reference Geometry", "NC type k-factor", nose == null ? "" :
				String.format("%.3f", noseKFactor(nose.getShape())), "Nose shape"));
		rows.add(row("Nosecone / Reference Geometry", "Length to fin LE", fin == null ? "" :
				UnitGroup.UNITS_LENGTH.toStringUnit(fin.getAxialLeadingEdgePosition()), "Fin set"));
	}

	private static void addFoSRows(List<InputRow> rows) {
		rows.add(row("Factors of Safety", "FoS - Tube buckling", "2.0", "Default"));
		rows.add(row("Factors of Safety", "FoS - Tube stress", "2.0", "Default"));
		rows.add(row("Factors of Safety", "FoS - Fin stress", "2.0", "Default"));
		rows.add(row("Factors of Safety", "FoS - Fin flutter", "1.5", "Default"));
		rows.add(row("Factors of Safety", "FoS - Bulkhead", "2.0", "Default"));
		rows.add(row("Factors of Safety", "FoS - Centering ring", "2.0", "Default"));
	}

	private static void addMaterialRows(List<InputRow> rows, List<ExtractedStructuralComponent> components) {
		for (ExtractedStructuralComponent component : components) {
			StructuralMaterial material = component.getStructuralMaterial();
			String section = component.getComponentName() + " / " + component.getType();
			rows.add(row(section, "OpenRocket material", material.getName(), "Component definition"));
			rows.add(row(section, "Density", Double.isFinite(material.getDensity()) ?
					UnitGroup.UNITS_DENSITY_BULK.toStringUnit(material.getDensity()) : "Missing", "Component definition"));
			rows.add(row(section, "E", pressure(material.getYoungsModulus()), materialSource(material.getYoungsModulus())));
			rows.add(row(section, "Tensile limit", pressure(material.getTensileAllowable()),
					materialSource(material.getTensileAllowable())));
			rows.add(row(section, "Compression limit", pressure(material.getCompressiveStrength()),
					materialSource(material.getCompressiveStrength())));
			rows.add(row(section, "Shear estimate (0.6 x tensile)",
					pressure(0.6 * material.getTensileAllowable()),
					materialSource(material.getTensileAllowable())));
			rows.add(row(section, "Poisson ratio", material.getPoissonRatio() == null ? "Missing" :
					String.format("%.3f", material.getPoissonRatio()), material.getPoissonRatio() == null ?
					"Not stored on assigned component material" : "Assigned component material"));
			rows.add(row(section, "G shear modulus", pressure(material.getShearModulus()), materialSource(material.getShearModulus())));
		}
	}

	private static String pressure(double value) {
		return Double.isFinite(value) && value > 0 ? UnitGroup.UNITS_PRESSURE.toStringUnit(value) : "Missing";
	}

	private static String materialSource(double value) {
		return Double.isFinite(value) && value > 0 ? "Assigned component material" :
				"Not stored on assigned component material";
	}

	private static double noseKFactor(String shape) {
		if (shape == null) {
			return Double.NaN;
		}
		String normalized = shape.toLowerCase(java.util.Locale.ROOT);
		if (normalized.contains("ogive")) {
			return 0.466;
		}
		if (normalized.contains("parabolic")) {
			return 0.5;
		}
		if (normalized.contains("ellipsoid")) {
			return 0.333;
		}
		return 0.667;
	}

	private static <T> T findFirst(List<ExtractedStructuralComponent> components, Class<T> type) {
		for (ExtractedStructuralComponent component : components) {
			if (type.isInstance(component.getGeometry())) {
				return type.cast(component.getGeometry());
			}
		}
		return null;
	}

	private static InputRow row(String section, String input, String value, String source) {
		return new InputRow(section, input, value, source);
	}

	private static final class InputRow {
		private final String section;
		private final String input;
		private final String value;
		private final String source;

		private InputRow(String section, String input, String value, String source) {
			this.section = section;
			this.input = input;
			this.value = value;
			this.source = source;
		}
	}

	private static final class InputTableModel extends AbstractTableModel {
		private final String[] columns = { "Section", "Input", "Value", "Source" };
		private final List<InputRow> rows = new ArrayList<>();

		void setRows(List<InputRow> newRows) {
			rows.clear();
			rows.addAll(newRows);
			fireTableDataChanged();
		}

		@Override
		public int getRowCount() {
			return rows.size();
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
			InputRow row = rows.get(rowIndex);
			switch (columnIndex) {
			case 0:
				return row.section;
			case 1:
				return row.input;
			case 2:
				return row.value;
			case 3:
				return row.source;
			default:
				return "";
			}
		}
	}
}
