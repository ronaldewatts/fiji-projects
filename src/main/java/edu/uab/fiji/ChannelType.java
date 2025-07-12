package edu.uab.fiji;

public enum ChannelType {
    CY5,
    GFP,
    DAPI,
    RFP,
    DISCARD;

    public static ChannelType fromString(String string) {
        for (ChannelType value : values()) {
            if (string.contains(value.toString())) {
                return value;
            }
        }
        return DISCARD;
    }
}
