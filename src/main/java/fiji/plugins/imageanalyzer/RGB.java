package fiji.plugins.imageanalyzer;

import ij.IJ;
import ij.ImagePlus;
import ij.plugin.ChannelSplitter;

import java.io.File;

public class RGB {
    public String imageName;
    public ImagePlus red;
    public ImagePlus green;
    public ImagePlus blue;

    public RGB(String image) {
        File imageFile = new File(image);
        imageName = imageFile.getName();
        ImagePlus[] imageChannels = ChannelSplitter.split(IJ.openImage(image));
        red = imageChannels[0];
        green = imageChannels[1];
        blue = imageChannels[2];
    }

    public void setThresholds(Thresholds thresholds) {
        IJ.setRawThreshold(red, thresholds.red.min, thresholds.red.max);
        IJ.setRawThreshold(green, thresholds.green.min, thresholds.green.max);
        IJ.setRawThreshold(blue, thresholds.blue.min, thresholds.blue.max);
    }

    public void subtract(double redValue, double greenValue, double blueValue) {
        IJ.run(red, "Subtract...", "value=" + redValue);
        IJ.run(green, "Subtract...", "value=" + greenValue);
        IJ.run(blue, "Subtract...", "value=" + blueValue);
    }

    public String toString() {
        return "RGB[imageName = " + imageName + "]";
    }
}
