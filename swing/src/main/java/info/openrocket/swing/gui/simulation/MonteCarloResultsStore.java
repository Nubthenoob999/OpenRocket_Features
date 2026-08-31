package info.openrocket.swing.gui.simulation;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import info.openrocket.core.document.Simulation;
import info.openrocket.core.montecarlo.MonteCarloRunRecord;

/**
 * Process-local store of the most recent Monte Carlo batch results per simulation.
 *
 * The setup panel publishes here when a batch finishes; the visualization panel reads
 * from here so plots survive closing and reopening the simulation dialog. Simulation
 * keys are weak and compared by identity, so results are neither shared between
 * distinct simulations nor able to retain a closed document.
 */
public final class MonteCarloResultsStore {
	private static final List<Entry> ENTRIES = new ArrayList<>();

	private MonteCarloResultsStore() {
	}

	/** Returns the last batch results for this simulation, or an empty list. */
	public static synchronized List<MonteCarloRunRecord> get(Simulation simulation) {
		if (simulation == null) {
			return Collections.emptyList();
		}
		Iterator<Entry> iterator = ENTRIES.iterator();
		while (iterator.hasNext()) {
			Entry entry = iterator.next();
			Simulation cached = entry.simulation.get();
			if (cached == null) {
				iterator.remove();
				continue;
			}
			if (cached == simulation) {
				return entry.records;
			}
		}
		return Collections.emptyList();
	}

	/** Replaces the stored results for this exact simulation. */
	public static synchronized void put(Simulation simulation, List<MonteCarloRunRecord> records) {
		Objects.requireNonNull(simulation, "simulation");
		Iterator<Entry> iterator = ENTRIES.iterator();
		while (iterator.hasNext()) {
			Simulation cached = iterator.next().simulation.get();
			if (cached == null || cached == simulation) {
				iterator.remove();
			}
		}
		List<MonteCarloRunRecord> copy = records == null
				? Collections.emptyList()
				: List.copyOf(records);
		ENTRIES.add(new Entry(new WeakReference<>(simulation), copy));
	}

	/** Drops any stored results for this simulation. */
	public static synchronized void clear(Simulation simulation) {
		ENTRIES.removeIf(entry -> {
			Simulation cached = entry.simulation.get();
			return cached == null || cached == simulation;
		});
	}

	private record Entry(WeakReference<Simulation> simulation, List<MonteCarloRunRecord> records) {
	}
}
