package edu.uab.fiji.plugins;

import edu.uab.fiji.domain.ChannelType;
import edu.uab.fiji.domain.Image;
import edu.uab.fiji.domain.Measurement;
import edu.uab.fiji.domain.Threshold;
import edu.uab.fiji.service.ResultsTableService;
import ij.IJ;
import ij.WindowManager;
import ij.io.DirectoryChooser;
import ij.plugin.frame.ThresholdAdjuster;
import ij.text.TextWindow;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

@Plugin(name = "Fluorescence Intensity", type = Command.class, headless = true, menuPath = "UAB>Fluorescence Intensity")
public class FluorescenceIntensityPlugin implements Command {

    @Override
    public void run() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | UnsupportedLookAndFeelException | InstantiationException |
                 IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        showStartupMessage();

        IJ.log("\\Clear");
        ThresholdAdjuster.setMode("B&W");

        DirectoryChooser od = new DirectoryChooser("Choose a directory to process...");
        if (od.getDirectory() == null) {
            System.exit(0);
        }
        String rootDirectory = od.getDirectory().substring(0, od.getDirectory().length() - 1); // Remove trailing /
        IJ.log("Running analysis of " + rootDirectory);

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

        File resultFile = writeResultsFile(rootDirectory, results);

        ResultsTableService.INSTANCE.reset();
        if (System.getProperty("ide") == null) {
            TextWindow resultsWindow = (TextWindow) WindowManager.getWindow("Results");
            resultsWindow.close();
        }

        String resultFileAbsolutePath = resultFile.getAbsolutePath();
        IJ.log("Results file: " + resultFileAbsolutePath);

        showCompletionMessage(resultFileAbsolutePath);
    }

    private void showStartupMessage() {
        JPanel bodyPanel = new JPanel(new GridLayout(4, 1));
        bodyPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        bodyPanel.add(new JLabel("This plugin analyzes all images in the subdirectories of the directory chosen."));
        bodyPanel.add(new JLabel("You must ensure that a 'Positive Control.tif` and 'Negative Control.tif' are defined at the root directory."));
        bodyPanel.add(new JLabel("Results will be created as a CSV file called FluorescenceIntensity_{Root Directory}_{Timestamp}.csv in the root directory."));
        bodyPanel.add(new JLabel("This file will be overwritten on each analysis of that directory."));
        showMessage(bodyPanel);
    }

    private void showCompletionMessage(String fileLocation) {
        JPanel bodyPanel = new JPanel(new GridLayout(3, 1));
        bodyPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        bodyPanel.add(new JLabel("Fluorescence Intensity completed. Results can be found at:"));
        bodyPanel.add(new JLabel(""));
        bodyPanel.add(new JLabel(fileLocation));
        showMessage(bodyPanel);
    }

    private void showMessage(JPanel body) {
        ImageIcon icon = new ImageIcon(Objects.requireNonNull(getClass().getResource("/new-uab-monogram.png")));
        JLabel iconLabel = new JLabel(icon);
        JPanel iconPanel = new JPanel(new GridBagLayout());
        iconPanel.add(iconLabel);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(iconPanel, BorderLayout.WEST);
        panel.add(body);
        JOptionPane.showMessageDialog(null, panel, "Fluorescence Intensity", JOptionPane.PLAIN_MESSAGE);
    }

    private List<Measurement> measure(List<String> imageDirectories, Map<ChannelType, Threshold> positiveThresholdMap, Map<ChannelType, BigDecimal> negativeMeansMap) {
        List<Measurement> measurements = new ArrayList<>();

        for (String subDir : imageDirectories) {
            IJ.log("Processing directory:" + subDir);
            try (Stream<Path> stream = Files.walk(Paths.get(subDir))) {
                stream.filter(path -> path.toString().endsWith(".tif"))
                        .forEach(path -> {
                            String absolutePath = path.toAbsolutePath().toString();
                            IJ.log("processing file: " + path.getFileName());
                            Image image = new Image(absolutePath, positiveThresholdMap, negativeMeansMap);
                            measurements.addAll(image.measure());
                        });
            } catch (IOException e) {
                throw new RuntimeException("Failed to process directory: " + subDir, e);
            }
        }
        return measurements;
    }

    private List<String> getImageDirectories(String rootDirectory) {
        try (Stream<Path> stream = Files.walk(Paths.get(rootDirectory))) {
            return stream
                    .filter(Files::isDirectory)
                    .map(Path::toString)
                    .filter(path -> !path.equals(rootDirectory))
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read directories from: " + rootDirectory, e);
        }
    }

    private File writeResultsFile(String rootDirectory, List<Measurement> measurements) {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmssSSS"));
            String dirName = Paths.get(rootDirectory).getFileName().toString();
            Path resultPath = Paths.get(rootDirectory, "FluorescenceIntensity_" + dirName + "_" + timestamp + ".csv");

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
}
