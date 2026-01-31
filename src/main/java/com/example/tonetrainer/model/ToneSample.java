package com.example.tonetrainer.model;

import java.util.List;

public class ToneSample {
    private final List<Float> pitchHz;
    private final int frameStepMs;

    public ToneSample(List<Float> pitchHz, int frameStepMs) {
        this.pitchHz = pitchHz;
        this.frameStepMs = frameStepMs;
    }

    public List<Float> getPitchHz() {
        return pitchHz;
    }

    public int getFrameStepMs() {
        return frameStepMs;
    }

    public Direction getDirection() {
        if (pitchHz == null || pitchHz.size() < 2) {
            return Direction.FLAT;
        }
        float first = 0f;
        float last = 0f;
        boolean firstSet = false;
        for (Float value : pitchHz) {
            if (value != null && value > 0f) {
                if (!firstSet) {
                    first = value;
                    firstSet = true;
                }
                last = value;
            }
        }
        if (!firstSet) {
            return Direction.FLAT;
        }
        float diff = last - first;
        if (diff > 20f) {
            return Direction.RISING;
        } else if (diff < -20f) {
            return Direction.FALLING;
        } else {
            return Direction.FLAT;
        }
    }

    public enum Direction {
        RISING,
        FALLING,
        FLAT
    }
}
