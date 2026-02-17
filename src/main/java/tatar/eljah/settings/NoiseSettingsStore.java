package tatar.eljah.settings;

import android.content.Context;
import android.content.SharedPreferences;

public final class NoiseSettingsStore {
    private static final String PREFS_NAME = "noise_settings";
    private static final String KEY_NOISE_THRESHOLD = "noise_threshold";
    public static final float DEFAULT_NOISE_THRESHOLD = 0.2f;

    private NoiseSettingsStore() {
    }

    public static float getNoiseThreshold(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getFloat(KEY_NOISE_THRESHOLD, DEFAULT_NOISE_THRESHOLD);
    }

    public static void setNoiseThreshold(Context context, float value) {
        float normalized = clamp(value);
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putFloat(KEY_NOISE_THRESHOLD, normalized).apply();
    }

    public static float clamp(float value) {
        if (value < 0f) {
            return 0f;
        }
        if (value > 1f) {
            return 1f;
        }
        return value;
    }
}
