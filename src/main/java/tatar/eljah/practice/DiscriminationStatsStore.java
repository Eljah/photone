package tatar.eljah.practice;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Base64;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DiscriminationStatsStore {

    private static final String PREF_NAME = "discrimination_stats";
    private static final String KEY_KEYS = "keys";
    private static final String KEY_PREFIX = "stat_";
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private DiscriminationStatsStore() {
    }

    public static void recordScore(Context context, String mode, String compared, int score) {
        if (context == null || TextUtils.isEmpty(mode) || TextUtils.isEmpty(compared)) {
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String encoded = encode(mode, compared);
        String prefKey = KEY_PREFIX + encoded;
        int currentMax = prefs.getInt(prefKey, Integer.MIN_VALUE);
        SharedPreferences.Editor editor = prefs.edit();
        if (score > currentMax) {
            editor.putInt(prefKey, score);
        }
        Set<String> keys = new HashSet<>(prefs.getStringSet(KEY_KEYS, new HashSet<String>()));
        if (keys.add(encoded)) {
            editor.putStringSet(KEY_KEYS, keys);
        }
        editor.apply();
    }

    public static List<Entry> getEntries(Context context) {
        if (context == null) {
            return Collections.emptyList();
        }
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Set<String> keys = prefs.getStringSet(KEY_KEYS, Collections.<String>emptySet());
        List<Entry> result = new ArrayList<>();
        for (String encoded : keys) {
            Decoded decoded = decode(encoded);
            if (decoded == null) {
                continue;
            }
            String prefKey = KEY_PREFIX + encoded;
            if (!prefs.contains(prefKey)) {
                continue;
            }
            int maxScore = prefs.getInt(prefKey, Integer.MIN_VALUE);
            result.add(new Entry(decoded.mode, decoded.compared, maxScore));
        }
        Collections.sort(result, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                if (a.maxScore != b.maxScore) {
                    return b.maxScore - a.maxScore;
                }
                int modeCmp = a.mode.compareTo(b.mode);
                if (modeCmp != 0) {
                    return modeCmp;
                }
                return a.compared.compareTo(b.compared);
            }
        });
        return result;
    }

    private static String encode(String mode, String compared) {
        String raw = mode + "\n" + compared;
        return Base64.encodeToString(raw.getBytes(UTF_8), Base64.NO_WRAP);
    }

    private static Decoded decode(String encoded) {
        try {
            byte[] raw = Base64.decode(encoded, Base64.NO_WRAP);
            String decoded = new String(raw, UTF_8);
            int split = decoded.indexOf('\n');
            if (split < 0) {
                return null;
            }
            String mode = decoded.substring(0, split);
            String compared = decoded.substring(split + 1);
            if (TextUtils.isEmpty(mode) || TextUtils.isEmpty(compared)) {
                return null;
            }
            return new Decoded(mode, compared);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static final class Decoded {
        private final String mode;
        private final String compared;

        private Decoded(String mode, String compared) {
            this.mode = mode;
            this.compared = compared;
        }
    }

    public static final class Entry {
        public final String mode;
        public final String compared;
        public final int maxScore;

        public Entry(String mode, String compared, int maxScore) {
            this.mode = mode;
            this.compared = compared;
            this.maxScore = maxScore;
        }
    }
}
