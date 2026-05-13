package com.lingua.app.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.lingua.app.R;
import com.lingua.app.models.LeaderboardEntry;

import java.util.List;

public class LeaderboardAdapter extends RecyclerView.Adapter<LeaderboardAdapter.VH> {
    private final List<LeaderboardEntry> items;
    /** Highlights the current user’s row when matched by id. -1 = no highlight. */
    private long currentUserId = -1L;

    public LeaderboardAdapter(List<LeaderboardEntry> items) {
        this.items = items;
    }

    public void setCurrentUserId(long id) {
        this.currentUserId = id;
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_leaderboard, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        LeaderboardEntry e = items.get(pos);
        String medal = pos == 0 ? "🥇" : pos == 1 ? "🥈" : pos == 2 ? "🥉" : String.valueOf(e.rank > 0 ? e.rank : pos + 1);
        h.tvRank.setText(medal);
        h.tvName.setText(e.displayName != null ? e.displayName : "User #" + e.id);
        h.tvXp.setText(e.xp + " XP");

        boolean me = currentUserId > 0 && e.id == currentUserId;
        h.itemView.setBackgroundColor(me ? 0xFFE3F2FD : Color.WHITE);
        h.tvName.setTextColor(me ? 0xFF1976D2 : 0xFF3C3C3C);

        // 6.10 FIX: hiển thị avatar nếu có URL. Dùng Glide (đã có trong
        // dependencies) qua reflection để tránh crash nếu module này build
        // không có Glide. Fallback icon mặc định khi URL trống/lỗi.
        if (h.ivAvatar != null) {
            String avatarUrl = e.avatarUrl;
            if (avatarUrl == null || avatarUrl.isEmpty()) {
                h.ivAvatar.setImageResource(android.R.drawable.ic_menu_myplaces);
            } else {
                try {
                    Class<?> glide = Class.forName("com.bumptech.glide.Glide");
                    Object req = glide.getMethod("with", View.class).invoke(null, h.ivAvatar);
                    Object loader = req.getClass().getMethod("load", String.class).invoke(req, avatarUrl);
                    loader.getClass().getMethod("into", ImageView.class).invoke(loader, h.ivAvatar);
                } catch (Throwable ignore) {
                    h.ivAvatar.setImageResource(android.R.drawable.ic_menu_myplaces);
                }
            }
        }
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvRank, tvName, tvXp;
        ImageView ivAvatar;
        VH(View v) {
            super(v);
            tvRank = v.findViewById(R.id.tvRank);
            tvName = v.findViewById(R.id.tvName);
            tvXp = v.findViewById(R.id.tvXp);
            ivAvatar = v.findViewById(R.id.ivAvatar);
        }
    }
}
