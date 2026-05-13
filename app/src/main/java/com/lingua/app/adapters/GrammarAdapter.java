package com.lingua.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.lingua.app.R;
import com.lingua.app.models.Grammar;

import java.util.List;

public class GrammarAdapter extends RecyclerView.Adapter<GrammarAdapter.VH> {
    public interface OnClick { void onClick(Grammar g); }

    private final List<Grammar> items;
    private final OnClick listener;

    public GrammarAdapter(List<Grammar> items, OnClick listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_grammar, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Grammar g = items.get(position);
        h.tvTitle.setText(g.title != null ? g.title : "");
        h.tvStructure.setText(g.structure != null ? g.structure : "");
        h.tvMeaning.setText(g.meaning != null ? g.meaning : "");
        String level = g.getDisplayLevel();
        if (level != null && !level.isEmpty()) {
            h.tvLevel.setText(level);
            h.tvLevel.setVisibility(View.VISIBLE);
        } else {
            h.tvLevel.setVisibility(View.GONE);
        }
        h.itemView.setOnClickListener(v -> { if (listener != null) listener.onClick(g); });
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTitle, tvStructure, tvMeaning, tvLevel;
        VH(View v) {
            super(v);
            tvTitle = v.findViewById(R.id.tvTitle);
            tvStructure = v.findViewById(R.id.tvStructure);
            tvMeaning = v.findViewById(R.id.tvMeaning);
            tvLevel = v.findViewById(R.id.tvLevel);
        }
    }
}
