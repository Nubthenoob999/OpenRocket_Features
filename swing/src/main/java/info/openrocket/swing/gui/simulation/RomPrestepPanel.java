package info.openrocket.swing.gui.simulation;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.DefaultTableModel;

import net.miginfocom.swing.MigLayout;

import info.openrocket.core.aerodynamics.rom.DragGridEvaluator;
import info.openrocket.core.aerodynamics.rom.DragSurface;
import info.openrocket.core.aerodynamics.rom.DragSurfaceInterpolator;
import info.openrocket.core.aerodynamics.rom.InducedDragModel;
import info.openrocket.core.aerodynamics.rom.RomGeometryParameters;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.util.MathUtil;

class RomPrestepPanel extends JPanel {

    private static final long serialVersionUID = 2770487667033110266L;
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("MM/dd HH:mm", Locale.ROOT);

    private final Simulation simulation;

    private final JLabel surfaceStatusValue = new JLabel("Not computed");
    private final JLabel geometryValue = new JLabel("-");
    private final JLabel geometryWarning = new JLabel("Geometry has changed - rebuild required");

    private final JLabel bodyLengthValue = new JLabel("-");
    private final JLabel maxDiameterValue = new JLabel("-");
    private final JLabel finenessValue = new JLabel("-");
    private final JLabel noseShapeValue = new JLabel("-");
    private final JLabel noseLengthValue = new JLabel("-");
    private final JLabel finCountValue = new JLabel("-");
    private final JLabel finSpanValue = new JLabel("-");
    private final JLabel finThicknessValue = new JLabel("-");

    private final JComboBox<String> roughnessOverride = new JComboBox<>(new String[] {
            "Use component finish", "Polished", "Smooth", "Paint", "Unfinished", "Rough"
    });
    private final JSpinner protuberanceSpinner = new JSpinner(new SpinnerNumberModel(1.04, 1.00, 1.20, 0.01));

    private final JButton buildButton = new JButton("Build aerodynamic surface");
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JLabel estimatedTime = new JLabel("~0.3 s");

    private final PreviewChartPanel previewChart = new PreviewChartPanel();

    private final JCheckBox validationToggle = new JCheckBox("Show validation");
    private final JPanel validationPanel = new JPanel(new MigLayout("fill, insets 0"));
    private final JTextArea validationInput = new JTextArea(6, 40);
    private final JButton compareButton = new JButton("Compare");
    private final DefaultTableModel validationModel = new DefaultTableModel(
            new Object[] { "M", "Re", "Cd_CFD", "Cd_ROM", "% error" }, 0) {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JLabel validationSummary = new JLabel("Mean absolute error: n/a");

    private long lastBuildMs = 300;

    RomPrestepPanel(Simulation simulation) {
        super(new BorderLayout());
        this.simulation = simulation;

        JPanel content = new JPanel(new MigLayout("fillx, wrap 1", "[grow]", ""));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        content.add(buildStatusPanel(), "growx");
        content.add(buildParametersPanel(), "growx");
        content.add(buildControlsPanel(), "growx");
        content.add(buildPreviewPanel(), "growx");
        content.add(buildValidationPanel(), "growx");

        add(content, BorderLayout.CENTER);

        wireEvents();
        refreshFromModel();
    }

    private JPanel buildStatusPanel() {
        JPanel panel = new JPanel(new MigLayout("fillx, insets 8", "[right][grow]", ""));
        panel.setBorder(BorderFactory.createTitledBorder("Status"));

        surfaceStatusValue.setForeground(new Color(120, 120, 120));
        geometryWarning.setForeground(new Color(180, 30, 30));

        panel.add(new JLabel("Aerodynamic surface:"));
        panel.add(surfaceStatusValue, "wrap");
        panel.add(new JLabel("Geometry:"));
        panel.add(geometryValue, "wrap");
        panel.add(geometryWarning, "span 2");
        return panel;
    }

    private JPanel buildParametersPanel() {
        JPanel panel = new JPanel(new MigLayout("fillx, insets 8", "[right][grow][right][grow]", ""));
        panel.setBorder(BorderFactory.createTitledBorder("Parameters"));

        panel.add(new JLabel("Body length:"));
        panel.add(bodyLengthValue);
        panel.add(new JLabel("Max diameter:"));
        panel.add(maxDiameterValue, "wrap");

        panel.add(new JLabel("Fineness ratio:"));
        panel.add(finenessValue);
        panel.add(new JLabel("Nose shape:"));
        panel.add(noseShapeValue, "wrap");

        panel.add(new JLabel("Nose length:"));
        panel.add(noseLengthValue);
        panel.add(new JLabel("Fin count:"));
        panel.add(finCountValue, "wrap");

        panel.add(new JLabel("Fin span:"));
        panel.add(finSpanValue);
        panel.add(new JLabel("Fin thickness:"));
        panel.add(finThicknessValue, "wrap");

        panel.add(new JLabel("Surface roughness:"));
        panel.add(roughnessOverride);
        panel.add(new JLabel("Protuberance factor:"));
        panel.add(protuberanceSpinner, "wrap");

        return panel;
    }

    private JPanel buildControlsPanel() {
        JPanel panel = new JPanel(new MigLayout("fillx, insets 8", "[][grow][]", ""));
        panel.setBorder(BorderFactory.createTitledBorder("Build"));

        progressBar.setVisible(false);
        progressBar.setStringPainted(true);

        panel.add(buildButton);
        panel.add(progressBar, "growx");
        panel.add(estimatedTime);

        return panel;
    }

    private JPanel buildPreviewPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Cd(M) preview"));
        previewChart.setPreferredSize(new Dimension(480, 240));
        panel.add(previewChart, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildValidationPanel() {
        JPanel container = new JPanel(new MigLayout("fillx, insets 0, wrap 1", "[grow]", ""));

        validationPanel.setBorder(BorderFactory.createTitledBorder("Validation"));
        validationInput.setText("# Paste rows: M,Re,Cd\n");

        JTable table = new JTable(validationModel);
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setPreferredSize(new Dimension(500, 130));

        validationPanel.add(new JLabel("CFD CSV rows (M,Re,Cd):"), "wrap");
        validationPanel.add(new JScrollPane(validationInput), "growx, wrap");
        validationPanel.add(compareButton, "split 2");
        validationPanel.add(validationSummary, "wrap");
        validationPanel.add(tableScroll, "growx");

        validationPanel.setVisible(false);

        container.add(validationToggle);
        container.add(validationPanel, "growx");
        return container;
    }

    private void wireEvents() {
        buildButton.addActionListener(e -> buildSurfaceAsync());
        validationToggle.addActionListener(e -> validationPanel.setVisible(validationToggle.isSelected()));
        compareButton.addActionListener(e -> runValidationCompare());
    }

    private void refreshFromModel() {
        FlightConfiguration config = activeConfiguration();
        RomGeometryParameters g = RomGeometryParameters.fromRocket(config);

        bodyLengthValue.setText(formatMeters(g.bodyLength));
        maxDiameterValue.setText(formatMeters(g.maxDiameter));
        finenessValue.setText(String.format(Locale.ROOT, "%.2f", g.finessRatio));
        noseShapeValue.setText(String.valueOf(g.noseShape));
        noseLengthValue.setText(formatMeters(g.noseLength));
        finCountValue.setText(Integer.toString(g.finCount));
        finSpanValue.setText(formatMeters(g.finSpan));
        finThicknessValue.setText(formatMeters(g.finThickness));

        geometryValue.setText(g.geometryHash().substring(0, 8));

        DragSurface current = simulation.getOptions().getRomDragSurface();
        if (current == null) {
            surfaceStatusValue.setText("Not computed");
            surfaceStatusValue.setForeground(new Color(120, 120, 120));
            geometryWarning.setVisible(false);
            previewChart.clear();
            return;
        }

        String ts = TS_FORMAT.format(Instant.ofEpochMilli(current.buildTimestampMs).atZone(ZoneId.systemDefault()));
        surfaceStatusValue.setText("Ready - built " + ts);
        surfaceStatusValue.setForeground(new Color(20, 120, 20));

        boolean mismatch = !g.geometryHash().equals(current.geometryHash);
        geometryWarning.setVisible(mismatch);

        rebuildPreview(current);
    }

    private void buildSurfaceAsync() {
        FlightConfiguration config = activeConfiguration();
        RomGeometryParameters g = RomGeometryParameters.fromRocket(config);
        applyRoughnessOverride(g);
        InducedDragModel.setProtuberanceFactor(((Number) protuberanceSpinner.getValue()).doubleValue());

        DragSurface existing = simulation.getOptions().getRomDragSurface();
        if (existing != null && g.geometryHash().equals(existing.geometryHash)) {
            surfaceStatusValue.setText("Ready - already current");
            surfaceStatusValue.setForeground(new Color(20, 120, 20));
            return;
        }

        buildButton.setEnabled(false);
        progressBar.setVisible(true);
        progressBar.setValue(0);

        long startNs = System.nanoTime();

        Thread worker = new Thread(() -> {
            try {
                DragSurface surface = DragGridEvaluator.evaluate(g,
                        fraction -> SwingUtilities.invokeLater(() -> progressBar.setValue((int) Math.round(fraction * 100.0))));

                SwingUtilities.invokeLater(() -> {
                    simulation.getOptions().setRomDragSurface(surface);
                    lastBuildMs = Math.max(1L, (System.nanoTime() - startNs) / 1_000_000L);
                    estimatedTime.setText(String.format(Locale.ROOT, "~%.1f s", lastBuildMs / 1000.0));
                    refreshFromModel();
                    progressBar.setVisible(false);
                    buildButton.setEnabled(true);
                });
            } catch (RuntimeException ex) {
                SwingUtilities.invokeLater(() -> {
                    surfaceStatusValue.setText("Build failed: " + ex.getMessage());
                    surfaceStatusValue.setForeground(new Color(180, 30, 30));
                    progressBar.setVisible(false);
                    buildButton.setEnabled(true);
                });
            }
        }, "rom-surface-build");
        worker.setDaemon(true);
        worker.start();
    }

    private void runValidationCompare() {
        DragSurface surface = simulation.getOptions().getRomDragSurface();
        validationModel.setRowCount(0);
        validationSummary.setText("Mean absolute error: n/a");
        if (surface == null) {
            validationSummary.setText("Mean absolute error: n/a (no surface built)");
            return;
        }

        DragSurfaceInterpolator interpolator = new DragSurfaceInterpolator(surface);
        String[] lines = validationInput.getText().split("\\r?\\n");

        int count = 0;
        double absSum = 0.0;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] parts = trimmed.split(",");
            if (parts.length < 3) {
                continue;
            }
            try {
                double mach = Double.parseDouble(parts[0].trim());
                double re = Double.parseDouble(parts[1].trim());
                double cdRef = Double.parseDouble(parts[2].trim());
                double cdRom = interpolator.queryCdPlumeOff(mach, re, 0.0);
                double errPct = Math.abs(cdRom - cdRef) * 100.0 / Math.max(1e-9, Math.abs(cdRef));

                validationModel.addRow(new Object[] {
                        formatDec(mach),
                        formatSci(re),
                        formatDec(cdRef),
                        formatDec(cdRom),
                        formatDec(errPct)
                });

                absSum += errPct;
                count++;
            } catch (RuntimeException ignore) {
                // Skip malformed rows.
            }
        }

        if (count > 0) {
            validationSummary.setText(String.format(Locale.ROOT, "Mean absolute error: %.2f%%", absSum / count));
        }
    }

    private void rebuildPreview(DragSurface surface) {
        DragSurfaceInterpolator interpolator = new DragSurfaceInterpolator(surface);
        double[] mach = new double[81];
        double[] off = new double[81];
        double[] on = new double[81];

        double re = 1e6;
        for (int i = 0; i < mach.length; i++) {
            mach[i] = 4.0 * i / (mach.length - 1);
            off[i] = interpolator.queryCdPlumeOff(mach[i], re, 0.0);
            on[i] = interpolator.queryCdPlumeOn(mach[i], re, 0.0);
        }
        previewChart.setSeries(mach, off, on);
    }

    private FlightConfiguration activeConfiguration() {
        return simulation.getRocket().getFlightConfiguration(simulation.getFlightConfigurationId());
    }

    private void applyRoughnessOverride(RomGeometryParameters g) {
        String choice = String.valueOf(roughnessOverride.getSelectedItem());
        if ("Polished".equals(choice)) {
            g.surfaceRoughness = 0.5e-6;
        } else if ("Smooth".equals(choice)) {
            g.surfaceRoughness = 2e-6;
        } else if ("Paint".equals(choice)) {
            g.surfaceRoughness = 6.4e-6;
        } else if ("Unfinished".equals(choice)) {
            g.surfaceRoughness = 60e-6;
        } else if ("Rough".equals(choice)) {
            g.surfaceRoughness = 500e-6;
        }
    }

    private static String formatMeters(double value) {
        return String.format(Locale.ROOT, "%.4f m", value);
    }

    private static String formatSci(double value) {
        return String.format(Locale.ROOT, "%.3e", value);
    }

    private static String formatDec(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static final class PreviewChartPanel extends JPanel {
        private static final long serialVersionUID = -1115574697605012065L;

        private double[] mach;
        private double[] off;
        private double[] on;

        void clear() {
            this.mach = null;
            this.off = null;
            this.on = null;
            repaint();
        }

        void setSeries(double[] mach, double[] off, double[] on) {
            this.mach = mach;
            this.off = off;
            this.on = on;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();

                int left = 44;
                int right = 16;
                int top = 18;
                int bottom = 28;

                int pw = Math.max(1, w - left - right);
                int ph = Math.max(1, h - top - bottom);

                g2.setColor(new Color(248, 248, 248));
                g2.fillRect(left, top, pw, ph);
                g2.setColor(new Color(200, 200, 200));
                g2.drawRect(left, top, pw, ph);

                drawVLine(g2, left, top, pw, ph, 0.8, new Color(150, 150, 150));
                drawVLine(g2, left, top, pw, ph, 1.2, new Color(150, 150, 150));

                g2.setColor(new Color(50, 50, 50));
                g2.setFont(getFont().deriveFont(Font.PLAIN, 11f));
                g2.drawString("0", left - 10, top + ph + 14);
                g2.drawString("4", left + pw - 4, top + ph + 14);
                g2.drawString("0", left - 20, top + ph + 4);
                g2.drawString("1.5", left - 28, top + 4);
                g2.drawString("Mach", left + pw / 2 - 12, h - 6);

                g2.rotate(-Math.PI / 2.0);
                g2.drawString("Cd", -top - ph / 2 - 8, 14);
                g2.rotate(Math.PI / 2.0);

                if (mach == null || off == null || on == null) {
                    return;
                }

                drawSeries(g2, left, top, pw, ph, mach, off, new Color(25, 90, 180), null);
                drawSeries(g2, left, top, pw, ph, mach, on, new Color(190, 80, 20), new float[] { 6f, 6f });
            } finally {
                g2.dispose();
            }
        }

        private static void drawSeries(Graphics2D g2, int left, int top, int pw, int ph,
                                       double[] x, double[] y, Color color, float[] dash) {
            g2.setColor(color);
            if (dash == null) {
                g2.setStroke(new BasicStroke(2f));
            } else {
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, dash, 0f));
            }
            for (int i = 1; i < x.length; i++) {
                int x1 = left + (int) Math.round(pw * MathUtil.clamp(x[i - 1] / 4.0, 0.0, 1.0));
                int y1 = top + ph - (int) Math.round(ph * MathUtil.clamp(y[i - 1] / 1.5, 0.0, 1.0));
                int x2 = left + (int) Math.round(pw * MathUtil.clamp(x[i] / 4.0, 0.0, 1.0));
                int y2 = top + ph - (int) Math.round(ph * MathUtil.clamp(y[i] / 1.5, 0.0, 1.0));
                g2.drawLine(x1, y1, x2, y2);
            }
        }

        private static void drawVLine(Graphics2D g2, int left, int top, int pw, int ph, double mach, Color color) {
            int x = left + (int) Math.round((mach / 4.0) * pw);
            BasicStroke old = (BasicStroke) g2.getStroke();
            g2.setColor(color);
            g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] { 4f, 4f }, 0f));
            g2.drawLine(x, top, x, top + ph);
            g2.setStroke(old);
        }
    }
}
