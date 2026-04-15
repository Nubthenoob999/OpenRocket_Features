package info.openrocket.swing.gui.dialogs.motor;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.motor.Motor;
import info.openrocket.core.motor.MotorRecommendation;
import info.openrocket.core.motor.MotorRecommendation.MotorRecommendationResult;
import info.openrocket.core.motor.ThrustCurveMotor;
import info.openrocket.core.rocketcomponent.FlightConfiguration;

import info.openrocket.swing.gui.util.GUIUtil;
import net.miginfocom.swing.MigLayout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dialog that analyzes the current rocket design and recommends suitable motors
 * based on safety and performance criteria. Accessible from the Tools menu.
 *
 * @author Alex Zoghlin
 */
public class MotorRecommendationDialog extends JDialog {
	private static final long serialVersionUID = 1L;
	private static final Logger log = LoggerFactory.getLogger(MotorRecommendationDialog.class);

	private static final Color COLOR_RECOMMENDED = new Color(200, 255, 200);
	private static final Color COLOR_MARGINAL = new Color(255, 255, 180);
	private static final Color COLOR_NOT_RECOMMENDED = new Color(255, 200, 200);

	private static final String[] COLUMN_NAMES = {
			"Motor", "Manufacturer", "Impulse Class", "Total Impulse (Ns)",
			"Thrust/Weight", "Est. Apogee (m)", "Safety Rating"
	};

	private final OpenRocketDocument document;
	private JTable resultTable;
	private MotorRecommendationTableModel tableModel;
	private JProgressBar progressBar;
	private JCheckBox singleUseCheck;
	private JCheckBox reloadableCheck;
	private JCheckBox hybridCheck;
	private JButton analyzeButton;
	private JButton closeButton;
	private List<MotorRecommendationResult> currentResults = new ArrayList<>();

	public MotorRecommendationDialog(OpenRocketDocument document, Window owner) {
		super(owner, "Motor Recommendation Engine", ModalityType.APPLICATION_MODAL);
		this.document = document;

		JPanel panel = new JPanel(new MigLayout("fill, ins dialog", "[grow]", "[][][][grow][]"));

		// Header
		JLabel header = new JLabel("Analyze your rocket design and find suitable motors");
		panel.add(header, "wrap");

		// Rocket info
		FlightConfiguration config = document.getSelectedConfiguration();
		JLabel rocketInfo = new JLabel("Configuration: " + config.getName());
		panel.add(rocketInfo, "wrap");

		// Filter panel
		JPanel filterPanel = new JPanel(new MigLayout("ins 0", "[]10[]10[]10[]"));
		filterPanel.add(new JLabel("Motor types:"));
		singleUseCheck = new JCheckBox("Single-use", true);
		reloadableCheck = new JCheckBox("Reloadable", true);
		hybridCheck = new JCheckBox("Hybrid", true);
		filterPanel.add(singleUseCheck);
		filterPanel.add(reloadableCheck);
		filterPanel.add(hybridCheck);

		analyzeButton = new JButton("Analyze");
		analyzeButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				runAnalysis();
			}
		});
		filterPanel.add(analyzeButton);
		panel.add(filterPanel, "wrap");

		// Result table
		tableModel = new MotorRecommendationTableModel();
		resultTable = new JTable(tableModel);
		resultTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		resultTable.setRowHeight(22);
		resultTable.setDefaultRenderer(Object.class, new RecommendationCellRenderer());
		resultTable.getTableHeader().setReorderingAllowed(false);

		// Set column widths
		resultTable.getColumnModel().getColumn(0).setPreferredWidth(120);
		resultTable.getColumnModel().getColumn(1).setPreferredWidth(120);
		resultTable.getColumnModel().getColumn(2).setPreferredWidth(80);
		resultTable.getColumnModel().getColumn(3).setPreferredWidth(100);
		resultTable.getColumnModel().getColumn(4).setPreferredWidth(90);
		resultTable.getColumnModel().getColumn(5).setPreferredWidth(100);
		resultTable.getColumnModel().getColumn(6).setPreferredWidth(180);

		JScrollPane scrollPane = new JScrollPane(resultTable);
		scrollPane.setPreferredSize(new Dimension(800, 400));
		panel.add(scrollPane, "grow, wrap");

		// Bottom panel with progress bar and buttons
		JPanel bottomPanel = new JPanel(new MigLayout("ins 0, fill", "[grow][]"));
		progressBar = new JProgressBar();
		progressBar.setStringPainted(true);
		progressBar.setString("Ready");
		bottomPanel.add(progressBar, "growx");

		closeButton = new JButton("Close");
		closeButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				dispose();
			}
		});
		bottomPanel.add(closeButton);
		panel.add(bottomPanel, "growx");

		add(panel);
		pack();
		setMinimumSize(new Dimension(700, 400));
		setLocationRelativeTo(owner);
		GUIUtil.setDisposableDialogOptions(this, closeButton);
	}

	/**
	 * Runs the motor analysis in a background thread with progress indication.
	 */
	private void runAnalysis() {
		analyzeButton.setEnabled(false);
		progressBar.setIndeterminate(true);
		progressBar.setString("Analyzing motors...");

		final FlightConfiguration config = document.getSelectedConfiguration();

		SwingWorker<List<MotorRecommendationResult>, Void> worker =
				new SwingWorker<List<MotorRecommendationResult>, Void>() {
					@Override
					protected List<MotorRecommendationResult> doInBackground() {
						MotorRecommendation engine = new MotorRecommendation(config);

						if (engine.getMountCount() == 0) {
							return null;
						}

						// Build type filter
						List<Motor.Type> typeFilter = new ArrayList<>();
						if (singleUseCheck.isSelected()) {
							typeFilter.add(Motor.Type.SINGLE);
						}
						if (reloadableCheck.isSelected()) {
							typeFilter.add(Motor.Type.RELOAD);
						}
						if (hybridCheck.isSelected()) {
							typeFilter.add(Motor.Type.HYBRID);
						}

						return engine.recommend(typeFilter.isEmpty() ? null : typeFilter);
					}

					@Override
					protected void done() {
						try {
							List<MotorRecommendationResult> results = get();
							if (results == null) {
								JOptionPane.showMessageDialog(
										MotorRecommendationDialog.this,
										"No motor mounts found in the current configuration.\n"
												+ "Please add a motor mount (body tube or inner tube) to your rocket design.",
										"No Motor Mounts",
										JOptionPane.WARNING_MESSAGE);
								progressBar.setString("No motor mounts found");
							} else {
								currentResults = results;
								tableModel.setResults(results);
								progressBar.setString(results.size() + " motors found");
							}
						} catch (Exception ex) {
							log.error("Error during motor analysis", ex);
							JOptionPane.showMessageDialog(
									MotorRecommendationDialog.this,
									"Error analyzing motors: " + ex.getMessage(),
									"Analysis Error",
									JOptionPane.ERROR_MESSAGE);
							progressBar.setString("Error");
						} finally {
							progressBar.setIndeterminate(false);
							analyzeButton.setEnabled(true);
						}
					}
				};

		worker.execute();
	}

	/**
	 * Table model for the motor recommendation results.
	 */
	private class MotorRecommendationTableModel extends AbstractTableModel {
		private static final long serialVersionUID = 1L;
		private List<MotorRecommendationResult> results = new ArrayList<>();

		public void setResults(List<MotorRecommendationResult> results) {
			this.results = results;
			fireTableDataChanged();
		}

		public MotorRecommendationResult getResult(int row) {
			if (row >= 0 && row < results.size()) {
				return results.get(row);
			}
			return null;
		}

		@Override
		public int getRowCount() {
			return results.size();
		}

		@Override
		public int getColumnCount() {
			return COLUMN_NAMES.length;
		}

		@Override
		public String getColumnName(int column) {
			return COLUMN_NAMES[column];
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			if (rowIndex < 0 || rowIndex >= results.size()) {
				return null;
			}
			MotorRecommendationResult result = results.get(rowIndex);
			ThrustCurveMotor motor = result.getMotor();

			switch (columnIndex) {
				case 0:
					return motor.getDesignation();
				case 1:
					return motor.getManufacturer().getDisplayName();
				case 2:
					return result.getImpulseClass();
				case 3:
					return String.format("%.1f", motor.getTotalImpulseEstimate());
				case 4:
					return String.format("%.1f:1", result.getThrustToWeightRatio());
				case 5:
					return String.format("%.0f", result.getEstimatedApogee());
				case 6:
					return result.getRecommendation();
				default:
					return null;
			}
		}
	}

	/**
	 * Custom cell renderer that color-codes rows based on safety rating.
	 */
	private class RecommendationCellRenderer extends DefaultTableCellRenderer {
		private static final long serialVersionUID = 1L;

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value,
				boolean isSelected, boolean hasFocus, int row, int column) {
			Component comp = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

			if (!isSelected) {
				MotorRecommendationResult result = tableModel.getResult(row);
				if (result != null) {
					double safety = result.getSafetyScore();
					if (safety >= 1.0) {
						comp.setBackground(COLOR_RECOMMENDED);
					} else if (safety >= 0.6) {
						comp.setBackground(COLOR_MARGINAL);
					} else {
						comp.setBackground(COLOR_NOT_RECOMMENDED);
					}
				}
			}

			if (column == 3 || column == 4 || column == 5) {
				setHorizontalAlignment(SwingConstants.RIGHT);
			} else {
				setHorizontalAlignment(SwingConstants.LEFT);
			}

			return comp;
		}
	}
}
