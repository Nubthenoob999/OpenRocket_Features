# RASAero-style Rocket Aerodynamics Benchmark Suite (first-pass)

This archive converts the attached deep-research source catalog into a normalized JSON benchmark package for testing a correlation-based OpenRocket aerodynamic implementation.

## What is ready now

- **Pointwise numeric acceptance data**: A53D02 zero-lift drag and ARCAS D4013 transonic CP/axial-force summaries.
- **Exact scalar regression checks**: L54D27 powered-vs-unpowered transonic drag increments.
- **Machine-readable physics/behavior contracts**: D2163 component deltas and uncertainty limits, L53D14a bluntness behavior, L52D15a fin-planform stability ordering, D4064 high-Mach plume envelope, TM74058 dynamic-stability behavior, and D4014 matched supersonic ARCAS coverage.
- **Catalog placeholders with explicit incompleteness**: R100 (177 configurations) and Aerobee 150A. They are registered and source-linked, but this package does not fabricate untranscribed curve points.

## Folder layout

- `data/` - benchmark JSON files and a flattened pointwise case list.
- `schema/` - permissive JSON Schema for dataset validation.
- `manifests/` - source URLs, report metadata, and provenance.
- `tools/validate_pointwise.py` - compares solver output against all numeric pointwise cases.
- `examples/` - example solver-output file.
- `notes/` - original deep-research guide and digitization notes.

## Pointwise solver interface

Return a JSON file of the form:

```json
{
  "predictions": [
    {
      "case_id": "A53D02:fig11_faired_zero_lift_cd:0",
      "outputs": {"CD_zero_lift": 0.36}
    }
  ]
}
```

Then run:

```bash
python tools/validate_pointwise.py my_solver_output.json
```

The flattened case list is also available as `data/pointwise_test_cases.jsonl` for JUnit parameterization or streaming ingestion.

## Quality labels

- **A** - exact value explicitly stated in source text/table.
- **B** - manually digitized from a clearly legible source figure.
- **C** - trend/ordering assertion from source narrative.
- **D** - metadata-only; not yet pointwise numeric acceptance data.

Every numeric series records a source figure/page and a tolerance. For figure-digitized values, the acceptance tolerance intentionally includes reading uncertainty and should not be treated as raw instrumentation precision.

## Important conventions

Do not mix coefficient definitions across reports without checking each source's reference area, reference length, moment origin, and CP definition. D4013 stores CP as **percent body length**. Other reports may use calibers, body diameters, or a different moment origin.

## Recommended testing order

1. A53D02 `CD_zero_lift(M)`.
2. D4013 transonic `CA_corr(M)` and CP migration.
3. L54D27 powered-flight delta checks.
4. D2163 component-delta and uncertainty-envelope checks.
5. L53D14a and L52D15a method-selection/ordering checks.
6. D4064 and TM74058 specialized plume/dynamic checks.

This is deliberately a **truthful first-pass benchmark suite**: pointwise curves are included only where the source was clearly digitized in this run; other sources are represented as exact source-derived regression assertions rather than invented numbers.


## v0.3 additions (2026-07-13)

- D4014 upgraded from an envelope-only contract to primary Figure 7 CP curves plus supersonic power-off CD points.
- R100 Table I smooth-configuration geometry is machine-readable (60 source rows/groups).
- Aerobee 150A Figure 22 sanity-check data and exact angle-of-attack behavior statements were added.
- The RASAero ARCAS comparison deck now has a separate secondary-reference dataset for Mach-Alt inputs and RASAero II supersonic CP prediction points.
- D4013 and A53D02 received exact source metadata/uncertainty additions without duplicating their existing pointwise curves.
- See `manifests/pdf_ingestion_audit_2026-07-13.json` for the PDF hash-based duplicate audit.

### v0.3.1 canonical repair (2026-07-14)

- Restored the source-series fixed Mach values to the six flattened A53D02 Figure 16 cases. Published ordinates and tolerances are unchanged.
