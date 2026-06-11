package edu.uab.fiji.plugins.flourescenceintensity;

import edu.uab.fiji.plugins.BasePlugin;
import edu.uab.fiji.plugins.DescribablePlugin;
import ij.IJ;
import ij.plugin.frame.ThresholdAdjuster;
import org.scijava.command.Command;
import org.scijava.plugin.Menu;
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
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Plugin(name = "Fluorescence Intensity", type = Command.class, headless = true,
    menu = {@Menu(label = "UAB"), @Menu(label = "Fluorescence Intensity", weight = 1d)})
public class FluorescenceIntensityPlugin extends BasePlugin implements DescribablePlugin {

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
    protected List<Pattern> getGeneratedFilePatterns() {
        return List.of(Pattern.compile("FluorescenceIntensity_.*\\.csv"));
    }

    @Override
    public String getPluginName() {
        return "Fluorescence Intensity";
    }

    @Override
    public String getDescription() {
        return """
            <p>Measures per-channel fluorescence intensity across all <code>.tif</code> images in the subdirectories of a chosen root directory.</p>
            <p>Before running, place a <code>Positive Control.tif</code> and a <code>Negative Control.tif</code> at the root directory — both must be multi-channel merged images with color channel information.</p>
            <p>The plugin derives per-channel thresholds from the positive control and background means from the negative control, then applies them when measuring each sample image. Images may be merged or separated by channel and can be nested in subdirectories.</p>
            <p><b>Output:</b> <code>FluorescenceIntensity_{RootDirectory}_{Timestamp}.csv</code> written to the root directory.<br>
            Columns: folder, channel type, area, mean, min, max, and integrated density.</p>""";
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

        if (!showStartupDialog(bodyPanel, "Fluorescence Intensity")) {
            return null;
        }
        return Map.of(INPUT_CLEAN_GENERATED_FILES, cleanGeneratedFilesCheckBox.isSelected());
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
                            image.flush();
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
