package fiji.plugins.imageanalyzer;

import ij.IJ;
import ij.ImagePlus;
import ij.process.ImageProcessor;

public class Thresholds {
    public Threshold red;
    public Threshold green;
    public Threshold blue;

    public Thresholds(RGB rgb) {
        red = getThreshold(rgb.red);
        IJ.log("positive red threshold = " + red);

        green = getThreshold(rgb.green);
        IJ.log("positive green threshold = " + green);

        blue = getThreshold(rgb.blue);
        IJ.log("positive blue threshold = " + blue);
    }

    private Threshold getThreshold(ImagePlus image) {
        image.setAutoThreshold("Default dark");
        ImageProcessor imgProc = image.getProcessor();
        return new Threshold((long) imgProc.getMinThreshold(), (long) imgProc.getMaxThreshold());
    }
}
