package fiji.plugins.imageanalyzer;

public class Threshold {
    public long min;
    public long max;

    public Threshold(long min, long max) {
        this.min = min;
        this.max = max;
    }

    public String toString() {
        return "Threshold[min = " + min + ", max = " + max + "]";
    }
}
