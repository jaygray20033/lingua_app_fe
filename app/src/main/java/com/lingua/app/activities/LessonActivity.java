package com.lingua.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Thin redirect activity that forwards to PracticeActivity with the given
 * lessonId. Kept for manifest backward compatibility — some deep-links and
 * older intents still target this activity name.
 *
 * BUG #R3-M6 FIX:
 *   - Previously the default value when `lessonId` was missing was 1, which
 *     silently routed users to a (potentially non-existent) lesson #1 and
 *     left them stuck on the PracticeActivity error screen.
 *   - Also the activity skipped setContentView() altogether — on slow
 *     devices users could see a brief blank/white flash before
 *     startActivity()→finish() completed.
 *   - Now we validate the extra and toast + finish gracefully when missing
 *     or invalid.
 */
public class LessonActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // -1 sentinel so we can detect "extra not present" vs "actual id 1".
        long lessonId = getIntent().getLongExtra("lessonId", -1L);
        if (lessonId <= 0) {
            Toast.makeText(this, "Bài học không hợp lệ", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        Intent intent = new Intent(this, PracticeActivity.class);
        intent.putExtra("lessonId", lessonId);
        startActivity(intent);
        finish();
    }
}
