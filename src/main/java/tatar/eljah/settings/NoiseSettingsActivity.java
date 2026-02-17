package tatar.eljah.settings;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
    private boolean isUpdatingUi;

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

        levelInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (isUpdatingUi) {
                    return;
                }
                String raw = editable.toString().trim();
                if (raw.isEmpty()) {
                    return;
                }
                try {
                    float parsed = Float.parseFloat(raw.replace(',', '.'));
                    updateThreshold(NoiseSettingsStore.clamp(parsed), true);
                } catch (NumberFormatException ignored) {
                }
            }
        });

        updateThreshold(noiseThreshold, false);
        ensurePermissionAndStart();
    }

    @Override
    protected void onDestroy() {
        if (pitchAnalyzer != null) {
            pitchAnalyzer.stop();
        }
        super.onDestroy();
    }

    private void ensurePermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            startMonitoring();
            return;
        }
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
        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (granted) {
            startMonitoring();
        }
    }

    private void startMonitoring() {
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
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        intensityGraph.addIntensity(intensity);
                    }
                });
            }
        });
    }

    private float[] applyNoiseGate(float[] magnitudes) {
        float max = 0f;
        for (float value : magnitudes) {
            if (value > max) {
                max = value;
            }
        }
        float normalized = Math.min(1f, max / 6000f);
        if (normalized >= noiseThreshold) {
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
        levelInput.setText(text);
        levelInput.setSelection(levelInput.getText().length());
        thresholdSeek.setProgress(Math.round(noiseThreshold * SEEKBAR_MAX));
        intensityGraph.setThreshold(noiseThreshold);
        isUpdatingUi = false;
    }
}
