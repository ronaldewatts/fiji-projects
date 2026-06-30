package edu.uab.fiji.plugins.membrane;

import edu.uab.fiji.plugins.BasePlugin;
import edu.uab.fiji.plugins.DescribablePlugin;
import edu.uab.fiji.service.ResultsTableService;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.RGBStackMerge;
import ij.process.AutoThresholder;
import ij.process.ImageProcessor;
import ij.process.LUT;
import net.imagej.lut.LUTService;
import net.imglib2.display.ColorTable8;
import org.scijava.Context;
import org.scijava.command.Command;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Plugin;

import javax.swing.*;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Plugin(name = "Membrane", type = Command.class, headless = true,
    menu = {@Menu(label = "UAB"), @Menu(label = "Membrane", weight = 1d)})
public class MembranePlugin extends BasePlugin implements DescribablePlugin {

    public static final String INPUT_CALR_MAXIMUM_BRIGHTNESS = "calrMaximumBrightness";
    public static final String INPUT_TOTAL_CALR_THRESHOLD = "totalCalrThreshold";
    public static final String INPUT_MEMBRANE_THRESHOLD = "membraneThreshold";
    public static final String INPUT_TOTAL_CALR_LIMIT_TO_THRESHOLD = "totalCalrLimitToThreshold";
    public static final String INPUT_MEMBRANE_LIMIT_TO_THRESHOLD = "membraneLimitToThreshold";

    // Default auto-threshold method and background for both slices; matches the historical "IsoData dark" behavior.
    private static final String DEFAULT_THRESHOLD_METHOD = "IsoData";
    private static final String DEFAULT_THRESHOLD = "IsoData dark";

    private static final Pattern DIGITS = Pattern.compile("\\d+");

    // Matches per-channel source files like "A--C00.tif"; group 1 = image name, group 2 = channel index (00=red, 01=green, 02=blue).
    private static final Pattern CHANNEL_FILE = Pattern.compile("(.+)--C0(\\d+)\\.tif");

    public static void main(String[] args) {
        System.setProperty("ide", "true");

        // To test, run this main and select the directory <project>/data/Colon Images
        new MembranePlugin().run();
    }

    @Override
    public File buildResultsFile(Map<String, Object> inputs, String rootDirectory) {
        // Include the root directory itself so images placed directly in the chosen directory (no subdirectories) are
        // still processed. Already-processed paths are deduplicated below, so overlap with subdirectories is harmless.
        List<String> imageDirectories = new ArrayList<>();
        imageDirectories.add(rootDirectory);
        imageDirectories.addAll(getImageDirectories(rootDirectory));
        Set<String> processedImages = new HashSet<>();
        List<Measurement> measurements = new ArrayList<>();
        Double calrMaximumBrightness = (Double) inputs.get(INPUT_CALR_MAXIMUM_BRIGHTNESS);
        String totalCalrThreshold = (String) inputs.getOrDefault(INPUT_TOTAL_CALR_THRESHOLD, DEFAULT_THRESHOLD);
        String membraneThreshold = (String) inputs.getOrDefault(INPUT_MEMBRANE_THRESHOLD, DEFAULT_THRESHOLD);
        boolean totalCalrLimit = (boolean) inputs.getOrDefault(INPUT_TOTAL_CALR_LIMIT_TO_THRESHOLD, true);
        boolean membraneLimit = (boolean) inputs.getOrDefault(INPUT_MEMBRANE_LIMIT_TO_THRESHOLD, true);

        try (Context context = new Context()) {
            LUTService lutService = context.getService(LUTService.class);
            ColorTable8 cyanHot;
            ColorTable8 orangeHot;
            try {
                Map<String, URL> luTs = lutService.findLUTs();
                cyanHot = (ColorTable8) lutService.loadLUT(luTs.get("WCIF/Cyan Hot.lut"));
                orangeHot = (ColorTable8) lutService.loadLUT(luTs.get("WCIF/Orange Hot.lut"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            // First pass: merge any per-channel source files into composite .tif files across every directory. This must
            // complete before processing begins, because the processing walk of the root directory recurses into
            // subdirectories and would otherwise read stale (or not-yet-created) merged files.
            for (String subDir : imageDirectories) {
                mergeChannelImages(subDir);
            }

            // Second pass: process the composite images.
            for (String subDir : imageDirectories) {
                try (Stream<Path> stream = Files.walk(Paths.get(subDir))) {
                    stream
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .filter(path -> path.toString().endsWith(".tif"))
                        .filter(path -> !path.getFileName().toString().startsWith("."))
                        .filter(path -> !CHANNEL_FILE.matcher(path.getFileName().toString()).matches())
                        .forEach(path -> {
                            String absolutePath = path.toAbsolutePath().toString();
                            if (!processedImages.contains(absolutePath)) {
                                processedImages.add(absolutePath);
                                IJ.log("processing file: " + absolutePath);

                                String parentFolder = path.getParent().getFileName().toString().replace(" ", "/");
                                ImagePlus image = IJ.openImage(absolutePath);
                                if (image == null) {
                                    IJ.log("skipping file (not a readable image): " + absolutePath);
                                    return;
                                }
                                if (image.getStackSize() >= 2) {
                                    IJ.run(image, "Subtract Background...", "rolling=25 stack");
                                    String imageNumber = image.getTitle().replace(".tif", "");

                                    List<ImagePlus> slices = getImageSlices(image);
                                    ImagePlus slice1 = slices.getFirst();
                                    ImagePlus slice1Duplicate = slice1.duplicate();
                                    slice1Duplicate.setAutoThreshold(totalCalrThreshold);
                                    slice1Duplicate.setTitle(parentFolder + "/Total CALR (" + totalCalrThreshold + ")/" + imageNumber);
                                    measurements.add(getMeasurement(slice1Duplicate, totalCalrLimit));

                                    ImagePlus slice2 = slices.get(1);
                                    slice2.setAutoThreshold(membraneThreshold);
                                    IJ.run(slice2, "Create Selection", "");
                                    Roi slice2Roi = slice2.getRoi();

                                    slice1Duplicate.setRoi(slice2Roi);
                                    slice1Duplicate.setTitle(parentFolder + "/Membrane CALR (" + membraneThreshold + ")/" + imageNumber);
                                    measurements.add(getMeasurement(slice1Duplicate, membraneLimit));

                                    slice1.setLut(new LUT(cyanHot.getValues()[0], cyanHot.getValues()[1], cyanHot.getValues()[2]));
                                    slice2.setLut(new LUT(orangeHot.getValues()[0], orangeHot.getValues()[1], orangeHot.getValues()[2]));
                                    ImagePlus composite = RGBStackMerge.mergeChannels(new ImagePlus[]{slice1, slice2}, true);

                                    if (calrMaximumBrightness != null) {
                                        slice1.setDisplayRange(0, calrMaximumBrightness);
                                    }

                                    slice2.deleteRoi();
                                    IJ.run(slice2, "Enhance Contrast", "saturated=0.35");

                                    IJ.run(slice1, "Calibration Bar...", "location=[Upper Right] fill=White label=Black number=2 decimal=0 font=12 zoom=3 overlay");
                                    IJ.saveAs(slice1, "PNG", path.getParent().toAbsolutePath() + "/" + imageNumber + "_CALR_RBS25.png");
                                    IJ.saveAs(slice2, "PNG", path.getParent().toAbsolutePath() + "/" + imageNumber + "_Membrane_RBS25.png");
                                    IJ.saveAs(composite, "PNG", path.getParent().toAbsolutePath() + "/" + imageNumber + "_MERGED_RBS25.png");

                                    // Release pixel buffers promptly to keep peak heap bounded across large batches.
                                    image.flush();
                                    slice1.flush();
                                    slice2.flush();
                                    slice1Duplicate.flush();
                                    composite.flush();
                                } else {
                                    IJ.log(absolutePath + " is not a valid image file as it has a stack size of " + image.getStackSize());
                                    image.flush();
                                }
                            }
                        });
                } catch (IOException e) {
                    throw new RuntimeException("Failed to process directory: " + subDir, e);
                }
            }

            // Sort by image number, then stain (which now carries the threshold).
            Comparator<Measurement> comparator = Comparator.comparing(Measurement::imageNumber)
                .thenComparing(Measurement::stain);

            measurements.sort(comparator);

            return writeResultsFile(rootDirectory, measurements);
        }
    }

    /**
     * Scans {@code subDir} for per-channel source files named {@code {image-name}--C0*.tif}, RGB-merges each group
     * (00=red, 01=green, 02=blue) into a composite saved as {@code {image-name}.tif} (overwriting any existing file),
     * and leaves the original channel files in place. If no channel files are present, this is a no-op.
     */
    private void mergeChannelImages(String subDir) {
        File directory = new File(subDir);
        File[] channelFiles = directory.listFiles((dir, name) -> !name.startsWith(".") && CHANNEL_FILE.matcher(name).matches());
        if (channelFiles == null || channelFiles.length == 0) {
            return;
        }

        // Group channel files by image name, keyed by channel index so they can be ordered red/green/blue.
        Map<String, Map<Integer, File>> imageGroups = new TreeMap<>();
        for (File channelFile : channelFiles) {
            Matcher matcher = CHANNEL_FILE.matcher(channelFile.getName());
            if (matcher.matches()) {
                String imageName = matcher.group(1);
                int channelIndex = Integer.parseInt(matcher.group(2));
                imageGroups.computeIfAbsent(imageName, key -> new TreeMap<>()).put(channelIndex, channelFile);
            }
        }

        for (Map.Entry<String, Map<Integer, File>> group : imageGroups.entrySet()) {
            String imageName = group.getKey();
            Map<Integer, File> channels = group.getValue();
            String mergedPath = new File(directory, imageName + ".tif").getAbsolutePath();
            IJ.log("merging channel images for: " + mergedPath);

            // RGBStackMerge expects images ordered by color: [0]=red, [1]=green, [2]=blue. Place each channel by its index.
            ImagePlus[] channelImages = new ImagePlus[3];
            for (Map.Entry<Integer, File> channel : channels.entrySet()) {
                int channelIndex = channel.getKey();
                if (channelIndex >= 0 && channelIndex < channelImages.length) {
                    channelImages[channelIndex] = IJ.openImage(channel.getValue().getAbsolutePath());
                } else {
                    IJ.log("ignoring unexpected channel index " + channelIndex + " for image " + imageName);
                }
            }

            ImagePlus merged = RGBStackMerge.mergeChannels(channelImages, true);
            IJ.saveAs(merged, "Tiff", mergedPath);

            merged.flush();
            for (ImagePlus channelImage : channelImages) {
                if (channelImage != null) {
                    channelImage.flush();
                }
            }
        }
    }

    private Measurement getMeasurement(ImagePlus imagePlus, boolean limitToThreshold) {
        ResultsTable rt = limitToThreshold
            ? ResultsTableService.INSTANCE.measureLimitedToThreshold(imagePlus)
            : ResultsTableService.INSTANCE.measure(imagePlus);
        String label = rt.getLabel(0);
        String[] parsedLabel = label.split("/");
        long area = (long) rt.getValue("Area", 0);
        BigDecimal mean = BigDecimal.valueOf(rt.getValue("Mean", 0)).setScale(3, RoundingMode.HALF_UP);
        BigDecimal stdDev = BigDecimal.valueOf(rt.getValue("StdDev", 0)).setScale(3, RoundingMode.HALF_UP);
        BigDecimal median = BigDecimal.valueOf(rt.getValue("Median", 0)).setScale(3, RoundingMode.HALF_UP);
        long min = (long) rt.getValue("Min", 0);
        long max = (long) rt.getValue("Max", 0);
        long integratedDensity = (long) rt.getValue("IntDen", 0);
        return new Measurement(
            parsedLabel[0],
            parsedLabel[1],
            parsedLabel[2],
            parsedLabel[3],
            parsedLabel[4],
            limitToThreshold,
            area,
            mean,
            stdDev,
            median,
            min,
            max,
            integratedDensity
        );
    }

    /**
     * Composes the threshold string consumed by {@link ImagePlus#setAutoThreshold(String)} from the selected method and
     * the "Dark background" checkbox — e.g. {@code "IsoData dark"} or just {@code "Otsu"}.
     */
    private String buildThresholdString(JComboBox<String> methodBox, JCheckBox darkBox) {
        String method = (String) methodBox.getSelectedItem();
        return darkBox.isSelected() ? method + " dark" : method;
    }

    private List<ImagePlus> getImageSlices(ImagePlus image) {
        String imageTitle = image.getTitle();
        String imageSliceLabel;
        ImageStack stack = image.getStack();

        int stackSize = stack.getSize();
        int currentSlice = image.getCurrentSlice();

        DecimalFormat df = new DecimalFormat("0000");
        List<ImagePlus> slices = new ArrayList<>();
        for (int i = 1; i <= stackSize; i++) {
            image.setSlice(i);

            // Get the current image processor from stack.  Whatever is
            // used here should do a COPY pixels from old processor to
            // new. For instance, ImageProcessor.crop() returns copy.
            ImageProcessor originalImageProcessor = image.getProcessor();
            ImageProcessor newImageProcessor = originalImageProcessor.createProcessor(originalImageProcessor.getWidth(), originalImageProcessor.getHeight());
            newImageProcessor.setPixels(originalImageProcessor.getPixelsCopy());

            // Create a suitable label, using the slice label if possible
            imageSliceLabel = image.getStack().getSliceLabel(i);
            if (imageSliceLabel == null || imageSliceLabel.isEmpty()) {
                imageSliceLabel = "slice" + df.format(i) + "_" + imageTitle;
            }
            // Create a new image corresponding to this slice.
            ImagePlus sliceImage = new ImagePlus(imageSliceLabel, newImageProcessor);
            sliceImage.setCalibration(image.getCalibration());

            slices.add(sliceImage);
        }
        // Reset the original stack state.
        image.setSlice(currentSlice);

        return slices;
    }

    private File writeResultsFile(String rootDirectory, List<Measurement> measurements) {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmssSSS"));
            String dirName = Paths.get(rootDirectory).getFileName().toString();
            Path resultPath = Paths.get(rootDirectory, "Membrane_" + dirName + "_" + timestamp + ".csv");
            try (FileWriter writer = new FileWriter(resultPath.toFile())) {
                writer.write(Measurement.toCsvHeader());
                for (Measurement measurement : measurements) {
                    writer.write(measurement.toCsvEntry());
                }
            }

            return resultPath.toFile();
        } catch (IOException e) {
            throw new RuntimeException("Failed to write results file: " + e.getMessage(), e);
        }
    }

    @Override
    protected List<Pattern> getGeneratedFilePatterns() {
        return List.of(
            Pattern.compile(".*_CALR_RBS25\\.png"),
            Pattern.compile(".*_Membrane_RBS25\\.png"),
            Pattern.compile(".*_MERGED_RBS25\\.png"),
            Pattern.compile("Membrane_.*\\.csv")
        );
    }

    @Override
    public String getPluginName() {
        return "Membrane";
    }

    @Override
    public String getDescription() {
        return """
            <p>Quantifies CALR signal both in total cells and restricted to the cell membrane region across all <code>.tif</code> images in the chosen root directory and its subdirectories. Images may live directly in the chosen directory (no subdirectories required) or be organized into subdirectories.</p>
            <p>Each <code>.tif</code> must be a two-slice stack where slice 1 is the CALR channel and slice 2 is the membrane marker channel. Rolling-ball background subtraction (radius 25) is applied before analysis.</p>
            <p><b>Channel-file merging:</b> any <code>{name}--C00/C01/C02.tif</code> source files are first RGB-merged (00=red, 01=green, 02=blue) into <code>{name}.tif</code>; slice 1 (red) is the CALR channel and slice 2 (green) the membrane channel, while blue (C02) is retained but not measured.</p>
            <p>Two measurements are produced per image:</p>
            <ul>
              <li><b>Total CALR</b> — the chosen <b>Total CALR threshold</b> applied to the CALR slice</li>
              <li><b>Membrane CALR</b> — the chosen <b>Membrane threshold</b> on the membrane slice creates an ROI, transferred to the CALR slice for measurement</li>
            </ul>
            <p>Both auto-threshold methods are selectable at startup (each with its own <b>Dark background</b> option); both default to <b>IsoData dark</b>. The chosen method is recorded in the <code>Stain</code> column — e.g. <code>Membrane CALR (IsoData dark)</code>.</p>
            <p>Two <b>Limit to Threshold</b> checkboxes at startup (one per measurement, both checked by default) independently restrict each measurement's statistics to the pixels inside its threshold; unchecked, that measurement covers the full region. The chosen mode is recorded per row in the <code>Limit to Threshold</code> column.</p>
            <p>Images are rendered with the <b>Cyan Hot</b> LUT for CALR and <b>Orange Hot</b> for the membrane channel. An optional <b>CALR Maximum Brightness</b> caps the CALR display range; the membrane channel display range is auto-adjusted. Image directories must be named <code>{Sex} {Treatment} {Mouse #}</code>.</p>
            <p><b>Output:</b> <code>Membrane_{RootDirectory}_{Timestamp}.csv</code> in the root directory, plus <code>{ImageNumber}_CALR_RBS25.png</code>, <code>_Membrane_RBS25.png</code> and <code>_MERGED_RBS25.png</code> alongside each source image.</p>""";
    }

    @Override
    public Map<String, Object> showStartupMessage() {
        JTextField calrMaximumBrightnessField = new JTextField(10);
        JCheckBox totalCalrLimitBox = new JCheckBox("Limit Total CALR to threshold", true);
        JCheckBox membraneLimitBox = new JCheckBox("Limit Membrane CALR to threshold", true);
        JCheckBox cleanGeneratedFilesCheckBox = new JCheckBox("Delete previously generated files in this directory before running?", true);

        ((AbstractDocument) calrMaximumBrightnessField.getDocument()).setDocumentFilter(new DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
                throws BadLocationException {
                if (string != null && DIGITS.matcher(string).matches()) {
                    super.insertString(fb, offset, string, attr);
                }
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException {
                if (text != null && DIGITS.matcher(text).matches()) {
                    super.replace(fb, offset, length, text, attrs);
                }
            }
        });

        JPanel textPanel = new JPanel(new BorderLayout());
        textPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 0, 15));
        textPanel.add(new JLabel(DescribablePlugin.toSplashHtml(getDescription())));

        JPanel separatorPanel = new JPanel(new BorderLayout());
        separatorPanel.setBorder(BorderFactory.createEmptyBorder(8, 15, 8, 15));
        separatorPanel.add(new JSeparator(), BorderLayout.CENTER);

        String[] thresholdMethods = Arrays.stream(AutoThresholder.Method.values())
            .map(Enum::name)
            .toArray(String[]::new);

        JComboBox<String> totalCalrMethodBox = new JComboBox<>(thresholdMethods);
        totalCalrMethodBox.setSelectedItem(DEFAULT_THRESHOLD_METHOD);
        JCheckBox totalCalrDarkBox = new JCheckBox("Dark background", true);

        JComboBox<String> membraneMethodBox = new JComboBox<>(thresholdMethods);
        membraneMethodBox.setSelectedItem(DEFAULT_THRESHOLD_METHOD);
        JCheckBox membraneDarkBox = new JCheckBox("Dark background", true);

        JPanel inputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 15));
        inputPanel.add(new JLabel("CALR Maximum Brightness: "));
        inputPanel.add(calrMaximumBrightnessField);

        JPanel totalCalrThresholdPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        totalCalrThresholdPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 15));
        totalCalrThresholdPanel.add(new JLabel("Total CALR threshold: "));
        totalCalrThresholdPanel.add(totalCalrMethodBox);
        totalCalrThresholdPanel.add(totalCalrDarkBox);

        JPanel membraneThresholdPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        membraneThresholdPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 15));
        membraneThresholdPanel.add(new JLabel("Membrane threshold: "));
        membraneThresholdPanel.add(membraneMethodBox);
        membraneThresholdPanel.add(membraneDarkBox);

        JPanel limitPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        limitPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 15));
        limitPanel.add(totalCalrLimitBox);
        limitPanel.add(membraneLimitBox);

        JPanel checkBoxPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        checkBoxPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 15));
        checkBoxPanel.add(cleanGeneratedFilesCheckBox);

        JPanel bodyPanel = new JPanel();
        bodyPanel.setLayout(new BoxLayout(bodyPanel, BoxLayout.Y_AXIS));
        bodyPanel.add(textPanel);
        bodyPanel.add(separatorPanel);
        bodyPanel.add(inputPanel);
        bodyPanel.add(totalCalrThresholdPanel);
        bodyPanel.add(membraneThresholdPanel);
        bodyPanel.add(limitPanel);
        bodyPanel.add(checkBoxPanel);

        if (!showStartupDialog(bodyPanel, "Membrane")) {
            return null;
        }

        Map<String, Object> result = new HashMap<>();
        result.put(INPUT_CLEAN_GENERATED_FILES, cleanGeneratedFilesCheckBox.isSelected());
        result.put(INPUT_TOTAL_CALR_THRESHOLD, buildThresholdString(totalCalrMethodBox, totalCalrDarkBox));
        result.put(INPUT_MEMBRANE_THRESHOLD, buildThresholdString(membraneMethodBox, membraneDarkBox));
        result.put(INPUT_TOTAL_CALR_LIMIT_TO_THRESHOLD, totalCalrLimitBox.isSelected());
        result.put(INPUT_MEMBRANE_LIMIT_TO_THRESHOLD, membraneLimitBox.isSelected());
        String text = calrMaximumBrightnessField.getText().trim();
        if (!text.isEmpty()) {
            result.put(INPUT_CALR_MAXIMUM_BRIGHTNESS, Double.parseDouble(text));
        }
        return result;
    }

    @Override
    public void showCompletionMessage(String fileLocation) {
        JPanel bodyPanel = new JPanel(new GridLayout(3, 1));
        bodyPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        bodyPanel.add(new JLabel("Membrane completed. Results can be found at:"));
        bodyPanel.add(new JLabel(""));
        bodyPanel.add(new JLabel(fileLocation));
        showMessage(bodyPanel, "Membrane");
    }

}
