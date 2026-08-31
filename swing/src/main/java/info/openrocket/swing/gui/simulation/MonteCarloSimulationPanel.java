package info.openrocket.swing.gui.simulation;

import info.openrocket.core.document.Simulation;
import info.openrocket.core.montecarlo.MonteCarloExtension;
import info.openrocket.core.montecarlo.MonteCarloRunRecord;
import info.openrocket.core.simulation.extension.SimulationExtension;
import net.miginfocom.swing.MigLayout;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import java.util.List;

/**
 * Monte Carlo tab of the simulation edit dialog.
 *
 * Hosts the shared {@link MonteCarloSetupPanel} together with the
 * {@link MonteCarloVisualizationPanel} viewer, and owns the lifecycle of the simulation's
 * {@link MonteCarloExtension}: the extension is created up front so the settings have
 * somewhere to live, but only attached to the simulation once the user actually opts in
 * (enables Monte Carlo, enables a disturbance, or starts a batch). That keeps simulations
 * that never touch this tab free of a stored extension.
 */
public class MonteCarloSimulationPanel extends JPanel {

	private static final int SETUP_TAB_IDX = 0;
	private static final int RESULTS_TAB_IDX = 1;

	private final Simulation simulation;
	private final MonteCarloExtension extension;
	private final MonteCarloSetupPanel setupPanel;
	private final MonteCarloVisualizationPanel visualizationPanel;
	private final JTabbedPane tabs;

	public MonteCarloSimulationPanel(Simulation simulation) {
		this.simulation = simulation;
		this.extension = findOrCreateExtension(simulation);

		setLayout(new MigLayout("fill, ins 0", "[grow, fill]", "[grow, fill]"));

		setupPanel = new MonteCarloSetupPanel(extension, simulation);
		setupPanel.setExtensionAttachRequest(this::attachExtension);

		visualizationPanel = new MonteCarloVisualizationPanel(simulation);
		setupPanel.addResultsListener(this::onBatchResults);

		tabs = new JTabbedPane(JTabbedPane.TOP);
		tabs.addTab("Setup", setupPanel.wrapInScrollPane());
		tabs.addTab("Results", visualizationPanel);
		tabs.setToolTipTextAt(SETUP_TAB_IDX, "Monte Carlo perturbations, batch execution and exports.");
		tabs.setToolTipTextAt(RESULTS_TAB_IDX, "Plots and statistics of the most recent batch.");

		add(tabs, "grow, push");
	}

	/** Shows the results viewer, e.g. after a batch has finished. */
	public void switchToResultsTab() {
		tabs.setSelectedIndex(RESULTS_TAB_IDX);
	}

	public MonteCarloExtension getExtension() {
		return extension;
	}

	private void onBatchResults(List<MonteCarloRunRecord> results) {
		visualizationPanel.setResults(results);
		if (results != null && !results.isEmpty()) {
			switchToResultsTab();
		}
	}

	/** Adds the extension to the simulation the first time the user commits to using it. */
	private void attachExtension() {
		List<SimulationExtension> extensions = simulation.getSimulationExtensions();
		if (!extensions.contains(extension)) {
			extensions.add(extension);
		}
		simulation.notifySimulationExtensionsChanged();
	}

	private static MonteCarloExtension findOrCreateExtension(Simulation simulation) {
		for (SimulationExtension candidate : simulation.getSimulationExtensions()) {
			if (candidate instanceof MonteCarloExtension monteCarlo) {
				return monteCarlo;
			}
		}
		return new MonteCarloExtension();
	}
}
