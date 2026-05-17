package com.lingua.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.lingua.app.R;
import com.lingua.app.models.Word;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Word list adapter.
 *
 * Adds:
 *  - Favorite (bookmark) button per row.
 *  - Whole-row click opens word detail.
 *  - Long-press shows the inline example sentence (if any).
 */
public class WordAdapter extends RecyclerView.Adapter<WordAdapter.ViewHolder> {

    private final List<Word> words;
    private final OnSpeakListener speakListener;
    private OnWordClickListener clickListener;
    private OnFavoriteListener favoriteListener;
    private final Set<Long> favoriteIds = new HashSet<>();

    public interface OnSpeakListener { void onSpeak(Word word); }
    public interface OnWordClickListener { void onClick(Word word); }
    public interface OnFavoriteListener { void onToggleFavorite(Word word, boolean nowFavorite); }

    public WordAdapter(List<Word> words, OnSpeakListener listener) {
        this.words = words;
        this.speakListener = listener;
    }

    public void setOnWordClickListener(OnWordClickListener l) { this.clickListener = l; }
    public void setOnFavoriteListener(OnFavoriteListener l) { this.favoriteListener = l; }

    public void setFavoriteIds(Set<Long> ids) {
        favoriteIds.clear();
        if (ids != null) favoriteIds.addAll(ids);
        notifyDataSetChanged();
    }

    public void markFavorite(long wordId, boolean fav) {
        if (fav) favoriteIds.add(wordId); else favoriteIds.remove(wordId);
    }

    // BUG #R3-M4 FIX: convenience helpers for VocabularyActivity to sync the
    // favoriteIds set immediately after a successful toggle (instead of
    // waiting for onResume() to reload from the backend). Without this, an
    // item that scrolls out of the viewport and back can flicker — its
    // ViewHolder gets rebound from `favoriteIds` which still reflects the
    // stale "before toggle" state.
    public void addFavoriteId(long id) {
        favoriteIds.add(id);
    }

    public void removeFavoriteId(long id) {
        favoriteIds.remove(id);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_word, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Word word = words.get(position);

        holder.tvWord.setText(word.getText());
        holder.tvReading.setText(word.getDisplayReading());

        String level = word.getLevel();
        if (level != null && !level.isEmpty()) {
            holder.tvLevel.setText(level);
            holder.tvLevel.setVisibility(View.VISIBLE);
        } else {
            holder.tvLevel.setVisibility(View.GONE);
        }

        if (word.getMeanings() != null && !word.getMeanings().isEmpty()) {
            holder.tvMeaning.setText(word.getMeanings().get(0).getMeaning());
            String example = word.getMeanings().get(0).getExample();
            String exTrans = word.getMeanings().get(0).getExampleTranslation();
            if (example != null && !example.isEmpty()) {
                holder.tvExample.setText(example);
                holder.tvExampleTrans.setText(exTrans != null ? exTrans : "");
            }
        }
        holder.layoutExample.setVisibility(View.GONE);

        // favorite state
        boolean isFav = favoriteIds.contains(word.getId());
        holder.btnFavorite.setImageResource(isFav
                ? android.R.drawable.btn_star_big_on
                : android.R.drawable.btn_star_big_off);

        holder.btnFavorite.setOnClickListener(v -> {
            boolean nowFav = !favoriteIds.contains(word.getId());
            markFavorite(word.getId(), nowFav);
            holder.btnFavorite.setImageResource(nowFav
                    ? android.R.drawable.btn_star_big_on
                    : android.R.drawable.btn_star_big_off);
            if (favoriteListener != null) favoriteListener.onToggleFavorite(word, nowFav);
        });

        holder.btnSpeak.setOnClickListener(v -> {
            if (speakListener != null) speakListener.onSpeak(word);
        });

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onClick(word);
        });

        holder.itemView.setOnLongClickListener(v -> {
            boolean isVisible = holder.layoutExample.getVisibility() == View.VISIBLE;
            holder.layoutExample.setVisibility(isVisible ? View.GONE : View.VISIBLE);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return words.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvWord, tvReading, tvMeaning, tvLevel, tvExample, tvExampleTrans;
        ImageButton btnSpeak, btnFavorite;
        View layoutExample;

        ViewHolder(View v) {
            super(v);
            tvWord = v.findViewById(R.id.tvWord);
            tvReading = v.findViewById(R.id.tvReading);
            tvMeaning = v.findViewById(R.id.tvMeaning);
            tvLevel = v.findViewById(R.id.tvLevel);
            tvExample = v.findViewById(R.id.tvExample);
            tvExampleTrans = v.findViewById(R.id.tvExampleTrans);
            btnSpeak = v.findViewById(R.id.btnSpeak);
            btnFavorite = v.findViewById(R.id.btnFavorite);
            layoutExample = v.findViewById(R.id.layoutExample);
        }
    }
}
