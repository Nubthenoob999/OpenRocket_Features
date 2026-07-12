package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import info.openrocket.core.aerodynamics.physicsaero.validation.rasaero.*;
import info.openrocket.core.aerodynamics.physicsaero.validation.rasaero.RasaeroColumnMap.Column;
import info.openrocket.core.aerodynamics.physicsaero.validation.rasaero.RasaeroNormalizationAdapter.*;

class RasaeroPhaseOneHarnessTest {
	private static final Path ROOT = Path.of("src/test/resources/physicsaero/rasaero");
	private static final Path RAW = ROOT.resolve("raw-aeroplots/phase3/SYNTHETIC-AEROPLOT-001.csv");

	@Test void p1Ras001PinnedFixturesHaveRequiredProvenanceAndHashes() throws Exception {
		RasaeroFixtureManifest manifest = RasaeroFixtureManifest.read(ROOT.resolve("manifests/coefficient-corpus-manifest.yaml"));
		manifest.requireCoefficientProvenance();
		assertEquals(manifest.value("raw_sha256"), sha256(RAW));
		assertEquals(manifest.value("normalized_sha256"), sha256(ROOT.resolve(manifest.value("normalized_export"))));
		RasaeroFixtureManifest flight = RasaeroFixtureManifest.read(ROOT.resolve("manifests/flight-dataset-manifest.yaml"));
		assertEquals(flight.value("source_sha256"), sha256(ROOT.resolve(flight.value("source_file"))));
	}

	@Test void p1Ras002HeaderParserAcceptsBenignVariantsAndRejectsAmbiguity() throws Exception {
		String canonical = Files.readString(RAW); List<?> expected = new RasaeroAeroPlotReader().read(canonical);
		assertEquals(expected, new RasaeroAeroPlotReader().read("\ufeff" + canonical));
		String[] lines = canonical.split("\\R"); String quotedHeader = Arrays.stream(lines[0].split(",", -1)).map(h -> "\" " + h.toUpperCase(Locale.ROOT) + " \"").reduce((a,b) -> a + "," + b).orElseThrow();
		assertEquals(expected, new RasaeroAeroPlotReader().read(quotedHeader + "\n" + String.join("\n", Arrays.copyOfRange(lines, 1, lines.length))));
		String tab = canonical.replace(',', '\t'); assertEquals(expected, new RasaeroAeroPlotReader().read(tab));
		List<String> headers = new ArrayList<>(List.of(lines[0].split(","))); Collections.reverse(headers);
		assertEquals(Column.values().length, RasaeroColumnMap.fromHeaders(headers).size());
		String missing = lines[0].replace(",Reynolds Number", "");
		assertEquals(RasaeroParseException.Reason.MISSING_COLUMN, assertThrows(RasaeroParseException.class, () -> RasaeroColumnMap.fromHeaders(List.of(missing.split(",")))).reason());
		String duplicate = lines[0] + ",MACH";
		assertEquals(RasaeroParseException.Reason.DUPLICATE_COLUMN, assertThrows(RasaeroParseException.class, () -> RasaeroColumnMap.fromHeaders(List.of(duplicate.split(",")))).reason());
		String nonnumeric = canonical.replaceFirst("2\\.0,0", "not-a-number,0");
		assertEquals(RasaeroParseException.Reason.NONNUMERIC_CELL, assertThrows(RasaeroParseException.class, () -> new RasaeroAeroPlotReader().read(nonnumeric)).reason());
	}

	@Test void p1Ras003WideRowsBecomeDistinctPowerStatesWithoutColumnMixing() throws Exception {
		var source = new RasaeroAeroPlotReader().read(RAW).get(1);
		Context context = new Context("case", "geometry", .1016, 1.6256, 0, RAW.getFileName().toString(), sha256(RAW));
		List<NormalizedRow> rows = new RasaeroNormalizationAdapter().normalize(source, context);
		assertEquals(2, rows.size()); assertEquals(PowerState.OFF, rows.get(0).powerState()); assertEquals(PowerState.ON, rows.get(1).powerState());
		assertEquals(.3, rows.get(0).ca()); assertEquals(.303307, rows.get(0).cd());
		assertEquals(.26, rows.get(1).ca()); assertEquals(.263282, rows.get(1).cd());
		assertEquals(rows.get(0).cn(), rows.get(1).cn());
	}

	@Test void p1Ras004UnitConversionsRoundTripAtSpecifiedValues() {
		for (double degrees : new double[] {0,2,4}) assertEquals(degrees, Math.toDegrees(Math.toRadians(degrees)), 1e-12);
		for (double inches : new double[] {0,10,50,100}) assertEquals(inches, inches * .0254 / .0254, Math.max(1, inches) * 1e-12);
		for (double diameterIn : new double[] {1.75,3,4,6}) {
			double diameterM = diameterIn * .0254, areaM2 = Math.PI * diameterM * diameterM / 4;
			assertEquals(Math.PI * diameterIn * diameterIn / 4, areaM2 / (.0254 * .0254), 1e-12);
			assertEquals(10, (10 * .0254) / diameterM * diameterIn, 1e-12);
		}
	}

	@Test void p1Ras005And006CoefficientFrameAndNormalSlopeMatchExport() throws Exception {
		List<RasaeroAeroPlotReader.SourceRow> sources = new RasaeroAeroPlotReader().read(RAW); RasaeroCoefficientFrameAdapter frame = new RasaeroCoefficientFrameAdapter();
		for (var source : sources) {
			double alpha = Math.toRadians(source.value(Column.ALPHA)); var transformed = frame.fromBodyAxes(source.value(Column.CA_OFF), source.value(Column.CN), alpha);
			assertEquals(source.value(Column.CD_OFF), transformed.cd(), 3e-6); assertEquals(source.value(Column.CL), transformed.cl(), 3e-6);
		}
		double reconstructed = (sources.get(2).value(Column.CN) - sources.get(0).value(Column.CN)) / Math.toRadians(4);
		assertEquals(sources.get(0).value(Column.CN_ALPHA), reconstructed, 1e-6);
	}

	@Test void p1Ras007NormalizedOrderingIsDeterministic() throws Exception {
		List<RasaeroAeroPlotReader.SourceRow> rows = new ArrayList<>(new RasaeroAeroPlotReader().read(RAW));
		String forward = deterministic(rows); Collections.reverse(rows); assertEquals(forward, deterministic(rows));
		Collections.shuffle(rows, new Random(12345)); assertEquals(forward, deterministic(rows));
	}

	@Test void p1Ras011PublicFlightCorpusReproducesPublishedStatistics() throws Exception {
		var rows = new RasaeroFlightDatasetReader().read(ROOT.resolve("upstream/flight_comparison.csv"));
		assertEquals(28, rows.size()); var rasaero = rows.stream().filter(RasaeroFlightDatasetReader.RasaeroFlightRow::hasRasaeroPrediction).toList(); assertEquals(25, rasaero.size());
		for (var row : rasaero) assertEquals(row.displayedRasaeroErrorPct(), row.reconstructedRasaeroErrorPct(), .006);
		var aggregate = RasaeroFlightDatasetReader.AggregateError.fromRasaeroRows(rasaero);
		assertEquals(5.34, aggregate.meanAbsolutePercentError(), .02); assertEquals(13, aggregate.countWithinFivePercent()); assertEquals(22, aggregate.countWithinTenPercent());
		assertEquals(.54, aggregate.minimumMach()); assertEquals(4.33, aggregate.maximumMach());
	}

	@Test void p1Ras012CumulativeDifferencingUsesMomentsAndReconstructsAssembly() {
		var first = RasaeroComponentDifferencer.Cumulative.fromCp(.2,.3,1.0,0,2);
		var second = RasaeroComponentDifferencer.Cumulative.fromCp(.25,.5,1.2,0,2);
		var increment = new RasaeroComponentDifferencer().difference(first, second, 1e-10);
		assertEquals(.05, increment.ca(), 1e-15); assertEquals(.2, increment.cn(), 1e-15);
		assertEquals(second.cm(), first.cm() + increment.cm(), 1e-15);
		assertEquals(1.5, increment.cpM(), 1e-15);
		var undefined = new RasaeroComponentDifferencer().difference(first, new RasaeroComponentDifferencer.Cumulative(.4,.3,-.1,0,2), 1e-10);
		assertNull(undefined.cpM());
	}

	private static String deterministic(List<RasaeroAeroPlotReader.SourceRow> source) {
		return source.stream().sorted(Comparator.comparingDouble(r -> r.value(Column.ALPHA))).map(r -> r.value(Column.MACH) + "," + r.value(Column.ALPHA) + "," + r.value(Column.CN)).reduce((a,b) -> a + "\n" + b).orElse("");
	}
	private static String sha256(Path path) throws Exception {
		byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)); StringBuilder result = new StringBuilder(); for (byte b : digest) result.append(String.format(Locale.ROOT, "%02x", b)); return result.toString();
	}
}
