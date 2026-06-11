package edu.uab.fiji.plugins;

/**
 * Implemented by plugins that expose a human-readable, HTML-formatted description of what they do. The description is
 * the single source of truth for both the plugin's own startup splash screen and the aggregated {@code Help} dialog,
 * so it lives here rather than being duplicated as inline label text.
 *
 * <p>{@link #getDescription()} returns only the HTML <em>fragment</em> (the {@code <p>}/{@code <ul>} blocks) — no
 * {@code <html>}/{@code <head>}/{@code <body>} wrapper — so it can be wrapped for a single splash via
 * {@link #toSplashHtml(String)} or concatenated under a heading in the Help dialog.</p>
 */
public interface DescribablePlugin {

    /** The plugin's display name, matching its {@code @Plugin(name = …)} / {@code UAB} menu entry. */
    String getPluginName();

    /**
     * The plugin's description as an HTML fragment: a sequence of block elements ({@code <p>}, {@code <ul>}, …) using
     * {@code <code>} for filenames/patterns and {@code <b>} for emphasis. No outer {@code <html>} wrapper.
     */
    String getDescription();

    /**
     * Wraps a {@link #getDescription()} fragment in the shared splash-screen chrome — a fixed width to force wrapping
     * and a stylesheet giving paragraphs/lists their whitespace. Used by each plugin's {@code showStartupMessage()}.
     */
    static String toSplashHtml(String descriptionFragment) {
        return "<html><head><style>"
            + "body { font-family: sans-serif; } p { margin: 0 0 12px 0; } ul { margin: 0 0 12px 0; }"
            + "</style></head><body style='width: 560px'>"
            + descriptionFragment
            + "</body></html>";
    }
}
