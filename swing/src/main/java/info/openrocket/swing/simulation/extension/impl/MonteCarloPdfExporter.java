package info.openrocket.swing.simulation.extension.impl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Font;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.Image;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

import info.openrocket.core.montecarlo.MonteCarloRunRecord;
import info.openrocket.core.simulation.montecarlo.MetricStatistics;
import info.openrocket.core.simulation.montecarlo.MonteCarloMetric;
import info.openrocket.core.simulation.montecarlo.MonteCarloParameter;

/** Compact, auditable Monte Carlo PDF report using OpenRocket's existing iText runtime. */
public final class MonteCarloPdfExporter {
    private MonteCarloPdfExporter() { }

    public static void export(File file, String title, List<MonteCarloRunRecord> records, File chart)
            throws IOException {
        Document document = new Document();
		FileOutputStream stream = new FileOutputStream(file);
		try {
            PdfWriter.getInstance(document, stream);
            document.open();
            Font heading = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Font subheading = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
            document.add(new Paragraph(title + " — Monte Carlo analysis", heading));

            long dispersed = records.stream().filter(record -> !record.nominal).count();
            long trajectoryFailures = records.stream()
                    .filter(record -> !record.nominal && record.failureMessage != null).count();
            document.add(new Paragraph("Dispersed trajectories: " + dispersed
					+ "    Nominal reference: included    Trajectory failures: " + trajectoryFailures
					+ "    Master seed: " + records.get(0).masterSeed));

            MonteCarloRunRecord nominal = records.stream().filter(record -> record.nominal)
                    .findFirst().orElse(null);
            if (nominal != null) {
                document.add(new Paragraph("Nominal reference", subheading));
                document.add(new Paragraph(String.format(
                        "Apogee %.3f m; maximum velocity %.3f m/s; flight time %.3f s",
                        nominal.apogee_m, nominal.maxVelocity_mps, nominal.flightTime_s)));
            }

            document.add(new Paragraph("Sampling settings", subheading));
            PdfPTable settings = new PdfPTable(3);
            settings.setWidthPercentage(100);
            settings.addCell("Parameter");
            settings.addCell("Distribution : spread (SI)");
            settings.addCell("Nominal sample");
            MonteCarloRunRecord first = records.get(0);
            for (MonteCarloParameter parameter : MonteCarloParameter.values()) {
                settings.addCell(parameter.name());
                settings.addCell(first.uncertaintySettings.getOrDefault(parameter, "NORMAL:0"));
                settings.addCell(Double.toString(first.sampledVariations.getOrDefault(parameter, 0.0)));
            }
            document.add(settings);

            if (chart != null && chart.isFile()) {
                Image image = Image.getInstance(chart.getAbsolutePath());
                image.scaleToFit(500, 320);
                document.add(new Paragraph("Landing dispersion", subheading));
                document.add(image);
            }

            document.add(new Paragraph("Branch metric distributions", subheading));
            PdfPTable metricsTable = new PdfPTable(8);
            metricsTable.setWidthPercentage(100);
            for (String header : new String[] { "Branch", "Metric", "N", "Mean", "Std dev",
                    "P05", "Median", "P95" }) {
                metricsTable.addCell(header);
            }
            Map<String, List<MonteCarloRunRecord.BodyResult>> branches = new LinkedHashMap<>();
            for (MonteCarloRunRecord record : records) {
                if (record.nominal || record.failureMessage != null) continue;
                for (MonteCarloRunRecord.BodyResult body : record.bodyResults) {
                    if (body.failureMessage == null) {
                        branches.computeIfAbsent(body.bodyId, ignored -> new ArrayList<>()).add(body);
                    }
                }
            }
            for (List<MonteCarloRunRecord.BodyResult> branch : branches.values()) {
                String name = branch.get(0).branchName;
                for (MonteCarloMetric metric : MonteCarloMetric.values()) {
                    List<Double> values = branch.stream().map(body -> body.metrics.get(metric))
                            .filter(value -> value != null && Double.isFinite(value)).toList();
                    if (values.isEmpty()) continue;
                    MetricStatistics statistics = MetricStatistics.from(values);
                    metricsTable.addCell(name);
                    metricsTable.addCell(metric.name());
                    metricsTable.addCell(Integer.toString(statistics.getSampleCount()));
                    metricsTable.addCell(format(statistics.getMean()));
                    metricsTable.addCell(format(statistics.getStandardDeviation()));
                    metricsTable.addCell(format(statistics.getQuantile(0.05)));
                    metricsTable.addCell(format(statistics.getMedian()));
                    metricsTable.addCell(format(statistics.getQuantile(0.95)));
                }
            }
            document.add(metricsTable);

            document.add(new Paragraph("Failures and exclusions", subheading));
            for (MonteCarloRunRecord record : records) {
                if (record.failureMessage != null) {
                    document.add(new Paragraph("Run " + record.runIndex + ": " + record.failureMessage));
                }
                for (MonteCarloRunRecord.BodyResult body : record.bodyResults) {
                    if (body.failureMessage != null) {
                        document.add(new Paragraph("Run " + record.runIndex + " / "
                                + body.branchName + ": " + body.failureMessage));
                    }
                }
            }
			document.close();
        } catch (DocumentException exception) {
            throw new IOException("Unable to generate Monte Carlo PDF", exception);
        } finally {
			if (document.isOpen()) document.close();
			if (stream.getChannel().isOpen()) stream.close();
        }
    }

    private static String format(double value) {
        return String.format(java.util.Locale.US, "%.4f", value);
    }
}
