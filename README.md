# UAB Fiji Plugins

These plugins are designed to simplify tasks traditionally done manually within
the [Fiji](https://imagej.net/software/fiji/) system.

## Installation (recommended): add the update site

The easiest way to install the plugins — and to keep them current — is to
subscribe to the **UAB Tools** update site. You only do this once; afterward Fiji
can pull new versions for you.

1. In Fiji, open **Help › Update…**. Fiji checks the core update sites, then
   shows the **ImageJ Updater** window.
2. Click **Manage Update Sites** (bottom-left).
3. In the **Manage Update Sites** window, click **Add Unlisted Site**, then fill
   in the new row:
   - **Name:** `UAB Tools`
   - **URL:** `https://ronaldwatts.com/fiji-plugins/`
4. Make sure the checkbox in the **Active** column (far left) is **checked**.
5. Click **Apply and Close**. Back in the ImageJ Updater, the UAB plugins appear
   in the list — click **Apply Changes** to download them.
6. Restart Fiji when prompted. The plugins appear under the **`UAB`** menu.

### Getting updates going forward

Once the update site is added, pull the latest versions at any time:

1. Open **Help › Update…** again.
2. Any UAB plugins with a newer version show **Update** in the **Status/Action**
   column.
3. Click **Apply Changes**, then restart Fiji.

If you ever need a fix or a new plugin, request it and a new version will be
published to the update site; the next time you run **Help › Update…** it will be
offered as an **Update**.

### Turning on automatic update checks

To have Fiji check the update site for you on launch:

1. Open **Edit › Options › ImageJ Updater…** (or click the **⋮** / **Options**
   menu inside the ImageJ Updater window).
2. Set **Check for updates** to run **at startup** (e.g. *Check daily*).
3. Click **OK**.

Fiji will now notify you when UAB (or any subscribed site) has updates available,
so you can apply them with a click and restart.

---

## Building and installing from source

> **Note:** The Maven command run from the command line does not generate the
> `META-INF/json/org.scijava.plugin.Plugin` file, so `clean` and `install` must
> be run from IntelliJ. This is likely related to the project using the Java
> version found in the Fiji installation.

The resulting JAR will be placed in `{project_root}/target/` as
`uab-fiji-plugins-{version}.jar`. Install it via `Plugins > Install...` in Fiji
and restart. All plugins appear under the `UAB` menu.

---

## Cleanup of previously generated files

Most plugins' startup screens include a **Delete previously generated files in
this directory before running** checkbox (checked by default). When left
checked, the plugin removes its own prior outputs — the generated PNGs and the
result CSVs it had written into the chosen directory tree — before processing,
so re-runs don't accumulate stale files. Source `.tif` images and unrelated
files are never touched. Uncheck it to keep earlier outputs. (The
[Image Merger](#image-merger) plugin produces no derived outputs and so has no
such checkbox.)

---

## Available Plugins

### Fluorescence Intensity

Measures per-channel fluorescence intensity across all `.tif` images in the
subdirectories of a chosen root directory. Before running, place a
`Positive Control.tif` and a `Negative Control.tif` at the root directory —
both must be multi-channel merged images with color channel information. The
plugin derives per-channel thresholds from the positive control and background
means from the negative control, then applies them when measuring each sample
image. Images may be merged or separated by channel and can be nested in
subdirectories.

**Output:** `FluorescenceIntensity_{RootDirectory}_{Timestamp}.csv` written to
the root directory. Columns: folder, channel type, area, mean, min, max, and
integrated density.

---

### WGA Mask

Quantifies CALR (calreticulin) signal both in full cells and within the
WGA-defined cell membrane region across all `.tif` images in the subdirectories
of a chosen root directory. Each `.tif` must be a two-slice stack where slice 1
is the CALR channel and slice 2 is the WGA channel. Rolling-ball background
subtraction (radius 25) is applied to the full stack before analysis.

The plugin produces two measurements per image:

- **Total CALR** — Huang dark auto-threshold applied to the CALR slice
- **WGA-mask CALR** — Intermodes threshold applied to the WGA slice to create
  an ROI, which is then transferred to the CALR slice before Huang dark
  thresholding

Each measurement is recorded twice — once over the full region and once with
**Limit to Threshold** enabled (statistics restricted to the pixels inside the
threshold) — distinguished by the `limit to threshold` column.

CALR images are rendered with the **Green Fire Blue** LUT and WGA images with
the **Yellow** LUT. Image directories must be named `{Sex} {Treatment} {Mouse #}`
as these fields are parsed into the results.

**Output:**

- `WGAMask_{RootDirectory}_{Timestamp}.csv` — columns: sex, treatment, mouse
  number, stain, image number, limit to threshold, area, mean, stddev, median,
  min, max, integrated density
- `{ImageNumber}_CALR_RBS25.png`, `{ImageNumber}_WGA_RBS25.png`,
  `{ImageNumber}_MERGED_RBS25.png` saved alongside each source image

---

### WGA Mask Alt Colors

Identical in analysis to [WGA Mask](#wga-mask) — same two-slice stack
requirement, same rolling-ball background subtraction, same Huang dark / WGA
Intermodes thresholding logic — but renders images with the **Cyan Hot** LUT
for CALR and the **Orange Hot** LUT for WGA. This variant also accepts an
optional **CALR Maximum Brightness** input at startup that sets the display
range ceiling for the CALR channel before saving PNGs, which helps normalize
visualization across datasets with varying intensity scales.

**Output:**

- `WGAMaskAltColors_{RootDirectory}_{Timestamp}.csv` — same columns as WGA Mask
- `{ImageNumber}_CALR_RBS25.png`, `{ImageNumber}_WGA_RBS25.png`,
  `{ImageNumber}_MERGED_RBS25.png` saved alongside each source image

---

### Membrane

Quantifies CALR signal both in total cells and restricted to the cell membrane
region across all `.tif` images in the chosen root directory and its
subdirectories. Images may live directly in the chosen directory (no
subdirectories required) or be organized into subdirectories. Each `.tif` must
be a two-slice stack where slice 1 is the CALR channel and slice 2 is the
membrane marker channel. Rolling-ball background subtraction (radius 25) is
applied before analysis.

**Channel-file merging:** before processing, each subdirectory is scanned for
per-channel source files named `{image-name}--C0*.tif` (e.g. `A--C00.tif`,
`A--C01.tif`, `A--C02.tif`). When found, each group is RGB-merged — `C00` →
red, `C01` → green, `C02` → blue — into a composite saved as `{image-name}.tif`,
overwriting any existing file of that name. The merged file then flows through
the normal analysis using slice 1 (red / `C00`) as the CALR channel and slice 2
(green / `C01`) as the membrane channel; the blue (`C02`) channel is retained in
the image but not measured. The original `--C0*.tif` files are left in place but
skipped by the per-image processing loop, so they are never analyzed directly.
If a subdirectory contains no `--C0*.tif` files, this step is skipped and
processing continues normally.

The plugin produces two measurements per image:

- **Total CALR** — IsoData dark auto-threshold applied to the CALR slice
- **Membrane CALR** — IsoData dark auto-threshold applied to the membrane slice
  to create an ROI, which is then transferred to the CALR slice for measurement

Each measurement is recorded twice — once over the full region and once with
**Limit to Threshold** enabled (statistics restricted to the pixels inside the
threshold) — distinguished by the `limit to threshold` column.

Images are rendered with the **Cyan Hot** LUT for CALR and **Orange Hot** for
the membrane channel. Like WGA Mask Alt Colors, an optional **CALR Maximum
Brightness** input can be provided to cap the CALR display range; the membrane
channel display range is auto-adjusted (equivalent to Fiji's B&C **Auto**)
before its PNG is saved. Image directories must be named
`{Sex} {Treatment} {Mouse #}`.

**Output:**

- `Membrane_{RootDirectory}_{Timestamp}.csv` — columns: sex, treatment, mouse
  number, stain, image number, limit to threshold, area, mean, stddev, median,
  min, max, integrated density
- `{ImageNumber}_CALR_RBS25.png`, `{ImageNumber}_Membrane_RBS25.png`,
  `{ImageNumber}_MERGED_RBS25.png` saved alongside each source image

---

### Image Merger

Merges per-channel source images into composite `.tif` files across the chosen
root directory and its subdirectories — the same channel-merging step performed
by [Membrane](#membrane), with no analysis attached. Use it when you only need
the merged composites and not any measurements.

Each directory is scanned for per-channel source files named
`{image-name}--C0*.tif` (e.g. `A--C00.tif`, `A--C01.tif`, `A--C02.tif`). When
found, each group is RGB-merged — `C00` → red, `C01` → green, `C02` → blue —
into a composite saved as `{image-name}.tif`, overwriting any existing file of
that name. The original `--C0*.tif` files are left in place. Directories with no
`--C0*.tif` files are skipped.

**Output:** `{image-name}.tif` composites written alongside their source channel
files. No measurements, PNGs, or CSV are produced.

---

### Help

Opens a single scrollable window listing every UAB plugin and its description —
a quick in-Fiji reference, with no directory to choose and no processing. The
list is generated at runtime by discovering the installed UAB plugins and
pulling each one's own description, so it always matches what the individual
plugins do (and any newly added plugin shows up here automatically).
