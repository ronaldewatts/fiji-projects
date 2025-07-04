package fiji.plugins.imageanalyzer;

import ij.IJ;
import ij.WindowManager;
import ij.io.DirectoryChooser;
import ij.measure.ResultsTable;
import ij.plugin.filter.Analyzer;
import ij.plugin.frame.ThresholdAdjuster;
import ij.text.TextWindow;
import org.scijava.command.Command;
import org.scijava.plugin.Plugin;

import javax.swing.*;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Plugin(name = "UAB Image Analyzer", type = Command.class, headless = true, menuPath = "UAB>Image Analyzer")
public class ImageAnalyzerPlugin implements Command {

    @Override
    public void run() {
        IJ.log("\\Clear");

        DirectoryChooser od = new DirectoryChooser("Choose a directory to process...");
        String rootDirectory = od.getDirectory().substring(0, od.getDirectory().length() - 1); // Remove trailing /
        IJ.log("Running analysis of " + rootDirectory);

        ThresholdAdjuster.setMode("B&W");
        IJ.run("Set Measurements...", "area mean min integrated redirect=None decimal=3");
        ResultsTable rt = Analyzer.getResultsTable();

        RGB positiveControlRGB = new RGB(rootDirectory + "/Positive Control.tif");
        Thresholds positiveThresholds = new Thresholds(positiveControlRGB);

        RGB negativeControlRGB = new RGB(rootDirectory + "/Negative Control.tif");
        negativeControlRGB.setThresholds(positiveThresholds);

        Result negativeRedResult = new Result(negativeControlRGB.imageName, "CY5", negativeControlRGB.red, rt, positiveThresholds.red, 0);
        double negativeRedMean = negativeRedResult.mean;
        IJ.log("negative red mean =" + negativeRedMean);

        Result negativeGreenResult = new Result(negativeControlRGB.imageName, "GFP", negativeControlRGB.green, rt, positiveThresholds.green, 0);
        double negativeGreenMean = negativeGreenResult.mean;
        IJ.log("negative green mean =" + negativeGreenMean);

        Result negativeBlueResult = new Result(negativeControlRGB.imageName, "DAPI", negativeControlRGB.blue, rt, positiveThresholds.blue, 0);
        double negativeBlueMean = negativeBlueResult.mean;
        IJ.log("negativeBlueResult=" + negativeBlueResult);

        List<String> imageDirectories = getImageDirectories(rootDirectory);

        List<Result> results = getResults(imageDirectories, positiveThresholds, negativeRedMean, negativeGreenMean, negativeBlueMean, rt);

        File resultFile = writeResultsFile(rootDirectory, results);

        rt.reset();
        if (System.getProperty("ide") == null) {
            TextWindow resultsWindow = (TextWindow) WindowManager.getWindow("Results");
            resultsWindow.close();
        }

        IJ.log("Wrote file " + resultFile.getAbsolutePath());
        JOptionPane.showMessageDialog(null, "Wrote file " + resultFile.getAbsolutePath());
    }

    private static List<Result> getResults(List<String> imageDirectories, Thresholds thresholds, double negativeRedMean, double negativeGreenMean, double negativeBlueMean, ResultsTable rt) {
        List<Result> results = new ArrayList<>();
        for (String subDir : imageDirectories) {
            IJ.log("processing " + subDir);
            Path subPath = Paths.get(subDir);
            try (Stream<Path> stream = Files.walk(subPath)) {
                stream.map(Path::toFile)
                        .forEach(file -> {
                            String absolutePath = file.getAbsolutePath();
                            String name = file.getName();
                            if (name.endsWith(".tif")) {
                                IJ.log("processing file = " + name);
                                RGB image = new RGB(absolutePath);
                                image.setThresholds(thresholds);
                                image.subtract(negativeRedMean, negativeGreenMean, negativeBlueMean);
                                results.add(new Result(image.imageName, "CY5", image.red, rt, thresholds.red, negativeRedMean));
                                results.add(new Result(image.imageName, "GFP", image.green, rt, thresholds.green, negativeGreenMean));
                                results.add(new Result(image.imageName, "DAPI", image.blue, rt, thresholds.blue, negativeBlueMean));
                            }
                        });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return results;
    }

    private List<String> getImageDirectories(String rootDirectory) {
        try {
            Path dir = Paths.get(rootDirectory);
            List<String> subDirs;
            try (Stream<Path> stream = Files.walk(dir)) {
                subDirs = stream
                        .filter(Files::isDirectory)
                        .map(Path::toString)
                        .filter(fileName -> !fileName.equals(rootDirectory))
                        .toList();
            }
            return subDirs;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private File writeResultsFile(String rootDirectory, List<Result> results) {
        try {
            Path resultPath = Paths.get(rootDirectory + "/Results.csv");
            Files.deleteIfExists(resultPath);
            Files.createFile(resultPath);
            File resultFile = resultPath.toFile();
            try (FileWriter fileWriter = new FileWriter(resultFile)) {
                fileWriter.write(
                        "Image Name," +
                                "Color," +
                                "Area," +
                                "Mean," +
                                "Min," +
                                "Max," +
                                "Integrated Density," +
                                "Positive Threshold Min," +
                                "Positive Threshold Max," +
                                "Negative Mean" +
                                System.lineSeparator()
                );
                for (Result result : results) {
                    fileWriter.write(result.toCsvEntry());
                }
            }
            return resultFile;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
