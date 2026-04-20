package info.openrocket.core.aerodynamics.rom.integration;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Optional CFD residual correction overlay.
 *
 * <p>Stores a sparse set of (mach, alpha, x_norm) → Cp_cfd residual corrections
 * loaded from CSV or OpenFOAM post-processing output. When loaded, the overlay
 * corrects the ROM Cp, CA, and base-drag predictions using interpolated residuals.
 *
 * <p>The overlay is never required for runtime use; the ROM operates correctly
 * without it. Its presence elevates transonic confidence to HIGH when checks
 * are clean (see Section 8.2).
 */
public class CalibrationOverlay {

    private static final String CSV_HEADER =
            "# PathlineROM Calibration Overlay v1.0\n"
            + "# Fields: mach, alpha_deg, x_norm (x/L_body), cp_cfd";

    private final List<double[]> records = new ArrayList<>(); // [mach, alpha_deg, x_norm, cp_cfd]
    private boolean loaded = false;

    public boolean isLoaded() {
        return loaded && !records.isEmpty();
    }

    /**
     * Load overlay from a CSV file.
     *
     * <p>CSV format: {@code mach, alpha_deg, x_norm, cp_cfd} (comments with {@code #}).
     *
     * @param csvFile  source file
     * @throws IOException on read error
     */
    public void loadFromCSV(File csvFile) throws IOException {
        records.clear();
        loaded = false;
        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split(",");
                if (parts.length < 4) continue;
                try {
                    double mach    = Double.parseDouble(parts[0].trim());
                    double alpha   = Double.parseDouble(parts[1].trim());
                    double xNorm   = Double.parseDouble(parts[2].trim());
                    double cpCfd   = Double.parseDouble(parts[3].trim());
                    records.add(new double[]{mach, alpha, xNorm, cpCfd});
                } catch (NumberFormatException ignored) {}
            }
        }
        loaded = !records.isEmpty();
    }

    /**
     * Save current overlay to a CSV file.
     *
     * @param csvFile  destination file
     * @throws IOException on write error
     */
    public void saveToCSV(File csvFile) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(csvFile))) {
            pw.println(CSV_HEADER);
            for (double[] r : records) {
                pw.printf("%.4f, %.4f, %.4f, %.6f%n", r[0], r[1], r[2], r[3]);
            }
        }
    }

    /**
     * Apply the residual Cp correction at a given station.
     *
     * @param cp_physics  ROM Cp before correction
     * @param mach        freestream Mach
     * @param alpha       angle of attack (deg)
     * @param x           axial station normalised to body length (x/L)
     * @return corrected Cp
     */
    public double applyResidualCp(double cp_physics, double mach, double alpha, double x) {
        if (!loaded) return cp_physics;
        double residual = interpolateResidual(mach, alpha, x);
        return cp_physics + residual;
    }

    /**
     * Apply residual correction to the axial-force coefficient.
     *
     * @param CA_physics  ROM CA before correction
     * @param mach        freestream Mach
     * @param alpha       angle of attack (deg)
     * @return corrected CA
     */
    public double applyResidualCA(double CA_physics, double mach, double alpha) {
        if (!loaded) return CA_physics;
        // M8: Surface-area-weighted integration of Cp residuals along body stations.
        // Each record has an x-station; we weight by the local panel spacing (dx).
        List<double[]> matching = new ArrayList<>();
        for (double[] r : records) {
            if (Math.abs(r[0] - mach) < 0.1 && Math.abs(r[1] - alpha) < 2.0) {
                matching.add(r);
            }
        }
        if (matching.isEmpty()) return CA_physics;
        // Sort by x_norm for trapezoidal integration
        matching.sort((a, b) -> Double.compare(a[2], b[2]));
        double integral = 0.0;
        for (int i = 0; i < matching.size() - 1; i++) {
            double dx = matching.get(i + 1)[2] - matching.get(i)[2];
            double cpAvg = (matching.get(i)[3] + matching.get(i + 1)[3]) / 2.0;
            integral += cpAvg * dx;
        }
        return CA_physics + integral;
    }

    /**
     * Apply residual correction to the base drag coefficient.
     *
     * @param Cd_base_physics  ROM base drag before correction
     * @param mach             freestream Mach
     * @return corrected base drag
     */
    public double applyResidualCdBase(double Cd_base_physics, double mach) {
        if (!loaded) return Cd_base_physics;
        // M8: Use last-station (x_norm ~ 1) residuals, weighted by station spacing
        List<double[]> aftRecords = new ArrayList<>();
        for (double[] r : records) {
            if (Math.abs(r[0] - mach) < 0.1 && r[2] > 0.9) {
                aftRecords.add(r);
            }
        }
        if (aftRecords.isEmpty()) return Cd_base_physics;
        // Sort and trapezoidally integrate over the aft region
        aftRecords.sort((a, b) -> Double.compare(a[2], b[2]));
        if (aftRecords.size() == 1) {
            return Cd_base_physics + aftRecords.get(0)[3] * 0.1; // single point, small aft fraction
        }
        double integral = 0.0;
        for (int i = 0; i < aftRecords.size() - 1; i++) {
            double dx = aftRecords.get(i + 1)[2] - aftRecords.get(i)[2];
            double cpAvg = (aftRecords.get(i)[3] + aftRecords.get(i + 1)[3]) / 2.0;
            integral += cpAvg * dx;
        }
        return Cd_base_physics + integral;
    }

    /**
     * Import a CFD surface result from an OpenFOAM postProcess output file.
     *
     * <p>Reads the {@code p} (pressure) field from an OpenFOAM
     * {@code surfaceFieldValue} output and converts to Cp residuals.
     *
     * @param ofPostProc  OpenFOAM post-processing file
     * @param mach        Mach number of this CFD run
     * @param alpha       angle of attack (deg) of this CFD run
     * @throws IOException on read error
     */
    public void importOpenFOAMSurface(File ofPostProc, double mach, double alpha,
                                      double bodyLength) throws IOException {
        if (ofPostProc == null || !ofPostProc.exists()) {
            throw new IOException("OpenFOAM post-processing file not found: " + ofPostProc);
        }
        if (bodyLength <= 0.0) {
            throw new IllegalArgumentException("bodyLength must be positive for x-normalization");
        }
        // Parse plain-text OpenFOAM field: lines with "(x y z) value"
        try (BufferedReader br = new BufferedReader(new FileReader(ofPostProc))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("//") || line.startsWith("/*")) continue;
                // Expecting format: (x y z) cp_value
                if (line.startsWith("(")) {
                    int end = line.indexOf(')');
                    if (end < 0) continue;
                    String[] coords = line.substring(1, end).split("\\s+");
                    String rest = line.substring(end + 1).trim();
                    if (coords.length >= 1 && !rest.isEmpty()) {
                        try {
                            // M7: Parse actual x coordinate and normalize by body length
                            double xActual = Double.parseDouble(coords[0]);
                            double xNorm = Math.max(0.0, Math.min(1.0, xActual / bodyLength));
                            double cpVal = Double.parseDouble(rest.split("\\s+")[0]);
                            records.add(new double[]{mach, alpha, xNorm, cpVal});
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        }
        loaded = !records.isEmpty();
    }

    /**
     * Validation report comparing the ROM to the loaded CFD data.
     */
    public static class ValidationReport {
        public final double rmsErrorCA;
        public final double maxErrorCA;
        public final double rmsErrorCp_surface;
        public final double[] errorPerCondition;
        public final String summary;

        public ValidationReport(double rmsErrorCA, double maxErrorCA,
                                double rmsErrorCp_surface, double[] errorPerCondition,
                                String summary) {
            this.rmsErrorCA       = rmsErrorCA;
            this.maxErrorCA       = maxErrorCA;
            this.rmsErrorCp_surface = rmsErrorCp_surface;
            this.errorPerCondition = errorPerCondition.clone();
            this.summary          = summary;
        }

        public void printToLog() {
            System.out.printf("[ROM CalibrationOverlay] RMS CA error: %.4f  Max CA error: %.4f  "
                    + "RMS Cp error: %.4f%n  %s%n",
                    rmsErrorCA, maxErrorCA, rmsErrorCp_surface, summary);
        }
    }

    // --- helpers ---

    private double interpolateResidual(double mach, double alphaDeg, double xNorm) {
        double bestDist = Double.MAX_VALUE;
        double bestVal  = 0.0;
        for (double[] r : records) {
            double dm = (r[0] - mach) / 0.5;
            double da = (r[1] - alphaDeg) / 5.0;
            double dx = (r[2] - xNorm) / 0.1;
            double dist = dm * dm + da * da + dx * dx;
            if (dist < bestDist) {
                bestDist = dist;
                bestVal  = r[3];
            }
        }
        // Gaussian weight: residual decays with distance
        double weight = Math.exp(-bestDist);
        return bestVal * weight;
    }
}
