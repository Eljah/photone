package tatar.eljah.settings;

import android.content.Context;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.RadioGroup;
import android.widget.Spinner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import tatar.eljah.R;
import tatar.eljah.tts.TtsVoicePreferences;

public class VoiceSettingsActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleManager.applyLocale(newBase));
    }

    private static final Locale TARGET_LOCALE = Locale.forLanguageTag("vi-VN");

    private Spinner voiceSpinner;
    private ArrayAdapter<String> voiceAdapter;
    private final List<VoiceOption> voiceOptions = new ArrayList<>();
    private boolean isUpdatingVoices;
    private Spinner localeSpinner;
    private ArrayAdapter<String> localeAdapter;
    private final List<LocaleOption> localeOptions = new ArrayList<>();
    private boolean isUpdatingLocales;
    private boolean isUserLocaleSelection;
    private TextToSpeech textToSpeech;
    private String pendingVoiceName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice_settings);
        setTitle(R.string.title_voice_settings);

        setupLocaleSpinner();

        RadioGroup genderGroup = findViewById(R.id.radio_voice_gender);
        String current = TtsVoicePreferences.getVoiceGender(this);
        if (TtsVoicePreferences.VOICE_GENDER_MALE.equals(current)) {
            genderGroup.check(R.id.radio_voice_gender_male);
        } else if (TtsVoicePreferences.VOICE_GENDER_FEMALE.equals(current)) {
            genderGroup.check(R.id.radio_voice_gender_female);
        } else {
            genderGroup.check(R.id.radio_voice_gender_auto);
        }

        genderGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                String selection = TtsVoicePreferences.VOICE_GENDER_AUTO;
                if (checkedId == R.id.radio_voice_gender_male) {
                    selection = TtsVoicePreferences.VOICE_GENDER_MALE;
                } else if (checkedId == R.id.radio_voice_gender_female) {
                    selection = TtsVoicePreferences.VOICE_GENDER_FEMALE;
                }
                TtsVoicePreferences.setVoiceGender(VoiceSettingsActivity.this, selection);
            }
        });

        pendingVoiceName = TtsVoicePreferences.getVoiceName(this);
        setupVoiceSpinner();
        textToSpeech = new TextToSpeech(this, this, "com.google.android.tts");
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.shutdown();
            textToSpeech = null;
        }
        super.onDestroy();
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS || textToSpeech == null) {
            return;
        }
        int languageStatus = textToSpeech.setLanguage(TARGET_LOCALE);
        Set<Voice> voices = textToSpeech.getVoices();
        if (voices == null || voices.isEmpty()) {
            return;
        }
        List<Voice> candidates = new ArrayList<>();
        for (Voice voice : voices) {
            if (voice == null || voice.isNetworkConnectionRequired()) {
                continue;
            }
            Locale voiceLocale = voice.getLocale();
            if (languageStatus != TextToSpeech.LANG_MISSING_DATA
                    && languageStatus != TextToSpeech.LANG_NOT_SUPPORTED
                    && voiceLocale != null
                    && TARGET_LOCALE.getLanguage().equals(voiceLocale.getLanguage())) {
                candidates.add(voice);
            }
        }
        if (candidates.isEmpty()) {
            for (Voice voice : voices) {
                if (voice == null || voice.isNetworkConnectionRequired()) {
                    continue;
                }
                candidates.add(voice);
            }
        }
        updateVoiceOptions(candidates);
    }

    private void setupVoiceSpinner() {
        voiceSpinner = findViewById(R.id.spinner_voice_selection);
        voiceAdapter = new ArrayAdapter<>(this, R.layout.spinner_item_dark, new ArrayList<String>());
        voiceAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_dark);
        voiceSpinner.setAdapter(voiceAdapter);
        voiceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isUpdatingVoices || position < 0 || position >= voiceOptions.size()) {
                    return;
                }
                VoiceOption option = voiceOptions.get(position);
                TtsVoicePreferences.setVoiceName(VoiceSettingsActivity.this, option.voiceName);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        updateVoiceOptions(new ArrayList<Voice>());
    }

    private void setupLocaleSpinner() {
        localeSpinner = findViewById(R.id.spinner_app_language);
        localeAdapter = new ArrayAdapter<>(this, R.layout.spinner_item_dark, new ArrayList<String>());
        localeAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_dark);
        localeSpinner.setAdapter(localeAdapter);
        localeSpinner.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {
                isUserLocaleSelection = true;
                return false;
            }
        });
        localeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isUpdatingLocales || !isUserLocaleSelection || position < 0 || position >= localeOptions.size()) {
                    return;
                }
                isUserLocaleSelection = false;
                LocaleOption option = localeOptions.get(position);
                String currentTag = LocaleManager.getLocaleTag(VoiceSettingsActivity.this);
                if (currentTag == null) {
                    currentTag = "";
                }
                if (!currentTag.equals(option.localeTag)) {
                    LocaleManager.setLocaleTag(VoiceSettingsActivity.this, option.localeTag);
                    recreate();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        updateLocaleOptions();
    }

    private void updateLocaleOptions() {
        isUpdatingLocales = true;
        localeOptions.clear();
        String defaultOriginal = getString(R.string.language_system_default_original);
        String defaultTranslated = getString(R.string.language_system_default);
        localeOptions.add(new LocaleOption(formatLanguageLabel(defaultOriginal, defaultTranslated), ""));
        List<String> localeTags = Arrays.asList(
                "en", "ru", "vi", "de", "fr", "es", "ar", "ja", "zh", "pt", "tr", "tt");
        Locale displayLocale = Locale.getDefault();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            displayLocale = getResources().getConfiguration().getLocales().get(0);
        }
        for (String tag : localeTags) {
            Locale locale = Locale.forLanguageTag(tag);
            String originalLabel = capitalize(locale.getDisplayName(locale));
            String translatedLabel = capitalize(locale.getDisplayName(displayLocale));
            localeOptions.add(new LocaleOption(formatLanguageLabel(originalLabel, translatedLabel), tag));
        }
        localeAdapter.clear();
        for (LocaleOption option : localeOptions) {
            localeAdapter.add(option.label);
        }
        localeAdapter.notifyDataSetChanged();
        localeSpinner.setSelection(findPreferredLocaleIndex());
        isUpdatingLocales = false;
    }

    private int findPreferredLocaleIndex() {
        String selected = LocaleManager.getLocaleTag(this);
        if (selected == null) {
            selected = "";
        }
        for (int i = 0; i < localeOptions.size(); i++) {
            if (selected.equals(localeOptions.get(i).localeTag)) {
                return i;
            }
        }
        return 0;
    }

    private void updateVoiceOptions(List<Voice> voices) {
        isUpdatingVoices = true;
        voiceOptions.clear();
        voiceOptions.add(new VoiceOption(getString(R.string.voice_selection_auto), null));
        for (Voice voice : voices) {
            voiceOptions.add(new VoiceOption(voice.getName(), voice.getName()));
        }
        voiceAdapter.clear();
        for (VoiceOption option : voiceOptions) {
            voiceAdapter.add(option.label);
        }
        voiceAdapter.notifyDataSetChanged();
        voiceSpinner.setSelection(findPreferredVoiceIndex());
        isUpdatingVoices = false;
    }

    private int findPreferredVoiceIndex() {
        if (pendingVoiceName == null) {
            return 0;
        }
        for (int i = 0; i < voiceOptions.size(); i++) {
            if (pendingVoiceName.equals(voiceOptions.get(i).voiceName)) {
                return i;
            }
        }
        return 0;
    }

    private String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return value.substring(0, 1).toUpperCase(Locale.getDefault()) + value.substring(1);
    }

    private String formatLanguageLabel(String original, String translated) {
        return getString(R.string.language_display_format, original, translated);
    }

    private static class VoiceOption {
        private final String label;
        private final String voiceName;

        private VoiceOption(String label, String voiceName) {
            this.label = label;
            this.voiceName = voiceName;
        }
    }

    private static class LocaleOption {
        private final String label;
        private final String localeTag;

        private LocaleOption(String label, String localeTag) {
            this.label = label;
            this.localeTag = localeTag;
        }
    }
}
