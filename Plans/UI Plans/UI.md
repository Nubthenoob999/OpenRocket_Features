# OpenRocket Modern UI Modernization Spec
**Target audience:** AI coding agent implementing a new modern UI mode for OpenRocket  
**Primary objective:** Add a full **Modern UI** mode that replaces the entire main window shell when enabled, while preserving the existing Swing UI as **Classic UI**.

---

## 1. Product Goal

Implement a **dual-UI architecture** for OpenRocket:

- **Classic UI**: existing Swing-based application shell
- **Modern UI**: a new JavaFX-based application shell with redesigned layout, theme support, and cleaner information hierarchy

When the user enables **Modern UI** in settings, OpenRocket should launch into the **JavaFX shell** instead of the legacy Swing shell.

This is **not** a simple skin or theme pass. The Modern UI must:
- replace the full application shell when turned on
- redesign layouts for usability and discoverability
- support **light** and **dark** appearance modes
- support **preset** and **custom** accent color schemes
- apply **minimal but improved styling** to plots/charts/graphs
- preserve engineering readability
- blend an **industrial / engineering dashboard** aesthetic with **Apple-like clean minimalism**
- Preserve the original look of Openrocket but overhaul it so that it looks modern and nicer. 

---

## 2. Non-Goals

Do **not**:
- delete or break the existing Swing UI
- attempt a one-shot full rewrite of every screen at once
- over-style scientific charts with flashy gradients or low-contrast palettes
- duplicate simulation/business logic into both UI stacks
- tightly couple JavaFX view code to simulation engine internals

---

## 3. Architectural Strategy

## 3.1 High-level approach

Build a **new JavaFX shell** for Modern UI while retaining the existing Swing shell for Classic UI.

Use a shared logic layer so both UI modes can interact with the same underlying simulation, configuration, persistence, and results logic.

### Required mode behavior
- If `ui.mode = classic`, start the existing Swing shell
- If `ui.mode = modern`, start the JavaFX shell

### Migration strategy
Use a staged migration:
1. add modern mode setting and startup seam
2. create JavaFX shell
3. create shared presentation/service layer
4. rebuild key screens in JavaFX
5. bridge any remaining Swing content temporarily only if needed

---

## 4. Technical Direction

## 4.1 UI stacks
- **Classic UI**: Swing + existing FlatLaf/OpenRocket theme path
- **Modern UI**: JavaFX + custom JavaFX CSS theme system
- Optional reference/inspiration library for JavaFX styling: **AtlantaFX**
- Avoid using JFoenix as the long-term foundation

## 4.2 Bridge strategy
Use Swing/JavaFX interop only as a migration tactic where necessary:
- `JFXPanel` if embedding JavaFX into legacy shell during transition
- `SwingNode` only if temporary legacy Swing content must appear inside JavaFX shell

Target end-state:
- Modern mode = full JavaFX main window shell

---

## 5. User-Facing Requirements

## 5.1 Settings
Add or extend preferences/settings to include:

### Global
- `UI Mode`
  - Classic
  - Modern

### Modern UI settings
- `Appearance`
  - Light
  - Dark
- `Accent Color Mode`
  - Preset
  - Custom
- `Accent Preset`
  - Aerospace Blue
  - Signal Orange
  - Graphite Teal
  - Titanium Violet
  - Safety Green
- `Custom Accent Color`
  - user-selected color
- `Density`
  - Compact
  - Comfortable
- `Chart Style`
  - Standard
  - Clean

## 5.2 Behavior
- Modern UI must replace the entire main shell when enabled
- Theme changes in Modern UI should apply as live as possible
- Accent colors should affect controls selectively, not all surfaces
- Charts should receive restrained theme-aware styling only

---

## 6. Design Language

## 6.1 Visual principles
The visual direction should combine:
- industrial / engineering dashboard clarity
- clean, quiet, Apple-like minimalism

### Desired traits
- high readability
- strong spacing and grouping
- minimal chrome
- crisp typography
- muted neutral surfaces
- limited but intentional accent color
- subtle borders
- clean cards/panels
- obvious visual hierarchy
- reduced clutter compared to current legacy forms

## 6.2 Avoid
- skeuomorphic styling
- heavy shadows everywhere
- neon colors
- overly vibrant charts
- dense, cramped forms
- too many modal dialogs for basic workflow

---

## 7. Design Token System

Create a theme token system for Modern UI.

## 7.1 Required tokens
Define at minimum:

### Color tokens
- `color-bg-base`
- `color-bg-surface`
- `color-bg-elevated`
- `color-bg-sidebar`
- `color-fg-primary`
- `color-fg-secondary`
- `color-fg-muted`
- `color-border-subtle`
- `color-border-strong`
- `color-accent`
- `color-accent-hover`
- `color-accent-pressed`
- `color-success`
- `color-warning`
- `color-error`
- `color-info`
- `color-chart-grid`
- `color-chart-axis`
- `color-chart-highlight`

### Typography tokens
- `font-family-base`
- `font-size-caption`
- `font-size-body`
- `font-size-label`
- `font-size-heading`
- `font-size-title`

### Layout tokens
- `spacing-xs`
- `spacing-sm`
- `spacing-md`
- `spacing-lg`
- `spacing-xl`
- `radius-sm`
- `radius-md`
- `radius-lg`
- `shadow-sm`
- `shadow-md`

These tokens should drive the entire JavaFX CSS layer.

---

## 8. Screen-Level UX Goals

## 8.1 Main shell
Modern UI shell should include:
- top toolbar/header
- left navigation rail/sidebar
- central workspace/content area
- optional right contextual details panel
- optional bottom diagnostics/log/data region

### Main navigation sections
- Design
- Flight Config
- Simulation
- Results
- Compare
- Preferences

---

## 8.2 Simulation screen
Current goal: make simulation setup easier to scan and less intimidating.

### Required layout ideas
- summary strip at top
- configuration groups in clean cards
- clear separation of standard vs advanced options
- warnings/validation visible inline
- sticky or obvious run actions
- reduced form clutter

### Suggested content grouping
- Launch Conditions
- Atmospheric / Wind Model
- Aerodynamics / Solver Model
- Recovery / Events
- Advanced
- Run Summary
- Actions

---

## 8.3 Flight Config screen
Goal: make configuration editing less scattered.

### Required layout ideas
- config list or selector on left
- current config details in center
- grouped sections/tabs/cards for:
  - motor
  - mass properties
  - aerodynamics
  - recovery
  - avionics/plugin options if relevant
- visible validation status without requiring hidden dialogs

---

## 8.4 Results screen
Goal: make analysis cleaner and easier to read.

### Required layout ideas
- top metrics row with key values
- central chart area
- run selector / comparison controls
- series toggles and filters
- expandable raw data or log pane
- right-side summary/details panel

### Chart theming rules
Only minimal visual changes:
- theme-aware background
- readable gridlines
- readable axis labels
- calmer series palette
- clearer selected/highlighted series
- improved tooltips/legend spacing

---

## 9. Required Internal Component Library

Build a reusable JavaFX component kit for consistency.

Create reusable components such as:
- `ORSidebarNav`
- `ORSidebarItem`
- `ORSectionCard`
- `ORMetricCard`
- `ORStatusBadge`
- `ORWarningBanner`
- `ORFormSection`
- `ORPropertyRow`
- `ORActionBar`
- `ORChartContainer`
- `ORDetailsPanel`
- `ORSplitWorkspace`
- `OREmptyState`
- `ORTogglePill`
- `ORAccentSwatch`

Do **not** hardcode one-off styles across screens.

---

## 10. Shared Logic Boundary

A major requirement is preventing duplication of logic across Swing and JavaFX.

## 10.1 Extract or preserve reusable layers
The following logic should remain independent of specific UI toolkit code:
- simulation configuration models
- validation logic
- simulation execution services
- results retrieval/transformation
- preferences persistence
- chart data preparation
- domain object mapping

## 10.2 Introduce presentation/service adapters if needed
If current Swing classes contain too much logic, create intermediate adapters/presenters/view-models that can be consumed by both:
- Swing screens
- JavaFX screens

---

## 11. Package Structure Recommendation

Use a structure similar to:

```text
info.openrocket.ui.common
info.openrocket.ui.common.model
info.openrocket.ui.common.service
info.openrocket.ui.common.settings
info.openrocket.ui.common.viewmodel

info.openrocket.ui.swing
info.openrocket.ui.swing.shell
info.openrocket.ui.swing.theme

info.openrocket.ui.fx
info.openrocket.ui.fx.shell
info.openrocket.ui.fx.theme
info.openrocket.ui.fx.components
info.openrocket.ui.fx.settings
info.openrocket.ui.fx.screens.design
info.openrocket.ui.fx.screens.flightconfig
info.openrocket.ui.fx.screens.simulation
info.openrocket.ui.fx.screens.results
info.openrocket.ui.fx.screens.compare
info.openrocket.ui.fx.charts