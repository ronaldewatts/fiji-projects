package edu.uab.fiji.domain;

import edu.uab.fiji.service.ResultsTableService;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.ChannelSplitter;
import ij.process.ImageProcessor;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Image {
    private final String name;
    private final List<ImageChannel> imageChannels = new ArrayList<>();

    public Image(String location) {
        this(location, Map.of(), Map.of());
    }

    public Image(String location, Map<ChannelType, Threshold> positiveThresholdsMap, Map<ChannelType, BigDecimal> negativeMeansMap) {
        ImagePlus image = IJ.openImage(location);
        File imageFile = new File(location);
        this.name = image.getTitle().replace(".tif", "");
        String folder = imageFile.getParent();

        ImagePlus[] splitImages = ChannelSplitter.split(image);
        ImageStack stack = image.getStack();
        for (int i = 0; i < splitImages.length; i++) {
            ImagePlus imageChannel = splitImages[i];
            String shortLabel = stack.getShortSliceLabel(i + 1);
            ChannelType channelType = ChannelType.fromString(shortLabel);
            if (channelType == ChannelType.DISCARD) {
                IJ.log("Unknown channel type " + shortLabel + " for " + name);
            } else {
                Threshold positiveThreshold = positiveThresholdsMap.get(channelType);
                BigDecimal negativeMean = negativeMeansMap.get(channelType);
                if ((!positiveThresholdsMap.isEmpty() && positiveThreshold == null) || (!negativeMeansMap.isEmpty() && negativeMean == null)) {
                    if (positiveThreshold == null) {
                        IJ.log("No positive threshold found for " + channelType);
                    }
                    if (negativeMean == null) {
                        IJ.log("No negative mean found for " + channelType);
                    }
                } else {
                    this.imageChannels.add(new ImageChannel(
                            folder,
                            this.name,
                            imageChannel,
                            channelType,
                            positiveThreshold,
                            negativeMean)
                    );
                }
            }
        }
    }

    public Map<ChannelType, Threshold> getThresholds() {
        return imageChannels.stream().collect(Collectors.toMap(ImageChannel::channelType, imageChannel -> {
            ImagePlus imagePlus = imageChannel.imagePlus();
            imagePlus.setAutoThreshold("Default dark");
            ImageProcessor imgProc = imagePlus.getProcessor();
            return new Threshold((long) imgProc.getMinThreshold(), (long) imgProc.getMaxThreshold());
        }));
    }

    public Map<ChannelType, BigDecimal> getMeans() {
        return imageChannels.stream().collect(Collectors.toMap(ImageChannel::channelType, imageChannel -> {
            ImagePlus imagePlus = imageChannel.imagePlus();
            return ResultsTableService.INSTANCE.getMean(imagePlus);
        }));
    }

    public List<Measurement> measure() {
        List<Measurement> measurements = new ArrayList<>();
        if (!imageChannels.isEmpty()) {
            for (ImageChannel imageChannel : imageChannels) {
                measurements.add(imageChannel.measure());
            }
        }
        return measurements;
    }

    @Override
    public String toString() {
        return "Image{" +
                "name='" + name + '\'' +
                ", imageChannels=" + imageChannels +
                '}';
    }
}
