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

## Architecture

### Plugin lifecycle — `BasePlugin`

All plugins extend `BasePlugin`, which implements the full UI flow via a template method pattern in `run()`:

1. `showStartupMessage()` — shows the startup dialog; returns a `Map<String, Object>` of user inputs (e.g. max
   brightness). Return `Map.of()` if no inputs are needed.
2. Directory chooser prompts the user for the root directory to process.
3. If the inputs map has `INPUT_CLEAN_GENERATED_FILES` set to `true`, `cleanGeneratedFiles(rootDirectory)` deletes this
   plugin's previously generated files before processing (see below).
4. `buildResultsFile(inputs, rootDirectory)` — performs all image processing and returns the written CSV `File`.
5. `showCompletionMessage(path)` — shows the completion dialog.

`BasePlugin.getImageDirectories()` walks the root directory and returns all immediate and nested subdirectories (
excluding the root itself).

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
