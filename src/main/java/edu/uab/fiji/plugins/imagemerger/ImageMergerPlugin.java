package edu.uab.fiji.plugins.imagemerger;

import edu.uab.fiji.plugins.BasePlugin;
import edu.uab.fiji.plugins.DescribablePlugin;
import ij.IJ;
import ij.ImagePlus;
import ij.plugin.RGBStackMerge;
import org.scijava.command.Command;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Plugin;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Plugin(name = "Image Merger", type = Command.class, headless = true,
    menu = {@Menu(label = "UAB"), @Menu(label = "Image Merger", weight = 1d)})
public class ImageMergerPlugin extends BasePlugin implements DescribablePlugin {

    // Matches per-channel source files like "A--C00.tif"; group 1 = image name, group 2 = channel index (00=red, 01=green, 02=blue).
    private static final Pattern CHANNEL_FILE = Pattern.compile("(.+)--C0(\\d+)\\.tif");

    public static void main(String[] args) {
        System.setProperty("ide", "true");

        // To test, run this main and select a directory containing '{name}--C0*.tif' channel files.
        new ImageMergerPlugin().run();
    }

    @Override
    public File buildResultsFile(Map<String, Object> inputs, String rootDirectory) {
        // Include the root directory itself so channel files placed directly in the chosen directory (no
        // subdirectories) are still merged.
        List<String> imageDirectories = new ArrayList<>();
        imageDirectories.add(rootDirectory);
        imageDirectories.addAll(getImageDirectories(rootDirectory));

        for (String subDir : imageDirectories) {
            mergeChannelImages(subDir);
        }

        // This plugin produces no results file; report the processed directory so the completion message has a location.
        return new File(rootDirectory);
    }

    /**
     * Scans {@code subDir} for per-channel source files named {@code {image-name}--C0*.tif}, RGB-merges each group
     * (00=red, 01=green, 02=blue) into a composite saved as {@code {image-name}.tif} (overwriting any existing file),
     * and leaves the original channel files in place. If no channel files are present, this is a no-op.
     */
    private void mergeChannelImages(String subDir) {
        File directory = new File(subDir);
        File[] channelFiles = directory.listFiles((dir, name) -> CHANNEL_FILE.matcher(name).matches());
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
            IJ.log("merging channel images for: " + imageName);

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
            IJ.saveAs(merged, "Tiff", new File(directory, imageName + ".tif").getAbsolutePath());

            merged.flush();
            for (ImagePlus channelImage : channelImages) {
                if (channelImage != null) {
                    channelImage.flush();
                }
            }
        }
    }

    @Override
    public String getPluginName() {
        return "Image Merger";
    }

    @Override
    public String getDescription() {
        return """
            <p>Merges per-channel source images into composite <code>.tif</code> files across the chosen root directory and its subdirectories — the same channel-merging step performed by <b>Membrane</b>, with no analysis attached. Use it when you only need the merged composites and not any measurements.</p>
            <p>Each directory is scanned for <code>{name}--C00/C01/C02.tif</code> source files, which are RGB-merged (00=red, 01=green, 02=blue) into a composite <code>{name}.tif</code>, overwriting any existing file of that name. The original <code>--C0*.tif</code> files are left in place. Directories with no <code>--C0*.tif</code> files are skipped.</p>
            <p><b>Output:</b> <code>{name}.tif</code> composites written alongside their source channel files. No measurements, PNGs, or CSV are produced.</p>""";
    }

    @Override
    public Map<String, Object> showStartupMessage() {
        JPanel textPanel = new JPanel(new BorderLayout());
        textPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        textPanel.add(new JLabel(DescribablePlugin.toSplashHtml(getDescription())));

        showMessage(textPanel, "Image Merger");

        return new HashMap<>();
    }

    @Override
    public void showCompletionMessage(String fileLocation) {
        JPanel bodyPanel = new JPanel(new GridLayout(3, 1));
        bodyPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        bodyPanel.add(new JLabel("Image Merger completed. Merged images can be found in:"));
        bodyPanel.add(new JLabel(""));
        bodyPanel.add(new JLabel(fileLocation));
        showMessage(bodyPanel, "Image Merger");
    }

}
