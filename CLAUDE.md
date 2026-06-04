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
3. `buildResultsFile(inputs, rootDirectory)` — performs all image processing and returns the written CSV `File`.
4. `showCompletionMessage(path)` — shows the completion dialog.

`BasePlugin.getImageDirectories()` walks the root directory and returns all immediate and nested subdirectories (
excluding the root itself).

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

These three plugins follow the same structural pattern but differ in LUTs and thresholding algorithm for the mask
channel:

| Plugin              | CALR LUT        | Mask LUT   | Mask threshold |
|---------------------|-----------------|------------|----------------|
| WGA Mask            | Green Fire Blue | Yellow     | Intermodes     |
| WGA Mask Alt Colors | Cyan Hot        | Orange Hot | Intermodes     |
| Membrane            | Cyan Hot        | Orange Hot | IsoData dark   |

Processing per image (applied to each 2-slice `.tif`):

1. Rolling-ball background subtraction (radius 25) on the full stack.
2. Slice 1 = CALR channel; Slice 2 = mask channel.
3. **Total CALR**: Huang dark threshold on a duplicate of slice 1.
4. **Masked CALR**: threshold the mask slice to create an ROI → apply ROI to a CALR duplicate → Huang dark threshold →
   measure.
5. LUTs applied, calibration bar added to CALR, three PNGs saved (`_CALR_RBS25`, `_WGA_RBS25`/`_Membrane_RBS25`,
   `_MERGED_RBS25`).

**Image directory naming convention:** subdirectory names must be `{Sex} {Treatment} {Mouse #}` (space-separated).
Spaces are replaced with `/` when constructing the ImagePlus title so that `label.split("/")` parses sex, treatment,
mouse number, stain, and image number into the `Measurement` record.

WGA Mask Alt Colors and Membrane accept an optional **Maximum Brightness** integer at startup; when provided it sets
`slice1.setDisplayRange(0, value)` before saving the CALR PNG.

### Shared `Measurement` record duplication

`wgamask.Measurement` and `membrane.Measurement` are structurally identical records. They are kept separate so each
package is self-contained.
