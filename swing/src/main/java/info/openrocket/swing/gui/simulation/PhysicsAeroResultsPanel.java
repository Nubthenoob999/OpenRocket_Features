package info.openrocket.swing.gui.simulation;

import java.awt.Dimension;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import info.openrocket.core.aerodynamics.physicsaero.table.AerodynamicTable;
import net.miginfocom.swing.MigLayout;

/** Computed coefficients by Mach for a selected angle and power state. */
final class PhysicsAeroResultsPanel extends JPanel {
	private final JComboBox<Double> alpha = angleSelector();
	private final JComboBox<Double> beta = angleSelector();
	private final JComboBox<Double> power = new JComboBox<>();
	private final CoefficientModel model = new CoefficientModel();
	private AerodynamicTable table;
	private boolean loading;

	PhysicsAeroResultsPanel() {
		super(new MigLayout("fillx, insets 8, gap 8 6, wrap 1", "[grow,fill]", ""));
		setBorder(BorderFactory.createTitledBorder("Computed coefficients"));
		setToolTipText("Stored coefficients at the table reference atmosphere.");
		JPanel conditions = new JPanel(new MigLayout("fillx, insets 0, gapx 6",
				"[][grow,fill][][grow,fill][][grow,fill]", ""));
		conditions.add(new JLabel("Angle of attack:"));
		conditions.add(alpha, "wmin 0");
		conditions.add(new JLabel("Sideslip:"));
		conditions.add(beta, "wmin 0");
		conditions.add(new JLabel("Power state:"));
		conditions.add(power, "wmin 0");
		power.setRenderer(new DefaultListCellRenderer() {
			@Override
			public java.awt.Component getListCellRendererComponent(JList<?> list, Object value,
					int index, boolean selected, boolean focused) {
				super.getListCellRendererComponent(list, value, index, selected, focused);
				if (value instanceof Double fraction) {
					setText(fraction == 0 ? "Coast" : fraction == 1 ? "Powered"
							: String.format(Locale.ROOT, "%.0f%%", fraction * 100));
				}
				return this;
			}
		});
		add(conditions, "growx, wmin 0");

		JTable values = new JTable(model);
		values.setFillsViewportHeight(true);
		values.setAutoCreateRowSorter(true);
		values.getTableHeader().setReorderingAllowed(false);
		values.setDefaultRenderer(Double.class, new DefaultTableCellRenderer() {
			@Override
			protected void setValue(Object value) {
				setHorizontalAlignment(SwingConstants.RIGHT);
				setText(value instanceof Double number && Double.isFinite(number)
						? String.format(Locale.ROOT, "%.5g", number) : "—");
			}
		});
		String[] descriptions = {"Mach number", "Drag coefficient", "Axial-force coefficient",
				"Normal-force coefficient", "Side-force coefficient", "Roll-moment coefficient",
				"Pitch-moment coefficient", "Yaw-moment coefficient"};
		values.getTableHeader().setDefaultRenderer(new DefaultTableCellRenderer() {
			@Override
			public java.awt.Component getTableCellRendererComponent(JTable source, Object value,
					boolean selected, boolean focused, int row, int column) {
				java.awt.Component header = headerRenderer.getTableCellRendererComponent(
						source, value, selected, focused, row, column);
				if (header instanceof javax.swing.JComponent component) {
					component.setToolTipText(descriptions[source.convertColumnIndexToModel(column)]);
				}
				return header;
			}
			private final javax.swing.table.TableCellRenderer headerRenderer =
					values.getTableHeader().getDefaultRenderer();
		});
		values.setPreferredScrollableViewportSize(new Dimension(640, 300));
		JScrollPane scroll = new JScrollPane(values);
		scroll.setColumnHeaderView(values.getTableHeader());
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		SimulationTabLayoutUtils.forceViewportWidth(scroll);
		add(scroll, "growx, wmin 0, hmin 180lp");
		for (JComboBox<Double> selector : java.util.List.of(alpha, beta, power)) {
			selector.setEnabled(false);
			selector.addActionListener(event -> {
				if (!loading) model.fireTableDataChanged();
			});
		}
	}

	void setTable(AerodynamicTable table) {
		loading = true;
		this.table = table;
		try {
			setAxis(alpha, table == null ? new double[0] : table.axes().alphaRad());
			setAxis(beta, table == null ? new double[0] : table.axes().betaRad());
			setAxis(power, table == null ? new double[0] : table.axes().poweredFraction());
		} finally {
			loading = false;
		}
		model.fireTableDataChanged();
	}

	private static void setAxis(JComboBox<Double> selector, double[] axis) {
		Object previous = selector.getSelectedItem();
		selector.removeAllItems();
		int selected = 0;
		for (int index = 0; index < axis.length; index++) {
			selector.addItem(axis[index]);
			if (Math.abs(axis[index]) < Math.abs(axis[selected])) selected = index;
		}
		if (axis.length > 0) {
			selector.setSelectedIndex(selected);
			for (double value : axis) {
				if (Double.valueOf(value).equals(previous)) selector.setSelectedItem(previous);
			}
		}
		selector.setEnabled(axis.length > 1);
	}

	private static JComboBox<Double> angleSelector() {
		JComboBox<Double> selector = new JComboBox<>();
		selector.setRenderer(new DefaultListCellRenderer() {
			@Override
			public java.awt.Component getListCellRendererComponent(JList<?> list, Object value,
					int index, boolean selected, boolean focused) {
				super.getListCellRendererComponent(list, value, index, selected, focused);
				if (value instanceof Double radians) {
					setText(String.format(Locale.ROOT, "%.1f°", Math.toDegrees(radians)));
				}
				return this;
			}
		});
		return selector;
	}

	private final class CoefficientModel extends AbstractTableModel {
		private static final String[] COLUMNS = {"Mach", "CD", "CA", "CN", "CY", "Cl", "Cm", "Cn"};

		@Override
		public int getRowCount() {
			return table == null ? 0 : table.axes().mach().length;
		}

		@Override
		public int getColumnCount() {
			return COLUMNS.length;
		}

		@Override
		public String getColumnName(int column) {
			return COLUMNS[column];
		}

		@Override
		public Class<?> getColumnClass(int column) {
			return Double.class;
		}

		@Override
		public Object getValueAt(int row, int column) {
			if (column == 0) return table.axes().mach()[row];
			var coefficients = table.cell(row, alpha.getSelectedIndex(), beta.getSelectedIndex(),
					power.getSelectedIndex()).coefficients();
			if (column == 1) {
				double a = (Double) alpha.getSelectedItem();
				double b = (Double) beta.getSelectedItem();
				return coefficients.ca() * Math.cos(a) * Math.cos(b)
						+ coefficients.cn() * Math.sin(a) * Math.cos(b) + coefficients.cy() * Math.sin(b);
			}
			return coefficients.toArray()[column - 2];
		}
	}
}
