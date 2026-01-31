package com.example.tonetrainer.audio;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

public class PitchAnalyzer {

    public interface PitchListener {
        void onPitch(float pitchHz);
    }

    private volatile boolean running;
    private Thread workerThread;

    public void startRealtimePitch(final PitchListener listener) {
        if (running) {
            return;
        }
        running = true;

        workerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                int sampleRate = 22050;
                int channelConfig = AudioFormat.CHANNEL_IN_MONO;
                int audioFormat = AudioFormat.ENCODING_PCM_16BIT;

                int minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
                if (minBufferSize <= 0) {
                    running = false;
                    return;
                }

                AudioRecord record = new AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        sampleRate,
                        channelConfig,
                        audioFormat,
                        minBufferSize
                );

                short[] buffer = new short[minBufferSize];

                try {
                    record.startRecording();
                    while (running) {
                        int read = record.read(buffer, 0, buffer.length);
                        if (read > 0) {
                            float pitch = estimatePitch(buffer, read, sampleRate);
                            if (pitch > 0f && listener != null) {
                                listener.onPitch(pitch);
                            }
                        }
                    }
                    record.stop();
                } finally {
                    record.release();
                }
            }
        });
        workerThread.start();
    }

    public void stop() {
        running = false;
        if (workerThread != null) {
            try {
                workerThread.join();
            } catch (InterruptedException ignored) {
            }
            workerThread = null;
        }
    }

    private float estimatePitch(short[] buffer, int length, int sampleRate) {
        int crossings = 0;
        for (int i = 1; i < length; i++) {
            short prev = buffer[i - 1];
            short curr = buffer[i];
            if ((prev >= 0 && curr < 0) || (prev < 0 && curr >= 0)) {
                crossings++;
            }
        }
        if (crossings == 0) {
            return 0f;
        }
        float frequency = (sampleRate * crossings) / (2f * length);
        if (frequency < 50f || frequency > 400f) {
            return 0f;
        }
        return frequency;
    }
}
