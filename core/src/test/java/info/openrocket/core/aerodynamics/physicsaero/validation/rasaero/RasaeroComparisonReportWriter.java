package info.openrocket.core.aerodynamics.physicsaero.validation.rasaero;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import info.openrocket.core.aerodynamics.physicsaero.validation.rasaero.RasaeroFlightRegressionRunner.FlightResult;

public final class RasaeroComparisonReportWriter {
	public void writeCoefficientReport(Path reportDirectory, RasaeroCoefficientComparator.ComparisonResult comparison,
			String geometryHash, String tableHash, int supported, int invalid) throws IOException {
		Files.createDirectories(reportDirectory);
		String json = "{\n  \"schemaVersion\": 1,\n  \"referenceSoftware\": \"RASAero II\",\n"
				+ "  \"geometryHash\": \"" + escape(geometryHash) + "\",\n  \"tableHash\": \"" + escape(tableHash) + "\",\n"
				+ "  \"supported\": " + supported + ",\n  \"invalid\": " + invalid + ",\n"
				+ "  \"mae\": " + number(comparison.mae()) + ",\n  \"rmse\": " + number(comparison.rmse()) + ",\n"
				+ "  \"bias\": " + number(comparison.bias()) + ",\n  \"maximumAbsoluteError\": " + number(comparison.maximumAbsoluteError()) + "\n}\n";
		String markdown = "# RASAero coefficient comparison\n\n- Reference: RASAero II\n- Geometry hash: `" + geometryHash
				+ "`\n- Table hash: `" + tableHash + "`\n- Supported: " + supported + "\n- Invalid: " + invalid
				+ "\n- MAE: " + number(comparison.mae()) + "\n- RMSE: " + number(comparison.rmse())
				+ "\n- Bias: " + number(comparison.bias()) + "\n- Worst index: " + comparison.worstIndex() + "\n";
		Files.writeString(reportDirectory.resolve("rasaero-coefficient-report.json"), json, StandardCharsets.UTF_8);
		Files.writeString(reportDirectory.resolve("rasaero-coefficient-report.md"), markdown, StandardCharsets.UTF_8);
	}
	public void writeFlightReport(Path reportDirectory, List<FlightResult> results) throws IOException {
		Files.createDirectories(reportDirectory); int supported = (int) results.stream().filter(FlightResult::supported).count();
		StringBuilder json = new StringBuilder("{\n  \"schemaVersion\": 1,\n  \"referenceSoftware\": \"RASAero II\",\n  \"supported\": ")
				.append(supported).append(",\n  \"results\": [\n");
		for (int i=0;i<results.size();i++) { FlightResult r=results.get(i); if(i>0)json.append(",\n"); json.append("    {\"flightId\": ").append(r.flightId())
				.append(", \"vehicle\": \"").append(escape(r.vehicle())).append("\", \"supported\": ").append(r.supported())
				.append(", \"reasonCode\": ").append(r.reasonCode()==null?"null":"\""+escape(r.reasonCode())+"\"").append("}"); }
		json.append("\n  ]\n}\n");
		StringBuilder markdown=new StringBuilder("# RASAero flight comparison\n\n| ID | Vehicle | Mach | Status | Reason |\n|---:|---|---:|---|---|\n");
		for(FlightResult r:results)markdown.append('|').append(r.flightId()).append('|').append(r.vehicle().replace("|","\\|")).append('|')
				.append(number(r.peakMachReference())).append('|').append(r.supported()?"SUPPORTED":"SKIPPED").append('|')
				.append(r.reasonCode()==null?"":r.reasonCode()).append("|\n");
		Files.writeString(reportDirectory.resolve("rasaero-flight-report.json"),json,StandardCharsets.UTF_8);
		Files.writeString(reportDirectory.resolve("rasaero-flight-report.md"),markdown,StandardCharsets.UTF_8);
	}
	private static String number(double value){return String.format(Locale.ROOT,"%.12g",value);}
	private static String escape(String value){return value.replace("\\","\\\\").replace("\"","\\\"");}
}
