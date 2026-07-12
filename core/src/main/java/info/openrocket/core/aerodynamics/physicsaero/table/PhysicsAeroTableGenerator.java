package info.openrocket.core.aerodynamics.physicsaero.table;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.nio.charset.StandardCharsets;
import com.google.gson.GsonBuilder;
import info.openrocket.core.aerodynamics.physicsaero.config.OutputConfiguration;
import info.openrocket.core.aerodynamics.physicsaero.diagnostics.*;

/** Bounded, deterministic and resumable table build skeleton. */
public final class PhysicsAeroTableGenerator {
	@FunctionalInterface public interface CellEvaluator { TableCell evaluate(DeterministicTaskIndex index, double mach, double alphaRad, double betaRad) throws Exception; }
	public BuildReport generate(TableAxes axes, TableMetadata metadata, OutputConfiguration output, Path checkpointDirectory,
			int workers, AtomicBoolean cancelled, CellEvaluator evaluator) throws IOException {
		Files.createDirectories(checkpointDirectory); TableCell[] cells = new TableCell[axes.cellCount()];
		List<ReproductionCase> failures = Collections.synchronizedList(new ArrayList<>());
		ExecutorService executor = Executors.newFixedThreadPool(workers); List<Future<?>> futures = new ArrayList<>();
		try {
			for (int flat = 0; flat < axes.cellCount(); flat++) {
				final DeterministicTaskIndex index = DeterministicTaskIndex.fromFlat(axes, flat); Path checkpoint = checkpointDirectory.resolve(String.format("cell-%08d.aero", flat));
				if (Files.exists(checkpoint)) {
					try { AerodynamicTable saved = new TableReader().read(checkpoint); if (saved.metadata().geometryHash().equals(metadata.geometryHash()) && saved.metadata().settingsHash().equals(metadata.settingsHash())) { cells[flat] = saved.cells().get(0); continue; } }
					catch (IOException | RuntimeException ignored) { Files.deleteIfExists(checkpoint); }
				}
				futures.add(executor.submit(() -> {
					if (cancelled.get()) return;
					try {
						double mach = axes.mach()[index.machIndex()], alpha = axes.alphaRad()[index.alphaIndex()], beta = axes.betaRad()[index.betaIndex()];
						TableCell cell = evaluator.evaluate(index, mach, alpha, beta); cells[index.flatIndex()] = cell;
						TableAxes one = new TableAxes(new double[] {mach}, new double[] {alpha}, new double[] {beta});
						new TableWriter().write(new AerodynamicTable(one, List.of(cell), metadata), checkpoint, checkpointDirectory.resolve(String.format("cell-%08d.json", index.flatIndex())));
					} catch (Exception e) {
						ReproductionCase reproduction = new ReproductionCase(index.flatIndex(), metadata.geometryHash(), Map.of(
								"mach", axes.mach()[index.machIndex()], "alphaRad", axes.alphaRad()[index.alphaIndex()], "betaRad", axes.betaRad()[index.betaIndex()]),
								FailureReason.NUMERICAL_FAILURE, e.toString());
						failures.add(reproduction);
						try { writeReproduction(checkpointDirectory, reproduction); }
						catch (IOException writeFailure) { e.addSuppressed(writeFailure); }
					}
				}));
			}
			for (Future<?> future : futures) try { future.get(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); cancelled.set(true); } catch (ExecutionException e) { throw new IllegalStateException(e.getCause()); }
		} finally { executor.shutdownNow(); }
		int completed = (int) Arrays.stream(cells).filter(Objects::nonNull).count();
		if (!cancelled.get() && failures.isEmpty() && completed == cells.length) {
			new TableWriter().write(new AerodynamicTable(axes, Arrays.asList(cells), metadata), output.tableFile(), output.manifestFile());
		}
		return new BuildReport(cells.length, completed, failures.size(), cancelled.get(), failures.stream().sorted(Comparator.comparingInt(ReproductionCase::cellIndex)).toList());
	}
	private static void writeReproduction(Path directory, ReproductionCase reproduction) throws IOException {
		String json = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(reproduction) + System.lineSeparator();
		Path target = directory.resolve(String.format("failure-%08d.json", reproduction.cellIndex()));
		Path temp = Files.createTempFile(directory, target.getFileName().toString(), ".tmp");
		Files.writeString(temp, json, StandardCharsets.UTF_8);
		try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
		catch (AtomicMoveNotSupportedException e) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
	}
}
