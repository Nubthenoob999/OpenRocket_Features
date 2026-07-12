package info.openrocket.swing.gui.structures;

import java.awt.BorderLayout;

import javax.swing.JLabel;
import javax.swing.JPanel;

import info.openrocket.core.structures.StructuresReport;
import info.openrocket.core.structures.StructuresSummary;

public final class StructuresReportPanel extends JPanel {
	private final JLabel summaryLabel = new JLabel("No report");

	public StructuresReportPanel() {
		super(new BorderLayout());
		add(summaryLabel, BorderLayout.CENTER);
	}

	public void setReport(StructuresReport report) {
		if (report == null) {
			summaryLabel.setText("No report");
			return;
		}
		StructuresSummary summary = report.getSummary();
		summaryLabel.setText(String.format("Pass: %d   Warning: %d   Fail: %d   Insufficient Data: %d",
				summary.getPassCount(), summary.getWarningCount(), summary.getFailCount(),
				summary.getInsufficientDataCount()));
	}
}
