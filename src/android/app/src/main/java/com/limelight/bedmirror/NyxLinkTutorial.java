package com.limelight.bedmirror;

import android.app.Activity;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;

/** Visual, diagram-driven interactive tutorial and Tips & Tricks system for NyxLink. */
public final class NyxLinkTutorial {
    private static final String PREF_OPT_OUT = "tutorialOptOut";
    private static final String PREF_COMPLETED = "tutorialCompleted";
    private static Dialog activeDialog;
    private static boolean shownThisSession;

    private record BadgeRow(String pill, String text) {}
    private record Topic(int id, String title, String tagline, BadgeRow[] rows, String quickTip) {}
    private record TipCard(int iconType, String badge, String title, String shortText) {}

    private static final Topic[] TOPICS = {
        new Topic(1, "Connect to Your PC", "Stream your Windows desktop over local Wi-Fi.",
            new BadgeRow[]{
                new BadgeRow("1 · PC App", "Open NyxLink on Windows"),
                new BadgeRow("2 · Same Wi-Fi", "Connect phone & PC to same LAN"),
                new BadgeRow("3 · Refresh", "Tap Refresh nearby PCs to scan")
            }, "Local LAN only — no account or cloud needed."),
        new Topic(2, "Pairing & Reconnecting", "One-time 5-digit code; instant reconnects after.",
            new BadgeRow[]{
                new BadgeRow("Pair", "Enter the 5-digit code shown on PC"),
                new BadgeRow("Connect", "Saved PCs connect in one tap"),
                new BadgeRow("Forget", "Remove saved pairing anytime")
            }, "Tap Reconnect in-stream if your Wi-Fi blips."),
        new Topic(3, "Keyboard Controls", "Switch instantly between PC keys and Android typing.",
            new BadgeRow[]{
                new BadgeRow("PC Keys", "Esc, F1–F12, Ctrl/Alt/Win, Arrows"),
                new BadgeRow("Android", "Native keyboard, swipe & voice"),
                new BadgeRow("Hide", "Close keyboard & view desktop")
            }, "All PC keys fit on screen with zero horizontal scrolling."),
        new Topic(4, "Touch & Mouse", "Use direct screen taps or relative trackpad motion.",
            new BadgeRow[]{
                new BadgeRow("1-Finger Tap", "Left-click · Long-press to drag"),
                new BadgeRow("2-Finger Tap", "Right-click · 2-finger drag scrolls"),
                new BadgeRow("Touch Mode", "Toggle Direct Touch ⇄ Trackpad")
            }, "Double-tap anywhere to hide or show the top toolbar."),
        new Topic(5, "Touch Lock & Zoom", "Lock remote clicks while freely zooming & panning.",
            new BadgeRow[]{
                new BadgeRow("Touch Lock", "Blocks accidental single-finger clicks"),
                new BadgeRow("2-Finger Pinch", "Zoom 1×–4× & drag to pan locally"),
                new BadgeRow("Hold Unlock", "Long-press corner button to unlock")
            }, "GPU scaling keeps the zoomed video sharp."),
        new Topic(6, "Monitor Selection", "Switch between physical PC displays on the fly.",
            new BadgeRow[]{
                new BadgeRow("Monitor", "Lists all active displays & resolutions"),
                new BadgeRow("Live Switch", "Switches in ~1s without disconnecting"),
                new BadgeRow("Remembered", "Restores your chosen display next time")
            }, "Your PC's monitor arrangement is never altered."),
        new Topic(7, "Fullscreen Mode", "Expand the stream edge-to-edge on your phone.",
            new BadgeRow[]{
                new BadgeRow("Fullscreen", "Hides Android status & nav bars"),
                new BadgeRow("Double-Tap", "Hides the top toolbar for a clean view")
            }, "Fullscreen preference is saved automatically."),
        new Topic(8, "Screen Rotation", "Lock orientation or follow your phone sensor.",
            new BadgeRow[]{
                new BadgeRow("Auto Rotate", "Follows phone orientation"),
                new BadgeRow("Lock Portrait", "Keeps vertical layout in bed"),
                new BadgeRow("Lock Landscape", "Maximizes wide desktop view")
            }, "The PC Keys panel adapts automatically to both orientations."),
        new Topic(9, "Quick Actions Menu", "One-tap Windows commands and custom shortcuts.",
            new BadgeRow[]{
                new BadgeRow("Built-In", "Alt+Tab, F11, Win+D, Play/Pause, Lock"),
                new BadgeRow("Clipboard", "Copy, Paste & Paste phone clipboard"),
                new BadgeRow("Custom 1–3", "Save your own keybinds & Discord mute")
            }, "Paste phone clipboard types copied phone text straight into Windows."),
        new Topic(10, "Privacy Pause", "Stop PC capture and blank the screen in one tap.",
            new BadgeRow[]{
                new BadgeRow("Tap Privacy", "Stops host video/audio & blacks screen"),
                new BadgeRow("Tap Again", "Resumes stream without re-pairing")
            }, "Stops the Windows encoder itself, not just the phone display."),
        new Topic(11, "Options & Settings", "Fine-tune quality, audio, brightness, and power.",
            new BadgeRow[]{
                new BadgeRow("Quality", "720p/1080p at 30/60 FPS on Home screen"),
                new BadgeRow("Audio & Dim", "PC/phone volume, mute & extra-low dim"),
                new BadgeRow("Helpers", "Locate cursor, Reset zoom, View Only, PiP")
            }, "Turn on Pin controls in Options to keep the toolbar always visible."),
        new Topic(12, "Disconnecting Safely", "Close your stream cleanly when finished.",
            new BadgeRow[]{
                new BadgeRow("Disconnect", "Releases held keys & mouse buttons"),
                new BadgeRow("Saved PC", "Keeps pairing ready for next session")
            }, "Use Actions → Lock Windows before disconnecting if away from your desk."),
        new Topic(13, "Keyboard Shortcuts", "Combine latched modifiers with any key.",
            new BadgeRow[]{
                new BadgeRow("1 · Latch", "Tap Ctrl, Alt, Win, or Shift (glows active)"),
                new BadgeRow("2 · Press Key", "Tap Tab / Esc or type C / V / S in Android"),
                new BadgeRow("Examples", "Ctrl+C, Ctrl+V, Alt+Tab, Win+Shift+S")
            }, "Modifiers clear automatically when you tap Hide Keyboard."),
        new Topic(14, "Troubleshooting", "Fast fixes for common connection or input hiccups.",
            new BadgeRow[]{
                new BadgeRow("No PC Found", "Check PC app is open & on same Wi-Fi"),
                new BadgeRow("Input Blocked", "Check Touch Lock or Options → View Only"),
                new BadgeRow("Lost Cursor", "Tap Options → Locate cursor or Reset zoom")
            }, "If a PC pairing was revoked, tap Forget device and pair again."),
        new Topic(15, "Best Experience Tips", "Handy features for everyday comfort.",
            new BadgeRow[]{
                new BadgeRow("Bed Viewing", "Touch Lock + Pinch Zoom + Low Brightness"),
                new BadgeRow("Multitask", "Options → Picture in picture (PiP)"),
                new BadgeRow("Vol Keys", "Options → Long-press volume controls PC")
            }, "Open the Tips & Tricks tab above anytime for visual quick cards.")
    };

    private static final TipCard[] TIPS_AND_TRICKS = {
        new TipCard(5, "Touch Lock + Zoom", "Bedside Viewing", "Lock single-finger clicks, pinch-zoom into videos, and dim brightness in Options."),
        new TipCard(13, "Ctrl / Win + Key", "Latch Modifiers", "Tap Ctrl, Alt, Win, or Shift in PC Keys, then press any key or type on Android."),
        new TipCard(9, "Actions Menu", "Paste Phone Text", "Use Actions → Paste phone clipboard to send copied phone text to Windows."),
        new TipCard(4, "Touch Mode", "Direct vs. Trackpad", "Use Direct Touch for quick taps, or Trackpad mode for tiny 4K UI targets."),
        new TipCard(11, "Options", "Locate Cursor", "Flashes a cyan ring around the Windows pointer or tells you if it's on another screen."),
        new TipCard(7, "Options → PiP", "Picture-in-Picture", "Keep a floating mini desktop window open while using other Android apps."),
        new TipCard(15, "Hardware Keys", "Volume Long-Press", "Hold phone Volume Up/Down to adjust Windows volume; tap for phone volume."),
        new TipCard(1, "Auto Quality", "Smooth Wi-Fi", "Enable Automatic stream quality + 720p/30 Battery mode for long sessions.")
    };

    private NyxLinkTutorial() {}

    private static int dp(Activity activity, float value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }

    /** Creates the subtle Help & Tips button for the bottom of the Home screen. */
    public static Button createHomeHelpButton(Activity activity, SharedPreferences prefs) {
        Button b = new Button(activity) {
            private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF r = new RectF();
            @Override protected void onDraw(Canvas canvas) {
                float inset = dp(activity, 1.5f), rad = dp(activity, 12f);
                r.set(inset, inset, getWidth() - inset, getHeight() - inset);
                boolean pressed = isPressed();
                p.setStyle(Paint.Style.FILL);
                p.setShader(new LinearGradient(0, r.top, 0, r.bottom,
                        pressed ? 0xFF1F1642 : 0xCC0A1026,
                        pressed ? 0xFF121836 : 0xCC070B1C, Shader.TileMode.CLAMP));
                canvas.drawRoundRect(r, rad, rad, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dp(activity, pressed ? 1.5f : 1f));
                p.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                        pressed ? 0xFF00E5FF : 0x9936D8FF,
                        pressed ? 0xFFD55CFF : 0x999B4DFF, Shader.TileMode.CLAMP));
                canvas.drawRoundRect(r, rad, rad, p);
                super.onDraw(canvas);
            }
        };
        b.setBackground(null);
        b.setAllCaps(false);
        b.setTextColor(0xFFD8ECFF);
        b.setTextSize(13.5f);
        b.setText("?   Help & Tips");
        b.setContentDescription("Help & Tips");
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(activity, 20), dp(activity, 10), dp(activity, 20), dp(activity, 10));
        b.setOnClickListener(v -> showMainTutorial(activity, prefs, 0, false));
        return b;
    }

    /** Automatically triggers the welcome tutorial introduction on launch unless opted out or completed. */
    public static void maybeShowFirstLaunch(Activity activity, SharedPreferences prefs) {
        if (shownThisSession) return;
        if (prefs.getBoolean(PREF_OPT_OUT, false) || prefs.getBoolean(PREF_COMPLETED, false)) return;
        shownThisSession = true;
        activity.getWindow().getDecorView().post(() -> {
            if (!activity.isFinishing() && !activity.isDestroyed()) {
                showWelcomeDialog(activity, prefs);
            }
        });
    }

    /** Shows a visual, low-text Welcome Dialog on first launch. */
    public static void showWelcomeDialog(Activity activity, SharedPreferences prefs) {
        dismissActive();
        Dialog dialog = createBaseDialog(activity);
        activeDialog = dialog;

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        LinearLayout card = createOuterCard(activity);
        scroll.addView(card);

        TextView title = new TextView(activity);
        title.setText("☾  Welcome to NyxLink");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        card.addView(title);

        TextView sub = new TextView(activity);
        sub.setText("Stream & control your Windows PC from your phone.");
        sub.setTextColor(0xFF98B4E6);
        sub.setTextSize(13f);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(activity, 2), 0, dp(activity, 10));
        card.addView(sub);

        // Visual Hero Diagram (PC <-> Wi-Fi <-> Phone)
        View hero = createIllustrationView(activity, 0);
        LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(-1, dp(activity, 108));
        heroLp.setMargins(0, 0, 0, dp(activity, 10));
        card.addView(hero, heroLp);

        // 3 compact visual step pills
        LinearLayout stepsBox = createInnerCard(activity, false);
        stepsBox.addView(createBadgeRowView(activity, new BadgeRow("1 · Open PC", "Run NyxLink on Windows (same Wi-Fi)")));
        stepsBox.addView(createBadgeRowView(activity, new BadgeRow("2 · Pair", "Enter the 5-digit code shown on PC")));
        stepsBox.addView(createBadgeRowView(activity, new BadgeRow("3 · Control", "Use PC Keys, Touch, Zoom & Monitors")));
        card.addView(stepsBox, new LinearLayout.LayoutParams(-1, -2));

        CheckBox dontShowAgain = new CheckBox(activity);
        dontShowAgain.setText("Don't show this automatically again");
        dontShowAgain.setTextColor(0xFFD6E4FF);
        dontShowAgain.setTextSize(13f);
        dontShowAgain.setChecked(prefs.getBoolean(PREF_OPT_OUT, false));
        LinearLayout.LayoutParams cbLp = new LinearLayout.LayoutParams(-1, -2);
        cbLp.setMargins(dp(activity, 2), dp(activity, 8), dp(activity, 2), dp(activity, 8));
        card.addView(dontShowAgain, cbLp);

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button skipBtn = createStyledButton(activity, "Skip Tutorial", false, () -> {
            if (dontShowAgain.isChecked()) prefs.edit().putBoolean(PREF_OPT_OUT, true).apply();
            dialog.dismiss();
        });
        Button startBtn = createStyledButton(activity, "Start Tutorial", true, () -> {
            if (dontShowAgain.isChecked()) prefs.edit().putBoolean(PREF_OPT_OUT, true).apply();
            dialog.dismiss();
            showMainTutorial(activity, prefs, 0, false);
        });
        LinearLayout.LayoutParams b1 = new LinearLayout.LayoutParams(0, dp(activity, 42), 1f);
        b1.setMargins(0, 0, dp(activity, 5), 0);
        LinearLayout.LayoutParams b2 = new LinearLayout.LayoutParams(0, dp(activity, 42), 1f);
        b2.setMargins(dp(activity, 5), 0, 0, 0);
        actions.addView(skipBtn, b1);
        actions.addView(startBtn, b2);
        card.addView(actions);

        dialog.setContentView(scroll);
        configureDialogWindow(activity, dialog);
        dialog.show();
    }

    /** Opens the visual Guided Tutorial & Tips dialog. */
    public static void showMainTutorial(Activity activity, SharedPreferences prefs, int initialIndex, boolean startInTips) {
        dismissActive();
        Dialog dialog = createBaseDialog(activity);
        activeDialog = dialog;

        final int[] currentStep = { Math.max(0, Math.min(TOPICS.length - 1, initialIndex)) };
        final boolean[] tipsMode = { startInTips };

        LinearLayout outer = createOuterCard(activity);
        outer.setLayoutParams(new FrameLayout.LayoutParams(-1, -2));

        // Top Bar
        LinearLayout topBar = new LinearLayout(activity);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);

        TextView headerTitle = new TextView(activity);
        headerTitle.setText("☾  Help & Visual Guide");
        headerTitle.setTextColor(Color.WHITE);
        headerTitle.setTextSize(16.5f);
        headerTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        topBar.addView(headerTitle, new LinearLayout.LayoutParams(0, -2, 1f));

        Button topicsMenuBtn = createSmallPillButton(activity, "Topics ▾", null);
        LinearLayout.LayoutParams tmLp = new LinearLayout.LayoutParams(-2, dp(activity, 30));
        tmLp.setMargins(0, 0, dp(activity, 6), 0);
        topBar.addView(topicsMenuBtn, tmLp);

        Button closeBtn = createSmallPillButton(activity, "✕", dialog::dismiss);
        closeBtn.setContentDescription("Close");
        topBar.addView(closeBtn, new LinearLayout.LayoutParams(dp(activity, 34), dp(activity, 30)));
        outer.addView(topBar);

        // Mode Switcher Tabs
        LinearLayout tabRow = new LinearLayout(activity);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams tabRowLp = new LinearLayout.LayoutParams(-1, -2);
        tabRowLp.setMargins(0, dp(activity, 8), 0, dp(activity, 6));
        outer.addView(tabRow, tabRowLp);

        final Runnable[] renderRef = new Runnable[1];
        Button tabTutorial = createTabButton(activity, "Visual Guide (15)", () -> {
            tipsMode[0] = false;
            renderRef[0].run();
        }, () -> !tipsMode[0]);
        Button tabTips = createTabButton(activity, "Tips & Tricks", () -> {
            tipsMode[0] = true;
            renderRef[0].run();
        }, () -> tipsMode[0]);

        LinearLayout.LayoutParams t1Lp = new LinearLayout.LayoutParams(0, dp(activity, 34), 1f);
        t1Lp.setMargins(0, 0, dp(activity, 4), 0);
        LinearLayout.LayoutParams t2Lp = new LinearLayout.LayoutParams(0, dp(activity, 34), 1f);
        t2Lp.setMargins(dp(activity, 4), 0, 0, 0);
        tabRow.addView(tabTutorial, t1Lp);
        tabRow.addView(tabTips, t2Lp);

        // Segmented Dot Progress Indicator
        LinearLayout progressSection = new LinearLayout(activity);
        progressSection.setOrientation(LinearLayout.VERTICAL);
        outer.addView(progressSection, new LinearLayout.LayoutParams(-1, -2));

        View segmentedBar = new View(activity) {
            private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF r = new RectF();
            @Override protected void onDraw(Canvas canvas) {
                int n = TOPICS.length;
                float gap = dp(activity, 3f);
                float segW = (getWidth() - gap * (n - 1)) / n;
                float h = getHeight(), rad = h / 2f;
                for (int i = 0; i < n; i++) {
                    float l = i * (segW + gap);
                    r.set(l, 0, l + segW, h);
                    if (i < currentStep[0]) {
                        p.setColor(0xFF00C8FF);
                    } else if (i == currentStep[0]) {
                        p.setColor(0xFFD55CFF);
                    } else {
                        p.setColor(0xFF1C264A);
                    }
                    canvas.drawRoundRect(r, rad, rad, p);
                }
            }
        };
        LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(-1, dp(activity, 5));
        sbLp.setMargins(0, dp(activity, 2), 0, dp(activity, 6));
        progressSection.addView(segmentedBar, sbLp);

        // Scrollable Content Area
        ScrollView contentScroll = new ScrollView(activity);
        contentScroll.setFillViewport(true);
        LinearLayout contentContainer = new LinearLayout(activity);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        contentScroll.addView(contentContainer);
        outer.addView(contentScroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        // Bottom Navigation Bar: Back | Skip | Next
        LinearLayout navBar = new LinearLayout(activity);
        navBar.setOrientation(LinearLayout.HORIZONTAL);
        navBar.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams navLp = new LinearLayout.LayoutParams(-1, -2);
        navLp.setMargins(0, dp(activity, 8), 0, 0);
        outer.addView(navBar, navLp);

        Button backBtn = createStyledButton(activity, "‹ Back", false, () -> {
            if (tipsMode[0]) {
                tipsMode[0] = false;
                renderRef[0].run();
            } else if (currentStep[0] > 0) {
                currentStep[0]--;
                renderRef[0].run();
            }
        });
        Button skipBtn = createStyledButton(activity, "Skip", false, dialog::dismiss);
        Button nextBtn = createStyledButton(activity, "Next ›", true, () -> {
            if (tipsMode[0]) {
                dialog.dismiss();
            } else if (currentStep[0] < TOPICS.length - 1) {
                currentStep[0]++;
                renderRef[0].run();
            } else {
                prefs.edit().putBoolean(PREF_COMPLETED, true).putBoolean(PREF_OPT_OUT, true).apply();
                dialog.dismiss();
            }
        });

        LinearLayout.LayoutParams nb1 = new LinearLayout.LayoutParams(0, dp(activity, 40), 1f);
        nb1.setMargins(0, 0, dp(activity, 4), 0);
        LinearLayout.LayoutParams nb2 = new LinearLayout.LayoutParams(0, dp(activity, 40), 0.75f);
        nb2.setMargins(dp(activity, 4), 0, dp(activity, 4), 0);
        LinearLayout.LayoutParams nb3 = new LinearLayout.LayoutParams(0, dp(activity, 40), 1f);
        nb3.setMargins(dp(activity, 4), 0, 0, 0);
        navBar.addView(backBtn, nb1);
        navBar.addView(skipBtn, nb2);
        navBar.addView(nextBtn, nb3);

        topicsMenuBtn.setOnClickListener(v -> {
            String[] names = new String[TOPICS.length];
            for (int i = 0; i < TOPICS.length; i++) {
                names[i] = (i + 1) + ". " + TOPICS[i].title();
            }
            NyxMenuDialog.showActionList(activity, "Jump to Topic", names, which -> {
                tipsMode[0] = false;
                currentStep[0] = which;
                renderRef[0].run();
            });
        });

        renderRef[0] = () -> {
            tabTutorial.invalidate();
            tabTips.invalidate();
            contentContainer.removeAllViews();
            contentScroll.scrollTo(0, 0);
            boolean landscape = activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

            if (!tipsMode[0]) {
                progressSection.setVisibility(View.VISIBLE);
                segmentedBar.invalidate();
                Topic t = TOPICS[currentStep[0]];

                LinearLayout topicCard = createInnerCard(activity, true);
                contentContainer.addView(topicCard, new LinearLayout.LayoutParams(-1, -2));

                // Step badge + Title header
                LinearLayout titleRow = new LinearLayout(activity);
                titleRow.setOrientation(LinearLayout.HORIZONTAL);
                titleRow.setGravity(Gravity.CENTER_VERTICAL);

                TextView stepPill = new TextView(activity);
                stepPill.setText(t.id() + " / " + TOPICS.length);
                stepPill.setTextColor(0xFF00E5FF);
                stepPill.setTextSize(11.5f);
                stepPill.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                stepPill.setPadding(0, 0, dp(activity, 8), 0);
                titleRow.addView(stepPill);

                TextView titleView = new TextView(activity);
                titleView.setText(t.title());
                titleView.setTextColor(Color.WHITE);
                titleView.setTextSize(16.5f);
                titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                titleRow.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1f));
                topicCard.addView(titleRow);

                TextView taglineView = new TextView(activity);
                taglineView.setText(t.tagline());
                taglineView.setTextColor(0xFFA8BCE8);
                taglineView.setTextSize(12.5f);
                taglineView.setPadding(0, dp(activity, 2), 0, dp(activity, 8));
                topicCard.addView(taglineView);

                // Visual Diagram + Key Badge Rows (side-by-side in landscape, stacked in portrait)
                View diagram = createIllustrationView(activity, t.id());
                LinearLayout rowsBox = new LinearLayout(activity);
                rowsBox.setOrientation(LinearLayout.VERTICAL);
                for (BadgeRow row : t.rows()) {
                    rowsBox.addView(createBadgeRowView(activity, row));
                }

                if (landscape) {
                    LinearLayout split = new LinearLayout(activity);
                    split.setOrientation(LinearLayout.HORIZONTAL);
                    split.setGravity(Gravity.CENTER_VERTICAL);
                    LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(0, dp(activity, 118), 0.95f);
                    dLp.setMargins(0, 0, dp(activity, 10), 0);
                    split.addView(diagram, dLp);
                    split.addView(rowsBox, new LinearLayout.LayoutParams(0, -2, 1.05f));
                    topicCard.addView(split, new LinearLayout.LayoutParams(-1, -2));
                } else {
                    LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(-1, dp(activity, 122));
                    dLp.setMargins(0, 0, 0, dp(activity, 8));
                    topicCard.addView(diagram, dLp);
                    topicCard.addView(rowsBox, new LinearLayout.LayoutParams(-1, -2));
                }

                // Compact 1-line Tip Footer
                TextView tipLine = new TextView(activity);
                tipLine.setText("💡 " + t.quickTip());
                tipLine.setTextColor(0xFF7CE8FF);
                tipLine.setTextSize(12f);
                tipLine.setPadding(dp(activity, 2), dp(activity, 6), dp(activity, 2), 0);
                topicCard.addView(tipLine);

                backBtn.setEnabled(currentStep[0] > 0);
                backBtn.setAlpha(currentStep[0] > 0 ? 1f : 0.4f);
                backBtn.setText("‹ Back");
                skipBtn.setVisibility(View.VISIBLE);
                nextBtn.setText(currentStep[0] == TOPICS.length - 1 ? "Done ✓" : "Next ›");
            } else {
                progressSection.setVisibility(View.GONE);
                for (int i = 0; i < TIPS_AND_TRICKS.length; i += (landscape ? 2 : 1)) {
                    if (landscape) {
                        LinearLayout row = new LinearLayout(activity);
                        row.setOrientation(LinearLayout.HORIZONTAL);
                        LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(-1, -2);
                        rLp.setMargins(0, 0, 0, dp(activity, 6));
                        contentContainer.addView(row, rLp);

                        LinearLayout c1 = buildVisualTipCard(activity, TIPS_AND_TRICKS[i]);
                        LinearLayout.LayoutParams c1Lp = new LinearLayout.LayoutParams(0, -1, 1f);
                        c1Lp.setMargins(0, 0, dp(activity, 4), 0);
                        row.addView(c1, c1Lp);

                        if (i + 1 < TIPS_AND_TRICKS.length) {
                            LinearLayout c2 = buildVisualTipCard(activity, TIPS_AND_TRICKS[i + 1]);
                            LinearLayout.LayoutParams c2Lp = new LinearLayout.LayoutParams(0, -1, 1f);
                            c2Lp.setMargins(dp(activity, 4), 0, 0, 0);
                            row.addView(c2, c2Lp);
                        } else {
                            row.addView(new Space(activity), new LinearLayout.LayoutParams(0, 1, 1f));
                        }
                    } else {
                        LinearLayout cardView = buildVisualTipCard(activity, TIPS_AND_TRICKS[i]);
                        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(-1, -2);
                        cLp.setMargins(0, 0, 0, dp(activity, 6));
                        contentContainer.addView(cardView, cLp);
                    }
                }
                backBtn.setEnabled(true);
                backBtn.setAlpha(1f);
                backBtn.setText("‹ Guide");
                skipBtn.setVisibility(View.GONE);
                nextBtn.setText("Close");
            }
        };

        renderRef[0].run();
        dialog.setContentView(outer);
        configureDialogWindow(activity, dialog);
        dialog.show();
    }

    /** Creates a compact visual row with a glowing pill badge on the left and short label on the right. */
    private static LinearLayout createBadgeRowView(Activity activity, BadgeRow row) {
        LinearLayout line = new LinearLayout(activity);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(0, dp(activity, 3), 0, dp(activity, 3));

        TextView badge = new TextView(activity) {
            private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF r = new RectF();
            @Override protected void onDraw(Canvas canvas) {
                r.set(dp(activity, 0.8f), dp(activity, 0.8f), getWidth() - dp(activity, 0.8f), getHeight() - dp(activity, 0.8f));
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFF172246);
                canvas.drawRoundRect(r, dp(activity, 6f), dp(activity, 6f), p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dp(activity, 1f));
                p.setShader(new LinearGradient(r.left, 0, r.right, 0, 0xAA00E5FF, 0xFFB84DFF, Shader.TileMode.CLAMP));
                canvas.drawRoundRect(r, dp(activity, 6f), dp(activity, 6f), p);
                super.onDraw(canvas);
            }
        };
        badge.setText(row.pill());
        badge.setTextColor(Color.WHITE);
        badge.setTextSize(11.5f);
        badge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(activity, 8), dp(activity, 4), dp(activity, 8), dp(activity, 4));
        badge.setMinWidth(dp(activity, 94));
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(-2, -2);
        bLp.setMargins(0, 0, dp(activity, 8), 0);
        line.addView(badge, bLp);

        TextView desc = new TextView(activity);
        desc.setText(row.text());
        desc.setTextColor(0xFFE6EEFF);
        desc.setTextSize(12.5f);
        line.addView(desc, new LinearLayout.LayoutParams(0, -2, 1f));
        return line;
    }

    /** Custom Canvas illustration view drawing a visual diagram for each tutorial topic. */
    private static View createIllustrationView(Activity activity, int topicId) {
        return new View(activity) {
            private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF r = new RectF();

            private float d(float v) { return v * getResources().getDisplayMetrics().density; }

            @Override protected void onDraw(Canvas c) {
                float w = getWidth(), h = getHeight();
                r.set(d(1), d(1), w - d(1), h - d(1));
                p.setStyle(Paint.Style.FILL);
                p.setShader(new LinearGradient(0, 0, w, h, 0xFF080D22, 0xFF130E2E, Shader.TileMode.CLAMP));
                c.drawRoundRect(r, d(10), d(10), p);
                p.setShader(null);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(d(1));
                p.setColor(0xFF283666);
                c.drawRoundRect(r, d(10), d(10), p);

                float cx = w / 2f, cy = h / 2f;
                p.setTextAlign(Paint.Align.CENTER);

                switch (topicId) {
                    case 0:
                    case 1: {
                        // PC Monitor <---> Wi-Fi Waves <---> Phone
                        drawMonitor(c, cx - d(76), cy - d(4), d(66), d(44), "PC · NyxLink", 0xFF00E5FF);
                        drawPhone(c, cx + d(76), cy, d(34), d(56), "Connect", 0xFFD55CFF);
                        p.setStyle(Paint.Style.STROKE);
                        p.setStrokeWidth(d(2));
                        p.setColor(0xFF00E5FF);
                        c.drawLine(cx - d(32), cy, cx + d(48), cy, p);
                        p.setStyle(Paint.Style.FILL);
                        c.drawCircle(cx - d(14), cy, d(3), p);
                        c.drawCircle(cx + d(8), cy, d(3.5f), p);
                        c.drawCircle(cx + d(30), cy, d(3), p);
                        drawPillLabel(c, cx + d(8), cy - d(22), "Same Wi-Fi / LAN", 0xFF00E5FF);
                        break;
                    }
                    case 2: {
                        // 5-Digit Pairing Code Boxes + Lock
                        String[] digits = {"4", "8", "2", "9", "1"};
                        float bw = d(24), bh = d(30), gap = d(5);
                        float total = digits.length * bw + (digits.length - 1) * gap;
                        float startX = cx - total / 2f;
                        for (int i = 0; i < digits.length; i++) {
                            float lx = startX + i * (bw + gap);
                            drawKeyBox(c, lx + bw / 2f, cy - d(8), bw, bh, digits[i], i == 4 ? 0xFFD55CFF : 0xFF00E5FF, true);
                        }
                        drawPillLabel(c, cx, cy + d(28), "5-Digit Code  →  Saved TLS Pairing", 0xFF78EAFF);
                        break;
                    }
                    case 3: {
                        // Mini PC Keyboard Panel Illustration
                        RectF panel = new RectF(cx - d(115), cy - d(38), cx + d(115), cy + d(40));
                        p.setStyle(Paint.Style.FILL);
                        p.setColor(0xFF0B1026);
                        c.drawRoundRect(panel, d(10), d(10), p);
                        p.setStyle(Paint.Style.STROKE);
                        p.setStrokeWidth(d(1.5f));
                        p.setShader(new LinearGradient(panel.left, panel.bottom, panel.right, panel.top, 0xFFD55CFF, 0xFF00E5FF, Shader.TileMode.CLAMP));
                        c.drawRoundRect(panel, d(10), d(10), p);
                        p.setShader(null);
                        drawKeyBox(c, cx - d(72), cy - d(20), d(66), d(20), "⌨ PC Keys", 0xFFD55CFF, true);
                        drawKeyBox(c, cx + d(2), cy - d(20), d(68), d(20), "📱 Android", 0xFF3A4B7C, false);
                        drawKeyBox(c, cx + d(74), cy - d(20), d(58), d(20), "Hide", 0xFF3A4B7C, false);
                        String[] keys = {"Esc", "F1", "Ctrl", "⊞", "Shift", "←", "↓", "→"};
                        float kw = d(24), kg = d(3.5f), sx = cx - (keys.length * kw + (keys.length - 1) * kg) / 2f;
                        for (int i = 0; i < keys.length; i++) {
                            drawKeyBox(c, sx + i * (kw + kg) + kw / 2f, cy + d(14), kw, d(22), keys[i], i == 2 ? 0xFFD55CFF : 0xFF3D4E80, i == 2);
                        }
                        break;
                    }
                    case 4: {
                        // 3 Gesture Cards: 1-Finger Tap | 2-Finger Tap | Double-Tap
                        drawTouchBadge(c, cx - d(78), cy, 1, "1-Tap", "Left Click", 0xFF00E5FF);
                        drawTouchBadge(c, cx, cy, 2, "2-Tap", "Right Click", 0xFFD55CFF);
                        drawTouchBadge(c, cx + d(78), cy, 3, "2× Tap", "Toolbar", 0xFF78EAFF);
                        break;
                    }
                    case 5: {
                        // Touch Lock + Pinch Zoom & Pan Diagram
                        drawMonitor(c, cx, cy, d(168), d(82), "", 0xFF3B4D80);
                        p.setStyle(Paint.Style.STROKE);
                        p.setStrokeWidth(d(1.6f));
                        p.setColor(0xFF00E5FF);
                        c.drawCircle(cx - d(24), cy + d(6), d(11), p);
                        c.drawCircle(cx + d(24), cy - d(10), d(11), p);
                        c.drawLine(cx - d(13), cy + d(2), cx - d(38), cy + d(14), p);
                        c.drawLine(cx + d(13), cy - d(6), cx + d(38), cy - d(18), p);
                        drawPillLabel(c, cx - d(42), cy - d(25), "🔒 Locked", 0xFFD55CFF);
                        drawPillLabel(c, cx + d(30), cy + d(25), "1×–4× Pinch & Pan", 0xFF00E5FF);
                        break;
                    }
                    case 6: {
                        // Dual Monitors Live Switch
                        drawMonitor(c, cx - d(56), cy - d(2), d(76), d(48), "Monitor 1", 0xFF384674);
                        drawMonitor(c, cx + d(56), cy - d(2), d(76), d(48), "Monitor 2 ✓", 0xFF00E5FF);
                        p.setColor(0xFFD55CFF);
                        p.setStyle(Paint.Style.STROKE);
                        p.setStrokeWidth(d(2));
                        c.drawLine(cx - d(10), cy - d(2), cx + d(10), cy - d(2), p);
                        break;
                    }
                    case 7: {
                        // Fullscreen Edge-to-Edge Expansion
                        drawMonitor(c, cx, cy, d(156), d(78), "FULLSCREEN", 0xFF00E5FF);
                        p.setColor(0xFFD55CFF);
                        p.setStyle(Paint.Style.STROKE);
                        p.setStrokeWidth(d(2));
                        float l = cx - d(64), rt = cx + d(64), t = cy - d(26), b = cy + d(26), s = d(10);
                        c.drawLine(l, t, l + s, t, p); c.drawLine(l, t, l, t + s, p);
                        c.drawLine(rt, t, rt - s, t, p); c.drawLine(rt, t, rt, t + s, p);
                        c.drawLine(l, b, l + s, b, p); c.drawLine(l, b, l, b - s, p);
                        c.drawLine(rt, b, rt - s, b, p); c.drawLine(rt, b, rt, b - s, p);
                        break;
                    }
                    case 8: {
                        // Portrait <-> Landscape Rotation
                        drawPhone(c, cx - d(58), cy, d(36), d(64), "Portrait", 0xFF00E5FF);
                        drawMonitor(c, cx + d(48), cy, d(82), d(44), "Landscape", 0xFFD55CFF);
                        drawPillLabel(c, cx - d(10), cy - d(26), "↻ Rotate", 0xFF78EAFF);
                        break;
                    }
                    case 9: {
                        // Quick Actions 2x2 Grid
                        drawKeyBox(c, cx - d(50), cy - d(16), d(88), d(24), "Alt + Tab", 0xFF00E5FF, true);
                        drawKeyBox(c, cx + d(50), cy - d(16), d(88), d(24), "Paste Text", 0xFFD55CFF, true);
                        drawKeyBox(c, cx - d(50), cy + d(16), d(88), d(24), "Discord Mute", 0xFF3D4E80, false);
                        drawKeyBox(c, cx + d(50), cy + d(16), d(88), d(24), "Custom 1–3", 0xFF3D4E80, false);
                        break;
                    }
                    case 10: {
                        // Privacy Pause Screen Blank
                        drawMonitor(c, cx, cy, d(146), d(74), "PRIVACY PAUSED", 0xFFD55CFF);
                        drawPillLabel(c, cx, cy + d(20), "Host Stopped · Tap to Resume", 0xFF00E5FF);
                        break;
                    }
                    case 11: {
                        // Options & Quality Badges
                        drawKeyBox(c, cx - d(52), cy - d(16), d(92), d(24), "1080p · 60 FPS", 0xFF00E5FF, true);
                        drawKeyBox(c, cx + d(52), cy - d(16), d(92), d(24), "☀ Brightness", 0xFF3D4E80, false);
                        drawKeyBox(c, cx - d(52), cy + d(16), d(92), d(24), "🎯 Locate Cursor", 0xFFD55CFF, true);
                        drawKeyBox(c, cx + d(52), cy + d(16), d(92), d(24), "🔊 PC Volume", 0xFF3D4E80, false);
                        break;
                    }
                    case 12: {
                        // Safe Disconnect Flow
                        drawKeyBox(c, cx - d(58), cy, d(82), d(30), "Disconnect", 0xFFD55CFF, true);
                        drawKeyBox(c, cx + d(58), cy, d(86), d(30), "Keys Released ✓", 0xFF00E5FF, false);
                        p.setColor(0xFF78EAFF);
                        p.setStrokeWidth(d(2));
                        c.drawLine(cx - d(12), cy, cx + d(10), cy, p);
                        break;
                    }
                    case 13: {
                        // Shortcut Key Chord Equation: [Ctrl] + [Shift] + [S]
                        drawKeyBox(c, cx - d(68), cy, d(46), d(32), "Ctrl ●", 0xFFD55CFF, true);
                        drawKeyBox(c, cx, cy, d(50), d(32), "Shift ●", 0xFFD55CFF, true);
                        drawKeyBox(c, cx + d(68), cy, d(42), d(32), "C / S", 0xFF00E5FF, true);
                        p.setStyle(Paint.Style.FILL);
                        p.setColor(Color.WHITE);
                        p.setTextSize(d(15));
                        c.drawText("+", cx - d(34), cy + d(5), p);
                        c.drawText("+", cx + d(36), cy + d(5), p);
                        break;
                    }
                    case 14: {
                        // Troubleshooting Visual Checklist
                        drawKeyBox(c, cx - d(66), cy, d(58), d(36), "Wi-Fi ✓", 0xFF00E5FF, true);
                        drawKeyBox(c, cx, cy, d(62), d(36), "Unlock ✓", 0xFFD55CFF, true);
                        drawKeyBox(c, cx + d(66), cy, d(58), d(36), "Cursor ✓", 0xFF00E5FF, true);
                        break;
                    }
                    default: {
                        // Pro Tips Visual Trio
                        drawKeyBox(c, cx - d(68), cy, d(60), d(36), "🌙 Bed", 0xFFD55CFF, true);
                        drawKeyBox(c, cx, cy, d(60), d(36), "🖼 PiP", 0xFF00E5FF, true);
                        drawKeyBox(c, cx + d(68), cy, d(60), d(36), "🔊 Vol", 0xFFD55CFF, true);
                        break;
                    }
                }
            }

            private void drawMonitor(Canvas c, float cx, float cy, float mw, float mh, String label, int borderColor) {
                RectF m = new RectF(cx - mw / 2f, cy - mh / 2f, cx + mw / 2f, cy + mh / 2f);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFF0D1530);
                c.drawRoundRect(m, d(6), d(6), p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(d(1.6f));
                p.setColor(borderColor);
                c.drawRoundRect(m, d(6), d(6), p);
                c.drawLine(cx - d(12), m.bottom + d(5), cx + d(12), m.bottom + d(5), p);
                c.drawLine(cx, m.bottom, cx, m.bottom + d(5), p);
                if (!label.isEmpty()) {
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(Color.WHITE);
                    p.setTextSize(d(10.5f));
                    c.drawText(label, cx, cy + d(3.5f), p);
                }
            }

            private void drawPhone(Canvas c, float cx, float cy, float pw, float ph, String label, int borderColor) {
                RectF m = new RectF(cx - pw / 2f, cy - ph / 2f, cx + pw / 2f, cy + ph / 2f);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFF0D1530);
                c.drawRoundRect(m, d(6), d(6), p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(d(1.6f));
                p.setColor(borderColor);
                c.drawRoundRect(m, d(6), d(6), p);
                p.setStyle(Paint.Style.FILL);
                p.setColor(Color.WHITE);
                p.setTextSize(d(8.5f));
                c.drawText(label, cx, cy + d(3), p);
            }

            private void drawKeyBox(Canvas c, float cx, float cy, float kw, float kh, String text, int border, boolean active) {
                RectF k = new RectF(cx - kw / 2f, cy - kh / 2f, cx + kw / 2f, cy + kh / 2f);
                p.setStyle(Paint.Style.FILL);
                p.setColor(active ? 0xFF231648 : 0xFF121A36);
                c.drawRoundRect(k, d(6), d(6), p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(d(active ? 1.5f : 1f));
                p.setColor(border);
                c.drawRoundRect(k, d(6), d(6), p);
                p.setStyle(Paint.Style.FILL);
                p.setColor(Color.WHITE);
                p.setTextSize(d(10.5f));
                c.drawText(text, cx, cy + d(3.8f), p);
            }

            private void drawPillLabel(Canvas c, float cx, float cy, String text, int color) {
                p.setTextSize(d(10f));
                float tw = p.measureText(text) + d(14);
                RectF pill = new RectF(cx - tw / 2f, cy - d(9), cx + tw / 2f, cy + d(9));
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xE60B122A);
                c.drawRoundRect(pill, d(9), d(9), p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(d(1f));
                p.setColor(color);
                c.drawRoundRect(pill, d(9), d(9), p);
                p.setStyle(Paint.Style.FILL);
                p.setColor(Color.WHITE);
                c.drawText(text, cx, cy + d(3.5f), p);
            }

            private void drawTouchBadge(Canvas c, float cx, float cy, int mode, String topText, String botText, int color) {
                RectF card = new RectF(cx - d(34), cy - d(36), cx + d(34), cy + d(36));
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFF111936);
                c.drawRoundRect(card, d(8), d(8), p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(d(1.2f));
                p.setColor(color);
                c.drawRoundRect(card, d(8), d(8), p);
                if (mode == 1) {
                    c.drawCircle(cx, cy - d(10), d(9), p);
                    p.setStyle(Paint.Style.FILL);
                    c.drawCircle(cx, cy - d(10), d(3.5f), p);
                } else if (mode == 2) {
                    c.drawCircle(cx - d(9), cy - d(10), d(7), p);
                    c.drawCircle(cx + d(9), cy - d(10), d(7), p);
                } else {
                    c.drawCircle(cx, cy - d(10), d(11), p);
                    c.drawCircle(cx, cy - d(10), d(5.5f), p);
                }
                p.setStyle(Paint.Style.FILL);
                p.setColor(Color.WHITE);
                p.setTextSize(d(10f));
                c.drawText(topText, cx, cy + d(14), p);
                p.setColor(0xFFA8BCE8);
                p.setTextSize(d(9f));
                c.drawText(botText, cx, cy + d(27), p);
            }
        };
    }

    private static LinearLayout buildVisualTipCard(Activity activity, TipCard tc) {
        LinearLayout card = createInnerCard(activity, false);
        LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(activity);
        title.setText(tc.title());
        title.setTextColor(Color.WHITE);
        title.setTextSize(14f);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView pill = new TextView(activity);
        pill.setText(tc.badge());
        pill.setTextColor(0xFF00E5FF);
        pill.setTextSize(11f);
        pill.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        top.addView(pill);
        card.addView(top);

        TextView body = new TextView(activity);
        body.setText(tc.shortText());
        body.setTextColor(0xFFC8D8F8);
        body.setTextSize(12f);
        body.setPadding(0, dp(activity, 3), 0, 0);
        card.addView(body);
        return card;
    }

    private static Dialog createBaseDialog(Activity activity) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        return dialog;
    }

    private static void configureDialogWindow(Activity activity, Dialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        boolean landscape = dm.widthPixels > dm.heightPixels;
        int width = (int) (dm.widthPixels * (landscape ? 0.88f : 0.94f));
        int maxHeight = (int) (dm.heightPixels * (landscape ? 0.90f : 0.82f));
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.copyFrom(window.getAttributes());
        lp.width = width;
        lp.height = maxHeight;
        lp.gravity = Gravity.CENTER;
        window.setAttributes(lp);
    }

    private static LinearLayout createOuterCard(Activity activity) {
        LinearLayout box = new LinearLayout(activity) {
            private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF r = new RectF();
            @Override protected void dispatchDraw(Canvas canvas) {
                float pad = dp(activity, 2f), rad = dp(activity, 18f);
                r.set(pad, pad, getWidth() - pad, getHeight() - pad);
                p.setStyle(Paint.Style.FILL);
                p.setShader(new LinearGradient(0, r.top, 0, r.bottom, 0xFA090E24, 0xFE050816, Shader.TileMode.CLAMP));
                canvas.drawRoundRect(r, rad, rad, p);
                super.dispatchDraw(canvas);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dp(activity, 1.6f));
                p.setShader(new LinearGradient(r.left, r.bottom, r.right, r.top,
                        new int[]{0xFFC845FF, 0xFF7B4DFF, 0xFF00E5FF}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
                canvas.drawRoundRect(r, rad, rad, p);
            }
        };
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(activity, 14);
        box.setPadding(pad, dp(activity, 12), pad, dp(activity, 12));
        return box;
    }

    private static LinearLayout createInnerCard(Activity activity, boolean highlightBorder) {
        LinearLayout box = new LinearLayout(activity) {
            private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF r = new RectF();
            @Override protected void dispatchDraw(Canvas canvas) {
                float pad = dp(activity, 1f), rad = dp(activity, 12f);
                r.set(pad, pad, getWidth() - pad, getHeight() - pad);
                p.setStyle(Paint.Style.FILL);
                p.setShader(new LinearGradient(0, r.top, 0, r.bottom, 0xFF101836, 0xFF0A0F24, Shader.TileMode.CLAMP));
                canvas.drawRoundRect(r, rad, rad, p);
                super.dispatchDraw(canvas);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dp(activity, 1f));
                p.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                        highlightBorder ? 0xAA00E5FF : 0x663A4B7C,
                        highlightBorder ? 0xAAB84DFF : 0x66283358, Shader.TileMode.CLAMP));
                canvas.drawRoundRect(r, rad, rad, p);
            }
        };
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(activity, 10);
        box.setPadding(pad, dp(activity, 9), pad, dp(activity, 9));
        return box;
    }

    private interface ActiveCheck { boolean isActive(); }

    private static Button createTabButton(Activity activity, String label, Runnable action, ActiveCheck activeCheck) {
        Button b = new Button(activity) {
            private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF r = new RectF();
            @Override protected void onDraw(Canvas canvas) {
                boolean active = activeCheck.isActive() || isPressed();
                float inset = dp(activity, 1f), rad = dp(activity, 9f);
                r.set(inset, inset, getWidth() - inset, getHeight() - inset);
                p.setStyle(Paint.Style.FILL);
                p.setShader(new LinearGradient(0, r.top, 0, r.bottom,
                        active ? 0xFF26174D : 0xFF0E152E,
                        active ? 0xFF141132 : 0xFF090D20, Shader.TileMode.CLAMP));
                canvas.drawRoundRect(r, rad, rad, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dp(activity, active ? 1.5f : 1f));
                p.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                        active ? 0xFF00E5FF : 0xFF2D3964,
                        active ? 0xFFD55CFF : 0xFF232C4E, Shader.TileMode.CLAMP));
                canvas.drawRoundRect(r, rad, rad, p);
                super.onDraw(canvas);
            }
        };
        b.setBackground(null);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(12f);
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        b.setPadding(0, 0, 0, 0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private static Button createSmallPillButton(Activity activity, String label, Runnable action) {
        Button b = new Button(activity) {
            private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF r = new RectF();
            @Override protected void onDraw(Canvas canvas) {
                float inset = dp(activity, 1f), rad = dp(activity, 8f);
                r.set(inset, inset, getWidth() - inset, getHeight() - inset);
                p.setStyle(Paint.Style.FILL);
                p.setColor(isPressed() ? 0xFF241B4B : 0xFF121A36);
                canvas.drawRoundRect(r, rad, rad, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dp(activity, 1f));
                p.setColor(isPressed() ? 0xFF00E5FF : 0xFF344475);
                canvas.drawRoundRect(r, rad, rad, p);
                super.onDraw(canvas);
            }
        };
        b.setBackground(null);
        b.setAllCaps(false);
        b.setTextColor(0xFFE0ECFF);
        b.setTextSize(11.5f);
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(activity, 9), 0, dp(activity, 9), 0);
        if (action != null) b.setOnClickListener(v -> action.run());
        return b;
    }

    private static Button createStyledButton(Activity activity, String label, boolean primary, Runnable action) {
        Button b = new Button(activity) {
            private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF r = new RectF();
            @Override protected void onDraw(Canvas canvas) {
                boolean lit = primary || isPressed();
                float inset = dp(activity, 1.2f), rad = dp(activity, 10f);
                r.set(inset, inset, getWidth() - inset, getHeight() - inset);
                p.setStyle(Paint.Style.FILL);
                p.setShader(new LinearGradient(0, r.top, 0, r.bottom,
                        lit ? 0xFF281852 : 0xFF121936,
                        lit ? 0xFF151236 : 0xFF0B1024, Shader.TileMode.CLAMP));
                canvas.drawRoundRect(r, rad, rad, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dp(activity, lit ? 1.5f : 1f));
                p.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                        lit ? 0xFF00E5FF : 0xFF354373,
                        lit ? 0xFFD55CFF : 0xFF273157, Shader.TileMode.CLAMP));
                canvas.drawRoundRect(r, rad, rad, p);
                super.onDraw(canvas);
            }
        };
        b.setBackground(null);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(12.5f);
        b.setTypeface(Typeface.DEFAULT, primary ? Typeface.BOLD : Typeface.NORMAL);
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(activity, 8), 0, dp(activity, 8), 0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    public static void dismissActive() {
        if (activeDialog != null) {
            try {
                if (activeDialog.isShowing()) activeDialog.dismiss();
            } catch (Exception ignored) {}
            activeDialog = null;
        }
    }
}
