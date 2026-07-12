# RASAero II benchmark corpus

This directory contains redistributable, pinned inputs and compact synthetic coefficient fixtures used by the Physics Aero Phase 1–3 tests.

- `upstream/` is preserved byte-for-byte from its cited public source.
- `raw-aeroplots/` contains unmodified synthetic/reference exports with sidecar manifests.
- `normalized/` is generated deterministically by the Java test harness.
- `cdx1-local/` is intentionally ignored because upstream redistribution rights are not assumed.

RASAero II is an engineering comparison source, not ground truth. No `.CDX1` files from the RASAero installer are redistributed here.
