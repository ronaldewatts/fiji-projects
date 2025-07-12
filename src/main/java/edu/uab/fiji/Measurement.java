package edu.uab.fiji;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Measurement(ImageChannel imageChannel, long area, BigDecimal mean, long min, long max,
                          long integratedDensity) {

    public String toCsvEntry() {
        return imageChannel.name() + "," +
                imageChannel.channelType() + "," +
                area + "," +
                mean + "," +
                min + "," +
                max + "," +
                integratedDensity + "," +
                new BigDecimal(integratedDensity).divide(new BigDecimal(area), 6, RoundingMode.HALF_UP) + "," +
                imageChannel.positiveThreshold().min() + "," +
                imageChannel.positiveThreshold().max() + "," +
                imageChannel.negativeMean() +
                System.lineSeparator();
    }

    public static String toCsvHeader() {
        return "Image Name," +
                "Color," +
                "Area," +
                "Mean," +
                "Min," +
                "Max," +
                "Integrated Density," +
                "Integrated Density/Area," +
                "Positive Threshold Min," +
                "Positive Threshold Max," +
                "Negative Mean" +
                System.lineSeparator();
    }
}
