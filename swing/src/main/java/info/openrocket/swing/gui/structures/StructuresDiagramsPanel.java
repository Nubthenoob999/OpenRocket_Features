package info.openrocket.swing.gui.structures;

import java.awt.GridLayout;

import javax.swing.JPanel;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import info.openrocket.core.structures.loads.StructuralLoadDiagram;
import info.openrocket.core.structures.loads.StructuralLoadDiagramPoint;
import info.openrocket.core.unit.Unit;
import info.openrocket.core.unit.UnitGroup;

public final class StructuresDiagramsPanel extends JPanel {
	private final XYSeriesCollection shearDataset = new XYSeriesCollection();
	private final XYSeriesCollection momentDataset = new XYSeriesCollection();
	private final JFreeChart shearChart;
	private final JFreeChart momentChart;

	public StructuresDiagramsPanel() {
		super(new GridLayout(2, 1, 0, 6));
		Unit lengthUnit = UnitGroup.UNITS_LENGTH.getDefaultUnit();
		Unit forceUnit = UnitGroup.UNITS_FORCE.getDefaultUnit();
		Unit momentUnit = UnitGroup.UNITS_MOMENT.getDefaultUnit();
		shearChart = ChartFactory.createXYLineChart("Shear Force", "Station (" + lengthUnit.getUnit() + ")",
				"Shear (" + forceUnit.getUnit() + ")", shearDataset);
		momentChart = ChartFactory.createXYLineChart("Bending Moment", "Station (" + lengthUnit.getUnit() + ")",
				"Moment (" + momentUnit.getUnit() + ")", momentDataset);
		add(new ChartPanel(shearChart));
		add(new ChartPanel(momentChart));
	}

	public void setDiagram(StructuralLoadDiagram diagram) {
		shearDataset.removeAllSeries();
		momentDataset.removeAllSeries();
		if (diagram == null || diagram.isEmpty()) {
			shearChart.setTitle("Shear Force - No diagram data");
			momentChart.setTitle("Bending Moment - No diagram data");
			return;
		}

		Unit lengthUnit = UnitGroup.UNITS_LENGTH.getDefaultUnit();
		Unit forceUnit = UnitGroup.UNITS_FORCE.getDefaultUnit();
		Unit momentUnit = UnitGroup.UNITS_MOMENT.getDefaultUnit();
		XYSeries shearSeries = new XYSeries(diagram.getComponentName(), false, true);
		XYSeries momentSeries = new XYSeries(diagram.getComponentName(), false, true);
		for (StructuralLoadDiagramPoint point : diagram.getPoints()) {
			shearSeries.add(lengthUnit.toUnit(point.getStation()), forceUnit.toUnit(point.getShearForce()));
			momentSeries.add(lengthUnit.toUnit(point.getStation()), momentUnit.toUnit(point.getBendingMoment()));
		}
		shearDataset.addSeries(shearSeries);
		momentDataset.addSeries(momentSeries);
		shearChart.setTitle("Shear Force - " + diagram.getLoadCaseDescription());
		momentChart.setTitle("Bending Moment - " + diagram.getLoadCaseDescription());
	}
}
