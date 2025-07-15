package edu.uab.fiji;

import edu.uab.fiji.plugins.FluorescenceIntensityPlugin;

public class MainRunner {
    public static void main(String[] args) {
        System.setProperty("ide", "true");

        // To test, run this main and select a top level directory from <project>/data
        new FluorescenceIntensityPlugin().run();
    }
}
