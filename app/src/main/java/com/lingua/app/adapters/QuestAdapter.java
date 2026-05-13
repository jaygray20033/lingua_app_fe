package com.lingua.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.lingua.app.R;
import com.lingua.app.models.DailyQuest;

import java.util.List;

public class QuestAdapter extends RecyclerView.Adapter<QuestAdapter.VH> {
    private final List<DailyQuest> items;
    private final OnClaimListener listener;

    public interface OnClaimListener {
        void onClaim(DailyQuest quest, int position);
    }

    public QuestAdapter(List<DailyQuest> items, OnClaimListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_quest, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        DailyQuest q = items.get(pos);
        h.tvQuestCode.setText(q.questCode != null ? q.questCode.replace('_', ' ') : "Nhiệm vụ");
        h.tvDescription.setText(q.description != null ? q.description : "");
        h.tvGems.setText("💎 " + q.rewardGems);

        // 6.11 FIX: ProgressBar.max được set động trong adapter (XML không có
        // android:max), đảm bảo progress chỉ tối đa = q.target.
        int max = Math.max(q.target, 1);
        h.progressQuest.setMax(max);
        h.progressQuest.setProgress(Math.min(q.progress, max));
        h.tvProgress.setText(q.progress + " / " + q.target);

        // 6.11 FIX: btnClaim chỉ visible khi có thể click (quest đã complete &
        // chưa claim). Trước đây nút luôn visible với 3 state (đã nhận / nhận
        // thưởng / đang làm). State "Đang làm" → ẩn nút hoàn toàn cho gọn UI.
        if (q.claimedAt != null) {
            h.btnClaim.setVisibility(View.VISIBLE);
            h.btnClaim.setText("Đã nhận ✅");
            h.btnClaim.setEnabled(false);
            h.btnClaim.setAlpha(0.5f);
            h.btnClaim.setOnClickListener(null);
        } else if (q.completed == 1) {
            h.btnClaim.setVisibility(View.VISIBLE);
            h.btnClaim.setText("Nhận thưởng");
            h.btnClaim.setEnabled(true);
            h.btnClaim.setAlpha(1.0f);
            h.btnClaim.setOnClickListener(v -> {
                if (listener != null) listener.onClaim(q, pos);
            });
        } else {
            // 6.11 FIX: ẩn nút khi quest chưa hoàn thành (thay vì hiện
            // "Đang làm…" disabled). Progress bar + text "X/Y" đã đủ rõ.
            h.btnClaim.setVisibility(View.GONE);
            h.btnClaim.setOnClickListener(null);
        }
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvQuestCode, tvDescription, tvProgress, tvGems;
        ProgressBar progressQuest;
        Button btnClaim;
        VH(View v) {
            super(v);
            tvQuestCode = v.findViewById(R.id.tvQuestCode);
            tvDescription = v.findViewById(R.id.tvDescription);
            tvProgress = v.findViewById(R.id.tvProgress);
            tvGems = v.findViewById(R.id.tvGems);
            progressQuest = v.findViewById(R.id.progressQuest);
            btnClaim = v.findViewById(R.id.btnClaim);
        }
    }
}
