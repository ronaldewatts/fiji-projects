package edu.uab.fiji.plugins.help;

import ij.Menus;
import net.imagej.legacy.plugin.LegacyPostRefreshMenus;
import org.scijava.event.EventHandler;
import org.scijava.plugin.Plugin;
import org.scijava.ui.event.UIShownEvent;

import javax.swing.Timer;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Inserts a separator above {@code Help} in the {@code UAB} menu of Fiji's legacy ImageJ menu bar.
 *
 * <p>Fiji's legacy menu bridge ({@code net.imagej.legacy.IJ1Helper$IJ1MenuWrapper}) does <b>not</b> honor SciJava's
 * weight-difference separator rule — it only ever inserts a single divider at the boundary between built-in IJ1 items
 * and bridge-injected items, and only when the target menu already had items. The UAB menu is entirely
 * bridge-injected, so it never qualifies and no amount of {@code @Menu} weight tuning produces a mid-menu rule.</p>
 *
 * <p>We therefore insert the separator ourselves once the menu is built. Two triggers are wired up because neither is
 * guaranteed on its own:</p>
 * <ul>
 *   <li>{@link LegacyPostRefreshMenus#run()} — called when the legacy menu bar is (re)built, but on a clean launch the
 *       menus are often built before this path runs, so it may not fire at startup at all (it does fire on later
 *       rebuilds, e.g. after the Updater).</li>
 *   <li>{@link #onUIShown(UIShownEvent)} — fired when the UI window (and its menu bar) is shown; SciJava auto-subscribes
 *       {@code @EventHandler} methods on plugin instances it creates, so this fires reliably at startup.</li>
 * </ul>
 *
 * <p>The UAB items are bridged in by {@code IJ1Helper.addMenuItems()}, which is separate from both triggers, so the
 * {@code Help} leaf may not exist yet when a trigger fires. Each trigger therefore kicks an EDT poll
 * ({@link Timer}, ~250 ms, bounded) that retries until {@code Help} appears, inserts the separator, then stops. The
 * idempotency check (skip if the item above {@code Help} is already a {@code -} separator) keeps repeated triggers from
 * stacking dividers, and a single shared poll runs at a time.</p>
 */
@Plugin(type = LegacyPostRefreshMenus.class)
public class UABMenuSeparator implements LegacyPostRefreshMenus {

    /** AWT marks separator menu items with this label, so it doubles as the idempotency check. */
    private static final String SEPARATOR_LABEL = "-";

    /** Poll cadence and ceiling: ~10s total, enough for the SciJava commands to be bridged in after startup. */
    private static final int RETRY_INTERVAL_MS = 250;
    private static final int MAX_ATTEMPTS = 40;

    /** Ensures at most one poll loop runs at a time even when both triggers fire. */
    private final AtomicBoolean polling = new AtomicBoolean(false);

    @Override
    public void run() {
        scheduleInsertion();
    }

    /** Fired by SciJava when the UI (and its menu bar) is shown — a reliable startup trigger. */
    @EventHandler
    public void onUIShown(UIShownEvent event) {
        scheduleInsertion();
    }

    private void scheduleInsertion() {
        if (insertSeparatorIfReady()) {
            return;
        }
        // The UAB items may not be bridged into the menu yet; keep checking on the EDT until Help shows up.
        if (!polling.compareAndSet(false, true)) {
            return; // a poll loop is already running
        }
        int[] attempts = {0};
        Timer timer = new Timer(RETRY_INTERVAL_MS, null);
        timer.addActionListener(event -> {
            if (insertSeparatorIfReady() || ++attempts[0] >= MAX_ATTEMPTS) {
                timer.stop();
                polling.set(false);
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
