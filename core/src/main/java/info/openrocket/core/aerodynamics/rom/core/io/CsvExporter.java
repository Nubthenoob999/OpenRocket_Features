package info.openrocket.core.aerodynamics.rom.core.io;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import info.openrocket.core.aerodynamics.rom.core.surface.AeroSurface4D;

public final class CsvExporter {

	private CsvExporter() {
	}

	public static void exportToCsv(AeroSurface4D surface, OutputStream out) throws IOException {
		String header = String.format(Locale.ROOT,
				"# AeroSurface4D export - geomHash=%s buildTimestampMs=%d looRmsePercent=%.4f%n"
						+ "mach,log10Re,alpha_deg,beta_deg,Cd_plume_off,Cd_plume_on,Cd_body,dCd_fin,CN,Cm%n",
				surface.geometryHash,
				surface.buildTimestampMs,
				surface.looRmsePercent);
		out.write(header.getBytes(StandardCharsets.UTF_8));

		for (int im = 0; im < surface.machAxis.length; im++) {
			for (int ir = 0; ir < surface.logReAxis.length; ir++) {
				for (int ia = 0; ia < surface.alphaAxis.length; ia++) {
					for (int ib = 0; ib < surface.betaAxis.length; ib++) {
						double cdOff = surface.cdPlumeOff[im][ir][ia][ib];
						double cdOn = surface.cdPlumeOn[im][ir][ia][ib];
						double cdBody = surface.cdBody[im][ir][ia][ib];
						double dCd = cdOff - cdBody;
						double cn = surface.CN[im][ir][ia][ib];
						double cm = surface.Cm[im][ir][ia][ib];
						String line = String.format(Locale.ROOT,
								"%.8f,%.8f,%.8f,%.8f,%.10f,%.10f,%.10f,%.10f,%.10f,%.10f%n",
								surface.machAxis[im],
								surface.logReAxis[ir],
								surface.alphaAxis[ia],
								surface.betaAxis[ib],
								cdOff,
								cdOn,
								cdBody,
								dCd,
								cn,
								cm);
						out.write(line.getBytes(StandardCharsets.UTF_8));
					}
				}
			}
		}
	}
}
