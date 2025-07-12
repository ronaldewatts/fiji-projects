package fiji.plugins.imageanalyzer.domain;

import fiji.plugins.imageanalyzer.service.ResultsTableService;
import ij.IJ;
import ij.ImagePlus;
import ij.measure.ResultsTable;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Data
public class ImageChannel {
    private final String name;
    private final ImagePlus imagePlus;
    private final ChannelType channelType;
    private final Threshold positiveThreshold;
    private final BigDecimal negativeMean;

    public ImageChannel(String name, ImagePlus imagePlus, ChannelType channelType, Threshold positiveThreshold, BigDecimal negativeMean) {
        this.name = name;
        this.imagePlus = imagePlus;
        this.channelType = channelType;
        this.positiveThreshold = positiveThreshold;
        this.negativeMean = negativeMean;
    }

    public Measurement measure() {
        IJ.setRawThreshold(imagePlus, positiveThreshold.getMin(), positiveThreshold.getMax());
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
