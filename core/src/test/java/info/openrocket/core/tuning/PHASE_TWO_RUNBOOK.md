# Phase-Two ROM Validation Runbook

## 1) Configure datasets

1. Copy `phase-two-config.example.json` to a writable location.
2. Prefer OpenRocket-vs-log datasets (`referenceCsv` + `orkPath`) so pressure, temperature, and derived density can be validated against flight logs.
3. Set `plugin.enabled=true` per dataset where the air-brakes plugin must run.
4. Default plugin JAR path is already set to:

`C:/Users/Opteron92/Projects/OpenRocket_Features/core/src/test/java/info/openrocket/core/tuning/Ab_jar/AirBrakes Plugin.jar`

5. For dataset-specific plugin arguments, set `plugin.argumentsFile` to a JSON file inside the dataset folder.
   - Example: `"argumentsFile": "Jackpot_Launch_2/airbrakes-plugin-args.json"`
   - File format:

```json
{
  "arguments": [
    "--dataset",
    "{datasetName}",
    "--telemetry",
    "{referenceCsv}"
  ]
}
```

   - Placeholders supported in argument values: `{datasetName}`, `{referenceCsv}`, `{candidateCsv}`, `{orkPath}`, `{datasetDir}`.

## 2) Execute batch runner

Run the Java main in `PhaseTwoBatchRunner` with two arguments:

1. Config JSON path
2. Output reports folder

Example reports produced:

- `phase-two-summary.csv`
- `phase-two-quantities.csv`
- `phase-two-improvements.csv`
- `phase-two-junit.xml`
- `phase-two-console.log`
- `phase-two-run-metadata.log`
- `*-plugin.stdout.log` and `*-plugin.stderr.log` (per dataset)

## 3) Visualize outputs

Use the Jupyter-first dashboard script:

`python phase_two_dashboard.py <reports_dir>`

This emits:

- `dashboard_scores.png`
- `dashboard_cd_proxy.png`
- `dashboard_accel_peaks.png`

## 4) Interpret low-score flags

- Warning threshold: score < 80
- Critical threshold: score < 65
- Flags map low-score channels to equation groups, for example:
  - `velocityZ` / `accelZ` -> `rom.drag.force-balance`
  - `altitude` -> `rom.integrator.vertical-kinematics`
  - `pressure` -> `rom.atmosphere.static-pressure`
  - `density` -> `rom.atmosphere.density`

## 5) Notes

- Batch is fail-soft for plugin execution: dataset continues even when plugin fails or times out.
- Cd is reported as a tuning proxy derived from acceleration and velocity scaling.
- Scoring is produced for full flight and phase windows: boost, coast, descent.