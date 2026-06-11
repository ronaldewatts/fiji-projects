package edu.uab.fiji.plugins.help;

import ij.Menus;
import net.imagej.legacy.plugin.LegacyPostRefreshMenus;
import org.scijava.plugin.Plugin;

import javax.swing.Timer;
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
 * LegacyPostRefreshMenus.class)} and calls {@link #run()} when the legacy menu bar is (re)built. But the UAB items are
 * bridged into the menu by {@code IJ1Helper.addMenuItems()}, which is <em>separate</em> from the menu-refresh that
 * triggers this hook and is not guaranteed to have run first — so on startup the {@code Help} leaf often does not exist
 * yet when {@code run()} first fires. Rather than rely on that ordering, we poll on the EDT until {@code Help} appears,
 * insert the separator directly above it, then stop. The check is idempotent so repeated refreshes never stack
 * dividers, and the poll self-terminates once done (or after a bounded number of attempts).</p>
 */
@Plugin(type = LegacyPostRefreshMenus.class)
public class UABMenuSeparator implements LegacyPostRefreshMenus {

    /** AWT marks separator menu items with this label, so it doubles as the idempotency check. */
    private static final String SEPARATOR_LABEL = "-";

    /** Poll cadence and ceiling: ~10s total, enough for the SciJava commands to be bridged in after startup. */
    private static final int RETRY_INTERVAL_MS = 250;
    private static final int MAX_ATTEMPTS = 40;

    @Override
    public void run() {
        if (insertSeparatorIfReady()) {
            return;
        }

        // The UAB items may not be bridged into the menu yet; keep checking on the EDT until Help shows up.
        int[] attempts = {0};
        Timer timer = new Timer(RETRY_INTERVAL_MS, null);
        timer.addActionListener(event -> {
            if (insertSeparatorIfReady() || ++attempts[0] >= MAX_ATTEMPTS) {
                timer.stop();
            }
        });
        timer.start();
    }

    /**
     * @return {@code true} once the separator is in place (already present or just inserted), or {@code false} if the
     *     UAB menu or its {@code Help} item does not exist yet and we should try again later.
     */
    private boolean insertSeparatorIfReady() {
        MenuBar menuBar = Menus.getMenuBar();
        if (menuBar == null) {
            return false;
        }
        for (int i = 0; i < menuBar.getMenuCount(); i++) {
            Menu menu = menuBar.getMenu(i);
            if ("UAB".equals(menu.getLabel())) {
                return ensureSeparatorBeforeHelp(menu);
            }
        }
        return false;
    }

    private boolean ensureSeparatorBeforeHelp(Menu uabMenu) {
        for (int i = 0; i < uabMenu.getItemCount(); i++) {
            MenuItem item = uabMenu.getItem(i);
            if ("Help".equals(item.getLabel())) {
                // Already separated (e.g. a prior pass inserted it) — leave it alone so dividers never stack.
                if (i > 0 && SEPARATOR_LABEL.equals(uabMenu.getItem(i - 1).getLabel())) {
                    return true;
                }
                uabMenu.insertSeparator(i);
                return true;
            }
        }
        return false;
    }
}
