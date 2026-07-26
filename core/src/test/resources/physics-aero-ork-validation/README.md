# External Physics-Aero `.ork` Validation Corpus

The corpus is intentionally not bundled. Run it with:

```shell
./gradlew :core:physicsAeroOrkValidation \
  -PphysicsAeroOrkValidationDir=/absolute/path/to/corpus \
  -PphysicsAeroOrkMode=diagnostic
```

Use `-PphysicsAeroRequireFlightValidation=true` in certification jobs so a
missing corpus, missing metric, or skipped case fails the task. The default
report is written to
`core/build/reports/physics-aero-ork-validation/report.json`; override it with
`-PphysicsAeroOrkValidationReport=/path/to/report.json`.

The directory contains a `manifest.json` and the referenced `.ork` files. The
`.ork` simulations must carry valid Physics-Aero table identities, and their
tables must already be present in the local cache. The integrator never builds
a table implicitly.

```json
{
  "cases": [
    {
      "id": "representative-rocket-1",
      "ork": "rocket-1.ork",
      "simulation_name": "Flight 1",
      "expected": {
        "apogee": { "value": 1524.0, "relative_tolerance": 0.05 },
        "time_to_apogee": { "value": 21.4, "absolute_tolerance": 0.5 },
        "maximum_mach": { "value": 1.7, "relative_tolerance": 0.03 }
      }
    }
  ]
}
```

Values use OpenRocket SI units: metres, seconds, metres per second, metres per
second squared, pascals, and dimensionless Mach. Exactly one explicit absolute
or relative tolerance is required for every metric.

Supported metrics are `apogee`, `time_to_apogee`, `maximum_velocity`,
`maximum_mach`, `maximum_acceleration`, `maximum_dynamic_pressure`,
`total_flight_time`, `burnout_time`, `rail_exit_time`, and `deployment_time`.
Any OpenRocket flight event can also be requested as its lowercase enum name
plus `_time`, for example `ignition_time` or `apogee_time`.
Certification mode requires strict table use, zero fallbacks, apogee and
time-to-apogee expectations, and at least three distinct rockets. Its apogee
error may not exceed five percent even if a looser sidecar tolerance is given.
