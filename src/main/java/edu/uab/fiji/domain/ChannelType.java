package edu.uab.fiji.domain;

public enum ChannelType {
    CY5,
    GFP,
    DAPI,
    RFP,
    DISCARD;

    public static ChannelType fromString(String sliceLabel) {
        if (sliceLabel != null) {
            for (ChannelType value : values()) {
                if (sliceLabel.contains(value.toString())) {
                    return value;
                }
            }
        }
        return DISCARD;
    }
}
