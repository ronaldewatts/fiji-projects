package edu.uab.fiji.plugins.flourescenceintensity;

import edu.uab.fiji.plugins.BasePlugin;
import ij.IJ;
import ij.plugin.frame.ThresholdAdjuster;
import org.scijava.command.Command;
import org.scijava.plugin.Plugin;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@Plugin(name = "Fluorescence Intensity", type = Command.class, headless = true, menuPath = "UAB>Fluorescence Intensity")
public class FluorescenceIntensityPlugin extends BasePlugin {

    public static void main(String[] args) {
        System.setProperty("ide", "true");

        // To test, run this main and select a top level directory from <project>/data
        new FluorescenceIntensityPlugin().run();
    }

    @Override
    public File buildResultsFile(Map<String, Object> inputs, String rootDirectory) {
        ThresholdAdjuster.setMode("B&W");

        IJ.log("=============Positive Control=================");
        String positiveControlFileLocation = rootDirectory + "/Positive Control.tif";
        if (!new File(positiveControlFileLocation).exists()) {
            IJ.log("No Positive Control.tif found. Exiting.");
            System.exit(0);
        }
        Image positiveImage = new Image(positiveControlFileLocation);
        Map<ChannelType, Threshold> positiveThresholdMap = positiveImage.getThresholds();
        IJ.log(positiveImage.toString());
        IJ.log(positiveThresholdMap.toString());
        if (positiveThresholdMap.isEmpty()) {
            IJ.log("No positive thresholds found. Exiting.");
        }
        IJ.log("==============================================");

        IJ.log("=============Negative Control=================");
        String negativeControlImageLocation = rootDirectory + "/Negative Control.tif";
        if (!new File(negativeControlImageLocation).exists()) {
            IJ.log("No Negative Control.tif found. Exiting.");
            System.exit(0);
        }
        Image negativeImage = new Image(negativeControlImageLocation);
        Map<ChannelType, BigDecimal> negativeMeansMap = negativeImage.getMeans();
        IJ.log(negativeImage.toString());
        IJ.log(negativeMeansMap.toString());
        if (negativeMeansMap.isEmpty()) {
            IJ.log("No negative means found. Exiting.");
        }
        IJ.log("==============================================");

        List<String> imageDirectories = getImageDirectories(rootDirectory);

        List<Measurement> results = measure(imageDirectories, positiveThresholdMap, negativeMeansMap);

        return writeResultsFile(rootDirectory, results);
    }

    @Override
    public Map<String, Object> showStartupMessage() {
        JPanel bodyPanel = new JPanel(new GridLayout(4, 1));
        bodyPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        bodyPanel.add(new JLabel("This plugin analyzes all images in the subdirectories of the directory chosen."));
        bodyPanel.add(new JLabel("You must ensure that a 'Positive Control.tif` and 'Negative Control.tif' are defined at the root directory and are merged with Color Channel information available."));
        bodyPanel.add(new JLabel("You can nest the images into subdirectories, organizing them as you see fit. Images can be merged or separated by channel, the channel information must be on the image."));
        bodyPanel.add(new JLabel("Results will be created as a CSV file called FluorescenceIntensity_{Root Directory}_{Timestamp}.csv in the root directory."));
        showMessage(bodyPanel, "Fluorescence Intensity");
        return Map.of();
    }

    @Override
    public void showCompletionMessage(String fileLocation) {
        JPanel bodyPanel = new JPanel(new GridLayout(3, 1));
        bodyPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        bodyPanel.add(new JLabel("Fluorescence Intensity completed. Results can be found at:"));
        bodyPanel.add(new JLabel(""));
        bodyPanel.add(new JLabel(fileLocation));
        showMessage(bodyPanel, "Fluorescence Intensity");
    }

    private List<Measurement> measure(List<String> imageDirectories, Map<ChannelType, Threshold> positiveThresholdMap, Map<ChannelType, BigDecimal> negativeMeansMap) {
        List<Measurement> measurements = new ArrayList<>();
        Set<String> processedImages = new HashSet<>();
        for (String subDir : imageDirectories) {
            try (Stream<Path> stream = Files.walk(Paths.get(subDir))) {
                stream.filter(path -> path.toString().endsWith(".tif"))
                    .forEach(path -> {
                        String absolutePath = path.toAbsolutePath().toString();
                        if (!processedImages.contains(absolutePath)) {
                            IJ.log("processing file: " + absolutePath);
                            Image image = new Image(absolutePath, positiveThresholdMap, negativeMeansMap);
                            measurements.addAll(image.measure());
                            processedImages.add(absolutePath);
                        }
                    });
            } catch (IOException e) {
                throw new RuntimeException("Failed to process directory: " + subDir, e);
            }
        }
        return measurements;
    }

    private File writeResultsFile(String rootDirectory, List<Measurement> measurements) {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmssSSS"));
            String dirName = Paths.get(rootDirectory).getFileName().toString();
            Path resultPath = Paths.get(rootDirectory, "FluorescenceIntensity_" + dirName + "_" + timestamp + ".csv");
            String removablePath = new File(rootDirectory).getParentFile().getAbsolutePath();
            try (FileWriter writer = new FileWriter(resultPath.toFile())) {
                writer.write(Measurement.toCsvHeader());
                measurements.sort(
                    Comparator.comparing((Measurement measurement) -> measurement.imageChannel().folder())
                        .thenComparing((Measurement measurement) -> measurement.imageChannel().channelType())
                );
                for (Measurement measurement : measurements) {
                    writer.write(measurement.toCsvEntry(removablePath));
                }
            }

            return resultPath.toFile();
        } catch (IOException e) {
            throw new RuntimeException("Failed to write results file: " + e.getMessage(), e);
        }
    }
}
