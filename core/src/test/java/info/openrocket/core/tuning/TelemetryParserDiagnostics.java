package info.openrocket.core.tuning;

public final class TelemetryParserDiagnostics {
	private final String schema;
	private final int rowsRead;
	private final int rowsAccepted;
	private final int droppedSamples;
	private final int sentinelDrops;
	private final int rangeDrops;
	private final int unitCorrections;
	private final int outlierDrops;

	public static final TelemetryParserDiagnostics EMPTY = new TelemetryParserDiagnostics(
			"UNKNOWN",
			0,
			0,
			0,
			0,
			0,
			0,
			0);

	public TelemetryParserDiagnostics(String schema,
										 int rowsRead,
										 int rowsAccepted,
										 int droppedSamples,
										 int sentinelDrops,
										 int rangeDrops,
										 int unitCorrections,
										 int outlierDrops) {
		this.schema = schema;
		this.rowsRead = rowsRead;
		this.rowsAccepted = rowsAccepted;
		this.droppedSamples = droppedSamples;
		this.sentinelDrops = sentinelDrops;
		this.rangeDrops = rangeDrops;
		this.unitCorrections = unitCorrections;
		this.outlierDrops = outlierDrops;
	}

	public String getSchema() {
		return schema;
	}

	public int getRowsRead() {
		return rowsRead;
	}

	public int getRowsAccepted() {
		return rowsAccepted;
	}

	public int getDroppedSamples() {
		return droppedSamples;
	}

	public int getSentinelDrops() {
		return sentinelDrops;
	}

	public double getSentinelRate() {
		double denominator = rowsAccepted + sentinelDrops;
		if (denominator <= 0.0) {
			return 0.0;
		}
		return sentinelDrops / denominator;
	}

	public String getSchemaVersion() {
		return schema;
	}

	public int getRangeDrops() {
		return rangeDrops;
	}

	public int getUnitCorrections() {
		return unitCorrections;
	}

	public int getOutlierDrops() {
		return outlierDrops;
	}

	public static final class Mutable {
		private final String schema;
		private int rowsRead;
		private int rowsAccepted;
		private int sentinelDrops;
		private int rangeDrops;
		private int unitCorrections;
		private int outlierDrops;

		public Mutable(String schema) {
			this.schema = schema;
		}

		public void incRowsRead() {
			rowsRead++;
		}

		public void incRowsAccepted() {
			rowsAccepted++;
		}

		public void incSentinelDrops() {
			sentinelDrops++;
		}

		public void incRangeDrops() {
			rangeDrops++;
		}

		public void incUnitCorrections() {
			unitCorrections++;
		}

		public void incOutlierDrops() {
			outlierDrops++;
		}

		public TelemetryParserDiagnostics freeze() {
			int droppedSamples = Math.max(0, rowsRead - rowsAccepted);
			return new TelemetryParserDiagnostics(
					schema,
					rowsRead,
					rowsAccepted,
					droppedSamples,
					sentinelDrops,
					rangeDrops,
					unitCorrections,
					outlierDrops);
		}
	}
}