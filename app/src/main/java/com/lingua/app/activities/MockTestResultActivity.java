package com.lingua.app.activities;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.lingua.app.R;

public class MockTestResultActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mock_test_result);

        int scorePercent = getIntent().getIntExtra("scorePercent", 0);
        int correctCount = getIntent().getIntExtra("correctCount", 0);
        int total = getIntent().getIntExtra("total", 0);
        int xp = getIntent().getIntExtra("xpEarned", 0);
        boolean passed = getIntent().getBooleanExtra("passed", scorePercent >= 60);

        TextView tvVerdict = findViewById(R.id.tvVerdict);
        TextView tvScore = findViewById(R.id.tvScore);
        TextView tvCorrect = findViewById(R.id.tvCorrect);
        TextView tvXp = findViewById(R.id.tvXp);
        Button btnDone = findViewById(R.id.btnDone);

        tvVerdict.setText(passed ? "🎉 PASSED" : "💪 KEEP TRYING");
        tvVerdict.setTextColor(getResources().getColor(passed ? R.color.lingua_green : R.color.lingua_red));
        tvScore.setText(scorePercent + "%");
        tvCorrect.setText("Đúng " + correctCount + "/" + total);
        tvXp.setText("⚡ +" + xp + " XP");

        btnDone.setOnClickListener(v -> finish());

        if (getSupportActionBar() != null) getSupportActionBar().setTitle("Kết quả");
    }
}
