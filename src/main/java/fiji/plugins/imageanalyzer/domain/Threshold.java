package fiji.plugins.imageanalyzer.domain;

import lombok.Data;

@Data
public class Threshold {
    private final long min;
    private final long max;
}
