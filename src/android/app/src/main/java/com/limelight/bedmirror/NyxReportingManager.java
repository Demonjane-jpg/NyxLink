package com.limelight.bedmirror;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Manages crash reporting, bug reports, user feedback, and anonymous diagnostics.
 * Automatic reporting is strictly opt-in and off by default.
 * No streamed desktop content, audio, keystrokes, clipboard, or credentials are ever collected.
 */
public final class NyxReportingManager {
    private static final String TAG = "NyxReporting";
    public static final String PREFS_NAME = "nyxlink_privacy_feedback_prefs";
    public static final String KEY_CRASH_REPORTS = "crash_reporting_enabled";
    public static final String KEY_DIAGNOSTICS = "anonymous_diagnostics_enabled";
    public static final String KEY_BUG_TITLE_DRAFT = "draft_bug_title";
    public static final String KEY_BUG_DESC_DRAFT = "draft_bug_description";
    public static final String KEY_BUG_STEPS_DRAFT = "draft_bug_steps";
    public static final String KEY_BUG_EMAIL_DRAFT = "draft_bug_email";
    public static final String KEY_FEEDBACK_COMMENTS_DRAFT = "draft_feedback_comments";
    public static final String KEY_FEEDBACK_EMAIL_DRAFT = "draft_feedback_email";
    public static final String KEY_FEATURE_TITLE_DRAFT = "draft_feature_title";
    public static final String KEY_FEATURE_DESC_DRAFT = "draft_feature_description";
    public static final String KEY_FEATURE_REASON_DRAFT = "draft_feature_reason";
    public static final String KEY_FEATURE_EMAIL_DRAFT = "draft_feature_email";

    private static final String PENDING_FILE = "nyxlink_pending_reports.json";
    private static final int MAX_PENDING_REPORTS = 50;

    private static final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private static boolean initialized = false;
    private static Thread.UncaughtExceptionHandler defaultHandler;
    private static Context appContext;

    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
            "(?i)(bearer\\s+|token[=:]\\s*|password[=:]\\s*|secret[=:]\\s*|pin[=:]\\s*)[^\\s,;&\"]+"
    );
    private static final Pattern KEY_PATTERN = Pattern.compile(
            "-----BEGIN [A-Z ]+PRIVATE KEY-----[^-]+-----END [A-Z ]+PRIVATE KEY-----"
    );

    public interface SubmissionCallback {
        void onSuccess(String reportId);
        void onError(Exception error, boolean savedLocally);
    }

    private NyxReportingManager() {}

    public static synchronized void init(Context context) {
        if (initialized) return;
        appContext = context.getApplicationContext();
        initialized = true;

        defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                if (isCrashReportingEnabled(appContext)) {
                    recordCrashSync(appContext, thread, throwable);
                }
            } catch (Throwable t) {
                Log.e(TAG, "Failed to capture crash", t);
            } finally {
                if (defaultHandler != null) {
                    defaultHandler.uncaughtException(thread, throwable);
                }
            }
        });
    }

    public static boolean isCrashReportingEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_CRASH_REPORTS, false);
    }

    public static void setCrashReportingEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_CRASH_REPORTS, enabled).apply();
        if (!enabled) {
            clearPendingReports(context);
        }
    }

    public static boolean isDiagnosticsEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_DIAGNOSTICS, false);
    }

    public static void setDiagnosticsEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_DIAGNOSTICS, enabled).apply();
        if (!enabled && !isCrashReportingEnabled(context)) {
            clearPendingReports(context);
        }
    }

    public static String sanitize(String text) {
        if (text == null || text.isEmpty()) return "";
        String sanitized = SENSITIVE_PATTERN.matcher(text).replaceAll("$1[REDACTED]");
        sanitized = KEY_PATTERN.matcher(sanitized).replaceAll("[REDACTED_PRIVATE_KEY]");
        return sanitized;
    }

    public static String getAppVersion(Context context) {
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return pInfo.versionName + " (" + pInfo.versionCode + ")";
        } catch (Exception e) {
            return "unknown";
        }
    }

    public static String gatherDiagnostics(Context context) {
        StringBuilder sb = new StringBuilder();
        try {
            ActivityManager actMgr = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (actMgr != null) {
                ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
                actMgr.getMemoryInfo(memInfo);
                long availMB = memInfo.availMem / (1024 * 1024);
                long totalMB = memInfo.totalMem / (1024 * 1024);
                sb.append("Memory: ").append(availMB).append("MB free / ").append(totalMB).append("MB total");
                sb.append(" (lowMemory=").append(memInfo.lowMemory).append(")\n");
            }
            DisplayMetrics dm = context.getResources().getDisplayMetrics();
            sb.append("Display: ").append(dm.widthPixels).append("x").append(dm.heightPixels)
              .append(" (density=").append(dm.densityDpi).append("dpi)\n");
            sb.append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL)
              .append(" (Board: ").append(Build.BOARD).append(", Hardware: ").append(Build.HARDWARE).append(")\n");
            sb.append("Android OS: ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n");
        } catch (Exception e) {
            sb.append("Diagnostics error: ").append(e.getMessage());
        }
        return sanitize(sb.toString());
    }

    private static String getIsoTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }

    public static synchronized void recordCrashSync(Context context, Thread thread, Throwable throwable) {
        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            throwable.printStackTrace(pw);
            String stackTrace = sanitize(sw.toString());

            String title = throwable.getClass().getSimpleName();
            if (throwable.getMessage() != null && !throwable.getMessage().isEmpty()) {
                title += ": " + throwable.getMessage();
            }

            JSONObject report = new JSONObject();
            report.put("id", UUID.randomUUID().toString().replace("-", ""));
            report.put("type", "crash");
            report.put("timestamp", getIsoTimestamp());
            report.put("title", sanitize(title));
            report.put("description", "Uncaught exception in thread: " + (thread != null ? thread.getName() : "unknown"));
            report.put("appVersion", getAppVersion(context));
            report.put("androidVersion", "Android " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")");
            report.put("deviceModel", Build.MANUFACTURER + " " + Build.MODEL);
            report.put("stackTrace", stackTrace);
            report.put("status", "new");

            if (isDiagnosticsEnabled(context)) {
                report.put("diagnostics", gatherDiagnostics(context));
            }

            enqueuePendingReport(context, report);
        } catch (Exception e) {
            Log.e(TAG, "Error writing crash report", e);
        }
    }

    public static void recordDecoderError(Context context, String decoderName, Throwable error) {
        if (!isCrashReportingEnabled(context)) return;
        backgroundExecutor.execute(() -> {
            try {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                error.printStackTrace(pw);

                JSONObject report = new JSONObject();
                report.put("id", UUID.randomUUID().toString().replace("-", ""));
                report.put("type", "crash");
                report.put("timestamp", getIsoTimestamp());
                report.put("title", sanitize("MediaCodec Decoder Failure: " + decoderName));
                report.put("description", sanitize("Decoder crashed or returned fatal state during stream playback"));
                report.put("appVersion", getAppVersion(context));
                report.put("androidVersion", "Android " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")");
                report.put("deviceModel", Build.MANUFACTURER + " " + Build.MODEL);
                report.put("stackTrace", sanitize(sw.toString()));
                report.put("status", "new");

                if (isDiagnosticsEnabled(context)) {
                    report.put("diagnostics", "Decoder: " + decoderName + "\n" + gatherDiagnostics(context));
                }

                enqueuePendingReport(context, report);
            } catch (Exception e) {
                Log.e(TAG, "Failed to record decoder error", e);
            }
        });
    }

    public static boolean triggerTestCrash(Context context) {
        if (!isCrashReportingEnabled(context)) {
            return false;
        }
        backgroundExecutor.execute(() -> {
            try {
                throw new RuntimeException("NyxLink Test Crash: verifying crash reporting pipeline and local queue.");
            } catch (RuntimeException e) {
                recordCrashSync(context, Thread.currentThread(), e);
            }
        });
        return true;
    }

    public static synchronized void enqueuePendingReport(Context context, JSONObject report) {
        File file = new File(context.getFilesDir(), PENDING_FILE);
        JSONArray array = new JSONArray();
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                if (sb.length() > 0) {
                    array = new JSONArray(sb.toString());
                }
            } catch (Exception e) {
                array = new JSONArray();
            }
        }

        // Prepend newest report
        JSONArray updated = new JSONArray();
        updated.put(report);
        for (int i = 0; i < array.length() && updated.length() < MAX_PENDING_REPORTS; i++) {
            try {
                updated.put(array.getJSONObject(i));
            } catch (Exception ignored) {}
        }

        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(updated.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.e(TAG, "Failed to save pending report", e);
        }
    }

    public static synchronized List<JSONObject> getPendingReports(Context context) {
        List<JSONObject> list = new ArrayList<>();
        File file = new File(context.getFilesDir(), PENDING_FILE);
        if (!file.exists()) return list;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            if (sb.length() > 0) {
                JSONArray array = new JSONArray(sb.toString());
                for (int i = 0; i < array.length(); i++) {
                    list.add(array.getJSONObject(i));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to read pending reports", e);
        }
        return list;
    }

    public static synchronized void clearPendingReports(Context context) {
        File file = new File(context.getFilesDir(), PENDING_FILE);
        if (file.exists()) {
            file.delete();
        }
    }

    public static synchronized void removePendingReport(Context context, String id) {
        File file = new File(context.getFilesDir(), PENDING_FILE);
        if (!file.exists()) return;
        List<JSONObject> remaining = new ArrayList<>();
        for (JSONObject obj : getPendingReports(context)) {
            if (!id.equals(obj.optString("id"))) {
                remaining.add(obj);
            }
        }
        JSONArray updated = new JSONArray();
        for (JSONObject obj : remaining) updated.put(obj);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(updated.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.e(TAG, "Failed to update pending reports", e);
        }
    }

    public static void submitReport(
            Context context,
            CompanionClient companion,
            JSONObject report,
            SubmissionCallback callback
    ) {
        backgroundExecutor.execute(() -> {
            try {
                if (companion == null) {
                    enqueuePendingReport(context, report);
                    if (callback != null) callback.onError(new IOException("PC Companion is not currently connected."), true);
                    return;
                }

                JSONObject response = companion.post("reports", report);
                if (response.optBoolean("success", false)) {
                    String id = response.optString("id", report.optString("id"));
                    if (callback != null) callback.onSuccess(id);
                } else {
                    enqueuePendingReport(context, report);
                    if (callback != null) callback.onError(new IOException("Companion rejected report: " + response), true);
                }
            } catch (Exception e) {
                enqueuePendingReport(context, report);
                if (callback != null) callback.onError(e, true);
            }
        });
    }

    public static void flushPendingQueue(Context context, CompanionClient companion) {
        if (companion == null) return;
        backgroundExecutor.execute(() -> {
            List<JSONObject> pending = getPendingReports(context);
            if (pending.isEmpty()) return;

            boolean crashOptedIn = isCrashReportingEnabled(context);
            for (JSONObject item : pending) {
                String type = item.optString("type", "");
                if ("crash".equalsIgnoreCase(type) && !crashOptedIn) {
                    removePendingReport(context, item.optString("id"));
                    continue;
                }
                try {
                    JSONObject res = companion.post("reports", item);
                    if (res.optBoolean("success", false)) {
                        removePendingReport(context, item.optString("id"));
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Flush report failed: " + e.getMessage());
                    break;
                }
            }
        });
    }

    public static String bitmapToBase64(Bitmap bitmap) {
        if (bitmap == null) return null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos);
            byte[] bytes = baos.toByteArray();
            return Base64.encodeToString(bytes, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "Failed to compress bitmap to base64", e);
            return null;
        }
    }

    public static Bitmap loadAndDownsampleBitmap(Context context, Uri uri, int maxDim) {
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) return null;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(input, null, opts);

            int width = opts.outWidth;
            int height = opts.outHeight;
            int sampleSize = 1;
            while (width / (sampleSize * 2) >= maxDim || height / (sampleSize * 2) >= maxDim) {
                sampleSize *= 2;
            }

            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sampleSize;

            try (InputStream input2 = context.getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(input2, null, opts);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load bitmap from uri", e);
            return null;
        }
    }
}
