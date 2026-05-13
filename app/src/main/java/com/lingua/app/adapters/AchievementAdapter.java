package com.lingua.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.lingua.app.R;
import com.lingua.app.models.Achievement;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AchievementAdapter extends RecyclerView.Adapter<AchievementAdapter.VH> {
    private final List<Achievement> items;
    /** Tracks which "unlocked" rows we have already animated, so the bounce
     *  plays exactly once per item rather than every recycle. */
    private final Set<Long> animatedIds = new HashSet<>();

    public AchievementAdapter(List<Achievement> items) {
        this.items = items;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_achievement, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Achievement a = items.get(pos);
        h.tvIcon.setText(a.icon != null ? a.icon : "🏅");
        h.tvTitle.setText(a.title != null ? a.title : a.code);
        h.tvDescription.setText(a.description != null ? a.description : "");
        h.tvRarity.setText(a.rarity != null ? a.rarity : "COMMON");

        if (a.unlocked) {
            h.itemView.setAlpha(1.0f);
            h.tvRarity.setBackgroundColor(h.itemView.getContext().getResources().getColor(R.color.lingua_green));
            h.tvRarity.setText("✅ Đã mở");
            // Play the celebratory bounce animation the first time the row appears.
            if (!animatedIds.contains(a.id)) {
                animatedIds.add(a.id);
                h.tvIcon.startAnimation(AnimationUtils.loadAnimation(
                        h.itemView.getContext(), R.anim.bounce_celebrate));
            }
        } else {
            h.itemView.setAlpha(0.5f);
            h.tvRarity.setBackgroundColor(h.itemView.getContext().getResources().getColor(R.color.lingua_gray));
            h.tvRarity.setText("🔒 Chưa mở");
        }
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvIcon, tvTitle, tvDescription, tvRarity;
        VH(View v) {
            super(v);
            tvIcon = v.findViewById(R.id.tvIcon);
            tvTitle = v.findViewById(R.id.tvTitle);
            tvDescription = v.findViewById(R.id.tvDescription);
            tvRarity = v.findViewById(R.id.tvRarity);
        }
    }
}
