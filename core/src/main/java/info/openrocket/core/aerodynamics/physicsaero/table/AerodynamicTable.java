package info.openrocket.core.aerodynamics.physicsaero.table;

import java.util.List;

public record AerodynamicTable(TableAxes axes, List<TableCell> cells, TableMetadata metadata) {
	public AerodynamicTable { cells = List.copyOf(cells); if (cells.size() != axes.cellCount()) throw new IllegalArgumentException("table cell count does not match axes"); }
	public TableCell cell(int mach, int alpha, int beta) { return cells.get(axes.index(mach, alpha, beta)); }
}
