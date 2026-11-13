package com.limelight.bedmirror;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.text.InputFilter;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.JSONObject;
import java.util.UUID;

/**
 * NyxLink branded custom dialogs matching the dark navy, cyan, and purple glowing theme.
 */
public final class NyxMenuDialog {

    public interface OnItemClickListener {
        void onClick(int index);
    }

    public interface OnSingleChoiceListener {
        void onSelected(int index);
    }

    public interface IsItemEnabledCallback {
        boolean isEnabled(int index);
    }

    public interface OnTextInputListener {
        void onConfirmed(String text);
    }

    public interface OnPairingCodeListener {
        boolean onValidateAndSubmit(String code, EditText inputView);
    }

    public interface SliderChangeListener {
        void onChanged(int value);
    }

    public static final int REQUEST_PICK_SCREENSHOT = 7942;

    public interface ScreenshotReceiver {
        void onScreenshotAttached(Bitmap bmp, String base64);
    }

    private static ScreenshotReceiver currentScreenshotReceiver;

    public static void setScreenshotReceiver(ScreenshotReceiver receiver) {
        currentScreenshotReceiver = receiver;
    }

    public static void onScreenshotResult(Context context, Uri uri) {
        if (uri != null && currentScreenshotReceiver != null) {
            Bitmap bmp = NyxReportingManager.loadAndDownsampleBitmap(context, uri, 1280);
            if (bmp != null) {
                String base64 = NyxReportingManager.bitmapToBase64(bmp);
                currentScreenshotReceiver.onScreenshotAttached(bmp, base64);
            }
        }
    }

    private static int dp(Context ctx, float dp) {
        return Math.round(dp * ctx.getResources().getDisplayMetrics().density);
    }

    private static Dialog createBaseDialog(Activity activity) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        return dialog;
    }

    private static void configureDialogWindow(Activity activity, Dialog dialog, int maxContentHeight) {
        Window window = dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setDimAmount(0.68f);
        window.setWindowAnimations(android.R.style.Animation_Dialog);

        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        boolean landscape = dm.widthPixels > dm.heightPixels;
        int maxAllowedWidth = landscape ? dp(activity, 520) : dp(activity, 440);
        int dialogWidth = Math.min((int)(dm.widthPixels * (landscape ? 0.68f : 0.90f)), maxAllowedWidth);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.copyFrom(window.getAttributes());
        lp.width = dialogWidth;
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        lp.gravity = Gravity.CENTER;
        window.setAttributes(lp);
    }

    /**
     * Outer container with glowing gradient border (cyan to purple) and dark navy fill.
     */
    private static LinearLayout createOuterCard(Activity activity) {
        LinearLayout outer = new LinearLayout(activity) {
            private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF bounds = new RectF();
            private final Path wavePath = new Path();

            @Override protected void dispatchDraw(Canvas canvas) {
                float pad = dp(activity, 2.5f);
                float radius = dp(activity, 20f);
                bounds.set(pad, pad, getWidth() - pad, getHeight() - pad);

                // Deep dark navy background fill
                fillPaint.setStyle(Paint.Style.FILL);
                fillPaint.setShader(new LinearGradient(0, bounds.top, 0, bounds.bottom,
                        0xFA080C1E, 0xFE050814, Shader.TileMode.CLAMP));
                canvas.drawRoundRect(bounds, radius, radius, fillPaint);

                // Top ambient wave flourish
                float waveY = bounds.top + dp(activity, 46f);
                wavePath.reset();
                wavePath.moveTo(bounds.left + dp(activity, 8f), waveY);
                wavePath.cubicTo(
                        bounds.left + bounds.width() * 0.35f, waveY + dp(activity, 6f),
                        bounds.left + bounds.width() * 0.68f, waveY - dp(activity, 6f),
                        bounds.right - dp(activity, 8f), waveY + dp(activity, 2f)
                );
                wavePaint.setStyle(Paint.Style.STROKE);
                wavePaint.setStrokeWidth(dp(activity, 1.2f));
                wavePaint.setShader(new LinearGradient(bounds.left, 0, bounds.right, 0,
                        new int[]{0x1000E5FF, 0x40A855F7, 0x2500E5FF}, null, Shader.TileMode.CLAMP));
                canvas.drawPath(wavePath, wavePaint);

                // Draw children
                super.dispatchDraw(canvas);

                // Subtle Cyan to Purple glowing border
                strokePaint.setStyle(Paint.Style.STROKE);
                strokePaint.setStrokeWidth(dp(activity, 1.8f));
                strokePaint.setShader(new LinearGradient(
                        bounds.left, bounds.top, bounds.right, bounds.bottom,
                        new int[]{0xFF00E5FF, 0xFF38BDF8, 0xFF818CF8, 0xFFA855F7, 0xFFC084FC},
                        new float[]{0f, 0.22f, 0.50f, 0.80f, 1f},
                        Shader.TileMode.CLAMP
                ));
                canvas.drawRoundRect(bounds, radius, radius, strokePaint);
            }
        };
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 14));
        return outer;
    }

    /**
     * Top header bar with NyxLink crescent moon, title, and close button.
     */
    private static LinearLayout createHeader(Activity activity, String title, Dialog dialog) {
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(activity, 4), dp(activity, 2), dp(activity, 4), dp(activity, 10));

        // Moon & star icon
        View moonIcon = new View(activity) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Path moonPath = new Path();
            private final Path outerCircle = new Path();
            private final Path innerCircle = new Path();

            @Override protected void onDraw(Canvas canvas) {
                float size = Math.min(getWidth(), getHeight());
                float cx = size * 0.44f;
                float cy = size * 0.52f;
                float r = size * 0.38f;

                outerCircle.reset();
                outerCircle.addCircle(cx, cy, r, Path.Direction.CW);
                innerCircle.reset();
                innerCircle.addCircle(cx + r * 0.45f, cy - r * 0.22f, r * 0.88f, Path.Direction.CW);

                moonPath.reset();
                moonPath.op(outerCircle, innerCircle, Path.Op.DIFFERENCE);

                paint.setStyle(Paint.Style.FILL);
                paint.setShader(new LinearGradient(cx - r, cy - r, cx + r, cy + r,
                        0xFF00E5FF, 0xFFC084FC, Shader.TileMode.CLAMP));
                canvas.drawPath(moonPath, paint);

                // Small sparkle star
                paint.setShader(null);
                paint.setColor(0xFFF1F5F9);
                float sx = cx + r * 0.90f;
                float sy = cy - r * 0.45f;
                float sr = r * 0.25f;
                Path star = new Path();
                star.moveTo(sx, sy - sr);
                star.quadTo(sx, sy, sx + sr, sy);
                star.quadTo(sx, sy, sx, sy + sr);
                star.quadTo(sx, sy, sx - sr, sy);
                star.quadTo(sx, sy, sx, sy - sr);
                canvas.drawPath(star, paint);
            }
        };
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(activity, 26), dp(activity, 26));
        iconLp.setMargins(0, 0, dp(activity, 10), 0);
        header.addView(moonIcon, iconLp);

        // Title text
        TextView titleView = new TextView(activity);
        titleView.setText(title);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(16.5f);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setSingleLine(true);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        header.addView(titleView, titleLp);

        // Close button 'X'
        TextView closeBtn = new TextView(activity) {
            private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF r = new RectF();

            @Override protected void onDraw(Canvas canvas) {
                float pad = dp(activity, 1f);
                r.set(pad, pad, getWidth() - pad, getHeight() - pad);
                float rad = dp(activity, 9f);
                bgPaint.setColor(isPressed() ? 0xFF2A2350 : 0xFF121834);
                bgPaint.setStyle(Paint.Style.FILL);
                canvas.drawRoundRect(r, rad, rad, bgPaint);

                strokePaint.setStyle(Paint.Style.STROKE);
                strokePaint.setStrokeWidth(dp(activity, 1.2f));
                strokePaint.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                        0xFF8B5CF6, 0xFF00E5FF, Shader.TileMode.CLAMP));
                canvas.drawRoundRect(r, rad, rad, strokePaint);

                super.onDraw(canvas);
            }
        };
        closeBtn.setText("✕");
        closeBtn.setTextColor(Color.WHITE);
        closeBtn.setTextSize(14f);
        closeBtn.setTypeface(Typeface.DEFAULT_BOLD);
        closeBtn.setGravity(Gravity.CENTER);
        closeBtn.setClickable(true);
        closeBtn.setFocusable(true);
        closeBtn.setContentDescription("Close");
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(dp(activity, 32), dp(activity, 32));
        closeLp.setMargins(dp(activity, 8), 0, 0, 0);
        header.addView(closeBtn, closeLp);

        return header;
    }

    /**
     * ScrollView that respects a maximum height limit so short menus stay compact and long menus scroll.
     */
    private static class MaxHeightScrollView extends ScrollView {
        private final int maxHeight;

        public MaxHeightScrollView(Context context, int maxHeight) {
            super(context);
            this.maxHeight = maxHeight;
            setVerticalScrollBarEnabled(true);
            setOverScrollMode(OVER_SCROLL_IF_CONTENT_SCROLLS);
        }

        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            if (maxHeight > 0) {
                int mode = MeasureSpec.getMode(heightMeasureSpec);
                int size = MeasureSpec.getSize(heightMeasureSpec);
                if (mode == MeasureSpec.UNSPECIFIED || size > maxHeight) {
                    heightMeasureSpec = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST);
                }
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    /**
     * A single menu row slot formatted as a rounded pill/card matching the design reference.
     */
    private static View createMenuItemRow(
            Activity activity,
            String label,
            boolean isSelected,
            boolean isEnabled,
            Runnable onClick
    ) {
        FrameLayout row = new FrameLayout(activity) {
            private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF r = new RectF();

            @Override protected void onDraw(Canvas canvas) {
                float pad = dp(activity, 1.2f);
                float rad = dp(activity, 12f);
                r.set(pad, pad, getWidth() - pad, getHeight() - pad);

                // Background
                bgPaint.setStyle(Paint.Style.FILL);
                if (!isEnabled) {
                    bgPaint.setColor(0xFF0C1024);
                } else if (isPressed()) {
                    bgPaint.setColor(0xFF223266);
                } else if (isSelected) {
                    bgPaint.setColor(0xFF15224D);
                } else {
                    bgPaint.setColor(0xFF101736);
                }
                canvas.drawRoundRect(r, rad, rad, bgPaint);

                // Border stroke
                borderPaint.setStyle(Paint.Style.STROKE);
                if (!isEnabled) {
                    borderPaint.setStrokeWidth(dp(activity, 1f));
                    borderPaint.setShader(null);
                    borderPaint.setColor(0xFF1A223E);
                } else if (isSelected || isPressed()) {
                    borderPaint.setStrokeWidth(dp(activity, 1.8f));
                    borderPaint.setShader(new LinearGradient(
                            r.left, r.top, r.right, r.bottom,
                            0xFF00E5FF, 0xFF38BDF8, Shader.TileMode.CLAMP));
                } else {
                    borderPaint.setStrokeWidth(dp(activity, 1f));
                    borderPaint.setShader(new LinearGradient(
                            r.left, r.top, r.right, r.bottom,
                            0x3300E5FF, 0x33C084FC, Shader.TileMode.CLAMP));
                }
                canvas.drawRoundRect(r, rad, rad, borderPaint);

                super.onDraw(canvas);
            }
        };

        row.setClickable(isEnabled);
        row.setFocusable(isEnabled);
        if (isEnabled) {
            row.setOnClickListener(v -> {
                if (onClick != null) onClick.run();
            });
        }
        row.setAlpha(isEnabled ? 1.0f : 0.45f);

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        row.setMinimumHeight(dp(activity, 46));
        rowLp.setMargins(dp(activity, 2), dp(activity, 3.5f), dp(activity, 2), dp(activity, 3.5f));
        row.setLayoutParams(rowLp);

        // Content layout inside row
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setPadding(dp(activity, 14), dp(activity, 8), dp(activity, 14), dp(activity, 8));
        row.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView tv = new TextView(activity);
        tv.setText(label);
        tv.setTextColor(isEnabled ? Color.WHITE : 0xFF94A3B8);
        tv.setTextSize(14f);
        tv.setTypeface(isSelected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        tv.setMaxLines(2);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        content.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (isSelected) {
            TextView check = new TextView(activity);
            check.setText("✓");
            check.setTextColor(0xFF00E5FF);
            check.setTextSize(15f);
            check.setTypeface(Typeface.DEFAULT_BOLD);
            check.setPadding(dp(activity, 6), 0, 0, 0);
            content.addView(check);
        }

        return row;
    }

    /**
     * Shows a list of actions (e.g. Quick Actions, Viewing Options, Custom Button Picker).
     */
    public static Dialog showActionList(
            Activity activity,
            String title,
            String[] items,
            OnItemClickListener listener
    ) {
        Dialog dialog = createBaseDialog(activity);
        LinearLayout outer = createOuterCard(activity);
        outer.addView(createHeader(activity, title, dialog));

        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        boolean landscape = dm.widthPixels > dm.heightPixels;
        int maxScrollHeight = (int)(dm.heightPixels * (landscape ? 0.74f : 0.70f));

        MaxHeightScrollView scroll = new MaxHeightScrollView(activity, maxScrollHeight);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(activity, 2), 0, dp(activity, 4));

        for (int i = 0; i < items.length; i++) {
            final int index = i;
            View row = createMenuItemRow(activity, items[i], false, true, () -> {
                dialog.dismiss();
                if (listener != null) listener.onClick(index);
            });
            list.addView(row);
        }
        scroll.addView(list);
        outer.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        dialog.setContentView(outer);
        configureDialogWindow(activity, dialog, maxScrollHeight);
        dialog.show();
        return dialog;
    }

    /**
     * Shows a single-choice list dialog (e.g. Audio Mode, Stream Quality, Monitor Selection, Orientation).
     */
    public static Dialog showSingleChoice(
            Activity activity,
            String title,
            String[] items,
            int selectedIndex,
            OnSingleChoiceListener listener,
            IsItemEnabledCallback isEnabled
    ) {
        Dialog dialog = createBaseDialog(activity);
        LinearLayout outer = createOuterCard(activity);
        outer.addView(createHeader(activity, title, dialog));

        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        boolean landscape = dm.widthPixels > dm.heightPixels;
        int maxScrollHeight = (int)(dm.heightPixels * (landscape ? 0.74f : 0.70f));

        MaxHeightScrollView scroll = new MaxHeightScrollView(activity, maxScrollHeight);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(activity, 2), 0, dp(activity, 4));

        for (int i = 0; i < items.length; i++) {
            final int index = i;
            boolean enabled = (isEnabled == null || isEnabled.isEnabled(i));
            boolean selected = (index == selectedIndex);
            View row = createMenuItemRow(activity, items[i], selected, enabled, () -> {
                dialog.dismiss();
                if (listener != null) listener.onSelected(index);
            });
            list.addView(row);
        }
        scroll.addView(list);
        outer.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        dialog.setContentView(outer);
        configureDialogWindow(activity, dialog, maxScrollHeight);
        dialog.show();
        return dialog;
    }

    /**
     * Shows a styled text input dialog for shortcuts (Custom shortcut, Discord shortcut).
     */
    public static Dialog showTextInput(
            Activity activity,
            String title,
            String message,
            String initialText,
            String hint,
            String positiveText,
            String negativeText,
            OnTextInputListener listener
    ) {
        Dialog dialog = createBaseDialog(activity);
        LinearLayout outer = createOuterCard(activity);
        outer.addView(createHeader(activity, title, dialog));

        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(activity, 4), dp(activity, 6), dp(activity, 4), dp(activity, 10));

        if (message != null && !message.isEmpty()) {
            TextView msgView = new TextView(activity);
            msgView.setText(message);
            msgView.setTextColor(0xFFCBD5E1);
            msgView.setTextSize(13.5f);
            msgView.setPadding(0, 0, 0, dp(activity, 12));
            body.addView(msgView);
        }

        EditText input = createStyledEditText(activity, initialText, hint, InputType.TYPE_CLASS_TEXT);
        body.addView(input);

        LinearLayout buttonBar = new LinearLayout(activity);
        buttonBar.setOrientation(LinearLayout.HORIZONTAL);
        buttonBar.setGravity(Gravity.END);
        buttonBar.setPadding(0, dp(activity, 16), 0, 0);

        Button cancelBtn = createPillButton(activity, negativeText != null ? negativeText : "Cancel", false, dialog::dismiss);
        buttonBar.addView(cancelBtn);

        View spacer = new View(activity);
        buttonBar.addView(spacer, new LinearLayout.LayoutParams(dp(activity, 10), 1));

        Button saveBtn = createPillButton(activity, positiveText != null ? positiveText : "Save", true, () -> {
            String text = input.getText().toString().trim();
            if (listener != null) listener.onConfirmed(text);
            dialog.dismiss();
        });
        buttonBar.addView(saveBtn);

        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO) {
                saveBtn.performClick();
                return true;
            }
            return false;
        });

        body.addView(buttonBar);
        outer.addView(body);

        dialog.setContentView(outer);
        configureDialogWindow(activity, dialog, 0);
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        dialog.show();
        input.requestFocus();
        input.post(() -> {
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        });
        return dialog;
    }

    /**
     * Shows the pairing code entry dialog with 5-digit numeric constraint and validation.
     */
    public static Dialog showPairingCode(
            Activity activity,
            String title,
            String message,
            String positiveText,
            String negativeText,
            OnPairingCodeListener listener,
            Runnable onCancel
    ) {
        Dialog dialog = createBaseDialog(activity);
        LinearLayout outer = createOuterCard(activity);
        outer.addView(createHeader(activity, title, dialog));

        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(activity, 4), dp(activity, 6), dp(activity, 4), dp(activity, 10));

        if (message != null && !message.isEmpty()) {
            TextView msgView = new TextView(activity);
            msgView.setText(message);
            msgView.setTextColor(0xFFCBD5E1);
            msgView.setTextSize(13.5f);
            msgView.setPadding(0, 0, 0, dp(activity, 12));
            body.addView(msgView);
        }

        EditText input = createStyledEditText(activity, "", "Five-digit code", InputType.TYPE_CLASS_NUMBER);
        input.setFilters(new InputFilter[]{ new InputFilter.LengthFilter(5) });
        body.addView(input);

        boolean[] submitted = { false };
        LinearLayout buttonBar = new LinearLayout(activity);
        buttonBar.setOrientation(LinearLayout.HORIZONTAL);
        buttonBar.setGravity(Gravity.END);
        buttonBar.setPadding(0, dp(activity, 16), 0, 0);

        Button cancelBtn = createPillButton(activity, negativeText != null ? negativeText : "Stop pairing", false, () -> {
            dialog.dismiss();
        });
        buttonBar.addView(cancelBtn);

        View spacer = new View(activity);
        buttonBar.addView(spacer, new LinearLayout.LayoutParams(dp(activity, 10), 1));

        Button pairBtn = createPillButton(activity, positiveText != null ? positiveText : "Pair", true, () -> {
            String code = input.getText().toString().trim();
            if (listener != null) {
                boolean ok = listener.onValidateAndSubmit(code, input);
                if (ok) {
                    submitted[0] = true;
                    dialog.dismiss();
                }
            }
        });
        buttonBar.addView(pairBtn);

        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO) {
                pairBtn.performClick();
                return true;
            }
            return false;
        });

        body.addView(buttonBar);
        outer.addView(body);

        dialog.setOnDismissListener(ignored -> {
            if (!submitted[0] && onCancel != null) onCancel.run();
        });

        dialog.setContentView(outer);
        configureDialogWindow(activity, dialog, 0);
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        dialog.show();
        input.requestFocus();
        return dialog;
    }

    /**
     * Shows a slider dialog (Window brightness, cursor sensitivity, stream volume).
     */
    public static Dialog showSlider(
            Activity activity,
            String title,
            int initialValue,
            int max,
            String positiveText,
            String neutralText,
            SliderChangeListener onChange,
            Runnable onNeutral
    ) {
        Dialog dialog = createBaseDialog(activity);
        LinearLayout outer = createOuterCard(activity);
        outer.addView(createHeader(activity, title, dialog));

        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(activity, 6), dp(activity, 10), dp(activity, 6), dp(activity, 10));

        // Readout
        TextView valueText = new TextView(activity);
        valueText.setTextColor(0xFF00E5FF);
        valueText.setTextSize(16f);
        valueText.setTypeface(Typeface.DEFAULT_BOLD);
        valueText.setGravity(Gravity.CENTER);
        valueText.setPadding(0, 0, 0, dp(activity, 12));
        valueText.setText(String.valueOf(initialValue));
        body.addView(valueText);

        // Styled SeekBar
        SeekBar bar = new SeekBar(activity);
        bar.setMax(max);
        bar.setProgress(initialValue);
        if (Build.VERSION.SDK_INT >= 21) {
            bar.setProgressTintList(ColorStateList.valueOf(0xFF00E5FF));
            bar.setThumbTintList(ColorStateList.valueOf(0xFF00E5FF));
        }
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int progress, boolean fromUser) {
                valueText.setText(String.valueOf(progress));
                if (fromUser && onChange != null) onChange.onChanged(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar b) {}
            @Override public void onStopTrackingTouch(SeekBar b) {}
        });
        body.addView(bar);

        LinearLayout buttonBar = new LinearLayout(activity);
        buttonBar.setOrientation(LinearLayout.HORIZONTAL);
        buttonBar.setGravity(Gravity.END);
        buttonBar.setPadding(0, dp(activity, 20), 0, 0);

        if (neutralText != null && onNeutral != null) {
            Button neutralBtn = createPillButton(activity, neutralText, false, () -> {
                onNeutral.run();
                dialog.dismiss();
            });
            buttonBar.addView(neutralBtn);
            View spacer = new View(activity);
            buttonBar.addView(spacer, new LinearLayout.LayoutParams(dp(activity, 10), 1));
        }

        Button doneBtn = createPillButton(activity, positiveText != null ? positiveText : "Done", true, dialog::dismiss);
        buttonBar.addView(doneBtn);

        body.addView(buttonBar);
        outer.addView(body);

        dialog.setContentView(outer);
        configureDialogWindow(activity, dialog, 0);
        dialog.show();
        return dialog;
    }

    /**
     * Shows a message/statistics dialog (e.g. Measured stream statistics).
     */
    public static Dialog showMessage(
            Activity activity,
            String title,
            String message,
            String closeText
    ) {
        Dialog dialog = createBaseDialog(activity);
        LinearLayout outer = createOuterCard(activity);
        outer.addView(createHeader(activity, title, dialog));

        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        int maxScrollHeight = (int)(dm.heightPixels * 0.55f);
        MaxHeightScrollView scroll = new MaxHeightScrollView(activity, maxScrollHeight);

        TextView msgView = new TextView(activity);
        msgView.setText(message != null ? message : "");
        msgView.setTextColor(0xFFE2E8F0);
        msgView.setTextSize(13.5f);
        msgView.setTypeface(Typeface.MONOSPACE);
        msgView.setPadding(dp(activity, 6), dp(activity, 6), dp(activity, 6), dp(activity, 14));
        scroll.addView(msgView);
        outer.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout buttonBar = new LinearLayout(activity);
        buttonBar.setOrientation(LinearLayout.HORIZONTAL);
        buttonBar.setGravity(Gravity.END);
        buttonBar.setPadding(0, dp(activity, 12), 0, 0);

        Button closeBtn = createPillButton(activity, closeText != null ? closeText : "Close", true, dialog::dismiss);
        buttonBar.addView(closeBtn);
        outer.addView(buttonBar);

        dialog.setContentView(outer);
        configureDialogWindow(activity, dialog, maxScrollHeight);
        dialog.show();
        return dialog;
    }

    /**
     * Shows a confirmation dialog (e.g. Forget device).
     */
    public static Dialog showConfirmation(
            Activity activity,
            String title,
            String message,
            String positiveText,
            String negativeText,
            Runnable onPositive
    ) {
        Dialog dialog = createBaseDialog(activity);
        LinearLayout outer = createOuterCard(activity);
        outer.addView(createHeader(activity, title, dialog));

        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(activity, 6), dp(activity, 6), dp(activity, 6), dp(activity, 10));

        if (message != null && !message.isEmpty()) {
            TextView msgView = new TextView(activity);
            msgView.setText(message);
            msgView.setTextColor(0xFFCBD5E1);
            msgView.setTextSize(14f);
            msgView.setPadding(0, 0, 0, dp(activity, 16));
            body.addView(msgView);
        }

        LinearLayout buttonBar = new LinearLayout(activity);
        buttonBar.setOrientation(LinearLayout.HORIZONTAL);
        buttonBar.setGravity(Gravity.END);
        buttonBar.setPadding(0, dp(activity, 8), 0, 0);

        Button cancelBtn = createPillButton(activity, negativeText != null ? negativeText : "Cancel", false, dialog::dismiss);
        buttonBar.addView(cancelBtn);

        View spacer = new View(activity);
        buttonBar.addView(spacer, new LinearLayout.LayoutParams(dp(activity, 10), 1));

        Button confirmBtn = createPillButton(activity, positiveText != null ? positiveText : "Confirm", true, () -> {
            dialog.dismiss();
            if (onPositive != null) onPositive.run();
        });
        buttonBar.addView(confirmBtn);

        body.addView(buttonBar);
        outer.addView(body);

        dialog.setContentView(outer);
        configureDialogWindow(activity, dialog, 0);
        dialog.show();
        return dialog;
    }

    /**
     * Creates a styled pill button with glowing borders or solid gradient.
     */
    private static Button createPillButton(
            Activity activity,
            String text,
            boolean isPrimary,
            Runnable onClick
    ) {
        Button btn = new Button(activity) {
            private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF r = new RectF();

            @Override protected void onDraw(Canvas canvas) {
                float pad = dp(activity, 1.2f);
                float rad = dp(activity, 11f);
                r.set(pad, pad, getWidth() - pad, getHeight() - pad);

                bgPaint.setStyle(Paint.Style.FILL);
                if (isPrimary) {
                    if (isPressed()) {
                        bgPaint.setShader(new LinearGradient(
                                r.left, r.top, r.right, r.bottom, 0xFF38BDF8, 0xFFC084FC, Shader.TileMode.CLAMP));
                    } else {
                        bgPaint.setShader(new LinearGradient(
                                r.left, r.top, r.right, r.bottom, 0xFF00E5FF, 0xFFA855F7, Shader.TileMode.CLAMP));
                    }
                    canvas.drawRoundRect(r, rad, rad, bgPaint);
                } else {
                    bgPaint.setShader(null);
                    bgPaint.setColor(isPressed() ? 0xFF222B52 : 0xFF101736);
                    canvas.drawRoundRect(r, rad, rad, bgPaint);

                    strokePaint.setStyle(Paint.Style.STROKE);
                    strokePaint.setStrokeWidth(dp(activity, 1.2f));
                    strokePaint.setShader(new LinearGradient(
                            r.left, r.top, r.right, r.bottom, 0x6600E5FF, 0x66A855F7, Shader.TileMode.CLAMP));
                    canvas.drawRoundRect(r, rad, rad, strokePaint);
                }

                super.onDraw(canvas);
            }
        };
        btn.setText(text);
        btn.setTextColor(isPrimary ? 0xFF050814 : 0xFFF1F5F9);
        btn.setTextSize(13.5f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setPadding(dp(activity, 16), dp(activity, 6), dp(activity, 16), dp(activity, 6));
        btn.setMinHeight(dp(activity, 38));
        btn.setMinimumHeight(dp(activity, 38));
        btn.setBackgroundTintList(null);
        btn.setBackgroundColor(Color.TRANSPARENT);
        btn.setClickable(true);
        btn.setFocusable(true);
        btn.setOnClickListener(v -> {
            if (onClick != null) onClick.run();
        });
        return btn;
    }

    /**
     * Creates a dark styled EditText with cyan glowing stroke on focus.
     */
    private static EditText createStyledEditText(
            Activity activity,
            String initialText,
            String hint,
            int inputType
    ) {
        EditText edit = new EditText(activity) {
            private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF r = new RectF();

            @Override protected void onDraw(Canvas canvas) {
                float pad = dp(activity, 1.2f);
                float rad = dp(activity, 10f);
                r.set(pad, pad, getWidth() - pad, getHeight() - pad);

                bgPaint.setStyle(Paint.Style.FILL);
                bgPaint.setColor(0xFF090E24);
                canvas.drawRoundRect(r, rad, rad, bgPaint);

                strokePaint.setStyle(Paint.Style.STROKE);
                strokePaint.setStrokeWidth(dp(activity, hasFocus() ? 1.8f : 1.1f));
                if (hasFocus()) {
                    strokePaint.setShader(new LinearGradient(
                            r.left, r.top, r.right, r.bottom, 0xFF00E5FF, 0xFF38BDF8, Shader.TileMode.CLAMP));
                } else {
                    strokePaint.setShader(null);
                    strokePaint.setColor(0xFF263259);
                }
                canvas.drawRoundRect(r, rad, rad, strokePaint);

                super.onDraw(canvas);
            }
        };
        edit.setText(initialText != null ? initialText : "");
        edit.setHint(hint != null ? hint : "");
        edit.setHintTextColor(0xFF64748B);
        edit.setTextColor(Color.WHITE);
        edit.setTextSize(14.5f);
        edit.setInputType(inputType);
        edit.setSingleLine(true);
        edit.setPadding(dp(activity, 14), dp(activity, 10), dp(activity, 14), dp(activity, 10));
        edit.setBackgroundColor(Color.TRANSPARENT);
        edit.setBackgroundTintList(null);
        return edit;
    }

    private static EditText createStyledMultiLineEditText(
            Activity activity,
            String initialText,
            String hint,
            int minLines,
            int maxLines
    ) {
        EditText edit = new EditText(activity) {
            private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF r = new RectF();

            @Override protected void onDraw(Canvas canvas) {
                float pad = dp(activity, 1.2f);
                float rad = dp(activity, 10f);
                r.set(pad, pad, getWidth() - pad, getHeight() - pad);

                bgPaint.setStyle(Paint.Style.FILL);
                bgPaint.setColor(0xFF090E24);
                canvas.drawRoundRect(r, rad, rad, bgPaint);

                strokePaint.setStyle(Paint.Style.STROKE);
                strokePaint.setStrokeWidth(dp(activity, hasFocus() ? 1.8f : 1.1f));
                if (hasFocus()) {
                    strokePaint.setShader(new LinearGradient(
                            r.left, r.top, r.right, r.bottom, 0xFF00E5FF, 0xFF38BDF8, Shader.TileMode.CLAMP));
                } else {
                    strokePaint.setShader(null);
                    strokePaint.setColor(0xFF263259);
                }
                canvas.drawRoundRect(r, rad, rad, strokePaint);

                super.onDraw(canvas);
            }
        };
        edit.setText(initialText != null ? initialText : "");
        edit.setHint(hint != null ? hint : "");
        edit.setHintTextColor(0xFF64748B);
        edit.setTextColor(Color.WHITE);
        edit.setTextSize(14f);
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        edit.setSingleLine(false);
        edit.setMinLines(minLines);
        edit.setMaxLines(maxLines);
        edit.setGravity(Gravity.TOP | Gravity.START);
        edit.setPadding(dp(activity, 14), dp(activity, 10), dp(activity, 14), dp(activity, 10));
        edit.setBackgroundColor(Color.TRANSPARENT);
        edit.setBackgroundTintList(null);
        return edit;
    }

    public interface RemoteConfigSummaryProvider {
        String getSummary();
    }

    public static Dialog showStreamingSettings(
            Activity activity,
            android.content.SharedPreferences prefs,
            boolean includeQuality,
            String connectionStatus,
            RemoteConfigSummaryProvider configSummaryProvider,
            Runnable onConfigureRemotePc,
            Runnable onSettingsChanged
    ) {
        return showStreamingSettings(activity, prefs, includeQuality, connectionStatus, configSummaryProvider, onConfigureRemotePc, null, onSettingsChanged);
    }

    public static Dialog showStreamingSettings(
            Activity activity,
            android.content.SharedPreferences prefs,
            boolean includeQuality,
            String connectionStatus,
            RemoteConfigSummaryProvider configSummaryProvider,
            Runnable onConfigureRemotePc,
            CompanionClient companion,
            Runnable onSettingsChanged
    ) {
        Dialog dialog = createBaseDialog(activity);
        LinearLayout outer = createOuterCard(activity);
        outer.addView(createHeader(activity, includeQuality ? "Streaming settings" : "Remote Access", dialog));

        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        boolean landscape = dm.widthPixels > dm.heightPixels;
        int maxScrollHeight = (int)(dm.heightPixels * (landscape ? 0.76f : 0.74f));

        MaxHeightScrollView scroll = new MaxHeightScrollView(activity, maxScrollHeight);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(activity, 2), 0, dp(activity, 6));

        Runnable[] rebuild = new Runnable[1];
        rebuild[0] = () -> {
            list.removeAllViews();

            if (includeQuality) {
                list.addView(createSectionHeader(activity, "Stream Quality"));
                String[] qualities = {
                    "720p · 30 FPS · battery friendly",
                    "720p · 60 FPS",
                    "1080p · 30 FPS",
                    "1080p · 60 FPS"
                };
                int currentQuality = prefs.getInt("quality", 0);
                for (int i = 0; i < qualities.length; i++) {
                    final int qIndex = i;
                    list.addView(createMenuItemRow(activity, qualities[i], i == currentQuality, true, () -> {
                        prefs.edit().putInt("quality", qIndex).apply();
                        rebuild[0].run();
                    }));
                }
            }

            list.addView(createSectionHeader(activity, "Remote Access"));

            // Connection Status card
            String actualStatus = (connectionStatus != null && !connectionStatus.isEmpty())
                    ? connectionStatus
                    : CompanionClient.STATUS_UNAVAILABLE;
            list.addView(createStatusBanner(activity, actualStatus));

            // Connection Mode subsection
            list.addView(createSubLabel(activity, "Connection Mode"));
            String[] modes = {
                "Automatic",
                "Local Only",
                "Remote Access"
            };
            int currentMode = prefs.getInt("connectionMode", CompanionClient.MODE_AUTOMATIC);
            for (int i = 0; i < modes.length; i++) {
                final int modeIndex = i;
                list.addView(createMenuItemRow(activity, modes[i], i == currentMode, true, () -> {
                    prefs.edit().putInt("connectionMode", modeIndex).apply();
                    if (modeIndex == CompanionClient.MODE_REMOTE_ACCESS && !prefs.getBoolean("remoteAccessEnabled", false)) {
                        prefs.edit().putBoolean("remoteAccessEnabled", true).apply();
                    }
                    if (onSettingsChanged != null) onSettingsChanged.run();
                    rebuild[0].run();
                }));
            }

            // Remote Access Setup subsection
            list.addView(createSubLabel(activity, "Remote Access Setup"));
            boolean remoteEnabled = prefs.getBoolean("remoteAccessEnabled", false);
            list.addView(createMenuItemRow(
                    activity,
                    remoteEnabled ? "Remote Access: Enabled" : "Remote Access: Disabled",
                    remoteEnabled,
                    true,
                    () -> {
                        boolean next = !prefs.getBoolean("remoteAccessEnabled", false);
                        prefs.edit().putBoolean("remoteAccessEnabled", next).apply();
                        if (onSettingsChanged != null) onSettingsChanged.run();
                        rebuild[0].run();
                    }
            ));

            String summaryText = configSummaryProvider != null ? configSummaryProvider.getSummary() : "Remote Connectivity: Not configured";
            TextView configInfo = new TextView(activity);
            configInfo.setText(summaryText + "\nPrivate encrypted network (e.g. Tailscale 100.x.y.z) is required when connecting across different Wi-Fi or mobile networks.");
            configInfo.setTextColor(0xFFCBD5E1);
            configInfo.setTextSize(12.5f);
            configInfo.setPadding(dp(activity, 8), dp(activity, 4), dp(activity, 8), dp(activity, 6));
            list.addView(configInfo);

            list.addView(createMenuItemRow(
                    activity,
                    "Configure Authorized Remote PC",
                    false,
                    true,
                    () -> {
                        dialog.dismiss();
                        if (onConfigureRemotePc != null) onConfigureRemotePc.run();
                    }
            ));

            list.addView(createMenuItemRow(
                    activity,
                    "Remote Access Setup & Troubleshooting",
                    false,
                    true,
                    () -> showRemoteSetupGuide(activity)
            ));

            // Section: Privacy & Feedback
            list.addView(createSectionHeader(activity, "Privacy & Feedback"));

            boolean crashOptedIn = NyxReportingManager.isCrashReportingEnabled(activity);
            list.addView(createMenuItemRow(
                    activity,
                    crashOptedIn ? "Automatic Crash Reports: Enabled" : "Automatic Crash Reports: Disabled (Off)",
                    crashOptedIn,
                    true,
                    () -> {
                        boolean next = !NyxReportingManager.isCrashReportingEnabled(activity);
                        NyxReportingManager.setCrashReportingEnabled(activity, next);
                        rebuild[0].run();
                    }
            ));

            boolean diagOptedIn = NyxReportingManager.isDiagnosticsEnabled(activity);
            list.addView(createMenuItemRow(
                    activity,
                    diagOptedIn ? "Anonymous Diagnostics: Enabled" : "Anonymous Diagnostics: Disabled (Off)",
                    diagOptedIn,
                    true,
                    () -> {
                        boolean next = !NyxReportingManager.isDiagnosticsEnabled(activity);
                        NyxReportingManager.setDiagnosticsEnabled(activity, next);
                        rebuild[0].run();
                    }
            ));

            list.addView(createMenuItemRow(
                    activity,
                    "Report a Bug",
                    false,
                    true,
                    () -> {
                        dialog.dismiss();
                        showBugReportDialog(activity, companion);
                    }
            ));

            list.addView(createMenuItemRow(
                    activity,
                    "Feedback & Suggestions",
                    false,
                    true,
                    () -> {
                        dialog.dismiss();
                        showFeedbackDialog(activity, companion);
                    }
            ));

            list.addView(createMenuItemRow(
                    activity,
                    "Request a Feature",
                    false,
                    true,
                    () -> {
                        dialog.dismiss();
                        showFeatureRequestDialog(activity, companion);
                    }
            ));

            list.addView(createMenuItemRow(
                    activity,
                    "Privacy Policy & Data Transparency",
                    false,
                    true,
                    () -> showPrivacyPolicyDialog(activity)
            ));

            list.addView(createMenuItemRow(
                    activity,
                    "Test Crash Reporter",
                    false,
                    true,
                    () -> {
                        if (!NyxReportingManager.isCrashReportingEnabled(activity)) {
                            showMessage(activity, "Crash Reports Disabled", "Automatic crash reporting is currently OFF. Please enable 'Automatic Crash Reports' above first to verify crash capture.", null);
                        } else {
                            showConfirmation(activity, "Trigger Test Crash", "This will generate a controlled test crash to verify that crash reports are captured and queued. Proceed?", "Trigger Test", "Cancel", () -> {
                                boolean ok = NyxReportingManager.triggerTestCrash(activity);
                                if (ok) {
                                    showMessage(activity, "Test Crash Captured", "A test crash was successfully generated and stored in pending reports for transmission to your PC dashboard.", null);
                                }
                            });
                        }
                    }
            ));
        };

        rebuild[0].run();
        scroll.addView(list);
        outer.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        dialog.setContentView(outer);
        configureDialogWindow(activity, dialog, maxScrollHeight);
        dialog.show();
        return dialog;
    }

    public static Dialog showBugReportDialog(Activity activity, CompanionClient companion) {
        Dialog dialog = createBaseDialog(activity);
        LinearLayout outer = createOuterCard(activity);
        outer.addView(createHeader(activity, "Report a Bug", dialog));

        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        boolean landscape = dm.widthPixels > dm.heightPixels;
        int maxScrollHeight = (int)(dm.heightPixels * (landscape ? 0.78f : 0.74f));

        MaxHeightScrollView scroll = new MaxHeightScrollView(activity, maxScrollHeight);
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(activity, 4), dp(activity, 4), dp(activity, 4), dp(activity, 8));

        SharedPreferences prefs = activity.getSharedPreferences(NyxReportingManager.PREFS_NAME, Context.MODE_PRIVATE);

        // Problem Title
        body.addView(createSubLabel(activity, "Problem Title *"));
        String draftTitle = prefs.getString(NyxReportingManager.KEY_BUG_TITLE_DRAFT, "");
        EditText titleInput = createStyledEditText(activity, draftTitle, "e.g. Video frozen after app switch", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        body.addView(titleInput);

        // Detailed Explanation
        body.addView(createSubLabel(activity, "Detailed Explanation *"));
        String draftDesc = prefs.getString(NyxReportingManager.KEY_BUG_DESC_DRAFT, "");
        EditText descInput = createStyledMultiLineEditText(activity, draftDesc, "What happened? What did you expect to happen?", 3, 6);
        body.addView(descInput);

        // Steps to Reproduce
        body.addView(createSubLabel(activity, "Steps to Reproduce (Optional)"));
        String draftSteps = prefs.getString(NyxReportingManager.KEY_BUG_STEPS_DRAFT, "");
        EditText stepsInput = createStyledMultiLineEditText(activity, draftSteps, "1. Connect to PC\n2. Open menu\n3. Tap...", 3, 5);
        body.addView(stepsInput);

        // Optional Screenshot Attachment
        body.addView(createSubLabel(activity, "Screenshot Attachment (Optional)"));
        final String[] attachedBase64 = { null };
        final Bitmap[] attachedBitmap = { null };

        LinearLayout previewContainer = new LinearLayout(activity);
        previewContainer.setOrientation(LinearLayout.VERTICAL);
        previewContainer.setVisibility(View.GONE);
        previewContainer.setPadding(0, dp(activity, 4), 0, dp(activity, 6));

        ImageView previewImg = new ImageView(activity);
        previewImg.setScaleType(ImageView.ScaleType.FIT_CENTER);
        previewImg.setAdjustViewBounds(true);
        previewImg.setMaxHeight(dp(activity, 150));
        previewContainer.addView(previewImg, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 140)));

        Button removeImgBtn = createPillButton(activity, "✕ Remove Screenshot", false, () -> {
            attachedBase64[0] = null;
            if (attachedBitmap[0] != null) {
                attachedBitmap[0].recycle();
                attachedBitmap[0] = null;
            }
            previewImg.setImageBitmap(null);
            previewContainer.setVisibility(View.GONE);
        });
        removeImgBtn.setTextColor(0xFFF87171);
        previewContainer.addView(removeImgBtn);

        Button attachBtn = createPillButton(activity, "📎 Attach Screenshot", false, () -> {
            prefs.edit()
                .putString(NyxReportingManager.KEY_BUG_TITLE_DRAFT, titleInput.getText().toString().trim())
                .putString(NyxReportingManager.KEY_BUG_DESC_DRAFT, descInput.getText().toString().trim())
                .putString(NyxReportingManager.KEY_BUG_STEPS_DRAFT, stepsInput.getText().toString().trim())
                .apply();
            try {
                setScreenshotReceiver((bmp, base64) -> {
                    attachedBitmap[0] = bmp;
                    attachedBase64[0] = base64;
                    activity.runOnUiThread(() -> {
                        previewImg.setImageBitmap(bmp);
                        previewContainer.setVisibility(View.VISIBLE);
                    });
                });
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                activity.startActivityForResult(Intent.createChooser(intent, "Select Screenshot"), REQUEST_PICK_SCREENSHOT);
            } catch (Exception e) {
                Toast.makeText(activity, "Unable to open photo picker: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        body.addView(attachBtn);
        body.addView(previewContainer);

        // Contact Email
        body.addView(createSubLabel(activity, "Contact Email (Optional)"));
        String draftEmail = prefs.getString(NyxReportingManager.KEY_BUG_EMAIL_DRAFT, "");
        EditText emailInput = createStyledEditText(activity, draftEmail, "user@example.com (for follow-up only)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        body.addView(emailInput);

        // Error message view
        TextView errorLabel = new TextView(activity);
        errorLabel.setTextColor(0xFFF87171);
        errorLabel.setTextSize(13f);
        errorLabel.setVisibility(View.GONE);
        errorLabel.setPadding(dp(activity, 4), dp(activity, 6), dp(activity, 4), 0);
        body.addView(errorLabel);

        // Button bar
        LinearLayout buttonBar = new LinearLayout(activity);
        buttonBar.setOrientation(LinearLayout.HORIZONTAL);
        buttonBar.setGravity(Gravity.END);
        buttonBar.setPadding(0, dp(activity, 14), 0, 0);

        Button cancelBtn = createPillButton(activity, "Cancel", false, dialog::dismiss);
        buttonBar.addView(cancelBtn);

        View spacer = new View(activity);
        buttonBar.addView(spacer, new LinearLayout.LayoutParams(dp(activity, 10), 1));

        Button submitBtn = createPillButton(activity, "Submit Bug Report", true, null);
        submitBtn.setOnClickListener(v -> {
            String title = titleInput.getText().toString().trim();
            String desc = descInput.getText().toString().trim();
            String steps = stepsInput.getText().toString().trim();
            String email = emailInput.getText().toString().trim();

            if (title.isEmpty()) {
                errorLabel.setText("Please enter a short problem title.");
                errorLabel.setVisibility(View.VISIBLE);
                titleInput.requestFocus();
                return;
            }
            if (desc.isEmpty()) {
                errorLabel.setText("Please describe what happened.");
                errorLabel.setVisibility(View.VISIBLE);
                descInput.requestFocus();
                return;
            }

            errorLabel.setVisibility(View.GONE);
            submitBtn.setEnabled(false);
            submitBtn.setText("Submitting…");

            try {
                JSONObject report = new JSONObject();
                report.put("id", UUID.randomUUID().toString().replace("-", ""));
                report.put("type", "bug");
                report.put("title", NyxReportingManager.sanitize(title));
                report.put("description", NyxReportingManager.sanitize(desc));
                if (!steps.isEmpty()) report.put("stepsToReproduce", NyxReportingManager.sanitize(steps));
                if (!email.isEmpty()) report.put("contactEmail", NyxReportingManager.sanitize(email));
                if (attachedBase64[0] != null) report.put("screenshotBase64", attachedBase64[0]);
                report.put("appVersion", NyxReportingManager.getAppVersion(activity));
                report.put("androidVersion", "Android " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")");
                report.put("deviceModel", Build.MANUFACTURER + " " + Build.MODEL);
                report.put("status", "new");
                if (NyxReportingManager.isDiagnosticsEnabled(activity)) {
                    report.put("diagnostics", NyxReportingManager.gatherDiagnostics(activity));
                }

                NyxReportingManager.submitReport(activity, companion, report, new NyxReportingManager.SubmissionCallback() {
                    @Override public void onSuccess(String reportId) {
                        prefs.edit()
                            .remove(NyxReportingManager.KEY_BUG_TITLE_DRAFT)
                            .remove(NyxReportingManager.KEY_BUG_DESC_DRAFT)
                            .remove(NyxReportingManager.KEY_BUG_STEPS_DRAFT)
                            .remove(NyxReportingManager.KEY_BUG_EMAIL_DRAFT)
                            .apply();
                        activity.runOnUiThread(() -> {
                            dialog.dismiss();
                            showMessage(activity, "Bug Report Submitted", "Thank you! Your bug report has been received and can be reviewed in the PC Developer Dashboard.", null);
                        });
                    }
                    @Override public void onError(Exception error, boolean savedLocally) {
                        prefs.edit()
                            .putString(NyxReportingManager.KEY_BUG_TITLE_DRAFT, title)
                            .putString(NyxReportingManager.KEY_BUG_DESC_DRAFT, desc)
                            .putString(NyxReportingManager.KEY_BUG_STEPS_DRAFT, steps)
                            .putString(NyxReportingManager.KEY_BUG_EMAIL_DRAFT, email)
                            .apply();
                        activity.runOnUiThread(() -> {
                            dialog.dismiss();
                            showMessage(activity, "Saved Locally", "PC Companion is currently offline or unreachable. Your report has been saved locally and will be submitted automatically once reconnected.", null);
                        });
                    }
                });
            } catch (Exception ex) {
                errorLabel.setText("Failed to construct report: " + ex.getMessage());
                errorLabel.setVisibility(View.VISIBLE);
                submitBtn.setEnabled(true);
                submitBtn.setText("Submit Bug Report");
            }
        });
        buttonBar.addView(submitBtn);

        body.addView(buttonBar);
        scroll.addView(body);
        outer.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        dialog.setContentView(outer);
        configureDialogWindow(activity, dialog, maxScrollHeight);
        dialog.setOnDismissListener(d -> setScreenshotReceiver(null));
        dialog.show();
        return dialog;
    }

    public static Dialog showFeedbackDialog(Activity activity, CompanionClient companion) {
        Dialog dialog = createBaseDialog(activity);
        LinearLayout outer = createOuterCard(activity);
        outer.addView(createHeader(activity, "Feedback & Suggestions", dialog));

        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        boolean landscape = dm.widthPixels > dm.heightPixels;
        int maxScrollHeight = (int)(dm.heightPixels * (landscape ? 0.76f : 0.70f));

        MaxHeightScrollView scroll = new MaxHeightScrollView(activity, maxScrollHeight);
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(activity, 4), dp(activity, 4), dp(activity, 4), dp(activity, 8));

        SharedPreferences prefs = activity.getSharedPreferences(NyxReportingManager.PREFS_NAME, Context.MODE_PRIVATE);

        // Rating
        body.addView(createSubLabel(activity, "Satisfaction Rating (Optional)"));
        int[] selectedRating = { 0 };
        TextView[] starViews = new TextView[5];
        LinearLayout starContainer = new LinearLayout(activity);
        starContainer.setOrientation(LinearLayout.HORIZONTAL);
        starContainer.setGravity(Gravity.CENTER);
        starContainer.setPadding(0, dp(activity, 4), 0, dp(activity, 8));

        Runnable updateStars = () -> {
            for (int i = 0; i < 5; i++) {
                if (i < selectedRating[0]) {
                    starViews[i].setText("★");
                    starViews[i].setTextColor(0xFFFFD700);
                } else {
                    starViews[i].setText("☆");
                    starViews[i].setTextColor(0xFF475569);
                }
            }
        };

        for (int i = 0; i < 5; i++) {
            final int starVal = i + 1;
            TextView star = new TextView(activity);
            star.setText("☆");
            star.setTextSize(32f);
            star.setPadding(dp(activity, 6), 0, dp(activity, 6), 0);
            star.setTextColor(0xFF475569);
            star.setClickable(true);
            star.setFocusable(true);
            star.setOnClickListener(v -> {
                if (selectedRating[0] == starVal) {
                    selectedRating[0] = 0;
                } else {
                    selectedRating[0] = starVal;
                }
                updateStars.run();
            });
            starViews[i] = star;
            starContainer.addView(star);
        }
        body.addView(starContainer);

        // Comments
        body.addView(createSubLabel(activity, "Your Comments & Thoughts *"));
        String draftComments = prefs.getString(NyxReportingManager.KEY_FEEDBACK_COMMENTS_DRAFT, "");
        EditText commentsInput = createStyledMultiLineEditText(activity, draftComments, "Share what you love or how NyxLink can improve...", 4, 7);
        body.addView(commentsInput);

        // Email
        body.addView(createSubLabel(activity, "Contact Email (Optional)"));
        String draftEmail = prefs.getString(NyxReportingManager.KEY_FEEDBACK_EMAIL_DRAFT, "");
        EditText emailInput = createStyledEditText(activity, draftEmail, "user@example.com (optional)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        body.addView(emailInput);

        TextView errorLabel = new TextView(activity);
        errorLabel.setTextColor(0xFFF87171);
        errorLabel.setTextSize(13f);
        errorLabel.setVisibility(View.GONE);
        errorLabel.setPadding(dp(activity, 4), dp(activity, 6), dp(activity, 4), 0);
        body.addView(errorLabel);

        // Button bar
        LinearLayout buttonBar = new LinearLayout(activity);
        buttonBar.setOrientation(LinearLayout.HORIZONTAL);
        buttonBar.setGravity(Gravity.END);
        buttonBar.setPadding(0, dp(activity, 14), 0, 0);

        Button cancelBtn = createPillButton(activity, "Cancel", false, dialog::dismiss);
        buttonBar.addView(cancelBtn);

        View spacer = new View(activity);
        buttonBar.addView(spacer, new LinearLayout.LayoutParams(dp(activity, 10), 1));

        Button submitBtn = createPillButton(activity, "Submit Feedback", true, null);
        submitBtn.setOnClickListener(v -> {
            String comments = commentsInput.getText().toString().trim();
            String email = emailInput.getText().toString().trim();

            if (comments.isEmpty()) {
                errorLabel.setText("Please enter your comments or feedback.");
                errorLabel.setVisibility(View.VISIBLE);
                commentsInput.requestFocus();
                return;
            }

            errorLabel.setVisibility(View.GONE);
            submitBtn.setEnabled(false);
            submitBtn.setText("Submitting…");

            try {
                JSONObject report = new JSONObject();
                report.put("id", UUID.randomUUID().toString().replace("-", ""));
                report.put("type", "feedback");
                report.put("title", "User Feedback" + (selectedRating[0] > 0 ? " (" + selectedRating[0] + "/5★)" : ""));
                report.put("description", NyxReportingManager.sanitize(comments));
                if (selectedRating[0] > 0) report.put("rating", selectedRating[0]);
                if (!email.isEmpty()) report.put("contactEmail", NyxReportingManager.sanitize(email));
                report.put("appVersion", NyxReportingManager.getAppVersion(activity));
                report.put("androidVersion", "Android " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")");
                report.put("deviceModel", Build.MANUFACTURER + " " + Build.MODEL);
                report.put("status", "new");

                NyxReportingManager.submitReport(activity, companion, report, new NyxReportingManager.SubmissionCallback() {
                    @Override public void onSuccess(String reportId) {
                        prefs.edit()
                            .remove(NyxReportingManager.KEY_FEEDBACK_COMMENTS_DRAFT)
                            .remove(NyxReportingManager.KEY_FEEDBACK_EMAIL_DRAFT)
                            .apply();
                        activity.runOnUiThread(() -> {
                            dialog.dismiss();
                            showMessage(activity, "Feedback Submitted", "Thank you for your feedback! Your comments have been sent to your PC Developer Dashboard.", null);
                        });
                    }
                    @Override public void onError(Exception error, boolean savedLocally) {
                        prefs.edit()
                            .putString(NyxReportingManager.KEY_FEEDBACK_COMMENTS_DRAFT, comments)
                            .putString(NyxReportingManager.KEY_FEEDBACK_EMAIL_DRAFT, email)
                            .apply();
                        activity.runOnUiThread(() -> {
                            dialog.dismiss();
                            showMessage(activity, "Saved Locally", "PC Companion is currently offline. Your feedback is saved locally and will be delivered once connected.", null);
                        });
                    }
                });
            } catch (Exception ex) {
                errorLabel.setText("Failed: " + ex.getMessage());
                errorLabel.setVisibility(View.VISIBLE);
                submitBtn.setEnabled(true);
                submitBtn.setText("Submit Feedback");
            }
        });
        buttonBar.addView(submitBtn);

        body.addView(buttonBar);
        scroll.addView(body);
        outer.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        dialog.setContentView(outer);
        configureDialogWindow(activity, dialog, maxScrollHeight);
        dialog.show();
        return dialog;
    }

    public static Dialog showFeatureRequestDialog(Activity activity, CompanionClient companion) {
        Dialog dialog = createBaseDialog(activity);
        LinearLayout outer = createOuterCard(activity);
        outer.addView(createHeader(activity, "Request a Feature", dialog));

        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        boolean landscape = dm.widthPixels > dm.heightPixels;
        int maxScrollHeight = (int)(dm.heightPixels * (landscape ? 0.78f : 0.72f));

        MaxHeightScrollView scroll = new MaxHeightScrollView(activity, maxScrollHeight);
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(activity, 4), dp(activity, 4), dp(activity, 4), dp(activity, 8));

        SharedPreferences prefs = activity.getSharedPreferences(NyxReportingManager.PREFS_NAME, Context.MODE_PRIVATE);

        // Feature Title
        body.addView(createSubLabel(activity, "Feature Name / Title *"));
        String draftTitle = prefs.getString(NyxReportingManager.KEY_FEATURE_TITLE_DRAFT, "");
        EditText titleInput = createStyledEditText(activity, draftTitle, "e.g. Haptic feedback on touch controls", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        body.addView(titleInput);

        // Feature Description
        body.addView(createSubLabel(activity, "Detailed Description *"));
        String draftDesc = prefs.getString(NyxReportingManager.KEY_FEATURE_DESC_DRAFT, "");
        EditText descInput = createStyledMultiLineEditText(activity, draftDesc, "Describe what capability you would like added...", 3, 6);
        body.addView(descInput);

        // Why useful
        body.addView(createSubLabel(activity, "Why would this feature be useful?"));
        String draftReason = prefs.getString(NyxReportingManager.KEY_FEATURE_REASON_DRAFT, "");
        EditText reasonInput = createStyledMultiLineEditText(activity, draftReason, "How would this improve your experience or workflow?", 3, 5);
        body.addView(reasonInput);

        // Contact Email
        body.addView(createSubLabel(activity, "Contact Email (Optional)"));
        String draftEmail = prefs.getString(NyxReportingManager.KEY_FEATURE_EMAIL_DRAFT, "");
        EditText emailInput = createStyledEditText(activity, draftEmail, "user@example.com (optional)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        body.addView(emailInput);

        TextView errorLabel = new TextView(activity);
        errorLabel.setTextColor(0xFFF87171);
        errorLabel.setTextSize(13f);
        errorLabel.setVisibility(View.GONE);
        errorLabel.setPadding(dp(activity, 4), dp(activity, 6), dp(activity, 4), 0);
        body.addView(errorLabel);

        // Button bar
        LinearLayout buttonBar = new LinearLayout(activity);
        buttonBar.setOrientation(LinearLayout.HORIZONTAL);
        buttonBar.setGravity(Gravity.END);
        buttonBar.setPadding(0, dp(activity, 14), 0, 0);

        Button cancelBtn = createPillButton(activity, "Cancel", false, dialog::dismiss);
        buttonBar.addView(cancelBtn);

        View spacer = new View(activity);
        buttonBar.addView(spacer, new LinearLayout.LayoutParams(dp(activity, 10), 1));

        Button submitBtn = createPillButton(activity, "Submit Feature Request", true, null);
        submitBtn.setOnClickListener(v -> {
            String title = titleInput.getText().toString().trim();
            String desc = descInput.getText().toString().trim();
            String reason = reasonInput.getText().toString().trim();
            String email = emailInput.getText().toString().trim();

            if (title.isEmpty()) {
                errorLabel.setText("Please enter a feature name / title.");
                errorLabel.setVisibility(View.VISIBLE);
                titleInput.requestFocus();
                return;
            }
            if (desc.isEmpty()) {
                errorLabel.setText("Please describe the requested feature.");
                errorLabel.setVisibility(View.VISIBLE);
                descInput.requestFocus();
                return;
            }

            errorLabel.setVisibility(View.GONE);
            submitBtn.setEnabled(false);
            submitBtn.setText("Submitting…");

            try {
                JSONObject report = new JSONObject();
                report.put("id", UUID.randomUUID().toString().replace("-", ""));
                report.put("type", "feature");
                report.put("title", NyxReportingManager.sanitize(title));
                report.put("description", NyxReportingManager.sanitize(desc));
                if (!reason.isEmpty()) report.put("utilityReason", NyxReportingManager.sanitize(reason));
                if (!email.isEmpty()) report.put("contactEmail", NyxReportingManager.sanitize(email));
                report.put("appVersion", NyxReportingManager.getAppVersion(activity));
                report.put("androidVersion", "Android " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")");
                report.put("deviceModel", Build.MANUFACTURER + " " + Build.MODEL);
                report.put("status", "new");

                NyxReportingManager.submitReport(activity, companion, report, new NyxReportingManager.SubmissionCallback() {
                    @Override public void onSuccess(String reportId) {
                        prefs.edit()
                            .remove(NyxReportingManager.KEY_FEATURE_TITLE_DRAFT)
                            .remove(NyxReportingManager.KEY_FEATURE_DESC_DRAFT)
                            .remove(NyxReportingManager.KEY_FEATURE_REASON_DRAFT)
                            .remove(NyxReportingManager.KEY_FEATURE_EMAIL_DRAFT)
                            .apply();
                        activity.runOnUiThread(() -> {
                            dialog.dismiss();
                            showMessage(activity, "Feature Request Submitted", "Thank you! Your feature idea has been sent to the PC Developer Dashboard for consideration.", null);
                        });
                    }
                    @Override public void onError(Exception error, boolean savedLocally) {
                        prefs.edit()
                            .putString(NyxReportingManager.KEY_FEATURE_TITLE_DRAFT, title)
                            .putString(NyxReportingManager.KEY_FEATURE_DESC_DRAFT, desc)
                            .putString(NyxReportingManager.KEY_FEATURE_REASON_DRAFT, reason)
                            .putString(NyxReportingManager.KEY_FEATURE_EMAIL_DRAFT, email)
                            .apply();
                        activity.runOnUiThread(() -> {
                            dialog.dismiss();
                            showMessage(activity, "Saved Locally", "PC Companion is currently offline. Your feature suggestion has been saved locally and will be delivered once connected.", null);
                        });
                    }
                });
            } catch (Exception ex) {
                errorLabel.setText("Failed: " + ex.getMessage());
                errorLabel.setVisibility(View.VISIBLE);
                submitBtn.setEnabled(true);
                submitBtn.setText("Submit Feature Request");
            }
        });
        buttonBar.addView(submitBtn);

        body.addView(buttonBar);
        scroll.addView(body);
        outer.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        dialog.setContentView(outer);
        configureDialogWindow(activity, dialog, maxScrollHeight);
        dialog.show();
        return dialog;
    }

    public static Dialog showPrivacyPolicyDialog(Activity activity) {
        String policy =
                "OUR PRIVACY COMMITMENT\n" +
                "NyxLink operates strictly as a private streaming link between your phone and your own Windows PC. Your desktop session remains private and encrypted.\n\n" +
                "DATA WE NEVER COLLECT OR TRANSMIT:\n" +
                "• Remote PC desktop display contents, video streams, or audio playback.\n" +
                "• Keystrokes, text input, or clipboard contents.\n" +
                "• Passwords, PINs, pairing tokens, or private certificates.\n" +
                "• Contacts, browsing history, files, or location data.\n\n" +
                "OPT-IN CRASH REPORTING & DIAGNOSTICS:\n" +
                "• Automatic Crash Reports are OFF by default. When enabled, unexpected application exceptions capture a sanitized stack trace, OS version, and device model to help fix crashes.\n" +
                "• Anonymous Diagnostics are OFF by default. When enabled, basic memory and resolution info are attached to reports.\n" +
                "• You can opt out at any time in Settings. Turning off reporting immediately deletes any queued pending reports on this device.\n\n" +
                "VOLUNTARY FEEDBACK & BUG REPORTS:\n" +
                "• Information you submit via Bug Report, Feedback, or Feature Request forms is transmitted directly to your paired Windows PC companion over encrypted mutual TLS.\n" +
                "• Screenshot attachments are strictly voluntary and never captured automatically.\n\n" +
                "PC DEVELOPER DASHBOARD & DATA DELETION:\n" +
                "• Reports are stored securely on your PC in reports.json.\n" +
                "• You can review, filter, export, or permanently delete any or all submitted reports anytime via the 'Developer Dashboard' button in the NyxLink Windows app.";
        return showMessage(activity, "Privacy Policy & Transparency", policy, "Understood");
    }

    public static Dialog showRemoteSetupGuide(Activity activity) {
        String guide =
                "WHEN A PRIVATE NETWORK IS REQUIRED\n" +
                "• Local Wi-Fi: No external software is required when your phone and PC share the same home Wi-Fi.\n" +
                "• Different Wi-Fi / Mobile Data: A private encrypted mesh network such as Tailscale (WireGuard) is required on both your Windows PC and Android phone.\n\n" +
                "INITIAL SETUP (ONE TIME)\n" +
                "1. Pair your phone with your Windows PC while on the same local Wi-Fi.\n" +
                "2. Install Tailscale on Windows and Android, and sign into the same account on both devices.\n" +
                "3. Keep NyxLink running on your Windows PC.\n" +
                "4. In NyxLink → Streaming settings → Remote Access:\n" +
                "   • Enable Remote Access.\n" +
                "   • Tap 'Configure Authorized Remote PC' and enter your PC's Tailscale IPv4 address (100.x.y.z) if not auto-detected.\n" +
                "   • Set Connection Mode to Automatic (Recommended) or Remote Access.\n\n" +
                "TROUBLESHOOTING\n" +
                "• Connection Unavailable: Verify Tailscale VPN is active on both your PC and Android phone, and that NyxLink is running on the PC.\n" +
                "• Windows Firewall: Run Configure-Firewall.ps1 or allow NyxLink-Windows.exe on private/Tailscale networks (TCP 47984, 47989, 47995, 48010; UDP 47996, 47998-48000).\n" +
                "• Authentication Rejected: If the device was revoked on the PC, use 'Forget device' on the phone and pair again on local Wi-Fi.";
        return showMessage(activity, "Remote Access Setup & Troubleshooting", guide, "Close");
    }

    private static TextView createSectionHeader(Activity activity, String text) {
        TextView tv = new TextView(activity);
        tv.setText(text);
        tv.setTextColor(0xFF00E5FF);
        tv.setTextSize(13.5f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(dp(activity, 6), dp(activity, 10), dp(activity, 6), dp(activity, 4));
        return tv;
    }

    private static TextView createSubLabel(Activity activity, String text) {
        TextView tv = new TextView(activity);
        tv.setText(text);
        tv.setTextColor(0xFFC084FC);
        tv.setTextSize(12.5f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(dp(activity, 6), dp(activity, 8), dp(activity, 6), dp(activity, 2));
        return tv;
    }

    private static View createStatusBanner(Activity activity, String statusText) {
        LinearLayout card = new LinearLayout(activity) {
            private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF r = new RectF();
            @Override protected void onDraw(Canvas canvas) {
                float pad = dp(activity, 1.2f);
                float rad = dp(activity, 11f);
                r.set(pad, pad, getWidth() - pad, getHeight() - pad);
                bg.setStyle(Paint.Style.FILL);
                bg.setColor(0xFF0B122B);
                canvas.drawRoundRect(r, rad, rad, bg);
                stroke.setStyle(Paint.Style.STROKE);
                stroke.setStrokeWidth(dp(activity, 1.2f));
                stroke.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                        0x6600E5FF, 0x66A855F7, Shader.TileMode.CLAMP));
                canvas.drawRoundRect(r, rad, rad, stroke);
                super.onDraw(canvas);
            }
        };
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(activity, 12), dp(activity, 9), dp(activity, 12), dp(activity, 9));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(activity, 2), dp(activity, 3), dp(activity, 2), dp(activity, 4));
        card.setLayoutParams(lp);

        TextView label = new TextView(activity);
        label.setText("Connection Status");
        label.setTextColor(0xFF94A3B8);
        label.setTextSize(13f);
        card.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView value = new TextView(activity);
        value.setText(statusText);
        boolean active = CompanionClient.STATUS_CONNECTED_LOCALLY.equals(statusText)
                || CompanionClient.STATUS_CONNECTED_REMOTELY.equals(statusText);
        boolean busy = CompanionClient.STATUS_CONNECTING.equals(statusText)
                || CompanionClient.STATUS_RECONNECTING.equals(statusText);
        value.setTextColor(active ? 0xFF00E5FF : (busy ? 0xFFFACC15 : 0xFFE2E8F0));
        value.setTextSize(13.5f);
        value.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(value);
        return card;
    }
}
