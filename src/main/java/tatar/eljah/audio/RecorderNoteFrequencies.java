package tatar.eljah.audio;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class RecorderNoteFrequencies {

    public static final class NoteFrequency {
        private final String note;
        private final float frequencyHz;

        public NoteFrequency(String note, float frequencyHz) {
            this.note = note;
            this.frequencyHz = frequencyHz;
        }

        public String getNote() {
            return note;
        }

        public float getFrequencyHz() {
            return frequencyHz;
        }
    }

    private static final List<NoteFrequency> YAMAHA_YRS20S3_RANGE = Collections.unmodifiableList(Arrays.asList(
            new NoteFrequency("C5", 523.25f),
            new NoteFrequency("D5", 587.33f),
            new NoteFrequency("E5", 659.25f),
            new NoteFrequency("F5", 698.46f),
            new NoteFrequency("G5", 783.99f),
            new NoteFrequency("A5", 880.00f),
            new NoteFrequency("B5", 987.77f),
            new NoteFrequency("C6", 1046.50f),
            new NoteFrequency("D6", 1174.66f),
            new NoteFrequency("E6", 1318.51f),
            new NoteFrequency("F6", 1396.91f),
            new NoteFrequency("G6", 1567.98f),
            new NoteFrequency("A6", 1760.00f),
            new NoteFrequency("B6", 1975.53f),
            new NoteFrequency("C7", 2093.00f),
            new NoteFrequency("D7", 2349.32f)
    ));

    private RecorderNoteFrequencies() {
    }

    public static List<NoteFrequency> getRecorderRange() {
        return YAMAHA_YRS20S3_RANGE;
    }

    public static float getFrequencyOrDefault(String note, float defaultHz) {
        if (note == null) {
            return defaultHz;
        }
        for (NoteFrequency entry : YAMAHA_YRS20S3_RANGE) {
            if (entry.getNote().equalsIgnoreCase(note)) {
                return entry.getFrequencyHz();
            }
        }
        return defaultHz;
    }

    public static NoteFrequency findNearest(float pitchHz) {
        if (pitchHz <= 0f) {
            return null;
        }
        NoteFrequency nearest = null;
        float minDiff = Float.MAX_VALUE;
        for (NoteFrequency entry : YAMAHA_YRS20S3_RANGE) {
            float diff = Math.abs(entry.getFrequencyHz() - pitchHz);
            if (diff < minDiff) {
                minDiff = diff;
                nearest = entry;
            }
        }
        return nearest;
    }

    public static String format(NoteFrequency noteFrequency) {
        if (noteFrequency == null) {
            return "";
        }
        return String.format(Locale.US, "%s (%.2f Hz)", noteFrequency.getNote(), noteFrequency.getFrequencyHz());
    }
}
