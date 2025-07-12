package edu.uab.fiji;

import edu.uab.fiji.service.ResultsTableService;
import ij.IJ;
import ij.ImagePlus;
import ij.measure.ResultsTable;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record ImageChannel(String name, ImagePlus imagePlus, ChannelType channelType, Threshold positiveThreshold,
                           BigDecimal negativeMean) {

    public Measurement measure() {
        IJ.setRawThreshold(imagePlus, positiveThreshold.min(), positiveThreshold.max());
        IJ.run(imagePlus, "Subtract...", "value=" + negativeMean);
        ResultsTable resultsTable = ResultsTableService.INSTANCE.measure(imagePlus);

        long area = (long) resultsTable.getValue("Area", 0);
        BigDecimal mean = new BigDecimal(Double.toString(resultsTable.getValue("Mean", 0)))
                .setScale(3, RoundingMode.HALF_UP);
        long min = (long) resultsTable.getValue("Min", 0);
        long max = (long) resultsTable.getValue("Max", 0);
        long integratedDensity = (long) resultsTable.getValue("IntDen", 0);

        return new Measurement(this, area, mean, min, max, integratedDensity);
    }


}
