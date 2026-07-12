package info.openrocket.swing.gui.structures;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

import info.openrocket.core.structures.geometry.StructuralComponentExtractor.ExtractedStructuralComponent;

public final class StructuresComponentSelectionPanel extends JPanel {
	private final ComponentTableModel model = new ComponentTableModel();

	public StructuresComponentSelectionPanel() {
		super(new BorderLayout());
		JTable table = new JTable(model);
		table.setFillsViewportHeight(true);
		add(new JScrollPane(table), BorderLayout.CENTER);
	}

	public void setComponents(List<ExtractedStructuralComponent> components) {
		model.setComponents(components);
	}

	public List<ExtractedStructuralComponent> getSelectedComponents() {
		return model.getSelectedComponents();
	}

	private static final class ComponentTableModel extends AbstractTableModel {
		private final List<ExtractedStructuralComponent> components = new ArrayList<>();
		private final List<Boolean> selected = new ArrayList<>();
		private final String[] columns = { "Analyze", "Component", "Type", "Geometry", "Material" };

		void setComponents(List<ExtractedStructuralComponent> newComponents) {
			components.clear();
			selected.clear();
			components.addAll(newComponents);
			for (ExtractedStructuralComponent component : newComponents) {
				selected.add(component.isSelectedByDefault());
			}
			fireTableDataChanged();
		}

		List<ExtractedStructuralComponent> getSelectedComponents() {
			List<ExtractedStructuralComponent> output = new ArrayList<>();
			for (int i = 0; i < components.size(); i++) {
				if (selected.get(i)) {
					output.add(components.get(i));
				}
			}
			return output;
		}

		@Override
		public int getRowCount() {
			return components.size();
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
		public Class<?> getColumnClass(int columnIndex) {
			return columnIndex == 0 ? Boolean.class : String.class;
		}

		@Override
		public boolean isCellEditable(int rowIndex, int columnIndex) {
			return columnIndex == 0;
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			ExtractedStructuralComponent component = components.get(rowIndex);
			switch (columnIndex) {
			case 0:
				return selected.get(rowIndex);
			case 1:
				return component.getComponentName();
			case 2:
				return component.getType();
			case 3:
				return component.isGeometryComplete() ? "Extracted" : "Needs input";
			case 4:
				return component.getStructuralMaterial().getName();
			default:
				return "";
			}
		}

		@Override
		public void setValueAt(Object value, int rowIndex, int columnIndex) {
			if (columnIndex == 0 && value instanceof Boolean) {
				selected.set(rowIndex, (Boolean) value);
				fireTableCellUpdated(rowIndex, columnIndex);
			}
		}
	}
}
