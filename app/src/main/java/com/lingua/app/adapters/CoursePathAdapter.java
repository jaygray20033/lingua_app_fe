package com.lingua.app.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.lingua.app.R;
import com.lingua.app.models.CoursePath;

import java.util.List;

/**
 * Renders a course path: each row is a Section that contains a vertical list of
 * Units, where each Unit shows its child Lessons with status emoji
 * (✅ completed / 🔓 available / 🔒 locked).
 */
public class CoursePathAdapter extends RecyclerView.Adapter<CoursePathAdapter.SectionVH> {

    public interface OnLessonClickListener {
        void onLessonClick(CoursePath.PathLesson lesson);
    }

    private final List<CoursePath.PathSection> sections;
    private final OnLessonClickListener listener;

    public CoursePathAdapter(List<CoursePath.PathSection> sections, OnLessonClickListener listener) {
        this.sections = sections;
        this.listener = listener;
    }

    @NonNull @Override
    public SectionVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_course_section, parent, false);
        return new SectionVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull SectionVH h, int pos) {
        CoursePath.PathSection section = sections.get(pos);
        h.tvSectionTitle.setText("📂 " + (section.title != null ? section.title : "Section"));
        h.layoutUnits.removeAllViews();

        Context ctx = h.itemView.getContext();
        if (section.units == null) return;

        for (CoursePath.PathUnit unit : section.units) {
            // Unit header
            TextView tvUnit = new TextView(ctx);
            tvUnit.setText("📘 " + (unit.title != null ? unit.title : "Unit") +
                    (unit.communicationGoal != null ? "  ·  " + unit.communicationGoal : ""));
            tvUnit.setTextSize(15);
            tvUnit.setTypeface(tvUnit.getTypeface(), android.graphics.Typeface.BOLD);
            tvUnit.setTextColor(0xFF3C3C3C);
            int pad = (int) (8 * ctx.getResources().getDisplayMetrics().density);
            tvUnit.setPadding(0, pad, 0, pad / 2);
            h.layoutUnits.addView(tvUnit);

            if (unit.lessons == null) continue;
            for (CoursePath.PathLesson lesson : unit.lessons) {
                TextView tvLesson = new TextView(ctx);
                String prefix = lesson.getStatusEmoji();
                String suffix = lesson.xpReward > 0 ? "  +" + lesson.xpReward + " XP" : "";
                tvLesson.setText("    " + prefix + "  " + (lesson.title != null ? lesson.title : "Bài học") + suffix);
                tvLesson.setTextSize(14);
                tvLesson.setTextColor(lesson.isLocked() ? 0xFFAFAFAF : 0xFF3C3C3C);
                tvLesson.setPadding(pad, pad / 2, pad, pad / 2);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                tvLesson.setLayoutParams(lp);
                if (!lesson.isLocked()) {
                    tvLesson.setOnClickListener(v -> {
                        if (listener != null) listener.onLessonClick(lesson);
                    });
                    tvLesson.setBackgroundResource(android.R.drawable.list_selector_background);
                }
                h.layoutUnits.addView(tvLesson);
            }
        }
    }

    @Override public int getItemCount() { return sections.size(); }

    static class SectionVH extends RecyclerView.ViewHolder {
        TextView tvSectionTitle;
        LinearLayout layoutUnits;
        SectionVH(View v) {
            super(v);
            tvSectionTitle = v.findViewById(R.id.tvSectionTitle);
            layoutUnits = v.findViewById(R.id.layoutUnits);
        }
    }
}
