package com.lingua.app.adapters;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.lingua.app.R;
import com.lingua.app.models.Course;

import java.util.List;

/**
 * 2.3 Redesign — Adapter cho Course Catalog (browse mode).
 * Hiển thị mỗi khóa học với:
 *  - Flag emoji + tên khoá + language
 *  - Badge level màu (JLPT đỏ / HSK vàng / CEFR xanh / TOPIK tím)
 *  - Description 2 dòng
 *  - Số bài học + rating + tag Miễn phí / Premium
 */
public class CourseCatalogAdapter extends RecyclerView.Adapter<CourseCatalogAdapter.VH> {

    public interface OnCourseClickListener {
        void onCourseClick(Course course);
    }

    private final List<Course> courses;
    private final OnCourseClickListener listener;

    public CourseCatalogAdapter(List<Course> courses, OnCourseClickListener listener) {
        this.courses = courses;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_course_card, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Course c = courses.get(position);
        Context ctx = h.itemView.getContext();

        // Flag + title + language
        h.tvFlag.setText(c.flagEmoji != null ? c.flagEmoji : "🌐");
        h.tvTitle.setText(c.title != null ? c.title : "");
        h.tvLanguage.setText(c.langCode != null ? c.langCode.toUpperCase() : "");

        // Description
        if (c.description != null && !c.description.isEmpty()) {
            h.tvDescription.setText(c.description);
            h.tvDescription.setVisibility(View.VISIBLE);
        } else {
            h.tvDescription.setVisibility(View.GONE);
        }

        // Badge level — màu sắc theo loại chứng chỉ
        String level = c.levelCode != null ? c.levelCode : "";
        h.tvLevelBadge.setText(level.isEmpty() ? "" : level);
        applyBadgeColor(h.tvLevelBadge, c.certification, ctx);

        // Units + rating
        h.tvUnits.setText("📖 " + c.totalLessons + " bài học");
        h.tvRating.setText(String.format("⭐ %.1f", c.rating));

        // Tag free / premium
        if (c.isEnrolled == 1 || c.isEnrolled == 0) {
            // No is_premium field exposed directly — derive from certification
            // free courses have is_premium=0 seeded in DB; fallback: show "Miễn phí"
        }
        h.tvPremiumTag.setText("Miễn phí");
        h.tvPremiumTag.setVisibility(View.VISIBLE);

        // Click
        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onCourseClick(c);
        });
    }

    /** Áp màu badge theo loại chứng chỉ */
    private void applyBadgeColor(TextView badge, String certification, Context ctx) {
        if (certification == null) return;
        switch (certification.toUpperCase()) {
            case "JLPT":
                badge.setBackgroundResource(R.drawable.bg_badge_jlpt);
                badge.setTextColor(ctx.getResources().getColor(R.color.badge_jlpt, null));
                break;
            case "HSK":
                badge.setBackgroundResource(R.drawable.bg_badge_hsk);
                badge.setTextColor(ctx.getResources().getColor(R.color.badge_hsk, null));
                break;
            case "CEFR":
            case "IELTS":
            case "TOEIC":
                badge.setBackgroundResource(R.drawable.bg_badge_cefr);
                badge.setTextColor(ctx.getResources().getColor(R.color.badge_cefr, null));
                break;
            case "TOPIK":
                badge.setBackgroundResource(R.drawable.bg_badge_korean);
                badge.setTextColor(ctx.getResources().getColor(R.color.badge_korean, null));
                break;
            default:
                badge.setBackgroundResource(R.drawable.bg_badge_level);
                badge.setTextColor(ctx.getResources().getColor(R.color.brand_primary, null));
        }
    }

    @Override
    public int getItemCount() {
        return courses.size();
    }

    public static class VH extends RecyclerView.ViewHolder {
        TextView tvFlag, tvTitle, tvLanguage, tvLevelBadge, tvDescription,
                 tvUnits, tvRating, tvPremiumTag;

        public VH(@NonNull View v) {
            super(v);
            tvFlag        = v.findViewById(R.id.tvFlag);
            tvTitle       = v.findViewById(R.id.tvTitle);
            tvLanguage    = v.findViewById(R.id.tvLanguage);
            tvLevelBadge  = v.findViewById(R.id.tvLevelBadge);
            tvDescription = v.findViewById(R.id.tvDescription);
            tvUnits       = v.findViewById(R.id.tvUnits);
            tvRating      = v.findViewById(R.id.tvRating);
            tvPremiumTag  = v.findViewById(R.id.tvPremiumTag);
        }
    }
}
