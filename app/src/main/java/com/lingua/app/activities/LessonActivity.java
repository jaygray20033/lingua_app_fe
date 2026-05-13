package com.lingua.app.activities;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.lingua.app.R;

public class LessonActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Redirect to PracticeActivity with lessonId
        long lessonId = getIntent().getLongExtra("lessonId", 1);
        Intent intent = new Intent(this, PracticeActivity.class);
        intent.putExtra("lessonId", lessonId);
        startActivity(intent);
        finish();
    }
}
