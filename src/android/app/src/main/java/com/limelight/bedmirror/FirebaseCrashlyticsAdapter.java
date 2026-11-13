package com.limelight.bedmirror;

import android.content.Context;
import android.util.Log;

/**
 * Adapter interface and reflection-based loader for Firebase Crashlytics.
 *
 * NOTE on Firebase Crashlytics integration:
 * Standard Firebase Crashlytics requires:
 * 1. A registered Google Cloud / Firebase project with an active 'google-services.json' file in app/.
 * 2. The Google Services Gradle plugin ('com.google.gms.google-services').
 * Without an active project and credentials file, Gradle build tasks halt with a fatal build error.
 * Additionally, Crashlytics is designed exclusively for unhandled crash capture; it does not support
 * structured bug reports with screenshots, user satisfaction ratings, or feature requests.
 *
 * This adapter provides optional hook points: when a developer provides their own google-services.json
 * and enables the Firebase SDK, this adapter forwards crash exceptions and custom keys seamlessly.
 */
public final class FirebaseCrashlyticsAdapter {
    private static final String TAG = "FirebaseCrashlytics";
    private static boolean available = false;
    private static Object crashlyticsInstance = null;

    static {
        try {
            Class<?> crashlyticsClass = Class.forName("com.google.firebase.crashlytics.FirebaseCrashlytics");
            java.lang.reflect.Method getInstance = crashlyticsClass.getMethod("getInstance");
            crashlyticsInstance = getInstance.invoke(null);
            available = (crashlyticsInstance != null);
            Log.i(TAG, "Firebase Crashlytics is present and initialized.");
        } catch (Throwable t) {
            available = false;
            Log.d(TAG, "Firebase Crashlytics is not configured in this build; using NyxLink private reporting pipeline.");
        }
    }

    public static boolean isAvailable() {
        return available;
    }

    public static void setCrashlyticsCollectionEnabled(boolean enabled) {
        if (!available || crashlyticsInstance == null) return;
        try {
            java.lang.reflect.Method setEnabled = crashlyticsInstance.getClass().getMethod("setCrashlyticsCollectionEnabled", Boolean.class);
            setEnabled.invoke(crashlyticsInstance, enabled);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to set Crashlytics collection state: " + t.getMessage());
        }
    }

    public static void recordException(Throwable throwable) {
        if (!available || crashlyticsInstance == null) return;
        try {
            java.lang.reflect.Method record = crashlyticsInstance.getClass().getMethod("recordException", Throwable.class);
            record.invoke(crashlyticsInstance, throwable);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to record exception to Crashlytics: " + t.getMessage());
        }
    }

    public static void setCustomKey(String key, String value) {
        if (!available || crashlyticsInstance == null) return;
        try {
            java.lang.reflect.Method setKey = crashlyticsInstance.getClass().getMethod("setCustomKey", String.class, String.class);
            setKey.invoke(crashlyticsInstance, key, value);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to set Crashlytics custom key: " + t.getMessage());
        }
    }
}
