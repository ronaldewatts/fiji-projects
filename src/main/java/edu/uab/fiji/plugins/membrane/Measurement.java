package edu.uab.fiji.plugins.membrane;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Measurement(String sex, String treatment, String mouseNumber, String stain, String imageNumber, long area, BigDecimal mean, long min, long max, long integratedDensity) {

    public static String toCsvHeader() {
        return "Sex," +
            "Treatment," +
            "Mouse #," +
            "Stain," +
            "Image Number," +
            "Area," +
            "Mean," +
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
            area + "," +
            mean + "," +
            min + "," +
            max + "," +
            integratedDensity + "," +
            new BigDecimal(integratedDensity).divide(new BigDecimal(area), 6, RoundingMode.HALF_UP) +
            System.lineSeparator();
    }
}
