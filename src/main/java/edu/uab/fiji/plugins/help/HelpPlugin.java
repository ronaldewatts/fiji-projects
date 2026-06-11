package edu.uab.fiji.plugins.help;

import edu.uab.fiji.plugins.DescribablePlugin;
import ij.IJ;
import org.scijava.command.Command;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Plugin;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * A documentation-only command: it shows a single scrollable splash listing every UAB plugin and its description. It
 * does not extend {@link edu.uab.fiji.plugins.BasePlugin} because it performs no directory processing.
 *
 * <p>Descriptions are <em>not</em> re-coded here. Instead the plugin scans its own jar (or {@code classes} directory
 * during development) for every class in {@link #PLUGIN_PACKAGE} that implements {@link DescribablePlugin}, instantiates
 * each, and pulls its {@link DescribablePlugin#getDescription()}. Adding a new describable plugin makes it appear here
 * automatically — nothing in this class needs to change. The scan deliberately avoids SciJava's {@code @Plugin}
 * annotation index (which the Maven CLI does not generate and which can be classloader-sensitive), so Help works
 * regardless of how the jar was built.</p>
 */
// A high leaf weight sorts Help last within the UAB menu. The separator above it is NOT weight-driven: Fiji's legacy
// menu bridge ignores weight-difference separators, so it is inserted at runtime by UABMenuSeparator.
@Plugin(type = Command.class, name = "Help",
    menu = {@Menu(label = "UAB"), @Menu(label = "Help", weight = 1000d)})
public class HelpPlugin implements Command {

    /** Only classes in this package are scanned. */
    private static final String PLUGIN_PACKAGE = "edu.uab.fiji.plugins";

    public static void main(String[] args) {
        System.setProperty("ide", "true");
        new HelpPlugin().run();
    }

    @Override
    public void run() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | UnsupportedLookAndFeelException | InstantiationException |
                 IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        List<DescribablePlugin> describables = discoverDescribablePlugins();
        describables.sort(Comparator.comparing(DescribablePlugin::getPluginName));

        showHelp(describables);
    }

    /**
     * Loads every class under {@link #PLUGIN_PACKAGE} (from this code's own jar or classes directory) and keeps the
     * concrete ones implementing {@link DescribablePlugin}, instantiating each via its no-arg constructor.
     */
    private List<DescribablePlugin> discoverDescribablePlugins() {
        List<DescribablePlugin> describables = new ArrayList<>();
        ClassLoader classLoader = getClass().getClassLoader();
        for (String className : findCandidateClassNames()) {
            try {
                Class<?> candidate = Class.forName(className, false, classLoader);
                if (DescribablePlugin.class.isAssignableFrom(candidate)
                    && !candidate.isInterface()
                    && !Modifier.isAbstract(candidate.getModifiers())) {
                    describables.add((DescribablePlugin) candidate.getDeclaredConstructor().newInstance());
                }
            } catch (ReflectiveOperationException | LinkageError e) {
                IJ.log("Help: could not load " + className + " - " + e);
            }
        }
        return describables;
    }

    /**
     * Collects the fully-qualified names of every {@code .class} in {@link #PLUGIN_PACKAGE} (and subpackages) found in
     * the code source containing this class — a jar entry scan when packaged, or a directory walk during development.
     * Inner/anonymous classes (names containing {@code $}) are skipped.
     */
    private Set<String> findCandidateClassNames() {
        Set<String> classNames = new TreeSet<>();
        String packagePath = PLUGIN_PACKAGE.replace('.', '/');

        if (getClass().getProtectionDomain().getCodeSource() == null) {
            IJ.log("Help: no code source available to scan for plugins.");
            return classNames;
        }
        URL location = getClass().getProtectionDomain().getCodeSource().getLocation();

        try {
            Path root = Paths.get(location.toURI());
            if (Files.isDirectory(root)) {
                Path packageDir = root.resolve(packagePath);
                if (Files.isDirectory(packageDir)) {
                    try (Stream<Path> stream = Files.walk(packageDir)) {
                        stream
                            .filter(path -> path.toString().endsWith(".class"))
                            .map(path -> root.relativize(path).toString().replace(File.separatorChar, '/'))
                            .forEach(entry -> addClassName(classNames, entry));
                    }
                }
            } else {
                try (JarFile jar = new JarFile(root.toFile())) {
                    Enumeration<JarEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        String entry = entries.nextElement().getName();
                        if (entry.startsWith(packagePath) && entry.endsWith(".class")) {
                            addClassName(classNames, entry);
                        }
                    }
                }
            }
        } catch (Exception e) {
            IJ.log("Help: failed to scan for plugins - " + e);
        }
        return classNames;
    }

    private void addClassName(Set<String> classNames, String classEntry) {
        if (classEntry.contains("$")) {
            return;
        }
        classNames.add(classEntry.substring(0, classEntry.length() - ".class".length()).replace('/', '.'));
    }

    private void showHelp(List<DescribablePlugin> describables) {
        StringBuilder html = new StringBuilder();
        html.append("<html><head><style>")
            .append("body { font-family: sans-serif; }")
            .append("h1 { font-size: 15px; margin: 0 0 4px 0; }")
            .append("h2 { font-size: 13px; margin: 18px 0 6px 0; }")
            .append("p { margin: 0 0 10px 0; }")
            .append("ul { margin: 0 0 10px 0; }")
            .append("</style></head><body style='width: 620px'>")
            .append("<h1>UAB Fiji Plugins</h1>")
            .append("<p>The following plugins are available under the <b>UAB</b> menu.</p>");

        if (describables.isEmpty()) {
            html.append("<p>No plugin descriptions could be found.</p>");
        } else {
            for (DescribablePlugin plugin : describables) {
                html.append("<h2>").append(plugin.getPluginName()).append("</h2>")
                    .append(plugin.getDescription());
            }
        }
        html.append("</body></html>");

        JLabel content = new JLabel(html.toString());
        content.setVerticalAlignment(SwingConstants.TOP);
        content.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Assume the screen is wide enough to show the full content width; only ever scroll vertically.
        JScrollPane scrollPane = new JScrollPane(content,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setPreferredSize(new Dimension(scrollPane.getPreferredSize().width, 620));

        ImageIcon icon = new ImageIcon(Objects.requireNonNull(getClass().getResource("/new-uab-monogram.png")));
        JPanel iconPanel = new JPanel(new GridBagLayout());
        iconPanel.add(new JLabel(icon));

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(iconPanel, BorderLayout.WEST);
        panel.add(scrollPane, BorderLayout.CENTER);

        JOptionPane.showMessageDialog(null, panel, "UAB Plugins — Help", JOptionPane.PLAIN_MESSAGE);
    }
}
