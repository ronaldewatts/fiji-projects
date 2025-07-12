# UAB Fiji Plugins

These plugins are designed to simplify tasks traditionally done manually within
the [Fiji](https://imagej.net/software/fiji/)
system. The plugin JAR can be built with the following command:

```shell
mvn install
```

The resulting JAR file will be the `{project_root}/target` directory named `fiji-plugins-{version}.jar`.
The plugin can then be installed Fiji via `Plugins > Install...` and restarting. The Plugins will be under the `UAB`
main menu folder.

## Available Plugins

### Fluorescence Intensity

This plugin analyzes images by measuring the individual color channels while applying the threshold from a
`Positive Control.tif` image and the mean from a `Negative Control.tif` image. It writes a report called
`FluorescenceIntensity_{Root Directory}_{Timestamp}.csv` in the root directory that was processed.