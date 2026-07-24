package edu.uab.fiji.plugins.wgamask;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Measurement(String sex, String treatment, String mouseNumber, String stain, String imageNumber, boolean limitToThreshold, long area, BigDecimal mean, BigDecimal stdDev, BigDecimal median, long min, long max, long integratedDensity) {

    public static String toCsvHeader() {
        return "Sex," +
            "Treatment," +
            "Mouse #," +
            "Stain," +
            "Image Number," +
            "Limit to Threshold," +
            "Area," +
            "Mean," +
            "StdDev," +
            "Median," +
            "Min," +
            "Max," +
            "Integrated Density," +
            "Integrated Density/Area" +
            System.lineSeparator();
    }

    public String toCsvEntry() {
        return sex + "," +
            treatment + "," +
            mouseNumber + "," +
            stain + "," +
            imageNumber + "," +
            limitToThreshold + "," +
            area + "," +
            mean + "," +
            stdDev + "," +
            median + "," +
            min + "," +
            max + "," +
            integratedDensity + "," +
            (area == 0
                ? "AREA ZERO - ERROR"
                : new BigDecimal(integratedDensity).divide(new BigDecimal(area), 6, RoundingMode.HALF_UP)) +
            System.lineSeparator();
    }
}
