package tatar.eljah;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import tatar.eljah.practice.DiscriminationStatsStore;
import tatar.eljah.practice.SyllableDiscriminationActivity;
import tatar.eljah.settings.LocaleManager;

public class AchievementsActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleManager.applyLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_achievements);
        setTitle(R.string.title_achievements);

        ListView listView = findViewById(R.id.list_achievements);
        TextView emptyView = findViewById(R.id.tv_no_achievements);

        List<DiscriminationStatsStore.Entry> entries = DiscriminationStatsStore.getEntries(this);
        List<String> rows = new ArrayList<>();
        for (DiscriminationStatsStore.Entry entry : entries) {
            rows.add(getString(
                    R.string.label_achievement_item,
                    modeLabel(entry.mode),
                    entry.compared,
                    entry.maxScore
            ));
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.simple_list_item_light, rows);
        listView.setAdapter(adapter);

        if (rows.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            listView.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            listView.setVisibility(View.VISIBLE);
        }
    }

    private String modeLabel(String mode) {
        if (SyllableDiscriminationActivity.MODE_TONE.equals(mode)) {
            return getString(R.string.title_tone_mode);
        }
        return getString(R.string.title_sound_mode);
    }
}
