package edu.uab.fiji.plugins;

import edu.uab.fiji.service.ResultsTableService;
import ij.IJ;
import ij.WindowManager;
import ij.io.DirectoryChooser;
import ij.text.TextWindow;
import org.scijava.command.Command;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public abstract class BasePlugin implements Command {

    public static final String INPUT_CLEAN_GENERATED_FILES = "cleanGeneratedFiles";

    @Override
    public void run() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | UnsupportedLookAndFeelException | InstantiationException |
                 IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        Map<String, Object> inputs = showStartupMessage();

        IJ.log("\\Clear");

        DirectoryChooser od = new DirectoryChooser("Choose a directory to process...");
        if (od.getDirectory() == null) {
            System.exit(0);
        }
        String rootDirectory = od.getDirectory().substring(0, od.getDirectory().length() - 1); // Remove trailing /
        IJ.log("Running analysis of " + rootDirectory);

        if (Boolean.TRUE.equals(inputs.get(INPUT_CLEAN_GENERATED_FILES))) {
            cleanGeneratedFiles(rootDirectory);
        }

        File resultFile = buildResultsFile(inputs, rootDirectory);

        ResultsTableService.INSTANCE.reset();
        if (System.getProperty("ide") == null) {
            TextWindow resultsWindow = (TextWindow) WindowManager.getWindow("Results");
            if (resultsWindow != null) {
                resultsWindow.close();
            }
        }

        String resultFileAbsolutePath = resultFile.getAbsolutePath();
        IJ.log("Results file: " + resultFileAbsolutePath);

        showCompletionMessage(resultFileAbsolutePath);
    }

    public abstract File buildResultsFile(Map<String, Object> inputs, String rootDirectory);

    public abstract Map<String, Object> showStartupMessage();

    public abstract void showCompletionMessage(String resultFileAbsolutePath);

    public void showMessage(JPanel body, String title) {
        ImageIcon icon = new ImageIcon(Objects.requireNonNull(getClass().getResource("/new-uab-monogram.png")));
        JLabel iconLabel = new JLabel(icon);
        JPanel iconPanel = new JPanel(new GridBagLayout());
        iconPanel.add(iconLabel);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(iconPanel, BorderLayout.WEST);
        panel.add(body);
        JOptionPane.showMessageDialog(null, panel, title, JOptionPane.PLAIN_MESSAGE);
    }

    /**
     * Regex patterns (matched against the file name only) identifying files this plugin generates and may safely
     * delete before a run. Defaults to none; plugins that produce output should override this.
     */
    protected List<Pattern> getGeneratedFilePatterns() {
        return List.of();
    }

    private void cleanGeneratedFiles(String rootDirectory) {
        List<Pattern> patterns = getGeneratedFilePatterns();
        if (patterns.isEmpty()) {
            return;
        }
        try (Stream<Path> stream = Files.walk(Paths.get(rootDirectory))) {
            stream
                .filter(Files::isRegularFile)
                .filter(path -> patterns.stream().anyMatch(pattern -> pattern.matcher(path.getFileName().toString()).matches()))
                .forEach(path -> {
                    try {
                        Files.delete(path);
                        IJ.log("Deleted generated file: " + path);
                    } catch (IOException e) {
                        IJ.log("Failed to delete generated file: " + path + " - " + e.getMessage());
                    }
                });
        } catch (Exception e) {
            IJ.log("Failed to clean generated files in: " + rootDirectory + " - " + e.getMessage());
        }
    }

    public List<String> getImageDirectories(String rootDirectory) {
        try (Stream<Path> stream = Files.walk(Paths.get(rootDirectory))) {
            return stream
                .filter(Files::isDirectory)
                .map(Path::toString)
                .filter(path -> !path.equals(rootDirectory))
                .sorted()
                .toList();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read directories from: " + rootDirectory, e);
        }
    }

}
