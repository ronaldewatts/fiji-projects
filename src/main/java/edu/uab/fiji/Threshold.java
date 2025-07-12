package edu.uab.fiji;

public record Threshold(long min, long max) {

    @Override
    public String toString() {
        return "Threshold{" +
                "min=" + min +
                ", max=" + max +
                '}';
    }
}
