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
import java.util.Objects;
import java.util.stream.Stream;

public abstract class BasePlugin implements Command {

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

        DirectoryChooser od = new DirectoryChooser("Choose a directory to process...");
        if (od.getDirectory() == null) {
            System.exit(0);
        }
        String rootDirectory = od.getDirectory().substring(0, od.getDirectory().length() - 1); // Remove trailing /
        IJ.log("Running analysis of " + rootDirectory);

        File resultFile = buildResultsFile(rootDirectory);

        ResultsTableService.INSTANCE.reset();
        if (System.getProperty("ide") == null) {
            TextWindow resultsWindow = (TextWindow) WindowManager.getWindow("Results");
            resultsWindow.close();
        }

        String resultFileAbsolutePath = resultFile.getAbsolutePath();
        IJ.log("Results file: " + resultFileAbsolutePath);

        showCompletionMessage(resultFileAbsolutePath);
    }

    public abstract File buildResultsFile(String rootDirectory);

    public abstract void showStartupMessage();

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

    public List<String> getImageDirectories(String rootDirectory) {
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

}
