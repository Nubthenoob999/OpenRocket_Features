package info.openrocket.swing.gui.simulation;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SpinnerNumberModel;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableModel;

import net.miginfocom.swing.MigLayout;

import info.openrocket.core.aerodynamics.rom.DragGridEvaluator;
import info.openrocket.core.aerodynamics.rom.DragSurface;
import info.openrocket.core.aerodynamics.rom.DragSurfaceInterpolator;
import info.openrocket.core.aerodynamics.rom.InducedDragModel;
import info.openrocket.core.aerodynamics.rom.RomGeometryParameters;
import info.openrocket.core.aerodynamics.rom.RomSurfaceMode;
import info.openrocket.core.aerodynamics.rom.RomSurfaceHashUtil;
import info.openrocket.core.aerodynamics.rom.adapter.GeometryAdapter;
import info.openrocket.core.aerodynamics.rom.adapter.SurfaceAdapter;
import info.openrocket.core.aerodynamics.rom.core.eval.AeroGridEvaluator4D;
import info.openrocket.core.aerodynamics.rom.core.io.CsvExporter;
import info.openrocket.core.aerodynamics.rom.core.surface.AeroSurface4D;
import info.openrocket.core.aerodynamics.rom.core.surface.AeroSurface4DInterpolator;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.util.MathUtil;

class RomPrestepPanel extends SimulationScrollablePanel {

    private static final long serialVersionUID = 2770487667033110266L;
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("MM/dd HH:mm", Locale.ROOT);

    private final Simulation simulation;

    private final JLabel surfaceStatusValue = new JLabel("Not computed");
    private final JLabel geometryValue = new JLabel("-");
    private final JTextArea geometryWarning = createWrappingTextArea();

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
    private final JComboBox<String> buildMode = new JComboBox<>(new String[] {
            "3D (Mach, Re, alpha)",
            "4D (Mach, Re, alpha, beta)"
    });

    private final JButton buildButton = new JButton("Build aerodynamic surface");
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JLabel estimatedTime = new JLabel("~0.3 s");

    private final PreviewChartPanel previewChart = new PreviewChartPanel();
    private final JTextArea previewSummary = createWrappingTextArea();

    private final JCheckBox validationToggle = new JCheckBox("Show validation");
    private final JPanel validationPanel = new JPanel(new MigLayout("fill, insets 0"));
    private final JTextArea validationInput = new JTextArea(6, 40);
    private final JButton compareButton = new JButton("Compare");
    private final JButton exportCsvButton = new JButton("Export 4D CSV");
    private final DefaultTableModel validationModel = new DefaultTableModel(
            new Object[] { "M", "Re", "beta", "Cd_CFD", "Cd_ROM", "% error" }, 0) {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JLabel validationSummary = new JLabel("Mean absolute error: n/a");

    private long lastBuildMs = 300;
    private boolean updatingModeSelection;

    RomPrestepPanel(Simulation simulation) {
        super(new MigLayout("fillx, insets 6, gap 8 8, wrap 2", "[grow,fill][grow,fill]", ""));
        this.simulation = simulation;
        add(buildStatusPanel(), "growx, top");
        add(buildControlsPanel(), "growx, top");
        add(buildParametersPanel(), "span 2, growx, top");
        add(buildPreviewPanel(), "grow, top");
        add(buildValidationPanel(), "grow, top");

        wireEvents();
        refreshFromModel();
    }

    private JPanel buildStatusPanel() {
        JPanel panel = new JPanel(new MigLayout("fillx, insets 6, gapx 8, gapy 6", "[right][grow]", ""));
        panel.setBorder(BorderFactory.createTitledBorder("Status"));

        surfaceStatusValue.setForeground(new Color(120, 120, 120));
        geometryWarning.setForeground(new Color(180, 30, 30));
        geometryWarning.setText("Geometry has changed - rebuild required");

        panel.add(new JLabel("Aerodynamic surface:"));
        panel.add(surfaceStatusValue, "wrap");
        panel.add(new JLabel("Geometry:"));
        panel.add(geometryValue, "wrap");
        panel.add(geometryWarning, "span 2, growx");
        return panel;
    }

    private JPanel buildParametersPanel() {
        JPanel panel = new JPanel(new MigLayout("fillx, insets 6, gapx 8, gapy 6", "[right][grow][right][grow]", ""));
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
        panel.add(new JLabel("Build mode:"));
        panel.add(buildMode, "wrap");

        panel.add(new JLabel("Protuberance factor:"));
        panel.add(protuberanceSpinner, "wrap");

        return panel;
    }

    private JPanel buildControlsPanel() {
        JPanel panel = new JPanel(new MigLayout("fillx, insets 6, gapx 8, gapy 6", "[][grow][]", ""));
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
        previewChart.setPreferredSize(new Dimension(360, 210));
        previewSummary.setBorder(BorderFactory.createEmptyBorder(6, 8, 2, 8));
        previewSummary.setText("Build a ROM surface to preview drag coefficient versus Mach.");
        panel.add(previewChart, BorderLayout.CENTER);
        panel.add(previewSummary, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildValidationPanel() {
        JPanel container = new JPanel(new MigLayout("fillx, insets 0, gapy 6, wrap 1", "[grow]", ""));

        validationPanel.setBorder(BorderFactory.createTitledBorder("Validation"));
        validationInput.setText("# Paste rows: M,Re,Cd\n");
        validationInput.setLineWrap(true);
        validationInput.setWrapStyleWord(false);

        JTable table = new JTable(validationModel);
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setPreferredSize(new Dimension(320, 130));

        validationPanel.add(new JLabel("CFD CSV rows (M,Re,Cd):"), "wrap");
        validationPanel.add(new JScrollPane(validationInput), "growx, wrap");
        validationPanel.add(compareButton, "split 3");
        validationPanel.add(exportCsvButton);
        validationPanel.add(validationSummary, "wrap");
        validationPanel.add(tableScroll, "growx");

        validationPanel.setVisible(false);

        container.add(validationToggle);
        container.add(validationPanel, "growx");
        return container;
    }

    private void wireEvents() {
        buildButton.addActionListener(e -> buildSurfaceAsync());
        buildMode.addActionListener(e -> handleBuildModeSelectionChanged());
        validationToggle.addActionListener(e -> validationPanel.setVisible(validationToggle.isSelected()));
        compareButton.addActionListener(e -> runValidationCompare());
        exportCsvButton.addActionListener(e -> exportSurfaceCsv());
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

        RomSurfaceMode selectedMode = simulation.getOptions().getRomSurfaceMode();
        setBuildModeSelection(selectedMode);

        DragSurface current = selectedMode == RomSurfaceMode.THREE_D
                ? simulation.getOptions().getRomDragSurface() : null;
        AeroSurface4D current4D = selectedMode == RomSurfaceMode.FOUR_D
                ? simulation.getOptions().getRomAeroSurface4D() : null;
        if (current == null && current4D == null) {
            boolean hasOtherModeSurface = simulation.getOptions().getRomDragSurface() != null
                    || simulation.getOptions().getRomAeroSurface4D() != null;
            surfaceStatusValue.setText(hasOtherModeSurface
                    ? "Selected ROM mode changed - rebuild required"
                    : "Not computed");
            surfaceStatusValue.setForeground(new Color(120, 120, 120));
            geometryWarning.setText(hasOtherModeSurface
                    ? "ROM dimensionality changed - rebuild required"
                    : "Geometry has changed - rebuild required");
            geometryWarning.setVisible(hasOtherModeSurface);
            previewChart.clear();
            previewSummary.setText("Build a ROM surface to preview drag coefficient versus Mach. "
                    + "The chart will highlight the transonic band, plume-on/plume-off behavior, "
                    + "and any beta sweep included in the selected ROM mode.");
            return;
        }

        long buildMs = current4D != null ? current4D.buildTimestampMs : current.buildTimestampMs;
        String ts = TS_FORMAT.format(Instant.ofEpochMilli(buildMs).atZone(ZoneId.systemDefault()));
        surfaceStatusValue.setText("Ready - built " + ts
                + (selectedMode == RomSurfaceMode.FOUR_D ? " (4D)" : " (3D)"));
        surfaceStatusValue.setForeground(new Color(20, 120, 20));

        String hash = current4D != null ? current4D.geometryHash : current.geometryHash;
        boolean mismatch = !RomSurfaceHashUtil.matchesGeometry(hash, g.geometryHash());
        geometryWarning.setText("Geometry has changed - rebuild required");
        geometryWarning.setVisible(mismatch);

        if (current4D != null) {
            rebuildPreview(current4D);
        } else {
            rebuildPreview(current);
        }
    }

    private void buildSurfaceAsync() {
        FlightConfiguration config = activeConfiguration();
        RomGeometryParameters g = RomGeometryParameters.fromRocket(config);
        applyRoughnessOverride(g);
        InducedDragModel.setProtuberanceFactor(((Number) protuberanceSpinner.getValue()).doubleValue());

        RomSurfaceMode selectedMode = simulation.getOptions().getRomSurfaceMode();
        DragSurface existing = simulation.getOptions().getRomDragSurface();
        AeroSurface4D existing4D = simulation.getOptions().getRomAeroSurface4D();
        boolean use4D = selectedMode == RomSurfaceMode.FOUR_D;

        if (use4D && existing4D != null && RomSurfaceHashUtil.matchesGeometry(existing4D.geometryHash, g.geometryHash())) {
            surfaceStatusValue.setText("Ready - already current");
            surfaceStatusValue.setForeground(new Color(20, 120, 20));
            return;
        }
        if (!use4D && existing != null && RomSurfaceHashUtil.matchesGeometry(existing.geometryHash, g.geometryHash())) {
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
                if (use4D) {
                    AeroSurface4D surface4D = AeroGridEvaluator4D.evaluate(
                            GeometryAdapter.toInput(g),
                            g.geometryHash(),
                            fraction -> SwingUtilities.invokeLater(() -> progressBar.setValue((int) Math.round(fraction * 100.0))));

                    SwingUtilities.invokeLater(() -> {
                        simulation.getOptions().setRomAeroSurface4D(surface4D);
                        simulation.getOptions().setRomDragSurface(SurfaceAdapter.toBetaZeroDragSurface(surface4D));
                        lastBuildMs = Math.max(1L, (System.nanoTime() - startNs) / 1_000_000L);
                        estimatedTime.setText(String.format(Locale.ROOT, "~%.1f s", lastBuildMs / 1000.0));
                        refreshFromModel();
                        progressBar.setVisible(false);
                        buildButton.setEnabled(true);
                    });
                } else {
                    DragSurface surface = DragGridEvaluator.evaluate(g,
                            fraction -> SwingUtilities.invokeLater(() -> progressBar.setValue((int) Math.round(fraction * 100.0))));

                    SwingUtilities.invokeLater(() -> {
                        simulation.getOptions().setRomDragSurface(surface);
                        simulation.getOptions().setRomAeroSurface4D(null);
                        lastBuildMs = Math.max(1L, (System.nanoTime() - startNs) / 1_000_000L);
                        estimatedTime.setText(String.format(Locale.ROOT, "~%.1f s", lastBuildMs / 1000.0));
                        refreshFromModel();
                        progressBar.setVisible(false);
                        buildButton.setEnabled(true);
                    });
                }
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
        AeroSurface4D surface4D = simulation.getOptions().getRomAeroSurface4D();
        validationModel.setRowCount(0);
        validationSummary.setText("Mean absolute error: n/a");
        if (surface == null && surface4D == null) {
            validationSummary.setText("Mean absolute error: n/a (no surface built)");
            return;
        }

        DragSurfaceInterpolator interpolator = surface != null ? new DragSurfaceInterpolator(surface) : null;
        AeroSurface4DInterpolator interpolator4D = surface4D != null ? new AeroSurface4DInterpolator(surface4D) : null;
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
                double beta = parts.length >= 4 ? Double.parseDouble(parts[2].trim()) : 0.0;
                double cdRef = Double.parseDouble(parts.length >= 4 ? parts[3].trim() : parts[2].trim());
                double cdRom;
                if (interpolator4D != null) {
                    cdRom = interpolator4D.queryCdPlumeOff(mach, re, 0.0, beta);
                } else {
                    cdRom = interpolator.queryCdPlumeOff(mach, re, 0.0);
                }
                double errPct = Math.abs(cdRom - cdRef) * 100.0 / Math.max(1e-9, Math.abs(cdRef));

                validationModel.addRow(new Object[] {
                        formatDec(mach),
                        formatSci(re),
                        formatDec(beta),
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
        previewChart.setSeries(mach, off, on, null);
        previewSummary.setText(buildPreviewSummary(re, mach, off, on, null));
    }

    private void rebuildPreview(AeroSurface4D surface) {
        AeroSurface4DInterpolator interpolator = new AeroSurface4DInterpolator(surface);
        double[] mach = new double[81];
        double[] off0 = new double[81];
        double[] on0 = new double[81];
        double[] off15 = new double[81];

        double re = 1e6;
        for (int i = 0; i < mach.length; i++) {
            mach[i] = 4.0 * i / (mach.length - 1);
            off0[i] = interpolator.queryCdPlumeOff(mach[i], re, 0.0, 0.0);
            on0[i] = interpolator.queryCdPlumeOn(mach[i], re, 0.0, 0.0);
            off15[i] = interpolator.queryCdPlumeOff(mach[i], re, 0.0, 15.0);
        }
        previewChart.setSeries(mach, off0, on0, off15);
        previewSummary.setText(buildPreviewSummary(re, mach, off0, on0, off15));
    }

    private void exportSurfaceCsv() {
        AeroSurface4D surface = simulation.getOptions().getRomAeroSurface4D();
        if (surface == null) {
            validationSummary.setText("Mean absolute error: n/a (build a 4D surface before export)");
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export 4D ROM CSV");
        chooser.setSelectedFile(new java.io.File("rom_surface_4d.csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path target = chooser.getSelectedFile().toPath();
        try (FileOutputStream out = new FileOutputStream(target.toFile())) {
            CsvExporter.exportToCsv(surface, out);
            validationSummary.setText("Exported 4D CSV: " + target.getFileName());
        } catch (IOException ex) {
            validationSummary.setText("CSV export failed: " + ex.getMessage());
        }
    }

    private FlightConfiguration activeConfiguration() {
        return simulation.getRocket().getFlightConfiguration(simulation.getFlightConfigurationId());
    }

    private static JTextArea createWrappingTextArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setOpaque(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFocusable(false);
        area.setBorder(BorderFactory.createEmptyBorder());
        return area;
    }

    private void handleBuildModeSelectionChanged() {
        if (updatingModeSelection) {
            return;
        }
        RomSurfaceMode selectedMode = getSelectedBuildMode();
        if (simulation.getOptions().getRomSurfaceMode() == selectedMode) {
            return;
        }

        simulation.getOptions().setRomSurfaceMode(selectedMode);
        simulation.getOptions().setRomDragSurface(null);
        simulation.getOptions().setRomAeroSurface4D(null);
        refreshFromModel();
    }

    private void setBuildModeSelection(RomSurfaceMode mode) {
        int index = mode == RomSurfaceMode.FOUR_D ? 1 : 0;
        if (buildMode.getSelectedIndex() == index) {
            return;
        }
        updatingModeSelection = true;
        try {
            buildMode.setSelectedIndex(index);
        } finally {
            updatingModeSelection = false;
        }
    }

    private RomSurfaceMode getSelectedBuildMode() {
        return buildMode.getSelectedIndex() == 1 ? RomSurfaceMode.FOUR_D : RomSurfaceMode.THREE_D;
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

    private static String buildPreviewSummary(double reynolds, double[] mach, double[] plumeOff,
                                              double[] plumeOn, double[] plumeOffBeta) {
        int peakIndex = indexOfPeak(plumeOff);
        double peakMach = peakIndex >= 0 ? mach[peakIndex] : Double.NaN;
        double peakCd = peakIndex >= 0 ? plumeOff[peakIndex] : Double.NaN;
        String betaLine = plumeOffBeta == null
                ? "Blue is plume-off and orange dashed is plume-on at alpha = 0 deg."
                : "Blue is plume-off at beta = 0 deg, orange dashed is plume-on at beta = 0 deg, and green is plume-off at beta = 15 deg.";
        String peakLine = peakIndex < 0
                ? "The transonic band (Mach 0.8 to 1.2) is where wave drag typically peaks."
                : String.format(Locale.ROOT,
                "The transonic band (Mach 0.8 to 1.2) captures the wave-drag rise, with the plume-off peak near Mach %.2f at Cd %.3f.",
                peakMach, peakCd);
        return String.format(Locale.ROOT,
                "Preview conditions: Re = %.3e, alpha = 0 deg. %s %s Above Mach 1.2 the curves relax toward their supersonic trend.",
                reynolds, betaLine, peakLine);
    }

    private static int indexOfPeak(double[] values) {
        if (values == null || values.length == 0) {
            return -1;
        }
        int bestIndex = 0;
        double bestValue = values[0];
        for (int i = 1; i < values.length; i++) {
            if (values[i] > bestValue) {
                bestValue = values[i];
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private static final class PreviewChartPanel extends JPanel {
        private static final long serialVersionUID = -1115574697605012065L;
        private static final double MAX_MACH = 4.0;
        private static final double TRANSONIC_START = 0.8;
        private static final double TRANSONIC_END = 1.2;

        private double[] mach;
        private double[] off;
        private double[] on;
        private double[] offBeta;

        void clear() {
            this.mach = null;
            this.off = null;
            this.on = null;
            this.offBeta = null;
            repaint();
        }

        void setSeries(double[] mach, double[] off, double[] on, double[] offBeta) {
            this.mach = mach;
            this.off = off;
            this.on = on;
            this.offBeta = offBeta;
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

                Color panelBackground = fallbackColor(getBackground(), UIManager.getColor("Panel.background"),
                        new Color(32, 36, 41));
                Color textColor = fallbackColor(getForeground(), UIManager.getColor("Label.foreground"), Color.WHITE);
                boolean darkTheme = isDark(panelBackground);
                Color plotBackground = darkTheme ? new Color(25, 29, 34) : new Color(248, 248, 248);
                Color plotBorder = darkTheme ? new Color(95, 103, 112) : new Color(200, 200, 200);
                Color gridColor = darkTheme ? new Color(255, 255, 255, 26) : new Color(0, 0, 0, 24);
                Color dimText = withAlpha(textColor, darkTheme ? 185 : 150);
                Color guideColor = darkTheme ? new Color(255, 218, 145, 150) : new Color(140, 110, 70, 130);
                Color transonicFill = darkTheme ? new Color(255, 184, 77, 28) : new Color(255, 171, 64, 32);
                Color machOneColor = darkTheme ? new Color(255, 240, 212, 100) : new Color(140, 100, 55, 100);
                Color offColor = darkTheme ? new Color(96, 174, 255) : new Color(25, 90, 180);
                Color onColor = darkTheme ? new Color(255, 164, 92) : new Color(190, 80, 20);
                Color betaColor = darkTheme ? new Color(123, 214, 145) : new Color(40, 140, 60);
                Color legendBackground = darkTheme ? new Color(18, 22, 28, 215) : new Color(255, 255, 255, 220);

                int left = 54;
                int right = 20;
                int top = 20;
                int bottom = 38;

                int pw = Math.max(1, w - left - right);
                int ph = Math.max(1, h - top - bottom);
                double yMax = computeRangeMax(off, on, offBeta);
                double yStep = computeTickStep(yMax / 4.0);

                g2.setColor(plotBackground);
                g2.fillRect(left, top, pw, ph);
                shadeMachBand(g2, left, top, pw, ph, TRANSONIC_START, TRANSONIC_END, transonicFill);

                g2.setFont(getFont().deriveFont(Font.PLAIN, 11f));
                for (double y = 0.0; y <= yMax + 1e-9; y += yStep) {
                    int py = toPixelY(y, top, ph, yMax);
                    g2.setColor(gridColor);
                    g2.drawLine(left, py, left + pw, py);
                    g2.setColor(dimText);
                    g2.drawString(formatTick(yStep, y), 10, py + 4);
                }

                for (int xTick = 0; xTick <= 4; xTick++) {
                    int px = toPixelX(xTick, left, pw);
                    g2.setColor(gridColor);
                    g2.drawLine(px, top, px, top + ph);
                    g2.setColor(dimText);
                    String label = Integer.toString(xTick);
                    g2.drawString(label, px - g2.getFontMetrics().stringWidth(label) / 2, top + ph + 16);
                }

                drawVLine(g2, left, top, pw, ph, TRANSONIC_START, guideColor);
                drawVLine(g2, left, top, pw, ph, TRANSONIC_END, guideColor);
                drawVLine(g2, left, top, pw, ph, 1.0, machOneColor);

                g2.setColor(plotBorder);
                g2.drawRect(left, top, pw, ph);

                g2.setColor(textColor);
                g2.drawString("Mach", left + pw / 2 - 14, h - 8);

                g2.rotate(-Math.PI / 2.0);
                g2.drawString("Cd", -top - ph / 2 - 8, 18);
                g2.rotate(Math.PI / 2.0);

                if (mach == null || off == null || on == null) {
                    g2.setColor(textColor);
                    g2.setFont(getFont().deriveFont(Font.PLAIN, 12f));
                    String line1 = "Build a ROM surface to preview drag versus Mach.";
                    String line2 = "The shaded band highlights the transonic regime around Mach 1.";
                    g2.drawString(line1, left + 14, top + ph / 2 - 6);
                    g2.setColor(dimText);
                    g2.drawString(line2, left + 14, top + ph / 2 + 14);
                    return;
                }

                drawSeries(g2, left, top, pw, ph, yMax, mach, off, offColor, null);
                drawSeries(g2, left, top, pw, ph, yMax, mach, on, onColor, new float[] { 6f, 6f });
                if (offBeta != null) {
                    drawSeries(g2, left, top, pw, ph, yMax, mach, offBeta, betaColor, new float[] { 2f, 4f });
                }

                drawLegend(g2, left + 10, top + 10, legendBackground, textColor, dimText, offBeta != null,
                        offColor, onColor, betaColor);
                drawPeakMarker(g2, left, top, pw, ph, yMax, mach, off, offColor, legendBackground, textColor);

                g2.setColor(dimText);
                g2.setFont(getFont().deriveFont(Font.PLAIN, 10f));
                String bandLabel = "Transonic band";
                int bandCenter = (toPixelX(TRANSONIC_START, left, pw) + toPixelX(TRANSONIC_END, left, pw)) / 2;
                g2.drawString(bandLabel, bandCenter - g2.getFontMetrics().stringWidth(bandLabel) / 2, top + 12);
            } finally {
                g2.dispose();
            }
        }

        private static void drawSeries(Graphics2D g2, int left, int top, int pw, int ph, double yMax,
                                       double[] x, double[] y, Color color, float[] dash) {
            g2.setColor(color);
            if (dash == null) {
                g2.setStroke(new BasicStroke(2f));
            } else {
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, dash, 0f));
            }
            for (int i = 1; i < x.length; i++) {
                int x1 = toPixelX(x[i - 1], left, pw);
                int y1 = toPixelY(y[i - 1], top, ph, yMax);
                int x2 = toPixelX(x[i], left, pw);
                int y2 = toPixelY(y[i], top, ph, yMax);
                g2.drawLine(x1, y1, x2, y2);
            }
        }

        private static void drawVLine(Graphics2D g2, int left, int top, int pw, int ph, double mach, Color color) {
            int x = toPixelX(mach, left, pw);
            java.awt.Stroke old = g2.getStroke();
            g2.setColor(color);
            g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] { 4f, 4f }, 0f));
            g2.drawLine(x, top, x, top + ph);
            g2.setStroke(old);
        }

        private static void shadeMachBand(Graphics2D g2, int left, int top, int pw, int ph,
                                          double startMach, double endMach, Color fill) {
            int x1 = toPixelX(startMach, left, pw);
            int x2 = toPixelX(endMach, left, pw);
            g2.setColor(fill);
            g2.fillRect(x1, top, Math.max(1, x2 - x1), ph);
        }

        private static void drawLegend(Graphics2D g2, int x, int y, Color background, Color textColor,
                                       Color dimText, boolean showBeta, Color offColor, Color onColor, Color betaColor) {
            int width = showBeta ? 180 : 156;
            int height = showBeta ? 56 : 42;
            g2.setColor(background);
            g2.fillRoundRect(x, y, width, height, 10, 10);
            g2.setColor(withAlpha(dimText, 90));
            g2.drawRoundRect(x, y, width, height, 10, 10);
            g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 10f));
            drawLegendEntry(g2, x + 10, y + 15, offColor, null, textColor, "Plume off");
            drawLegendEntry(g2, x + 10, y + 29, onColor, new float[] { 6f, 6f }, textColor, "Plume on");
            if (showBeta) {
                drawLegendEntry(g2, x + 10, y + 43, betaColor, new float[] { 2f, 4f }, textColor, "Beta = 15 deg");
            }
        }

        private static void drawLegendEntry(Graphics2D g2, int x, int y, Color lineColor, float[] dash,
                                            Color textColor, String label) {
            java.awt.Stroke old = g2.getStroke();
            if (dash == null) {
                g2.setStroke(new BasicStroke(2f));
            } else {
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, dash, 0f));
            }
            g2.setColor(lineColor);
            g2.drawLine(x, y, x + 18, y);
            g2.setStroke(old);
            g2.setColor(textColor);
            g2.drawString(label, x + 24, y + 4);
        }

        private static void drawPeakMarker(Graphics2D g2, int left, int top, int pw, int ph, double yMax,
                                           double[] mach, double[] off, Color offColor, Color boxColor, Color textColor) {
            int peakIndex = indexOfPeak(off);
            if (peakIndex < 0) {
                return;
            }
            int px = toPixelX(mach[peakIndex], left, pw);
            int py = toPixelY(off[peakIndex], top, ph, yMax);
            String label = String.format(Locale.ROOT, "Peak %.3f @ M %.2f", off[peakIndex], mach[peakIndex]);
            int labelWidth = g2.getFontMetrics().stringWidth(label);
            int boxX = px > left + pw * 0.63 ? px - labelWidth - 22 : px + 12;
            boxX = Math.max(left + 6, Math.min(boxX, left + pw - labelWidth - 14));
            int boxY = Math.max(top + 6, py - 18);

            g2.setColor(offColor);
            g2.fillOval(px - 4, py - 4, 8, 8);
            g2.drawLine(px, py, boxX + (boxX < px ? labelWidth + 8 : 0), boxY + 8);

            g2.setColor(boxColor);
            g2.fillRoundRect(boxX - 4, boxY - 10, labelWidth + 10, 16, 8, 8);
            g2.setColor(withAlpha(textColor, 90));
            g2.drawRoundRect(boxX - 4, boxY - 10, labelWidth + 10, 16, 8, 8);
            g2.setColor(textColor);
            g2.drawString(label, boxX, boxY + 2);
        }

        private static int toPixelX(double mach, int left, int pw) {
            return left + (int) Math.round(pw * MathUtil.clamp(mach / MAX_MACH, 0.0, 1.0));
        }

        private static int toPixelY(double value, int top, int ph, double yMax) {
            return top + ph - (int) Math.round(ph * MathUtil.clamp(value / yMax, 0.0, 1.0));
        }

        private static double computeRangeMax(double[]... series) {
            double max = 0.3;
            for (double[] values : series) {
                if (values == null) {
                    continue;
                }
                for (double value : values) {
                    if (Double.isFinite(value)) {
                        max = Math.max(max, value);
                    }
                }
            }
            double padded = max * 1.12;
            double step = computeTickStep(padded / 4.0);
            return Math.ceil(padded / step) * step;
        }

        private static double computeTickStep(double roughStep) {
            if (!(roughStep > 0.0)) {
                return 0.1;
            }
            double exponent = Math.pow(10.0, Math.floor(Math.log10(roughStep)));
            double fraction = roughStep / exponent;
            double niceFraction;
            if (fraction <= 1.0) {
                niceFraction = 1.0;
            } else if (fraction <= 2.0) {
                niceFraction = 2.0;
            } else if (fraction <= 5.0) {
                niceFraction = 5.0;
            } else {
                niceFraction = 10.0;
            }
            return niceFraction * exponent;
        }

        private static String formatTick(double step, double value) {
            if (step >= 1.0) {
                return String.format(Locale.ROOT, "%.0f", value);
            }
            if (step >= 0.1) {
                return String.format(Locale.ROOT, "%.1f", value);
            }
            return String.format(Locale.ROOT, "%.2f", value);
        }

        private static Color fallbackColor(Color preferred, Color secondary, Color fallback) {
            if (preferred != null) {
                return preferred;
            }
            if (secondary != null) {
                return secondary;
            }
            return fallback;
        }

        private static Color withAlpha(Color color, int alpha) {
            return new Color(color.getRed(), color.getGreen(), color.getBlue(), MathUtil.clamp(alpha, 0, 255));
        }

        private static boolean isDark(Color color) {
            double luminance = (0.2126 * color.getRed() + 0.7152 * color.getGreen() + 0.0722 * color.getBlue()) / 255.0;
            return luminance < 0.5;
        }
    }
}
