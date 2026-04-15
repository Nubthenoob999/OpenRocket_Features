# Plan to Stitch `alexzogh/openrocket` Features into `Nubthenoob999/OpenRocket_Features` (`ROM_dev_testing`)

## Goal
Integrate the **new feature code from `alexzogh/openrocket`** into the ROM-focused repository **without breaking the existing ROM/aerodynamics work, packaging changes, or custom Gradle tasks**.

This plan assumes the **main source files are already copied into the correct folders** and the remaining work is to **wire them into OpenRocket’s existing factories, menus, module exports, services, and save/export flows**.

## Important Integration Strategy
Do **not** do a wholesale merge of `alexzogh/openrocket` into `ROM_dev_testing`.

Why:
- `alexzogh/openrocket` is a small feature fork ahead of its own base.
- `ROM_dev_testing` already contains **substantial custom work** in ROM, tuning, motor database handling, packaging, and installer logic.
- Replacing `build.gradle`, `core/build.gradle`, or broad UI/core files wholesale would likely destroy ROM-specific work.

Use this strategy instead:
1. Treat **`ROM_dev_testing` as the source of truth**.
2. Treat the `alexzogh` files as **feature donors**.
3. Port only the **feature-specific files** and **surgically merge the glue code**.

---

## What the `alexzogh` fork adds
The feature set to port is mainly:
- **Ring Tail Fin Set** component support
- **STL export** support
- **Motor recommendation** feature
- **Ejection charge calculator** feature
- **Flight animation** UI feature

These features require both:
- the copied source files themselves, and
- a set of **registration / stitching edits** in existing files.

---

## High-Risk Rule
Never overwrite these files wholesale from the `alexzogh` repo:
- `build.gradle`
- `core/build.gradle`
- `swing/build.gradle`
- `core/src/main/java/module-info.java`
- `swing/src/main/java/module-info.java`
- `swing/src/main/java/info/openrocket/swing/gui/main/BasicFrame.java`
- `swing/src/main/java/info/openrocket/swing/gui/main/ComponentAddButtons.java`
- `swing/src/main/java/info/openrocket/swing/gui/main/ComponentIcons.java`
- `core/src/main/resources/l10n/messages.properties`
- `swing/src/main/resources/META-INF/services/info.openrocket.swing.gui.rocketfigure.RocketComponentShapeService`

These must be **manually merged**, because the ROM branch already changed them or depends on them.

---

## Phase 1: Verify the copied donor files exist
Before writing any new glue code, verify that these files are present in `ROM_dev_testing`.
If any are missing, stop and report the missing file list.

### Ring Tail Fin Set
- `core/src/main/java/info/openrocket/core/rocketcomponent/RingTailFinSet.java`
- `core/src/main/java/info/openrocket/core/aerodynamics/barrowman/RingTailFinSetCalc.java`
- `swing/src/main/java/info/openrocket/swing/gui/configdialog/RingTailFinSetConfig.java`
- `swing/src/main/java/info/openrocket/swing/gui/rocketfigure/RingTailFinSetShapes.java`

### STL Export
- `core/src/main/java/info/openrocket/core/file/stl/STLExportOptions.java`
- `core/src/main/java/info/openrocket/core/file/stl/STLExporter.java`
- `swing/src/main/java/info/openrocket/swing/gui/choosers/STLOptionChooser.java`

### Motor Recommendation
- `core/src/main/java/info/openrocket/core/motor/MotorRecommendation.java`
- `swing/src/main/java/info/openrocket/swing/gui/dialogs/motor/MotorRecommendationDialog.java`

### Ejection Charge Calculator
- `core/src/main/java/info/openrocket/core/util/EjectionChargeCalculator.java`
- `swing/src/main/java/info/openrocket/swing/gui/dialogs/EjectionChargeDialog.java`

### Flight Animation
- `swing/src/main/java/info/openrocket/swing/gui/dialogs/flightanimation/FlightAnimationDialog.java`
- `swing/src/main/java/info/openrocket/swing/gui/dialogs/flightanimation/FlightAnimationPanel.java`

### Supporting modified donor files that likely need merge work
- `core/src/main/java/info/openrocket/core/document/OpenRocketDocument.java`
- `core/src/main/java/info/openrocket/core/document/StorageOptions.java`
- `core/src/main/java/info/openrocket/core/rocketcomponent/Transition.java`
- `swing/src/main/java/info/openrocket/swing/gui/main/BasicFrame.java`
- `swing/src/main/java/info/openrocket/swing/gui/main/ComponentAddButtons.java`
- `swing/src/main/java/info/openrocket/swing/gui/main/ComponentIcons.java`
- `swing/src/main/java/info/openrocket/swing/gui/main/DesignFileSaveAsFileChooser.java`

---

## Phase 2: Apply the minimum required stitching edits

### 2.1 Core JPMS export for STL package
Edit:
- `core/src/main/java/module-info.java`

Add this export if it is not already present:
```java
exports info.openrocket.core.file.stl;
```

Reason:
The STL exporter package must be visible to the Swing module and related UI/export code.

Do **not** remove any ROM-related exports already present in the ROM branch.

---

### 2.2 Swing JPMS registration for RingTail shapes
Edit:
- `swing/src/main/java/module-info.java`

In the `provides info.openrocket.swing.gui.rocketfigure.RocketComponentShapeService with ...` list,
add:
```java
info.openrocket.swing.gui.rocketfigure.RingTailFinSetShapes,
```

Place it near the other component shape providers.

Do **not** remove any existing providers.

---

### 2.3 Swing service file registration for RingTail shapes
Edit:
- `swing/src/main/resources/META-INF/services/info.openrocket.swing.gui.rocketfigure.RocketComponentShapeService`

Add this line:
```text
info.openrocket.swing.gui.rocketfigure.RingTailFinSetShapes
```

Reason:
The repo uses both JPMS and the service file for shape provider discovery.
If this line is missing, the component may compile but still fail to render correctly.

---

## Phase 3: Stitch the Ring Tail Fin Set component into the existing component pipeline

### 3.1 Core component availability
Mirror how an existing fin-like component is integrated, preferably using `TubeFinSet`, `TrapezoidFinSet`, or `FreeformFinSet` as the template.

Search for all places where component classes are:
- instantiated
- recognized by `instanceof`
- assigned a config dialog
- assigned an icon
- assigned a shape provider
- added to the “add component” UI

At minimum, merge the `alexzogh` changes into:
- `Transition.java`
- `ComponentAddButtons.java`
- `ComponentIcons.java`
- any config-dialog factory/dispatch path used by the Swing config dialogs

### 3.2 Configuration dialog hookup
Ensure `RingTailFinSetConfig` is reachable when editing a `RingTailFinSet`.

Use the existing component-dialog dispatch pattern in Swing and add a mapping for:
- `RingTailFinSet.class -> RingTailFinSetConfig`

### 3.3 Shape rendering hookup
After adding `RingTailFinSetShapes` to `module-info.java` and the service file, verify there is no second registration path that also needs a manual mapping.

### 3.4 Add-component palette hookup
In `ComponentAddButtons.java`, add the new component to the same category where the donor repo placed it.
Do not disturb the ROM branch’s current layout or any airbrake-related additions.

### 3.5 Icon hookup
In `ComponentIcons.java`, add the icon mapping for `RingTailFinSet`.
Reuse the donor repo’s exact mapping pattern.
If the icon asset is not new, point at the existing asset used in the donor repo.

---

## Phase 4: Stitch the STL export flow into document/export behavior

This is one of the most important glue areas.
The agent should **not** treat STL as a normal `.ork` save.
It must be wired as an export-like path.

### 4.1 Core export classes
Ensure these classes compile and remain in the core module:
- `STLExportOptions`
- `STLExporter`

### 4.2 Save/export chooser hookup
Merge donor logic into:
- `swing/src/main/java/info/openrocket/swing/gui/main/DesignFileSaveAsFileChooser.java`

Goal:
- expose STL as a selectable output path
- invoke the STL options chooser when STL is selected
- route the final export action to `STLExporter`
- do **not** corrupt the existing `.ork`, RockSim, Wavefront OBJ, SVG, or RASAero flows

### 4.3 Storage/document model support
Merge targeted changes from donor repo into:
- `core/src/main/java/info/openrocket/core/document/OpenRocketDocument.java`
- `core/src/main/java/info/openrocket/core/document/StorageOptions.java`

Goal:
- carry any export options/state required by the STL flow
- preserve current ROM branch document behavior
- avoid touching unrelated serialization logic unless required by compiler errors

### 4.4 Recommended guardrail
If the donor repo adds STL-specific branches to generic save code, preserve those branches, but keep `.ork` as the default path.
STL should be explicitly selected, not silently used.

---

## Phase 5: Stitch the three new GUI tools into the main window
These three features are mainly blocked by `BasicFrame.java` wiring.

### 5.1 Files to wire
- `swing/src/main/java/info/openrocket/swing/gui/dialogs/motor/MotorRecommendationDialog.java`
- `swing/src/main/java/info/openrocket/swing/gui/dialogs/EjectionChargeDialog.java`
- `swing/src/main/java/info/openrocket/swing/gui/dialogs/flightanimation/FlightAnimationDialog.java`
- `swing/src/main/java/info/openrocket/swing/gui/dialogs/flightanimation/FlightAnimationPanel.java`
- `swing/src/main/java/info/openrocket/swing/gui/main/BasicFrame.java`

### 5.2 Merge behavior into `BasicFrame.java`
Use the donor repo implementation as the source for:
- menu item creation
- toolbar hookup if present
- action listeners
- selected document / selected rocket access
- dialog launch points

Do not replace the file wholesale.
Instead:
1. locate the existing menu construction blocks in ROM branch `BasicFrame.java`
2. identify the donor menu items/actions
3. manually port those blocks into equivalent sections
4. re-use existing helpers already present in ROM branch where possible

### 5.3 What to preserve
Do not break:
- existing ROM UI actions
- any Monte Carlo/airbrake menu entries already in your fork
- current document lifecycle assumptions
- recent packaging-related or launcher-related code

---

## Phase 6: Merge localization keys without overwriting ROM branch translations
Edit:
- `core/src/main/resources/l10n/messages.properties`

Merge in only the **new keys** required by:
- Ring tail fin set UI
- STL export UI
- Motor recommendation dialog
- Ejection charge dialog
- Flight animation dialog

Rules:
- do not replace the full file
- append or insert only missing keys
- if the ROM branch already has a conflicting key, keep the ROM branch wording unless the donor key is required for correctness

If compilation fails because a localization key is missing, add exactly that key rather than mass-copying all translation files.

---

## Phase 7: Keep the ROM build files as the baseline

### Root `build.gradle`
Keep the ROM repo version.
It already contains custom packaging/install4j/jpackage logic that should not be lost.

Only merge donor changes if they are strictly required for the new features.
Most likely, no root-level dependency replacement is needed.

### `core/build.gradle`
Keep the ROM repo version.
It already contains:
- ROM-specific test tasks
- publishing/signing logic
- packaging-related customizations
- motor DB and tuning workflows

Only merge in donor changes if a compile error proves a missing dependency or task configuration.

### `swing/build.gradle`
Prefer keeping the ROM repo version unless there is a feature-specific compile failure.

### `settings.gradle`
No broad module restructuring should be necessary.
Keep the ROM repo version unless the build explicitly fails on module inclusion.

---

## Phase 8: Compile in the right order and fix failures one class of problem at a time
Use this exact progression.

### Step A: Compile core only
```bash
./gradlew :core:compileJava
```

If this fails, fix in this order:
1. missing copied classes
2. missing imports
3. `module-info.java` export issues
4. `Transition.java` / component registration issues
5. missing localization constants only if required by compilation

### Step B: Compile swing only
```bash
./gradlew :swing:compileJava
```

If this fails, fix in this order:
1. missing dialog/config/shape classes
2. missing service registration
3. `swing module-info.java` provider issues
4. `BasicFrame.java` unresolved references
5. `ComponentAddButtons.java` / `ComponentIcons.java` mappings

### Step C: Compile whole project
```bash
./gradlew compileJava
```

### Step D: Run targeted tests if available
```bash
./gradlew test
```

If the full test suite is too expensive, at least run:
```bash
./gradlew :core:test
./gradlew :swing:compileJava
```

---

## Phase 9: Functional smoke checks the agent must perform
The integration is not done until these manual/automated smoke checks pass.

### Ring Tail Fin Set
- component appears in add-component UI
- component can be inserted into a design
- opening its config dialog works
- rocket figure renders it without provider errors
- simulation can run without crashing due to missing calc hookups

### STL Export
- STL appears as an export/save target in the UI
- choosing STL opens STL options UI if expected
- export completes without throwing `ClassNotFoundException`, `NoSuchMethodError`, or service/module errors

### Motor Recommendation
- menu or action opens dialog
- dialog can access current rocket/document context
- no null-pointer on empty/no-motor edge cases

### Ejection Charge Calculator
- dialog opens from main UI
- can read component/document values correctly
- does not crash with no valid deployment device selected

### Flight Animation
- animation dialog opens
- panel initializes with current simulation data
- no rendering/bootstrap failure from missing listeners or state setup

---

## Likely Merge Hotspots
If something still does not work after compilation, inspect these first:
- `BasicFrame.java`
- `DesignFileSaveAsFileChooser.java`
- `ComponentAddButtons.java`
- `ComponentIcons.java`
- `core/src/main/java/module-info.java`
- `swing/src/main/java/module-info.java`
- `swing/src/main/resources/META-INF/services/info.openrocket.swing.gui.rocketfigure.RocketComponentShapeService`
- `OpenRocketDocument.java`
- `StorageOptions.java`
- `Transition.java`
- `messages.properties`

---

## Recommended Implementation Order for the AI Agent
1. Verify copied donor files exist
2. Patch `core module-info.java`
3. Patch `swing module-info.java`
4. Patch Swing service registration file
5. Integrate RingTail component wiring
6. Integrate STL export wiring
7. Integrate `BasicFrame` launch points for the three dialogs
8. Merge missing localization keys
9. Compile core
10. Compile swing
11. Run whole-project compile
12. Run smoke checks

---

## Definition of Done
The task is complete when all of the following are true:
- project compiles on `ROM_dev_testing`
- no ROM-specific build logic was overwritten
- RingTail Fin Set is visible and editable in UI
- STL export path works
- Motor Recommendation dialog opens and runs
- Ejection Charge dialog opens and runs
- Flight Animation dialog opens and runs
- no missing module/service/provider errors remain

---

## Final Instruction to the AI Agent
When in doubt, **copy the donor feature logic but preserve the ROM branch infrastructure**.
This is a **surgical merge task**, not a repo sync.
