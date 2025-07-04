package fiji.plugins.imageanalyzer;

public class ImageAnalyzerMain {
    public static void main(String[] args) {
        System.setProperty("ide", "true");
        new ImageAnalyzerPlugin().run();
    }
}
