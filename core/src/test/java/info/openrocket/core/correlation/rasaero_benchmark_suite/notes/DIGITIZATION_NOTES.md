# Digitization notes

## Method

The linked source PDFs were inspected through their public NASA NTRS or RASAero-hosted PDF views. Values labeled `quality: B` were manually read from the plotted source figure and rounded to a precision consistent with the figure resolution. Values labeled `quality: A` are explicitly stated in report text or tabulated test conditions.

## A53D02

- Figure 11 was used for a compact faired `CD_zero_lift` versus Mach curve from 0.6 to 10.
- Figure 16 was used for Reynolds-sensitivity checks at Mach 4.7 and 7.2.
- Recommended tolerance: about +/-0.025 in CD for acceptance, larger than pure visual digitization uncertainty so the test remains useful across interpolation implementations.

## D4013

- Figures 11 and 12 were read for short/long ARCAS CP location and corrected axial force.
- CP values are stored as percent body length, matching the source plot.
- The plots contain multiple line styles and tightly spaced transonic curves; values are intentionally rounded and use broader tolerances.

## Exact-text regression datasets

D2163, L53D14a, L52D15a, L54D27, D4064, and TM74058 include exact numerical ranges, uncertainties, increments, or method-ordering expectations explicitly stated in the reports. These are encoded as assertions so automated tests can check method ownership and trend logic even before every plotted curve is digitized.

## Known incompleteness

- R100 has 177 configurations and multi-page geometry tables; full transcription is a separate ingestion project.
- D4014 direct PDF rendering was unreliable during this run; its verified envelope is included without fabricated point data.
- D4064 Table 1 is dense and scan-ordered; the global experiment envelope is included now, but every pressure-ratio row is not yet transcribed.
- Aerobee 150A is registered as a later sanity-check source.
