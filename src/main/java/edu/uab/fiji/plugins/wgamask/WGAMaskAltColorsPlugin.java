package edu.uab.fiji.plugins.wgamask;

import edu.uab.fiji.plugins.BasePlugin;
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
import java.util.stream.Stream;

@Plugin(name = "WGA Mask Alt Colors", type = Command.class, headless = true, menuPath = "UAB>WGA Mask Alt Colors")
public class WGAMaskAltColorsPlugin extends BasePlugin {

    public static void main(String[] args) {
        System.setProperty("ide", "true");

        // To test, run this main and select the directory <project>/data/Colon Images
        new WGAMaskAltColorsPlugin().run();
    }

    @Override
    public File buildResultsFile(String rootDirectory) {
        List<String> imageDirectories = getImageDirectories(rootDirectory);
        Set<String> processedImages = new HashSet<>();
        List<Measurement> measurements = new ArrayList<>();

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
                                if (image.getStackSize() == 2) {
                                    IJ.run(image, "Subtract Background...", "rolling=25 stack");
                                    String imageNumber = image.getTitle().replace(".tif", "");

                                    List<ImagePlus> slices = getImageSlices(image);
                                    ImagePlus slice1 = slices.getFirst();
                                    ImagePlus slice1Duplicate = slice1.duplicate();
                                    slice1Duplicate.setAutoThreshold("Huang dark");
                                    slice1Duplicate.setTitle(parentFolder + "/Total CALR/" + imageNumber);
                                    measurements.add(getMeasurement(slice1Duplicate));

                                    ImagePlus slice2 = slices.getLast();
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

                                    IJ.run(slice1, "Calibration Bar...", "location=[Upper Right] fill=White label=Black number=2 decimal=0 font=12 zoom=3 overlay");
                                    IJ.saveAs(slice1, "PNG", path.getParent().toAbsolutePath() + "/" + imageNumber + "_CALR_RBS25.png");
                                    IJ.saveAs(slice2, "PNG", path.getParent().toAbsolutePath() + "/" + imageNumber + "_WGA_RBS25.png");
                                    IJ.saveAs(composite, "PNG", path.getParent().toAbsolutePath() + "/" + imageNumber + "_MERGED_RBS25.png");

                                } else {
                                    IJ.log(absolutePath + " is not a valid image file as it has a stack size of " + image.getStackSize());
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
        if (image.isProcessor()) {
            ImageProcessor ip = image.getProcessor();
            ip.setPixels(ip.getPixels());
        }
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
    public void showStartupMessage() {
        JPanel bodyPanel = new JPanel(new GridLayout(5, 1));
        bodyPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        bodyPanel.add(new JLabel("This plugin analyzes all images in the subdirectories of the directory chosen using Cyan Hot and Orange Hot LUTs."));
        bodyPanel.add(new JLabel("You must ensure that the .tif files contain exactly 2 slices."));
        bodyPanel.add(new JLabel("Image directories are expected to be in the name format '{Sex} {Treatment} {Mouse #}' as this is included in the results."));
        bodyPanel.add(new JLabel("Along with results, CALR and WGA .png images will be created for each image in the image directory."));
        bodyPanel.add(new JLabel("Results will be created as a CSV file called WGAMask_{Root Directory}_{Timestamp}.csv in the root directory."));
        showMessage(bodyPanel, "WGA Mask");
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
