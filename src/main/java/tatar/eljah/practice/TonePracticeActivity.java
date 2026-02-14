package tatar.eljah.practice;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;

import tatar.eljah.R;
import tatar.eljah.audio.PitchAnalyzer;
import tatar.eljah.audio.RecorderNoteFrequencies;
import tatar.eljah.model.ToneSample;
import tatar.eljah.model.VietnameseSyllable;
import tatar.eljah.settings.LocaleManager;
import tatar.eljah.tts.TtsVoiceSelector;
import tatar.eljah.ui.SpectrogramView;
import tatar.eljah.ui.ToneVisualizerView;
import tatar.eljah.util.TextDiffUtil;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TonePracticeActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleManager.applyLocale(newBase));
    }

    private static final int REQUEST_RECORD_AUDIO = 1001;
    private static final String[] CONSONANTS = {
            "", "b", "c", "ch", "d", "đ", "g", "gh", "gi", "h", "k", "kh", "l", "m", "n",
            "ng", "ngh", "nh", "p", "ph", "qu", "r", "s", "t", "th", "tr", "v", "x"
    };
    private static final String[] VOWELS = {
            "a", "ă", "â", "e", "ê", "i", "o", "ô", "ơ", "u", "ư", "y"
    };
    private static final String[] FINAL_CONSONANTS = {
            "", "c", "ch", "m", "n", "ng", "nh", "p", "t"
    };
    private static final String[] TONES = {"ngang", "sắc", "huyền", "hỏi", "ngã", "nặng"};
    private static final Map<String, String[]> TONE_FORMS = new HashMap<>();

    static {
        TONE_FORMS.put("a", new String[]{"a", "á", "à", "ả", "ã", "ạ"});
        TONE_FORMS.put("ă", new String[]{"ă", "ắ", "ằ", "ẳ", "ẵ", "ặ"});
        TONE_FORMS.put("â", new String[]{"â", "ấ", "ầ", "ẩ", "ẫ", "ậ"});
        TONE_FORMS.put("e", new String[]{"e", "é", "è", "ẻ", "ẽ", "ẹ"});
        TONE_FORMS.put("ê", new String[]{"ê", "ế", "ề", "ể", "ễ", "ệ"});
        TONE_FORMS.put("i", new String[]{"i", "í", "ì", "ỉ", "ĩ", "ị"});
        TONE_FORMS.put("o", new String[]{"o", "ó", "ò", "ỏ", "õ", "ọ"});
        TONE_FORMS.put("ô", new String[]{"ô", "ố", "ồ", "ổ", "ỗ", "ộ"});
        TONE_FORMS.put("ơ", new String[]{"ơ", "ớ", "ờ", "ở", "ỡ", "ợ"});
        TONE_FORMS.put("u", new String[]{"u", "ú", "ù", "ủ", "ũ", "ụ"});
        TONE_FORMS.put("ư", new String[]{"ư", "ứ", "ừ", "ử", "ữ", "ự"});
        TONE_FORMS.put("y", new String[]{"y", "ý", "ỳ", "ỷ", "ỹ", "ỵ"});
    }

    private ToneVisualizerView visualizerView;
    private TextView referenceTitle;
    private TextView tvRecognized;
    private TextView tvDiff;
    private TextView tvToneResult;
    private Button btnPlayReference;
    private Button btnRecordUser;
    private Button btnPlayUserRecording;
    private SpectrogramView spectrogramView;
    private Spinner practiceConsonantSpinner;
    private Spinner practiceVowelSpinner;
    private Spinner practiceFinalConsonantSpinner;
    private Spinner practiceToneSpinner;

    private VietnameseSyllable targetSyllable;
    private ToneSample referenceSample;
    private ToneSample userSample;

    private TextToSpeech textToSpeech;
    private boolean isTtsReady = false;
    private static final float REFERENCE_SPEECH_RATE = 0.8f;
    private static final float DEFAULT_REFERENCE_THRESHOLD = 12f;
    private static final int DEFAULT_REFERENCE_MIN_SAMPLES = 2;
    private static final int REFERENCE_RECORDING_DURATION_MS = 500;
    private static final int USER_RECORDING_DURATION_MS = 3000;

    private PitchAnalyzer pitchAnalyzer;
    private final List<Float> userPitch = new ArrayList<>();
    private boolean isRecording = false;
    private boolean shouldRecognizeSpeech = false;
    private String referenceFileUtteranceId;
    private boolean pendingStartRecording = false;
    private File userRecordingFile;
    private ByteArrayOutputStream userPcmStream;
    private int userSampleRate;
    private MediaPlayer userPlayer;
    private boolean hasUserRecording = false;
    private final List<float[]> userSpectrogramFrames = new ArrayList<>();
    private int userSpectrogramSampleRate = 0;
    private long userRecordingStateToken = 0L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable stopRecordingRunnable = new Runnable() {
        @Override
        public void run() {
            stopRecordingAndAnalyze(shouldRecognizeSpeech);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tone_practice);

        visualizerView = findViewById(R.id.toneVisualizerView);
        referenceTitle = findViewById(R.id.tv_reference_title);
        tvRecognized = findViewById(R.id.tv_recognized);
        tvDiff = findViewById(R.id.tv_diff);
        tvToneResult = findViewById(R.id.tv_tone_result);
        btnPlayReference = findViewById(R.id.btn_play_reference);
        btnRecordUser = findViewById(R.id.btn_record_user);
        btnPlayUserRecording = findViewById(R.id.btn_play_user_recording);
        spectrogramView = findViewById(R.id.spectrogramView);
        practiceConsonantSpinner = findViewById(R.id.spinner_practice_consonant);
        practiceVowelSpinner = findViewById(R.id.spinner_practice_vowel);
        practiceFinalConsonantSpinner = findViewById(R.id.spinner_practice_final_consonant);
        practiceToneSpinner = findViewById(R.id.spinner_practice_tone);

        pitchAnalyzer = new PitchAnalyzer();

        setupSpinners();
        referenceSample = createSimpleReferenceSample();
        visualizerView.setReferenceData(referenceSample.getPitchHz());
        visualizerView.setUserData(null);
        updateTargetFromSelection();
        ensureRecordingPermission();

        textToSpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    Locale vietnameseLocale = Locale.forLanguageTag("vi-VN");
                    int languageStatus = textToSpeech.setLanguage(vietnameseLocale);
                    isTtsReady = languageStatus != TextToSpeech.LANG_MISSING_DATA
                            && languageStatus != TextToSpeech.LANG_NOT_SUPPORTED;
                    if (isTtsReady) {
                        TtsVoiceSelector.applyPreferredVoice(TonePracticeActivity.this, textToSpeech, vietnameseLocale);
                        textToSpeech.setSpeechRate(REFERENCE_SPEECH_RATE);
                    }
                }
            }
        }, "com.google.android.tts");

        btnPlayReference.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playReference();
            }
        });

        btnRecordUser.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                recordUser();
            }
        });

        updatePlayUserRecordingEnabled();
        btnPlayUserRecording.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playUserRecording();
            }
        });
    }

    private void setupSpinners() {
        ArrayAdapter<String> consonantAdapter = new ArrayAdapter<>(this,
                R.layout.spinner_item_dark, CONSONANTS);
        consonantAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_dark);

        ArrayAdapter<String> vowelAdapter = new ArrayAdapter<>(this,
                R.layout.spinner_item_dark, VOWELS);
        vowelAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_dark);

        ArrayAdapter<String> finalConsonantAdapter = new ArrayAdapter<>(this,
                R.layout.spinner_item_dark, FINAL_CONSONANTS);
        finalConsonantAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_dark);

        ArrayAdapter<String> toneAdapter = new ArrayAdapter<>(this,
                R.layout.spinner_item_dark, TONES);
        toneAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_dark);

        practiceConsonantSpinner.setAdapter(consonantAdapter);
        practiceVowelSpinner.setAdapter(vowelAdapter);
        practiceFinalConsonantSpinner.setAdapter(finalConsonantAdapter);
        practiceToneSpinner.setAdapter(toneAdapter);

        AdapterView.OnItemSelectedListener updateListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateTargetFromSelection();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        };
        practiceConsonantSpinner.setOnItemSelectedListener(updateListener);
        practiceVowelSpinner.setOnItemSelectedListener(updateListener);
        practiceFinalConsonantSpinner.setOnItemSelectedListener(updateListener);
        practiceToneSpinner.setOnItemSelectedListener(updateListener);
    }

    private void updateTargetFromSelection() {
        String syllable = buildSyllable(
                practiceConsonantSpinner,
                practiceVowelSpinner,
                practiceFinalConsonantSpinner,
                practiceToneSpinner
        );
        String tone = String.valueOf(practiceToneSpinner.getSelectedItem());
        targetSyllable = new VietnameseSyllable(syllable, tone, 0);
        referenceTitle.setText(getString(R.string.label_sample_selected, syllable));
        hasUserRecording = false;
        resetUserRecording();
        visualizerView.setUserData(null);
        if (referenceSample != null) {
            visualizerView.setReferenceData(referenceSample.getPitchHz());
        }
    }

    private String buildSyllable(
            Spinner consonantSpinner,
            Spinner vowelSpinner,
            Spinner finalConsonantSpinner,
            Spinner toneSpinner
    ) {
        String consonant = String.valueOf(consonantSpinner.getSelectedItem());
        String vowel = String.valueOf(vowelSpinner.getSelectedItem());
        String finalConsonant = String.valueOf(finalConsonantSpinner.getSelectedItem());
        String tone = String.valueOf(toneSpinner.getSelectedItem());
        String tonedVowel = applyTone(vowel, tone);
        return (consonant + tonedVowel + finalConsonant).trim();
    }

    private String applyTone(String vowel, String toneLabel) {
        int toneIndex = 0;
        for (int i = 0; i < TONES.length; i++) {
            if (TONES[i].equals(toneLabel)) {
                toneIndex = i;
                break;
            }
        }
        String[] forms = TONE_FORMS.get(vowel);
        if (forms == null || toneIndex >= forms.length) {
            return vowel;
        }
        return forms[toneIndex];
    }

    private ToneSample createSimpleReferenceSample() {
        List<Float> data = new ArrayList<>();
        float referenceHz = RecorderNoteFrequencies.getFrequencyOrDefault("C5", 523.25f);
        for (int i = 0; i < 50; i++) {
            data.add(referenceHz);
        }
        return new ToneSample(data, 20);
    }

    private void playReference() {
        visualizerView.setReferenceData(referenceSample.getPitchHz());
        visualizerView.setUserData(null);
        if (spectrogramView != null) {
            spectrogramView.clear();
        }
        updatePlayUserRecordingEnabled();
        synthesizeReferenceToFile();
    }

    private void playReferenceAudio() {
        if (targetSyllable == null) {
            return;
        }
        if (!isTtsReady || textToSpeech == null) {
            return;
        }
        textToSpeech.stop();
        textToSpeech.speak(targetSyllable.getText(), TextToSpeech.QUEUE_FLUSH, null, "reference-utterance");
    }

    private void synthesizeReferenceToFile() {
        if (targetSyllable == null || !isTtsReady || textToSpeech == null) {
            return;
        }
        final File outputFile;
        try {
            outputFile = File.createTempFile("reference_tts_", ".wav", getCacheDir());
        } catch (IOException e) {
            return;
        }

        referenceFileUtteranceId = "reference-file-" + System.currentTimeMillis();
        textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
            }

            @Override
            public void onDone(String utteranceId) {
                if (utteranceId != null && utteranceId.equals(referenceFileUtteranceId)) {
                    analyzeReferenceFile(outputFile);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            playReferenceAudio();
                        }
                    });
                }
            }

            @Override
            public void onError(String utteranceId) {
                if (utteranceId != null && utteranceId.equals(referenceFileUtteranceId)) {
                    deleteTempFile(outputFile);
                }
            }
        });

        Bundle params = new Bundle();
        textToSpeech.synthesizeToFile(targetSyllable.getText(), params, outputFile, referenceFileUtteranceId);
    }

    private void analyzeReferenceFile(final File outputFile) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                WavData wavData;
                try {
                    wavData = readWavFile(outputFile);
                } catch (IOException e) {
                    deleteTempFile(outputFile);
                    return;
                }
                final List<Float> pitchData = new ArrayList<>();
                final List<float[]> spectrumFrames = new ArrayList<>();
                pitchAnalyzer.analyzePcm(
                        wavData.samples,
                        wavData.sampleRate,
                        new PitchAnalyzer.PitchListener() {
                            @Override
                            public void onPitch(float pitchHz) {
                                pitchData.add(pitchHz);
                            }
                        },
                        new PitchAnalyzer.SpectrumListener() {
                            @Override
                            public void onSpectrum(float[] magnitudes, int sampleRate) {
                                spectrumFrames.add(magnitudes);
                            }
                        }
                );
                deleteTempFile(outputFile);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (hasValidPitchData(pitchData)) {
                            referenceSample = new ToneSample(pitchData, 20);
                        } else {
                            referenceSample = createSimpleReferenceSample();
                        }
                        visualizerView.setReferenceData(referenceSample.getPitchHz());
                        if (spectrogramView != null) {
                            spectrogramView.clear();
                            for (float[] frame : spectrumFrames) {
                                spectrogramView.addSpectrumFrame(frame, wavData.sampleRate, frame.length * 2);
                            }
                        }
                    }
                });
            }
        }).start();
    }

    private void deleteTempFile(File file) {
        if (file == null) {
            return;
        }
        if (!file.delete()) {
            file.deleteOnExit();
        }
    }

    private boolean hasValidPitchData(List<Float> pitchData) {
        if (pitchData == null) {
            return false;
        }
        int validSamples = 0;
        for (Float value : pitchData) {
            if (value != null && value > 0f) {
                validSamples++;
                if (validSamples >= 2) {
                    return true;
                }
            }
        }
        return false;
    }

    private static class WavData {
        private final short[] samples;
        private final int sampleRate;

        private WavData(short[] samples, int sampleRate) {
            this.samples = samples;
            this.sampleRate = sampleRate;
        }
    }

    private WavData readWavFile(File file) throws IOException {
        byte[] bytes = readAllBytes(file);
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (buffer.remaining() < 12) {
            throw new IOException("Invalid WAV header");
        }
        byte[] riff = new byte[4];
        buffer.get(riff);
        buffer.getInt();
        byte[] wave = new byte[4];
        buffer.get(wave);
        int sampleRate = 0;
        short channels = 0;
        short bitsPerSample = 0;
        int dataOffset = -1;
        int dataSize = 0;
        while (buffer.remaining() >= 8) {
            byte[] chunkIdBytes = new byte[4];
            buffer.get(chunkIdBytes);
            String chunkId = new String(chunkIdBytes);
            int chunkSize = buffer.getInt();
            if ("fmt ".equals(chunkId)) {
                short audioFormat = buffer.getShort();
                channels = buffer.getShort();
                sampleRate = buffer.getInt();
                buffer.getInt();
                buffer.getShort();
                bitsPerSample = buffer.getShort();
                if (chunkSize > 16) {
                    buffer.position(buffer.position() + (chunkSize - 16));
                }
                if (audioFormat != 1) {
                    throw new IOException("Unsupported WAV format");
                }
            } else if ("data".equals(chunkId)) {
                dataOffset = buffer.position();
                dataSize = chunkSize;
                buffer.position(buffer.position() + chunkSize);
            } else {
                buffer.position(buffer.position() + chunkSize);
            }
        }
        if (dataOffset < 0 || bitsPerSample != 16 || channels == 0 || sampleRate == 0) {
            throw new IOException("Invalid WAV data");
        }
        ByteBuffer dataBuffer = ByteBuffer.wrap(bytes, dataOffset, dataSize).order(ByteOrder.LITTLE_ENDIAN);
        int totalSamples = dataSize / 2 / channels;
        short[] samples = new short[totalSamples];
        for (int i = 0; i < totalSamples; i++) {
            short sample = dataBuffer.getShort();
            if (channels > 1) {
                for (int c = 1; c < channels; c++) {
                    dataBuffer.getShort();
                }
            }
            samples[i] = sample;
        }
        return new WavData(samples, sampleRate);
    }

    private byte[] readAllBytes(File file) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        try (FileInputStream inputStream = new FileInputStream(file)) {
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
        }
        return outputStream.toByteArray();
    }

    private void stopReferenceAudio() {
        if (textToSpeech != null) {
            textToSpeech.stop();
        }
    }

    private void recordUser() {
        if (!ensureRecordingPermission()) {
            pendingStartRecording = true;
            return;
        }
        startRecording(true);
    }

    private boolean ensureRecordingPermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        ActivityCompat.requestPermissions(
                this,
                new String[]{android.Manifest.permission.RECORD_AUDIO},
                REQUEST_RECORD_AUDIO
        );
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_RECORD_AUDIO) {
            return;
        }
        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (granted && pendingStartRecording) {
            pendingStartRecording = false;
            startRecording(true);
        } else if (!granted) {
            pendingStartRecording = false;
        }
    }

    private void startRecording(boolean recognizeSpeech) {
        if (isRecording) {
            handler.removeCallbacks(stopRecordingRunnable);
            pitchAnalyzer.stop();
        }
        isRecording = true;
        shouldRecognizeSpeech = recognizeSpeech;

        userPitch.clear();
        resetUserRecording();
        visualizerView.setUserData(userPitch);
        if (spectrogramView != null) {
            spectrogramView.clear();
        }
        if (recognizeSpeech) {
            tvRecognized.setText("");
            tvDiff.setText("");
        }
        tvToneResult.setText("");

        pitchAnalyzer.startRealtimePitch(new PitchAnalyzer.PitchListener() {
            @Override
            public void onPitch(float pitchHz) {
                userPitch.add(pitchHz);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        visualizerView.setUserData(userPitch);
                    }
                });
            }
        }, new PitchAnalyzer.SpectrumListener() {
            @Override
            public void onSpectrum(final float[] magnitudes, final int sampleRate) {
                final float[] frameCopy = new float[magnitudes.length];
                System.arraycopy(magnitudes, 0, frameCopy, 0, magnitudes.length);
                synchronized (userSpectrogramFrames) {
                    userSpectrogramFrames.add(frameCopy);
                    if (userSpectrogramSampleRate == 0) {
                        userSpectrogramSampleRate = sampleRate;
                    }
                }
                if (spectrogramView == null) {
                    return;
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        spectrogramView.addSpectrumFrame(frameCopy, sampleRate, frameCopy.length * 2);
                    }
                });
            }
        }, new PitchAnalyzer.AudioListener() {
            @Override
            public void onAudio(short[] samples, int length, int sampleRate) {
                appendUserPcm(samples, length, sampleRate);
            }
        });

        int recordingDurationMs = recognizeSpeech
                ? USER_RECORDING_DURATION_MS
                : REFERENCE_RECORDING_DURATION_MS;
        handler.postDelayed(stopRecordingRunnable, recordingDurationMs);
    }

    private void stopRecordingAndAnalyze(boolean recognizeSpeech) {
        pitchAnalyzer.stop();
        isRecording = false;
        userSample = new ToneSample(new ArrayList<>(userPitch), 20);
        finalizeUserRecording();
        compareToneDirection(recognizeSpeech);
        String recorderHint = buildRecorderRecognitionHint(userPitch);
        if (!recorderHint.isEmpty()) {
            tvToneResult.append("\n" + recorderHint);
        }
        if (recognizeSpeech) {
            startSpeechRecognition();
        }
    }

    private void resetUserRecording() {
        stopUserPlayback();
        userRecordingStateToken++;
        hasUserRecording = false;
        deleteTempFile(userRecordingFile);
        userRecordingFile = null;
        userPcmStream = new ByteArrayOutputStream();
        userSampleRate = 0;
        synchronized (userSpectrogramFrames) {
            userSpectrogramFrames.clear();
            userSpectrogramSampleRate = 0;
        }
        updatePlayUserRecordingEnabled();
    }

    private synchronized void appendUserPcm(short[] samples, int length, int sampleRate) {
        if (userPcmStream == null) {
            return;
        }
        if (userSampleRate == 0) {
            userSampleRate = sampleRate;
        }
        byte[] bytes = new byte[length * 2];
        int index = 0;
        for (int i = 0; i < length; i++) {
            short sample = samples[i];
            bytes[index++] = (byte) (sample & 0xff);
            bytes[index++] = (byte) ((sample >> 8) & 0xff);
        }
        userPcmStream.write(bytes, 0, bytes.length);
    }

    private void finalizeUserRecording() {
        ByteArrayOutputStream pcmStream = userPcmStream;
        userPcmStream = null;
        if (pcmStream == null || pcmStream.size() == 0 || userSampleRate == 0) {
            return;
        }
        File outputFile;
        try {
            outputFile = File.createTempFile("user_recording_", ".wav", getCacheDir());
        } catch (IOException e) {
            return;
        }
        byte[] pcmData = pcmStream.toByteArray();
        if (!writeWavFile(outputFile, pcmData, userSampleRate)) {
            deleteTempFile(outputFile);
            return;
        }
        userRecordingFile = outputFile;
        hasUserRecording = true;
        updatePlayUserRecordingEnabled();
    }

    private boolean writeWavFile(File file, byte[] pcmData, int sampleRate) {
        int channels = 1;
        int bitsPerSample = 16;
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        int dataSize = pcmData.length;
        int chunkSize = 36 + dataSize;
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            outputStream.write(new byte[]{'R', 'I', 'F', 'F'});
            writeIntLE(outputStream, chunkSize);
            outputStream.write(new byte[]{'W', 'A', 'V', 'E'});
            outputStream.write(new byte[]{'f', 'm', 't', ' '});
            writeIntLE(outputStream, 16);
            writeShortLE(outputStream, (short) 1);
            writeShortLE(outputStream, (short) channels);
            writeIntLE(outputStream, sampleRate);
            writeIntLE(outputStream, byteRate);
            writeShortLE(outputStream, (short) (channels * bitsPerSample / 8));
            writeShortLE(outputStream, (short) bitsPerSample);
            outputStream.write(new byte[]{'d', 'a', 't', 'a'});
            writeIntLE(outputStream, dataSize);
            outputStream.write(pcmData);
            outputStream.flush();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void writeIntLE(FileOutputStream outputStream, int value) throws IOException {
        outputStream.write(value & 0xff);
        outputStream.write((value >> 8) & 0xff);
        outputStream.write((value >> 16) & 0xff);
        outputStream.write((value >> 24) & 0xff);
    }

    private void writeShortLE(FileOutputStream outputStream, short value) throws IOException {
        outputStream.write(value & 0xff);
        outputStream.write((value >> 8) & 0xff);
    }

    private void playUserRecording() {
        if (!hasUserRecording || userRecordingFile == null || !userRecordingFile.exists()) {
            return;
        }
        long expectedStateToken = userRecordingStateToken;
        String expectedRecordingPath = userRecordingFile.getAbsolutePath();
        renderSavedUserSpectrogram(expectedStateToken, expectedRecordingPath);
        stopUserPlayback();
        userPlayer = new MediaPlayer();
        try {
            userPlayer.setDataSource(userRecordingFile.getAbsolutePath());
            userPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    stopUserPlayback();
                }
            });
            userPlayer.prepare();
            userPlayer.start();
        } catch (IOException e) {
            stopUserPlayback();
        }
    }


    private void updatePlayUserRecordingEnabled() {
        boolean enabled = hasUserRecording && userRecordingFile != null && userRecordingFile.exists();
        btnPlayUserRecording.setEnabled(enabled);
        btnPlayUserRecording.setAlpha(enabled ? 1f : 0.5f);
    }

    private void renderSavedUserSpectrogram(long expectedStateToken, String expectedRecordingPath) {
        if (spectrogramView == null) {
            return;
        }
        final List<float[]> frames = new ArrayList<>();
        final int sampleRate;
        synchronized (userSpectrogramFrames) {
            for (float[] sourceFrame : userSpectrogramFrames) {
                float[] copy = new float[sourceFrame.length];
                System.arraycopy(sourceFrame, 0, copy, 0, sourceFrame.length);
                frames.add(copy);
            }
            sampleRate = userSpectrogramSampleRate;
        }
        if (frames.isEmpty() || sampleRate == 0) {
            return;
        }
        File currentRecording = userRecordingFile;
        if (isRecording
                || expectedStateToken != userRecordingStateToken
                || currentRecording == null
                || !currentRecording.exists()
                || !currentRecording.getAbsolutePath().equals(expectedRecordingPath)) {
            return;
        }
        spectrogramView.clear();
        for (float[] frame : frames) {
            spectrogramView.addSpectrumFrame(frame, sampleRate, frame.length * 2);
        }
    }

    private void stopUserPlayback() {
        if (userPlayer != null) {
            userPlayer.release();
            userPlayer = null;
        }
    }

    private void compareToneDirection(boolean strictAnalysis) {
        if (referenceSample == null || userSample == null) {
            tvToneResult.setText(getString(R.string.tone_result_no_data));
            return;
        }
        ToneSample.Direction referenceDirection = referenceSample.getDirection();
        ToneSample.Direction userDirection;
        if (strictAnalysis) {
            userDirection = userSample.getDirection();
        } else {
            userDirection = userSample.getDirection(
                    DEFAULT_REFERENCE_THRESHOLD,
                    DEFAULT_REFERENCE_MIN_SAMPLES
            );
        }

        String text;
        if (userPitch.isEmpty()) {
            text = getString(R.string.tone_result_no_data);
        } else if (referenceDirection == userDirection) {
            text = getString(R.string.tone_result_match);
        } else {
            text = getString(R.string.tone_result_diff);
        }
        tvToneResult.setText(text);
    }


    private float getAveragePitchHz(List<Float> pitchValues) {
        if (pitchValues == null || pitchValues.isEmpty()) {
            return 0f;
        }
        float sum = 0f;
        int count = 0;
        for (Float value : pitchValues) {
            if (value != null && value > 0f) {
                sum += value;
                count++;
            }
        }
        if (count == 0) {
            return 0f;
        }
        return sum / count;
    }

    private String buildRecorderRecognitionHint(List<Float> pitchValues) {
        float averagePitch = getAveragePitchHz(pitchValues);
        RecorderNoteFrequencies.NoteFrequency nearest = RecorderNoteFrequencies.findNearest(averagePitch);
        if (nearest == null) {
            return "";
        }
        return "Recorder: " + RecorderNoteFrequencies.format(nearest);
    }

    private void startSpeechRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            tvRecognized.setText(R.string.label_recognition_failed);
            tvDiff.setText("");
            return;
        }

        tvRecognized.setText(R.string.label_recognition_in_progress);
        tvDiff.setText("");

        final SpeechRecognizer recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "vi-VN");
        intent.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true);

        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
            }

            @Override
            public void onBeginningOfSpeech() {
            }

            @Override
            public void onRmsChanged(float rmsdB) {
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
            }

            @Override
            public void onEndOfSpeech() {
            }

            @Override
            public void onError(int error) {
                tvRecognized.setText(R.string.label_recognition_failed);
                tvDiff.setText("");
                recognizer.destroy();
            }

            @Override
            public void onResults(Bundle results) {
                List<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                String recognized = "";
                if (matches != null && !matches.isEmpty()) {
                    recognized = matches.get(0);
                }
                if (recognized.isEmpty()) {
                    tvRecognized.setText(R.string.label_recognition_failed);
                    tvDiff.setText("");
                } else {
                    tvRecognized.setText(getString(R.string.label_recognition_success, recognized));
                    tvDiff.setText(TextDiffUtil.highlightDiff(targetSyllable.getText(), recognized));
                }
                recognizer.destroy();
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
            }

            @Override
            public void onEvent(int eventType, Bundle params) {
            }
        });

        recognizer.startListening(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(stopRecordingRunnable);
        pitchAnalyzer.stop();
        stopReferenceAudio();
        stopUserPlayback();
        deleteTempFile(userRecordingFile);
        if (textToSpeech != null) {
            textToSpeech.shutdown();
            textToSpeech = null;
        }
    }
}
