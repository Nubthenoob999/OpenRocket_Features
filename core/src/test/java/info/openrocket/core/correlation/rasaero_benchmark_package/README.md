# RASAero / NASA-NACA Benchmark Package v0.3.1

This release extends v0.2 by reviewing all six user-supplied PDFs, checking them by SHA-256 against the existing package, and appending only non-duplicate benchmark information.

## What changed

- Added the previously missing `source_pdfs/D-4014.pdf`.
- Reused the five already-identical PDFs instead of storing duplicates.
- Upgraded NASA TN D-4014 to a pointwise supersonic ARCAS benchmark (CP and power-off CD).
- Added machine-readable NASA TR R-100 Table I smooth-body geometry (60 source rows/groups).
- Added Aerobee 150A Figure 22 sanity-check data and exact angle-of-attack behavior assertions.
- Added a separate RASAero ARCAS secondary-reference dataset with Mach-Alt setup and RASAero II CP predictions.
- Added exact D-4013 and A53D02 metadata/uncertainty information without duplicating existing curves.
- Regenerated the pointwise aggregate and JSONL with duplicate suppression.

## Important files

- `benchmark_suite/data/pointwise_test_cases.json` and `.jsonl`: default pointwise regression cases.
- `benchmark_suite/data/D4014_ARCAS_supersonic_summary.json`: new D4014 pointwise data.
- `benchmark_suite/data/R100_catalog_and_smooth_geometry.json`: R100 Table I geometry.
- `benchmark_suite/data/AST-E1R-13319_Aerobee150A_basic_aero.json`: Aerobee sanity data.
- `benchmark_suite/data/RASAero_ARCAS_comparison_reference.json`: secondary RASAero reference points/setup.
- `benchmark_suite/manifests/pdf_ingestion_audit_2026-07-13.json`: PDF deduplication and data-action audit.
- `source_pdfs/SHA256SUMS.txt`: hashes for all six included source PDFs.

## Validate

```bash
python benchmark_suite/tools/validate_pointwise.py your_solver_output.json
```

The Aerobee Figure 22 digitization is intentionally excluded from the default pointwise regression aggregate because the contractor handbook scan is low resolution; it remains available as a documented sanity-check dataset.
