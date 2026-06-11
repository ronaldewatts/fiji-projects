# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build

```bash
mvn package          # produces target/uab-fiji-plugins-{version}.jar
```

> Maven CLI does not generate `META-INF/json/org.scijava.plugin.Plugin`. Run **clean + install from IntelliJ** to
> produce a jar that Fiji can discover plugins from.

Each plugin class has a `main()` method for local testing — set the `ide` system property to `"true"` and point it at a
directory under `data/`.

## Commit messages

Every commit message subject must be formatted as `{version}: {detailed description}`, where `{version}` is the current
project version from `pom.xml` (the `<version>` element, e.g. `1.0.12`) and `{detailed description}` is a specific,
imperative summary of what the commit does — not a vague "update plugin". Examples from history:

```
1.0.11: Merge per-channel source files and process root-level images in MembranePlugin
1.0.10: Reduce memory pressure and per-call overhead in plugins
```

Bump `pom.xml`'s `<version>` when the change warrants a new release, and use that same version as the commit prefix.
Multiple commits may share a version while it is in progress.

## Architecture

### Plugin lifecycle — `BasePlugin`

All plugins extend `BasePlugin`, which implements the full UI flow via a template method pattern in `run()`:

1. `showStartupMessage()` — shows the startup dialog; returns a `Map<String, Object>` of user inputs (e.g. max
   brightness). Return `Map.of()` if no inputs are needed, or **`null` to signal the user cancelled** (see below).
   When `run()` receives `null` it logs `Cancelled.` and returns without choosing a directory or processing anything.
2. Directory chooser prompts the user for the root directory to process.
3. If the inputs map has `INPUT_CLEAN_GENERATED_FILES` set to `true`, `cleanGeneratedFiles(rootDirectory)` deletes this
   plugin's previously generated files before processing (see below).
4. `buildResultsFile(inputs, rootDirectory)` — performs all image processing and returns the written CSV `File`.
5. `showCompletionMessage(path)` — shows the completion dialog.

`BasePlugin.getImageDirectories()` walks the root directory and returns all immediate and nested subdirectories (
excluding the root itself).

### Plugin descriptions — `DescribablePlugin`

Every user-facing processing plugin implements `DescribablePlugin` (alongside extending `BasePlugin`), which exposes
`getPluginName()` and `getDescription()`. **The `getDescription()` HTML fragment is the single source of truth for that
plugin's description** — it feeds both the plugin's own startup splash and the aggregated `Help` dialog, so it is never
duplicated as inline label text.

`getDescription()` returns only the HTML *fragment* (a sequence of `<p>`/`<ul>` blocks), no `<html>`/`<body>` wrapper.
`DescribablePlugin.toSplashHtml(fragment)` wraps a fragment in the shared splash chrome (fixed width + the whitespace
stylesheet); each plugin's `showStartupMessage()` renders `new JLabel(DescribablePlugin.toSplashHtml(getDescription()))`
into a `textPanel` (`JPanel(new BorderLayout())`).

**Plugin documentation is the source of truth (always keep these in sync).** Whenever a plugin is added or its behavior
changes, two things must happen together: (1) update that plugin's section in `README.md`, and (2) update its
`getDescription()` fragment to match. They must always tell the same story. Because the splash and the `Help` dialog both
read `getDescription()`, fixing it in one place updates everywhere.

Authoring the HTML fragment (Swing renders a subset of HTML 3.2 + CSS):

- **Every chunk of text must be wrapped in its own `<p>` (or `<ul>`).** Swing collapses plain newlines, so any bare
  sentence outside a block element renders tight against its neighbor with no gap. The shared stylesheet in
  `toSplashHtml` (and in `HelpPlugin`) gives `<p>`/`<ul>` their bottom margin; a common mistake is leaving the lead
  sentence or a list lead-in unwrapped.
- Use `<code>` for filenames and patterns (e.g. `<code>{name}--C0*.tif</code>`), `<ul>`/`<li>` for the per-measurement
  lists, and `<b>` for emphasis and section labels like `<b>Output:</b>`. Build the fragment with a Java text block.

Note the dialog's input/checkbox panels and `showCompletionMessage` still use plain Swing layouts (`GridLayout`) — only
the descriptive `textPanel` is HTML.

Startup dialogs are shown via `BasePlugin.showStartupDialog(body, title)` (an OK/Cancel `JOptionPane`), **not** the
OK-only `showMessage(...)` used for completion dialogs. Each `showStartupMessage()` returns `null` when
`showStartupDialog` returns `false` (Cancel or window-close) so `run()` aborts. Both helpers share the UAB-monogram
chrome via `wrapWithIcon`.

### Help — aggregated descriptions (`HelpPlugin`)

`HelpPlugin` (menu `UAB>Help`) is a documentation-only `Command` that does **not** extend `BasePlugin` (it processes no
directories). It shows a single scrollable dialog listing every plugin's name and description. It discovers them at
runtime by **scanning its own code source** — the jar when packaged, or the `classes` directory during development,
located via `getProtectionDomain().getCodeSource()`. It walks/enumerates every `.class` under `edu.uab.fiji.plugins`,
loads each with its own classloader, and keeps the concrete ones implementing `DescribablePlugin`, instantiating each
(no-arg constructor) to read `getDescription()`. **Adding a new `DescribablePlugin` makes it appear in Help
automatically — nothing in `HelpPlugin` needs editing.**

This scan deliberately does **not** use SciJava's `PluginService`/`@Plugin` annotation index: that index is not
generated by the Maven CLI and can be classloader-sensitive, which previously made Help find nothing. The code-source
scan works regardless of how the jar was built. (This means the plugins' no-arg constructors must stay side-effect-free
— they are, since none declare `@Parameter` fields.)

**Menu placement.** UAB plugins declare their menu via the `@Menu` array form (not the `menuPath` string), because the
string form leaves every leaf at the default weight (`+Infinity`) and gives no ordering control. SciJava sorts menu
items by leaf `weight` ascending (ties alphabetical). The five processing plugins use `@Menu(label = "…", weight = 1d)`
(equal weight → alphabetical); `HelpPlugin` uses `weight = 1000d` so it sorts to the bottom of the UAB menu. To add
another normal plugin, give it `weight = 1d`; only Help carries the high weight.

**Menu separator (legacy menu).** SciJava's *own* Swing/AWT menu UI (`AbstractMenuCreator`) auto-inserts a separator
wherever consecutive leaf weights differ by more than 1 — but Fiji does **not** use that UI. Fiji runs the legacy
ImageJ 1.x menu bar, and `net.imagej.legacy.IJ1Helper$IJ1MenuWrapper` bridges our `@Plugin` commands into it. That
bridge honors weight only for *ordering*; for separators it inserts at most one divider per menu, solely at the boundary
between built-in IJ1 items and bridge-injected items, and only when the menu already had items. The UAB menu is entirely
bridge-injected, so it never qualifies — **weight differences produce no separator there.** To get the rule above Help we
register `UABMenuSeparator` as a `@Plugin(type = LegacyPostRefreshMenus.class)`; Fiji calls its `run()` after the menu
bar is (re)built (startup and every refresh), and it inserts an AWT separator above the `Help` item idempotently. This
needs `imagej-legacy` at compile time, added to `pom.xml` with `provided` scope (Fiji supplies it at runtime; it is not
bundled). Do not re-add a weight-based separator claim — it silently does nothing in Fiji.

**Generated-file cleanup:** `BasePlugin` exposes the `INPUT_CLEAN_GENERATED_FILES` input key and an overridable
`getGeneratedFilePatterns()` hook returning regexes matched against file names only (default empty = delete nothing).
Each plugin adds a startup checkbox (checked by default) that sets `INPUT_CLEAN_GENERATED_FILES`, and overrides
`getGeneratedFilePatterns()` to declare its output PNG suffixes and result-CSV prefix. When the box is checked,
`cleanGeneratedFiles()` walks the root tree and deletes every regular file whose name matches a pattern, logging each
deletion; `.tif` sources and non-matching files are never touched. There is no confirmation dialog — the checkbox is the
only gate.

### `ResultsTableService` singleton

Wraps ImageJ's global `ResultsTable`/`Analyzer`. It configures measurements to area, mean, min, max, and integrated
density. **Always call `reset()` between images** — failing to do so causes rows to accumulate and index reads to return
wrong values.

### Fluorescence Intensity — object model

This plugin uses a richer model than the others:

- `Image` — opens a `.tif`, splits channels via `ChannelSplitter`, and maps each slice label to a `ChannelType` (CY5,
  GFP, DAPI, RFP). Slices with unrecognized labels become `ChannelType.DISCARD` and are skipped.
- `ImageChannel` — holds one split channel alongside its positive `Threshold` and negative `mean`. `measure()` applies
  the raw threshold, subtracts the negative mean, then delegates to `ResultsTableService`.
- `Threshold` / `Measurement` — simple value records.

Control images at the root directory drive all thresholding: the positive control (Default dark auto-threshold) provides
per-channel min/max thresholds; the negative control provides per-channel background means to subtract.

### WGA Mask / WGA Mask Alt Colors / Membrane — inline processing

These three plugins follow the same structural pattern but differ in LUTs and thresholding algorithms:

| Plugin              | CALR LUT        | Mask LUT   | CALR threshold | Mask threshold |
|---------------------|-----------------|------------|----------------|----------------|
| WGA Mask            | Green Fire Blue | Yellow     | Huang dark     | Intermodes     |
| WGA Mask Alt Colors | Cyan Hot        | Orange Hot | Huang dark     | Intermodes     |
| Membrane            | Cyan Hot        | Orange Hot | IsoData dark   | IsoData dark   |

Processing per image (applied to each 2-slice `.tif`):

1. Rolling-ball background subtraction (radius 25) on the full stack.
2. Slice 1 = CALR channel; Slice 2 = mask channel.
3. **Total CALR**: the CALR threshold (see table) applied to a duplicate of slice 1.
4. **Masked CALR**: the mask threshold (see table) applied to the mask slice to create an ROI → ROI transferred to the
   thresholded CALR duplicate → measure.
5. LUTs applied, calibration bar added to CALR, three PNGs saved (`_CALR_RBS25`, `_WGA_RBS25`/`_Membrane_RBS25`,
   `_MERGED_RBS25`).

**Image directory naming convention:** subdirectory names must be `{Sex} {Treatment} {Mouse #}` (space-separated).
Spaces are replaced with `/` when constructing the ImagePlus title so that `label.split("/")` parses sex, treatment,
mouse number, stain, and image number into the `Measurement` record.

WGA Mask Alt Colors and Membrane accept an optional **Maximum Brightness** integer at startup; when provided it sets
`slice1.setDisplayRange(0, value)` before saving the CALR PNG.

**Membrane-only deviations** (the other two plugins do neither):

- **Root-directory processing.** Membrane prepends `rootDirectory` itself to the list returned by `getImageDirectories()`,
  so `.tif`s placed directly in the chosen directory (no subdirectories) are processed. Because the per-directory
  `Files.walk` recurses, merging and processing are split into two passes — merge across *all* directories first, then
  process — so the recursive root walk never reads a stale or not-yet-created merged file. A `processedImages` set
  dedupes paths that the root walk and a subdirectory walk both reach.
- **Channel-file merging.** Before processing each directory, `mergeChannelImages()` scans for per-channel source files
  matching `CHANNEL_FILE` (`{name}--C0{n}.tif`) and RGB-merges each group via `RGBStackMerge.mergeChannels` — `C00`→red,
  `C01`→green, `C02`→blue — into a composite saved (overwriting) as `{name}.tif`. The resulting 3-channel composite feeds
  the normal slice-1/slice-2 path (red=CALR, green=membrane; blue retained but unmeasured). The `--C0*.tif` sources stay
  on disk but are filtered out of the processing walk by the same pattern.

### Shared `Measurement` record duplication

`wgamask.Measurement` and `membrane.Measurement` are structurally identical records. They are kept separate so each
package is self-contained.
