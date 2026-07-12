package info.openrocket.swing.gui.structures;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;

import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.structures.calculators.ShearBendingMomentDiagramCalculator;
import info.openrocket.core.structures.StructuresAnalysisService;
import info.openrocket.core.structures.StructuresReport;
import info.openrocket.core.structures.geometry.FinGeometryStructural;
import info.openrocket.core.structures.geometry.NoseConeGeometryStructural;
import info.openrocket.core.structures.geometry.StructuralComponentExtractor;
import info.openrocket.core.structures.geometry.StructuralComponentExtractor.ExtractedStructuralComponent;
import info.openrocket.core.structures.geometry.TubeGeometry;
import info.openrocket.core.structures.loads.FlightLoadSeries;
import info.openrocket.core.structures.loads.SimulationLoadExtractor;
import info.openrocket.core.structures.loads.StructuralLoadDiagram;
import net.miginfocom.swing.MigLayout;

public final class StructuresToolDialog extends JDialog {
	private final OpenRocketDocument document;
	private final JComboBox<Simulation> simulationComboBox = new JComboBox<>();
	private final JLabel simulationStatusLabel = new JLabel();
	private final StructuresComponentSelectionPanel componentSelectionPanel = new StructuresComponentSelectionPanel();
	private final StructuresInputsPanel inputsPanel = new StructuresInputsPanel();
	private final StructuresResultsPanel resultsPanel = new StructuresResultsPanel();
	private final StructuresDiagramsPanel diagramsPanel = new StructuresDiagramsPanel();
	private final StructuresReportPanel reportPanel = new StructuresReportPanel();
	private final StructuralComponentExtractor componentExtractor = new StructuralComponentExtractor();
	private final SimulationLoadExtractor loadExtractor = new SimulationLoadExtractor();
	private final StructuresAnalysisService analysisService = new StructuresAnalysisService();
	private final ShearBendingMomentDiagramCalculator diagramCalculator = new ShearBendingMomentDiagramCalculator();

	public StructuresToolDialog(Frame owner, OpenRocketDocument document) {
		super(owner, "Structures Tool", false);
		this.document = document;
		buildUi();
		refresh();
		setMinimumSize(new Dimension(950, 620));
		setLocationRelativeTo(owner);
	}

	private void buildUi() {
		JPanel top = new JPanel(new MigLayout("fillx, ins 8", "[][grow][]", ""));
		top.add(new JLabel("Simulation"), "right");
		top.add(simulationComboBox, "growx");
		JButton refreshButton = new JButton("Refresh");
		refreshButton.addActionListener(this::refreshAction);
		top.add(refreshButton, "wrap");
		top.add(simulationStatusLabel, "skip 1, span 2, growx");

		JPanel left = new JPanel(new BorderLayout(6, 6));
		JSplitPane inputSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, componentSelectionPanel, inputsPanel);
		inputSplitPane.setResizeWeight(0.35);
		left.add(inputSplitPane, BorderLayout.CENTER);

		JPanel right = new JPanel(new BorderLayout(6, 6));
		right.add(reportPanel, BorderLayout.NORTH);
		JTabbedPane outputTabs = new JTabbedPane();
		outputTabs.addTab("Results", resultsPanel);
		outputTabs.addTab("Diagrams", diagramsPanel);
		right.add(outputTabs, BorderLayout.CENTER);

		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
		splitPane.setResizeWeight(0.45);

		JButton runButton = new JButton("Run Analysis");
		runButton.addActionListener(this::runAnalysis);
		JPanel bottom = new JPanel(new MigLayout("ins 8", "[grow][]", ""));
		bottom.add(new JLabel("Results use SI internally and OpenRocket simulation data directly."), "growx");
		bottom.add(runButton);

		setLayout(new BorderLayout());
		add(top, BorderLayout.NORTH);
		add(splitPane, BorderLayout.CENTER);
		add(bottom, BorderLayout.SOUTH);
	}

	private void refreshAction(ActionEvent event) {
		refresh();
	}

	private void refresh() {
		simulationComboBox.removeAllItems();
		for (Simulation simulation : document.getSimulations()) {
			simulationComboBox.addItem(simulation);
		}
		simulationComboBox.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
			JLabel label = new JLabel(value == null ? "" : value.getName());
			if (isSelected) {
				label.setOpaque(true);
				label.setBackground(list.getSelectionBackground());
				label.setForeground(list.getSelectionForeground());
			}
			return label;
		});

		if (document.getSimulationCount() == 0) {
			simulationStatusLabel.setText("No simulation results are available. Please run a simulation before using the Structures Tool.");
		} else {
			simulationStatusLabel.setText("Select a simulation result for flight loads.");
		}

		List<ExtractedStructuralComponent> components = componentExtractor.extract(document.getRocket());
		componentSelectionPanel.setComponents(components);
		inputsPanel.updateInputs(components, null);
	}

	private void runAnalysis(ActionEvent event) {
		Simulation simulation = (Simulation) simulationComboBox.getSelectedItem();
		if (simulation == null) {
			JOptionPane.showMessageDialog(this,
					"No simulation results are available. Please run a simulation before using the Structures Tool.",
					"Structures Tool", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		FlightLoadSeries loadSeries = loadExtractor.extract(simulation);
		List<ExtractedStructuralComponent> selectedComponents = componentSelectionPanel.getSelectedComponents();
		inputsPanel.updateInputs(selectedComponents, loadSeries);
		StructuresReport report = analysisService.analyze(selectedComponents, loadSeries);
		reportPanel.setReport(report);
		resultsPanel.setReport(report);
		diagramsPanel.setDiagram(createDiagram(selectedComponents, loadSeries));
	}

	private StructuralLoadDiagram createDiagram(List<ExtractedStructuralComponent> selectedComponents,
			FlightLoadSeries loadSeries) {
		if (loadSeries == null || loadSeries.isEmpty()) {
			return null;
		}
		TubeGeometry tube = findFirst(selectedComponents, TubeGeometry.class);
		if (tube == null) {
			return null;
		}
		NoseConeGeometryStructural nose = findFirst(selectedComponents, NoseConeGeometryStructural.class);
		FinGeometryStructural fin = findFirst(selectedComponents, FinGeometryStructural.class);
		return diagramCalculator.calculate(tube, nose, fin, loadSeries.getWorstTubeStressCase());
	}

	private static <T> T findFirst(List<ExtractedStructuralComponent> components, Class<T> type) {
		for (ExtractedStructuralComponent component : components) {
			if (type.isInstance(component.getGeometry())) {
				return type.cast(component.getGeometry());
			}
		}
		return null;
	}
}
