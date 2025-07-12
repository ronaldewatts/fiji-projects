package fiji.plugins.imageanalyzer.service;

import ij.IJ;
import ij.ImagePlus;
import ij.measure.ResultsTable;
import ij.plugin.filter.Analyzer;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class ResultsTableService {

    private final ResultsTable resultsTable;

    public static final ResultsTableService INSTANCE = new ResultsTableService();

    private ResultsTableService() {
        resultsTable = Analyzer.getResultsTable();
        reset();
    }

    public void reset() {
        IJ.run("Set Measurements...", "area mean min integrated redirect=None decimal=3");
        resultsTable.reset();
    }

    public BigDecimal getMean(ImagePlus imagePlus) {
        IJ.run("Set Measurements...", "mean redirect=None decimal=3");
        IJ.run(imagePlus, "Measure", "");

        BigDecimal mean = new BigDecimal(Double.toString(resultsTable.getValue("Mean", 0)))
                .setScale(3, RoundingMode.HALF_UP);

        reset();

        return mean;
    }

    public ResultsTable measure(ImagePlus imagePlus) {
        reset();
        IJ.run(imagePlus, "Measure", "");
        return resultsTable;
    }
}
