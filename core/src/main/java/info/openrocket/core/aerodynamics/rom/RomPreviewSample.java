package info.openrocket.core.aerodynamics.rom;

import java.util.Collections;
import java.util.List;

/**
 * Immutable snapshot of a single Mach/AoA preview-sweep cell. Mirrors the
 * columns presented in the diagnostics table.
 */
public final class RomPreviewSample {

	public enum Status {
		OK,
		FALLBACK_USED,
		SINGULARITY,
		CANCELLED,
		DISABLED,
		ERROR
	}

	private final double mach;
	private final double aoaDeg;
	private final double thetaDeg;
	private final double betaDeg;
	private final double plumeState;
	private final String regime;

	private final double cdLegacy;
	private final double cdRom;
	private final double cdFinal;
	private final double cnLegacy;
	private final double cnRom;
	private final double cnFinal;
	private final double cmLegacy;
	private final double cmRom;
	private final double cmFinal;
	private final double cpxFinalMeters;

	private final double confidence;
	private final double fallbackWeight;
	private final boolean fallbackUsed;
	private final double separationFraction;
	private final int seedCount;
	private final int marchingSteps;

	private final int transitionedCount;
	private final int separatedCount;
	private final double meanStiffness;
	private final double maxStiffness;
	private final double meanCf;
	private final double minEdgeCp;
	private final double maxEdgeCp;
	private final double minEdgeMach;
	private final double maxEdgeMach;

	private final Status status;
	private final String notes;
	private final List<String> warnings;

	private RomPreviewSample(Builder b) {
		this.mach = b.mach;
		this.aoaDeg = b.aoaDeg;
		this.thetaDeg = b.thetaDeg;
		this.betaDeg = b.betaDeg;
		this.plumeState = b.plumeState;
		this.regime = b.regime != null ? b.regime : "";
		this.cdLegacy = b.cdLegacy;
		this.cdRom = b.cdRom;
		this.cdFinal = b.cdFinal;
		this.cnLegacy = b.cnLegacy;
		this.cnRom = b.cnRom;
		this.cnFinal = b.cnFinal;
		this.cmLegacy = b.cmLegacy;
		this.cmRom = b.cmRom;
		this.cmFinal = b.cmFinal;
		this.cpxFinalMeters = b.cpxFinalMeters;
		this.confidence = b.confidence;
		this.fallbackWeight = b.fallbackWeight;
		this.fallbackUsed = b.fallbackUsed;
		this.separationFraction = b.separationFraction;
		this.seedCount = b.seedCount;
		this.marchingSteps = b.marchingSteps;
		this.transitionedCount = b.transitionedCount;
		this.separatedCount = b.separatedCount;
		this.meanStiffness = b.meanStiffness;
		this.maxStiffness = b.maxStiffness;
		this.meanCf = b.meanCf;
		this.minEdgeCp = b.minEdgeCp;
		this.maxEdgeCp = b.maxEdgeCp;
		this.minEdgeMach = b.minEdgeMach;
		this.maxEdgeMach = b.maxEdgeMach;
		this.status = b.status != null ? b.status : Status.OK;
		this.notes = b.notes != null ? b.notes : "";
		this.warnings = b.warnings != null
				? Collections.unmodifiableList(List.copyOf(b.warnings))
				: Collections.emptyList();
	}

	public double getMach() { return mach; }
	public double getAoaDeg() { return aoaDeg; }
	public double getThetaDeg() { return thetaDeg; }
	public double getBetaDeg() { return betaDeg; }
	public double getPlumeState() { return plumeState; }
	public String getRegime() { return regime; }

	public double getCdLegacy() { return cdLegacy; }
	public double getCdRom() { return cdRom; }
	public double getCdFinal() { return cdFinal; }
	public double getCnLegacy() { return cnLegacy; }
	public double getCnRom() { return cnRom; }
	public double getCnFinal() { return cnFinal; }
	public double getCmLegacy() { return cmLegacy; }
	public double getCmRom() { return cmRom; }
	public double getCmFinal() { return cmFinal; }
	public double getCpxFinalMeters() { return cpxFinalMeters; }

	public double getConfidence() { return confidence; }
	public double getFallbackWeight() { return fallbackWeight; }
	public boolean isFallbackUsed() { return fallbackUsed; }
	public double getSeparationFraction() { return separationFraction; }
	public int getSeedCount() { return seedCount; }
	public int getMarchingSteps() { return marchingSteps; }

	public int getTransitionedCount() { return transitionedCount; }
	public int getSeparatedCount() { return separatedCount; }
	public double getMeanStiffness() { return meanStiffness; }
	public double getMaxStiffness() { return maxStiffness; }
	public double getMeanCf() { return meanCf; }
	public double getMinEdgeCp() { return minEdgeCp; }
	public double getMaxEdgeCp() { return maxEdgeCp; }
	public double getMinEdgeMach() { return minEdgeMach; }
	public double getMaxEdgeMach() { return maxEdgeMach; }

	public Status getStatus() { return status; }
	public String getNotes() { return notes; }
	public List<String> getWarnings() { return warnings; }

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private double mach;
		private double aoaDeg;
		private double thetaDeg;
		private double betaDeg;
		private double plumeState;
		private String regime;
		private double cdLegacy;
		private double cdRom;
		private double cdFinal;
		private double cnLegacy;
		private double cnRom;
		private double cnFinal;
		private double cmLegacy;
		private double cmRom;
		private double cmFinal;
		private double cpxFinalMeters;
		private double confidence;
		private double fallbackWeight;
		private boolean fallbackUsed;
		private double separationFraction;
		private int seedCount;
		private int marchingSteps;
		private int transitionedCount;
		private int separatedCount;
		private double meanStiffness;
		private double maxStiffness;
		private double meanCf;
		private double minEdgeCp;
		private double maxEdgeCp;
		private double minEdgeMach;
		private double maxEdgeMach;
		private Status status = Status.OK;
		private String notes;
		private List<String> warnings;

		public Builder mach(double v) { this.mach = v; return this; }
		public Builder aoaDeg(double v) { this.aoaDeg = v; return this; }
		public Builder thetaDeg(double v) { this.thetaDeg = v; return this; }
		public Builder betaDeg(double v) { this.betaDeg = v; return this; }
		public Builder plumeState(double v) { this.plumeState = v; return this; }
		public Builder regime(String v) { this.regime = v; return this; }
		public Builder cdLegacy(double v) { this.cdLegacy = v; return this; }
		public Builder cdRom(double v) { this.cdRom = v; return this; }
		public Builder cdFinal(double v) { this.cdFinal = v; return this; }
		public Builder cnLegacy(double v) { this.cnLegacy = v; return this; }
		public Builder cnRom(double v) { this.cnRom = v; return this; }
		public Builder cnFinal(double v) { this.cnFinal = v; return this; }
		public Builder cmLegacy(double v) { this.cmLegacy = v; return this; }
		public Builder cmRom(double v) { this.cmRom = v; return this; }
		public Builder cmFinal(double v) { this.cmFinal = v; return this; }
		public Builder cpxFinalMeters(double v) { this.cpxFinalMeters = v; return this; }
		public Builder confidence(double v) { this.confidence = v; return this; }
		public Builder fallbackWeight(double v) { this.fallbackWeight = v; return this; }
		public Builder fallbackUsed(boolean v) { this.fallbackUsed = v; return this; }
		public Builder separationFraction(double v) { this.separationFraction = v; return this; }
		public Builder seedCount(int v) { this.seedCount = v; return this; }
		public Builder marchingSteps(int v) { this.marchingSteps = v; return this; }
		public Builder transitionedCount(int v) { this.transitionedCount = v; return this; }
		public Builder separatedCount(int v) { this.separatedCount = v; return this; }
		public Builder meanStiffness(double v) { this.meanStiffness = v; return this; }
		public Builder maxStiffness(double v) { this.maxStiffness = v; return this; }
		public Builder meanCf(double v) { this.meanCf = v; return this; }
		public Builder minEdgeCp(double v) { this.minEdgeCp = v; return this; }
		public Builder maxEdgeCp(double v) { this.maxEdgeCp = v; return this; }
		public Builder minEdgeMach(double v) { this.minEdgeMach = v; return this; }
		public Builder maxEdgeMach(double v) { this.maxEdgeMach = v; return this; }
		public Builder status(Status v) { this.status = v; return this; }
		public Builder notes(String v) { this.notes = v; return this; }
		public Builder warnings(List<String> v) { this.warnings = v; return this; }

		public RomPreviewSample build() {
			return new RomPreviewSample(this);
		}
	}
}
