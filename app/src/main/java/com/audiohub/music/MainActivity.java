package com.audiohub.music;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    int background = Color.rgb(9, 9, 15);
    int purple = Color.rgb(168, 85, 247);
    int white = Color.WHITE;
    int gray = Color.rgb(161, 161, 170);

    LinearLayout root;
    TextView content;

    TextView makeText(String value, int size, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        return text;
    }

    void showPage(String page) {
        content.setText(page);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 35, 24, 20);
        root.setBackgroundColor(background);

        TextView title = makeText("オーディオハブ", 27, white);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, 1);
        root.addView(title);

        TextView subtitle = makeText("YOUR MUSIC. YOUR WORLD.", 11, purple);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle);

        content = makeText(
            "\nWelcome to Audio Hub\n\nYour music, all in one place.\n\nStart building your music world.",
            18, white
        );
        content.setGravity(Gravity.CENTER);
        root.addView(content, new LinearLayout.LayoutParams(
            -1, 0, 1
        ));

        LinearLayout navigation = new LinearLayout(this);
        navigation.setGravity(Gravity.CENTER);
        navigation.setOrientation(LinearLayout.HORIZONTAL);

        String[] pages = {"Home", "Library", "Downloads", "AI DJ"};

        for (String page : pages) {
            TextView button = makeText(page, 12, gray);
            button.setGravity(Gravity.CENTER);
            button.setPadding(5, 18, 5, 18);
            button.setOnClickListener(v -> showPage(page));
            navigation.addView(button, new LinearLayout.LayoutParams(
                0, -2, 1
            ));
        }

        root.addView(navigation);
        setContentView(root);
    }
}
