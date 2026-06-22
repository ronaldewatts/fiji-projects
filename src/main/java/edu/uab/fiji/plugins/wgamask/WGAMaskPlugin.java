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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Plugin(name = "WGA Mask", type = Command.class, headless = true,
    menu = {@Menu(label = "UAB"), @Menu(label = "WGA Mask", weight = 1d)})
public class WGAMaskPlugin extends BasePlugin implements DescribablePlugin {

    public static void main(String[] args) {
        System.setProperty("ide", "true");

        // To test, run this main and select the directory <project>/data/Colon Images
        new WGAMaskPlugin().run();
    }

    @Override
    public File buildResultsFile(Map<String, Object> inputs, String rootDirectory) {
        List<String> imageDirectories = getImageDirectories(rootDirectory);
        Set<String> processedImages = new HashSet<>();
        List<Measurement> measurements = new ArrayList<>();

        try (Context context = new Context()) {
            LUTService lutService = context.getService(LUTService.class);
            ColorTable8 greenFireBlue;
            ColorTable8 yellow;
            try {
                Map<String, URL> luTs = lutService.findLUTs();
                greenFireBlue = (ColorTable8) lutService.loadLUT(luTs.get("WCIF/Green Fire Blue.lut"));
                yellow = (ColorTable8) lutService.loadLUT(luTs.get("Yellow.lut"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            for (String subDir : imageDirectories) {
                try (Stream<Path> stream = Files.walk(Paths.get(subDir))) {
                    stream
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .filter(path -> path.toString().endsWith(".tif"))
                        .filter(path -> !path.getFileName().toString().startsWith("."))
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
                                    measurements.add(getMeasurement(slice1Duplicate, false));
                                    measurements.add(getMeasurement(slice1Duplicate, true));

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
                                    measurements.add(getMeasurement(slice1Duplicate, false));
                                    measurements.add(getMeasurement(slice1Duplicate, true));

                                    slice1.setLut(new LUT(greenFireBlue.getValues()[0], greenFireBlue.getValues()[1], greenFireBlue.getValues()[2]));
                                    slice2.setLut(new LUT(yellow.getValues()[0], yellow.getValues()[1], yellow.getValues()[2]));
                                    ImagePlus composite = RGBStackMerge.mergeChannels(new ImagePlus[]{slice1, slice2}, true);

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

            // Sort by image number, then stain. Equal keys keep insertion order, so the full-region row precedes its
            // Limit-to-Threshold counterpart.
            Comparator<Measurement> comparator = Comparator.comparing(Measurement::imageNumber)
                .thenComparing(Measurement::stain);

            measurements.sort(comparator);

            return writeResultsFile(rootDirectory, measurements);
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
            Path resultPath = Paths.get(rootDirectory, "WGAMask_" + dirName + "_" + timestamp + ".csv");
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
            Pattern.compile("WGAMask_.*\\.csv")
        );
    }

    @Override
    public String getPluginName() {
        return "WGA Mask";
    }

    @Override
    public String getDescription() {
        return """
            <p>Quantifies CALR (calreticulin) signal both in full cells and within the WGA-defined cell membrane region across all <code>.tif</code> images in the subdirectories of a chosen root directory.</p>
            <p>Each <code>.tif</code> must be a two-slice stack where slice 1 is the CALR channel and slice 2 is the WGA channel. Rolling-ball background subtraction (radius 25) is applied to the full stack before analysis.</p>
            <p>Two measurements are produced per image:</p>
            <ul>
              <li><b>Total CALR</b> — Huang dark auto-threshold applied to the CALR slice</li>
              <li><b>WGA-mask CALR</b> — Intermodes threshold on the WGA slice creates an ROI, transferred to the CALR slice before Huang dark thresholding</li>
            </ul>
            <p>Each measurement is recorded twice — once over the full region and once with <b>Limit to Threshold</b> enabled (statistics restricted to pixels inside the threshold) — distinguished by the <code>Limit to Threshold</code> column.</p>
            <p>CALR images are rendered with the <b>Green Fire Blue</b> LUT and WGA images with the <b>Yellow</b> LUT. Image directories must be named <code>{Sex} {Treatment} {Mouse #}</code> as these fields are parsed into the results.</p>
            <p><b>Output:</b> <code>WGAMask_{RootDirectory}_{Timestamp}.csv</code> in the root directory, plus <code>{ImageNumber}_CALR_RBS25.png</code>, <code>_WGA_RBS25.png</code> and <code>_MERGED_RBS25.png</code> alongside each source image.</p>""";
    }

    @Override
    public Map<String, Object> showStartupMessage() {
        JCheckBox cleanGeneratedFilesCheckBox = new JCheckBox("Delete previously generated files in this directory before running?", true);

        JPanel textPanel = new JPanel(new BorderLayout());
        textPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 0, 15));
        textPanel.add(new JLabel(DescribablePlugin.toSplashHtml(getDescription())));

        JPanel separatorPanel = new JPanel(new BorderLayout());
        separatorPanel.setBorder(BorderFactory.createEmptyBorder(8, 15, 8, 15));
        separatorPanel.add(new JSeparator(), BorderLayout.CENTER);

        JPanel inputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 15));
        inputPanel.add(cleanGeneratedFilesCheckBox);

        JPanel bodyPanel = new JPanel();
        bodyPanel.setLayout(new BoxLayout(bodyPanel, BoxLayout.Y_AXIS));
        bodyPanel.add(textPanel);
        bodyPanel.add(separatorPanel);
        bodyPanel.add(inputPanel);

        if (!showStartupDialog(bodyPanel, "WGA Mask")) {
            return null;
        }
        return Map.of(INPUT_CLEAN_GENERATED_FILES, cleanGeneratedFilesCheckBox.isSelected());
    }

    @Override
    public void showCompletionMessage(String fileLocation) {
        JPanel bodyPanel = new JPanel(new GridLayout(3, 1));
        bodyPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        bodyPanel.add(new JLabel("WGA Mask completed. Results can be found at:"));
        bodyPanel.add(new JLabel(""));
        bodyPanel.add(new JLabel(fileLocation));
        showMessage(bodyPanel, "WGA Mask");
    }

}
