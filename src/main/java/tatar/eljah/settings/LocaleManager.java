package tatar.eljah.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;

import java.util.Locale;

public final class LocaleManager {
    private static final String PREFS_NAME = "app_preferences";
    private static final String KEY_LOCALE = "app_locale";

    private LocaleManager() {
    }

    public static void setLocaleTag(Context context, String localeTag) {
        if (context == null) {
            return;
        }
        String value = localeTag == null ? "" : localeTag;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LOCALE, value).apply();
    }

    public static String getLocaleTag(Context context) {
        if (context == null) {
            return null;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String tag = prefs.getString(KEY_LOCALE, "");
        return tag == null || tag.trim().isEmpty() ? null : tag;
    }

    public static Context applyLocale(Context context) {
        String tag = getLocaleTag(context);
        if (tag == null || tag.isEmpty()) {
            return context;
        }
        Locale locale = Locale.forLanguageTag(tag);
        return updateResources(context, locale);
    }

    private static Context updateResources(Context context, Locale locale) {
        Locale.setDefault(locale);
        Resources resources = context.getResources();
        Configuration configuration = new Configuration(resources.getConfiguration());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            configuration.setLocale(locale);
            return context.createConfigurationContext(configuration);
        }
        configuration.locale = locale;
        resources.updateConfiguration(configuration, resources.getDisplayMetrics());
        return context;
    }
}
