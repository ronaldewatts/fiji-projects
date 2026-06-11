package edu.uab.fiji.plugins.wgamask;

import edu.uab.fiji.plugins.BasePlugin;
import edu.uab.fiji.plugins.DescribablePlugin;
import edu.uab.fiji.service.ResultsTableService;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.RGBStackMerge;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Plugin(name = "WGA Mask Alt Colors", type = Command.class, headless = true,
    menu = {@Menu(label = "UAB"), @Menu(label = "WGA Mask Alt Colors", weight = 1d)})
public class WGAMaskAltColorsPlugin extends BasePlugin implements DescribablePlugin {

    public static final String INPUT_CALR_MAXIMUM_BRIGHTNESS = "calrMaximumBrightness";

    private static final Pattern DIGITS = Pattern.compile("\\d+");

    public static void main(String[] args) {
        System.setProperty("ide", "true");

        // To test, run this main and select the directory <project>/data/Colon Images
        new WGAMaskAltColorsPlugin().run();
    }

    @Override
    public File buildResultsFile(Map<String, Object> inputs, String rootDirectory) {
        List<String> imageDirectories = getImageDirectories(rootDirectory);
        Set<String> processedImages = new HashSet<>();
        List<Measurement> measurements = new ArrayList<>();
        Double calrMaximumBrightness = (Double) inputs.get(INPUT_CALR_MAXIMUM_BRIGHTNESS);

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

            for (String subDir : imageDirectories) {
                try (Stream<Path> stream = Files.walk(Paths.get(subDir))) {
                    stream
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .filter(path -> path.toString().endsWith(".tif"))
                        .forEach(path -> {
                            String absolutePath = path.toAbsolutePath().toString();
                            if (!processedImages.contains(absolutePath)) {
                                processedImages.add(absolutePath);
                                IJ.log("processing file: " + absolutePath);

                                String parentFolder = path.getParent().getFileName().toString().replace(" ", "/");
                                ImagePlus image = IJ.openImage(absolutePath);
                                if (image.getStackSize() >= 2) {
                                    IJ.run(image, "Subtract Background...", "rolling=25 stack");
                                    String imageNumber = image.getTitle().replace(".tif", "");

                                    List<ImagePlus> slices = getImageSlices(image);
                                    ImagePlus slice1 = slices.getFirst();
                                    ImagePlus slice1Duplicate = slice1.duplicate();
                                    slice1Duplicate.setAutoThreshold("Huang dark");
                                    slice1Duplicate.setTitle(parentFolder + "/Total CALR/" + imageNumber);
                                    measurements.add(getMeasurement(slice1Duplicate));

                                    ImagePlus slice2 = slices.get(1);
                                    ImagePlus slice2Duplicate = slice2.duplicate();
                                    slice2.setAutoThreshold("Intermodes");
                                    IJ.run(slice2, "Create Selection", "");
                                    Roi slice2Roi = slice2.getRoi();
                                    slice2Duplicate.setRoi(slice2Roi);
                                    IJ.run(slice2Duplicate, "Clear Outside", "");
                                    IJ.run(slice2Duplicate, "Select None", "");
                                    slice2Duplicate.setAutoThreshold("Huang dark");
                                    IJ.run(slice2Duplicate, "Create Selection", "");
                                    Roi slice2DuplicateRoi = slice2Duplicate.getRoi();

                                    slice1Duplicate.setRoi(slice2DuplicateRoi);
                                    slice1Duplicate.setTitle(parentFolder + "/WGA-mask CALR/" + imageNumber);
                                    measurements.add(getMeasurement(slice1Duplicate));

                                    slice1.setLut(new LUT(cyanHot.getValues()[0], cyanHot.getValues()[1], cyanHot.getValues()[2]));
                                    slice2.setLut(new LUT(orangeHot.getValues()[0], orangeHot.getValues()[1], orangeHot.getValues()[2]));
                                    ImagePlus composite = RGBStackMerge.mergeChannels(new ImagePlus[]{slice1, slice2}, true);

                                    if (calrMaximumBrightness != null) {
                                        slice1.setDisplayRange(0, calrMaximumBrightness);
                                    }

                                    IJ.run(slice1, "Calibration Bar...", "location=[Upper Right] fill=White label=Black number=2 decimal=0 font=12 zoom=3 overlay");
                                    IJ.saveAs(slice1, "PNG", path.getParent().toAbsolutePath() + "/" + imageNumber + "_CALR_RBS25.png");
                                    IJ.saveAs(slice2, "PNG", path.getParent().toAbsolutePath() + "/" + imageNumber + "_WGA_RBS25.png");
                                    IJ.saveAs(composite, "PNG", path.getParent().toAbsolutePath() + "/" + imageNumber + "_MERGED_RBS25.png");

                                    // Release pixel buffers promptly to keep peak heap bounded across large batches.
                                    image.flush();
                                    slice1.flush();
                                    slice2.flush();
                                    slice1Duplicate.flush();
                                    slice2Duplicate.flush();
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

            Comparator<Measurement> comparator = Comparator.comparing(Measurement::sex).reversed()
                .thenComparing(Comparator.comparing(Measurement::treatment).reversed())
                .thenComparing(Measurement::stain)
                .thenComparing(Measurement::mouseNumber)
                .thenComparing(Measurement::imageNumber);

            measurements.sort(comparator);

            return writeResultsFile(rootDirectory, measurements);
        }
    }

    private Measurement getMeasurement(ImagePlus imagePlus) {
        ResultsTable rt = ResultsTableService.INSTANCE.measure(imagePlus);
        String label = rt.getLabel(0);
        String[] parsedLabel = label.split("/");
        long area = (long) rt.getValue("Area", 0);
        BigDecimal mean = BigDecimal.valueOf(rt.getValue("Mean", 0)).setScale(3, RoundingMode.HALF_UP);
        long min = (long) rt.getValue("Min", 0);
        long max = (long) rt.getValue("Max", 0);
        long integratedDensity = (long) rt.getValue("IntDen", 0);
        return new Measurement(
            parsedLabel[0],
            parsedLabel[1],
            parsedLabel[2],
            parsedLabel[3],
            parsedLabel[4],
            area,
            mean,
            min,
            max,
            integratedDensity
        );
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
            Path resultPath = Paths.get(rootDirectory, "WGAMaskAltColors_" + dirName + "_" + timestamp + ".csv");
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
            Pattern.compile(".*_WGA_RBS25\\.png"),
            Pattern.compile(".*_MERGED_RBS25\\.png"),
            Pattern.compile("WGAMaskAltColors_.*\\.csv")
        );
    }

    @Override
    public String getPluginName() {
        return "WGA Mask Alt Colors";
    }

    @Override
    public String getDescription() {
        return """
            <p>Identical in analysis to <b>WGA Mask</b> — same two-slice stack requirement, same rolling-ball background subtraction, same Huang dark / WGA Intermodes thresholding logic.</p>
            <p>Renders images with the <b>Cyan Hot</b> LUT for CALR and the <b>Orange Hot</b> LUT for WGA.</p>
            <p>Accepts an optional <b>CALR Maximum Brightness</b> input that sets the CALR display-range ceiling before saving PNGs, helping normalize visualization across datasets with varying intensity scales. Image directories must be named <code>{Sex} {Treatment} {Mouse #}</code> as these fields are parsed into the results.</p>
            <p><b>Output:</b> <code>WGAMaskAltColors_{RootDirectory}_{Timestamp}.csv</code> in the root directory, plus <code>{ImageNumber}_CALR_RBS25.png</code>, <code>_WGA_RBS25.png</code> and <code>_MERGED_RBS25.png</code> alongside each source image.</p>""";
    }

    @Override
    public Map<String, Object> showStartupMessage() {
        JTextField calrMaximumBrightnessField = new JTextField(10);
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
            public void replace(DocumentFilter.FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
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

        JPanel inputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 15));
        inputPanel.add(new JLabel("CALR Maximum Brightness: "));
        inputPanel.add(calrMaximumBrightnessField);

        JPanel checkBoxPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        checkBoxPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 15));
        checkBoxPanel.add(cleanGeneratedFilesCheckBox);

        JPanel bodyPanel = new JPanel();
        bodyPanel.setLayout(new BoxLayout(bodyPanel, BoxLayout.Y_AXIS));
        bodyPanel.add(textPanel);
        bodyPanel.add(separatorPanel);
        bodyPanel.add(inputPanel);
        bodyPanel.add(checkBoxPanel);

        showMessage(bodyPanel, "WGA Mask Alt Colors");

        Map<String, Object> result = new HashMap<>();
        result.put(INPUT_CLEAN_GENERATED_FILES, cleanGeneratedFilesCheckBox.isSelected());
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
        bodyPanel.add(new JLabel("WGA Mask Alt Colors completed. Results can be found at:"));
        bodyPanel.add(new JLabel(""));
        bodyPanel.add(new JLabel(fileLocation));
        showMessage(bodyPanel, "WGA Mask Alt Colors");
    }

}
