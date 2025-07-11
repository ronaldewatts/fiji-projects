package fiji.plugins.imageanalyzer;

public class ImageAnalyzerMain {
    public static void main(String[] args) {
        // To test, run this main and select the folder <project>/data/Cal3
        System.setProperty("ide", "true");
        new ImageAnalyzerPlugin().run();
    }
}
