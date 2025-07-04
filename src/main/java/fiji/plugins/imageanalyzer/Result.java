package fiji.plugins.imageanalyzer;

import ij.IJ;
import ij.ImagePlus;
import ij.measure.ResultsTable;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class Result {

    public String imageName;
    public String color;
    public long area;
    public double mean;
    public long min;
    public long max;
    public long integratedDensity;
    public Threshold positiveThreshold;
    public double negativeMean;

    public Result(String imageName, String color, ImagePlus image, ResultsTable rt, Threshold positiveThreshold, double negativeMean) {
        this.imageName = imageName;
        this.color = color;
        rt.reset();
        IJ.run(image, "Measure", "");
        area = (long) rt.getValue("Area", 0);
        mean = new BigDecimal(Double.toString(rt.getValue("Mean", 0))).setScale(3, RoundingMode.HALF_UP).doubleValue();
        min = (long) rt.getValue("Min", 0);
        max = (long) rt.getValue("Max", 0);
        integratedDensity = (long) rt.getValue("IntDen", 0);
        this.positiveThreshold = positiveThreshold;
        this.negativeMean = negativeMean;
    }

    public String toString() {
        return "Result[" +
                "imageName = " + imageName +
                ", color = " + color +
                ", area = " + area +
                ", mean = " + mean +
                ", min = " + min +
                ", max = " + max +
                ", integratedDensity = " + integratedDensity +
                ", positiveThreshold = " + positiveThreshold +
                ", negativeMean = " + negativeMean +
                "]";
    }

    public String toCsvEntry() {
        return
                imageName + "," +
                        color + "," +
                        area + "," +
                        mean + "," +
                        min + "," +
                        max + "," +
                        integratedDensity + "," +
                        positiveThreshold.min + "," +
                        positiveThreshold.max + "," +
                        negativeMean +
                        System.lineSeparator();
    }
}
