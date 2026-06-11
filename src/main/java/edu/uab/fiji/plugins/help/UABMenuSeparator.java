package edu.uab.fiji.plugins.help;

import ij.IJ;
import ij.Menus;
import net.imagej.legacy.plugin.LegacyPostRefreshMenus;
import org.scijava.plugin.Plugin;

import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;

/**
 * Inserts a separator above {@code Help} in the {@code UAB} menu of Fiji's legacy ImageJ menu bar.
 *
 * <p>Fiji's legacy menu bridge ({@code net.imagej.legacy.IJ1Helper$IJ1MenuWrapper}) does <b>not</b> honor SciJava's
 * weight-difference separator rule — it only ever inserts a single divider at the boundary between built-in IJ1 items
 * and bridge-injected items, and only when the target menu already had items. The UAB menu is entirely
 * bridge-injected, so it never qualifies and no amount of {@code @Menu} weight tuning produces a mid-menu rule.</p>
 *
 * <p>{@link LegacyPostRefreshMenus} is the supported hook: Fiji discovers every {@code @Plugin(type =
 * LegacyPostRefreshMenus.class)} and calls {@link #run()} after the legacy menu bar is (re)built — at startup and after
 * every refresh (e.g. following an update). We grab the live AWT menu bar, find the UAB menu and the Help leaf, and
 * insert the separator directly above it. The operation is idempotent so repeated refreshes never stack dividers.</p>
 */
@Plugin(type = LegacyPostRefreshMenus.class)
public class UABMenuSeparator implements LegacyPostRefreshMenus {

    /** AWT marks separator menu items with this label, so it doubles as the idempotency check. */
    private static final String SEPARATOR_LABEL = "-";

    @Override
    public void run() {
        MenuBar menuBar = Menus.getMenuBar();
        if (menuBar == null) {
            return;
        }
        for (int i = 0; i < menuBar.getMenuCount(); i++) {
            Menu menu = menuBar.getMenu(i);
            if ("UAB".equals(menu.getLabel())) {
                insertSeparatorBeforeHelp(menu);
                return;
            }
        }
    }

    private void insertSeparatorBeforeHelp(Menu uabMenu) {
        for (int i = 0; i < uabMenu.getItemCount(); i++) {
            MenuItem item = uabMenu.getItem(i);
            if ("Help".equals(item.getLabel())) {
                // Already separated (e.g. a prior refresh inserted it) — leave it alone so dividers never stack.
                if (i > 0 && SEPARATOR_LABEL.equals(uabMenu.getItem(i - 1).getLabel())) {
                    return;
                }
                uabMenu.insertSeparator(i);
                IJ.log("UAB: inserted separator above Help.");
                return;
            }
        }
    }
}
