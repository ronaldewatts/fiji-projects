# UAB Fiji Plugins

These plugins are designed to simplify tasks traditionally done manually within
the [Fiji](https://imagej.net/software/fiji/) system.

> **Note:** The Maven command run from the command line does not generate the
> `META-INF/json/org.scijava.plugin.Plugin` file, so `clean` and `install` must
> be run from IntelliJ. This is likely related to the project using the Java
> version found in the Fiji installation.

The resulting JAR will be placed in `{project_root}/target/` as
`uab-fiji-plugins-{version}.jar`. Install it via `Plugins > Install...` in Fiji
and restart. All plugins appear under the `UAB` menu.

Every plugin's startup screen includes a **Delete previously generated files in
this directory before running** checkbox (checked by default). When left
checked, the plugin removes its own prior outputs — the generated PNGs and the
result CSVs it had written into the chosen directory tree — before processing,
so re-runs don't accumulate stale files. Source `.tif` images and unrelated
files are never touched. Uncheck it to keep earlier outputs.

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

CALR images are rendered with the **Green Fire Blue** LUT and WGA images with
the **Yellow** LUT. Image directories must be named `{Sex} {Treatment} {Mouse #}`
as these fields are parsed into the results.

**Output:**

- `WGAMask_{RootDirectory}_{Timestamp}.csv` — columns: sex, treatment, mouse
  number, stain, image number, area, mean, min, max, integrated density
- `{ImageNumber}_CALR_RBS25.png`, `{ImageNumber}_WGA_RBS25.png`,
  `{ImageNumber}_MERGED_RBS25.png` saved alongside each source image

---

### WGA Mask Alt Colors

Identical in analysis to [WGA Mask](#wga-mask) — same two-slice stack
requirement, same rolling-ball background subtraction, same Huang dark / WGA
Intermodes thresholding logic — but renders images with the **Cyan Hot** LUT
for CALR and the **Orange Hot** LUT for WGA. This variant also accepts an
optional **Maximum Brightness** input at startup that sets the display range
ceiling for the CALR channel before saving PNGs, which helps normalize
visualization across datasets with varying intensity scales.

**Output:**

- `WGAMaskAltColors_{RootDirectory}_{Timestamp}.csv` — same columns as WGA Mask
- `{ImageNumber}_CALR_RBS25.png`, `{ImageNumber}_WGA_RBS25.png`,
  `{ImageNumber}_MERGED_RBS25.png` saved alongside each source image

---

### Membrane

Quantifies CALR signal both in total cells and restricted to the cell membrane
region across all `.tif` images in the subdirectories of a chosen root
directory. Each `.tif` must be a two-slice stack where slice 1 is the CALR
channel and slice 2 is the membrane marker channel. Rolling-ball background
subtraction (radius 25) is applied before analysis.

The plugin produces two measurements per image:

- **Total CALR** — IsoData dark auto-threshold applied to the CALR slice
- **Membrane CALR** — IsoData dark auto-threshold applied to the membrane slice
  to create an ROI, which is then transferred to the CALR slice for measurement

Images are rendered with the **Cyan Hot** LUT for CALR and **Orange Hot** for
the membrane channel. Like WGA Mask Alt Colors, an optional **Maximum
Brightness** input can be provided to cap the CALR display range; the membrane
channel display range is auto-adjusted (equivalent to Fiji's B&C **Auto**)
before its PNG is saved. Image directories must be named
`{Sex} {Treatment} {Mouse #}`.

**Output:**

- `Membrane_{RootDirectory}_{Timestamp}.csv` — columns: sex, treatment, mouse
  number, stain, image number, area, mean, min, max, integrated density
- `{ImageNumber}_CALR_RBS25.png`, `{ImageNumber}_Membrane_RBS25.png`,
  `{ImageNumber}_MERGED_RBS25.png` saved alongside each source image
