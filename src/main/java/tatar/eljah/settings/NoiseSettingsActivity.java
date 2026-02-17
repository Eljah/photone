package tatar.eljah.settings;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.Locale;

import tatar.eljah.R;
import tatar.eljah.audio.PitchAnalyzer;
import tatar.eljah.ui.IntensityGraphView;
import tatar.eljah.ui.SpectrogramView;

public class NoiseSettingsActivity extends AppCompatActivity {

    private static final int REQUEST_RECORD_AUDIO = 1201;
    private static final int SEEKBAR_MAX = 100;

    private TextView levelText;
    private EditText levelInput;
    private SeekBar thresholdSeek;
    private IntensityGraphView intensityGraph;
    private SpectrogramView spectrogramView;

    private PitchAnalyzer pitchAnalyzer;
    private float noiseThreshold;
    private volatile float currentIntensity;
    private boolean isUpdatingUi;
    private boolean hasRecordPermission;
    private boolean isMonitoring;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleManager.applyLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_noise_settings);
        setTitle(R.string.title_noise_settings);

        levelText = findViewById(R.id.tv_noise_level_value);
        levelInput = findViewById(R.id.et_noise_level_input);
        thresholdSeek = findViewById(R.id.seek_noise_level);
        intensityGraph = findViewById(R.id.intensityGraphView);
        spectrogramView = findViewById(R.id.noiseSpectrogramView);

        noiseThreshold = NoiseSettingsStore.getNoiseThreshold(this);
        pitchAnalyzer = new PitchAnalyzer();

        thresholdSeek.setMax(SEEKBAR_MAX);
        thresholdSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                updateThreshold(progress / (float) SEEKBAR_MAX, true);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        intensityGraph.setOnThresholdChangedListener(new IntensityGraphView.OnThresholdChangedListener() {
            @Override
            public void onThresholdChanged(float threshold) {
                updateThreshold(threshold, true);
            }
        });

        levelInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
                if (actionId == EditorInfo.IME_ACTION_DONE
                        || actionId == EditorInfo.IME_ACTION_GO
                        || actionId == EditorInfo.IME_ACTION_NEXT
                        || actionId == EditorInfo.IME_ACTION_SEND) {
                    commitThresholdFromInput();
                    return true;
                }
                return false;
            }
        });

        levelInput.setOnFocusChangeListener(new TextView.OnFocusChangeListener() {
            @Override
            public void onFocusChange(android.view.View v, boolean hasFocus) {
                if (!hasFocus) {
                    commitThresholdFromInput();
                }
            }
        });

        updateThreshold(noiseThreshold, false);
        ensurePermissionAndStart();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (hasRecordPermission) {
            startMonitoring();
        }
    }

    @Override
    protected void onStop() {
        stopMonitoring();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        stopMonitoring();
        super.onDestroy();
    }

    private void ensurePermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            hasRecordPermission = true;
            startMonitoring();
            return;
        }
        hasRecordPermission = false;
        ActivityCompat.requestPermissions(this,
                new String[]{android.Manifest.permission.RECORD_AUDIO},
                REQUEST_RECORD_AUDIO);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_RECORD_AUDIO) {
            return;
        }
        hasRecordPermission = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (hasRecordPermission) {
            startMonitoring();
        } else {
            stopMonitoring();
        }
    }

    private void startMonitoring() {
        if (isMonitoring || pitchAnalyzer == null) {
            return;
        }
        isMonitoring = true;
        currentIntensity = 0f;
        pitchAnalyzer.startRealtimePitch(null, new PitchAnalyzer.SpectrumListener() {
            @Override
            public void onSpectrum(final float[] magnitudes, final int sampleRate) {
                final float[] frame = applyNoiseGate(magnitudes);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        spectrogramView.addSpectrumFrame(frame, sampleRate, frame.length * 2);
                    }
                });
            }
        }, new PitchAnalyzer.AudioListener() {
            @Override
            public void onAudio(short[] samples, int length, int sampleRate) {
                final float intensity = computeIntensity(samples, length);
                currentIntensity = intensity;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        intensityGraph.addIntensity(intensity);
                    }
                });
            }
        });
    }

    private void stopMonitoring() {
        if (!isMonitoring || pitchAnalyzer == null) {
            return;
        }
        pitchAnalyzer.stop();
        isMonitoring = false;
    }

    private float[] applyNoiseGate(float[] magnitudes) {
        if (currentIntensity >= noiseThreshold) {
            return magnitudes;
        }
        return new float[magnitudes.length];
    }

    private float computeIntensity(short[] samples, int length) {
        if (samples == null || length <= 0) {
            return 0f;
        }
        double sumSquares = 0d;
        for (int i = 0; i < length; i++) {
            double normalized = samples[i] / 32768d;
            sumSquares += normalized * normalized;
        }
        double rms = Math.sqrt(sumSquares / length);
        return (float) Math.min(1d, rms * 8d);
    }

    private void updateThreshold(float value, boolean persist) {
        noiseThreshold = NoiseSettingsStore.clamp(value);
        if (persist) {
            NoiseSettingsStore.setNoiseThreshold(this, noiseThreshold);
        }
        isUpdatingUi = true;
        String text = String.format(Locale.US, "%.2f", noiseThreshold);
        levelText.setText(getString(R.string.noise_level_value_format, text));
        if (!levelInput.hasFocus()) {
            levelInput.setText(text);
            levelInput.setSelection(levelInput.getText().length());
        }
        thresholdSeek.setProgress(Math.round(noiseThreshold * SEEKBAR_MAX));
        intensityGraph.setThreshold(noiseThreshold);
        isUpdatingUi = false;
    }

    private void commitThresholdFromInput() {
        if (isUpdatingUi) {
            return;
        }
        String raw = levelInput.getText().toString().trim();
        if (raw.isEmpty()) {
            updateThreshold(noiseThreshold, false);
            return;
        }
        try {
            float parsed = Float.parseFloat(raw.replace(',', '.'));
            updateThreshold(NoiseSettingsStore.clamp(parsed), true);
        } catch (NumberFormatException ignored) {
            updateThreshold(noiseThreshold, false);
        }
    }
}
