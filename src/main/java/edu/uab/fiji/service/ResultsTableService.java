package edu.uab.fiji.service;

import ij.IJ;
import ij.ImagePlus;
import ij.measure.ResultsTable;
import ij.plugin.filter.Analyzer;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class ResultsTableService {

    private static final String FULL_MEASUREMENTS = "area mean min integrated display redirect=None decimal=3";

    public static final ResultsTableService INSTANCE = new ResultsTableService();
    private final ResultsTable resultsTable;

    private ResultsTableService() {
        resultsTable = Analyzer.getResultsTable();
        // The measurement set never changes during a run, so configure it once here rather than on every measurement.
        IJ.run("Set Measurements...", FULL_MEASUREMENTS);
        resultsTable.reset();
    }

    public void reset() {
        resultsTable.reset();
    }

    public BigDecimal getMean(ImagePlus imagePlus) {
        IJ.run("Set Measurements...", "mean redirect=None decimal=3");
        IJ.run(imagePlus, "Measure", "");

        BigDecimal mean = BigDecimal.valueOf(resultsTable.getValue("Mean", 0)).setScale(3, RoundingMode.HALF_UP);

        // Restore the full measurement set for subsequent measure() calls.
        IJ.run("Set Measurements...", FULL_MEASUREMENTS);
        reset();

        return mean;
    }

    public ResultsTable measure(ImagePlus imagePlus) {
        reset();
        IJ.run(imagePlus, "Measure", "");
        return resultsTable;
    }

    public ResultsTable measure(ImagePlus imagePlus, boolean reset) {
        if (reset) {
            reset();
        }
        IJ.run(imagePlus, "Measure", "");
        return resultsTable;
    }
}
