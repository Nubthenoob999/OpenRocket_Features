package info.openrocket.swing.gui.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.JComboBox;
import javax.swing.JTable;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.physicsaero.diagnostics.CellDiagnostics;
import info.openrocket.core.aerodynamics.physicsaero.force.AerodynamicCoefficients;
import info.openrocket.core.aerodynamics.physicsaero.force.ReferenceState;
import info.openrocket.core.aerodynamics.physicsaero.table.AerodynamicTable;
import info.openrocket.core.aerodynamics.physicsaero.table.CertificationState;
import info.openrocket.core.aerodynamics.physicsaero.table.TableAxes;
import info.openrocket.core.aerodynamics.physicsaero.table.TableCell;
import info.openrocket.core.aerodynamics.physicsaero.table.TableMetadata;
import info.openrocket.core.util.Coordinate;

class PhysicsAeroResultsPanelTest {
	@Test
	void selectsStoredCellsAndComputesWindAxisDrag() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			PhysicsAeroResultsPanel panel = new PhysicsAeroResultsPanel();
			panel.setTable(table());
			JTable values = descendants(panel, JTable.class).get(0);
			List<JComboBox> selectors = descendants(panel, JComboBox.class);
			assertEquals(3, values.getRowCount());
			assertEquals(0.1, values.getValueAt(0, 0));
			assertEquals(7.0, values.getValueAt(0, 2)); // zero angles, coast
			assertFalse(values.isCellEditable(0, 2));
			assertEquals(Double.class, values.getColumnClass(2));

			selectors.get(0).setSelectedIndex(0);
			selectors.get(1).setSelectedIndex(0);
			selectors.get(2).setSelectedIndex(1);
			assertEquals(2.0, values.getValueAt(0, 2));
			double expectedDrag = 2 * Math.cos(-0.2) * Math.cos(-0.1)
					+ 3 * Math.sin(-0.2) * Math.cos(-0.1) + 4 * Math.sin(-0.1);
			assertEquals(expectedDrag, (Double) values.getValueAt(0, 1), 1e-12);
			assertEquals(7.0, values.getValueAt(0, 7));

			values.getRowSorter().toggleSortOrder(2);
			values.getRowSorter().toggleSortOrder(2);
			assertEquals(18.0, values.getValueAt(0, 2)); // numeric descending order
			panel.setTable(null);
			assertEquals(0, values.getRowCount());
			selectors.forEach(selector -> assertFalse(selector.isEnabled()));
		});
	}

	private static AerodynamicTable table() {
		TableAxes axes = new TableAxes(new double[] {0.1, 0.5, 1.0},
				new double[] {-0.2, 0}, new double[] {-0.1, 0}, new double[] {0, 1});
		List<TableCell> cells = new ArrayList<>();
		for (int index = 0; index < axes.cellCount(); index++) {
			double value = index + 1;
			cells.add(new TableCell(new AerodynamicCoefficients(value, value + 1, value + 2,
					value + 3, value + 4, value + 5), Map.of(), Map.of(), List.of(),
					new double[6], new double[6], List.of(),
					new ReferenceState(1000, 1, 1, new Coordinate()), CellDiagnostics.direct(), true));
		}
		return new AerodynamicTable(axes, cells, new TableMetadata(TableMetadata.CURRENT_SCHEMA,
				"geometry", "settings", "code", "registry", TableMetadata.REQUIRED_UNITS,
				TableMetadata.REQUIRED_AXIS_CONVENTION, Map.of(), Map.of(),
				CertificationState.EXPERIMENTAL_FLIGHT_PENDING));
	}

	private static <T extends Component> List<T> descendants(Container parent, Class<T> type) {
		List<T> result = new ArrayList<>();
		for (Component child : parent.getComponents()) {
			if (type.isInstance(child)) result.add(type.cast(child));
			if (child instanceof Container container) result.addAll(descendants(container, type));
		}
		return result;
	}
}
