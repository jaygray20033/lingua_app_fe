package com.lingua.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.lingua.app.R;
import com.lingua.app.models.Enrollment;

import java.util.List;

public class EnrollmentAdapter extends RecyclerView.Adapter<EnrollmentAdapter.VH> {

    public interface OnEnrollmentClickListener {
        void onEnrollmentClick(Enrollment enrollment);
    }

    private final List<Enrollment> items;
    private final OnEnrollmentClickListener listener;

    public EnrollmentAdapter(List<Enrollment> items, OnEnrollmentClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_enrollment, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Enrollment e = items.get(pos);
        String flag = e.flagEmoji != null ? e.flagEmoji + "  " : "";
        h.tvCourseTitle.setText(flag + e.getDisplayTitle());
        StringBuilder sub = new StringBuilder();
        if (e.levelCode != null) sub.append(e.levelCode);
        if (e.totalLessons > 0) {
            if (sub.length() > 0) sub.append("  ·  ");
            sub.append(e.completedLessons).append("/").append(e.totalLessons).append(" bài");
        }
        if (e.nextLessonTitle != null) {
            if (sub.length() > 0) sub.append("\n");
            sub.append("Tiếp theo: ").append(e.nextLessonTitle);
        }
        h.tvCourseSubtitle.setText(sub.toString());

        h.progressCourse.setMax(100);
        h.progressCourse.setProgress(e.progress);
        h.tvCourseProgress.setText(e.progress + "% hoàn thành");

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onEnrollmentClick(e);
        });
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvCourseTitle, tvCourseSubtitle, tvCourseProgress;
        ProgressBar progressCourse;
        VH(View v) {
            super(v);
            tvCourseTitle = v.findViewById(R.id.tvCourseTitle);
            tvCourseSubtitle = v.findViewById(R.id.tvCourseSubtitle);
            tvCourseProgress = v.findViewById(R.id.tvCourseProgress);
            progressCourse = v.findViewById(R.id.progressCourse);
        }
    }
}
