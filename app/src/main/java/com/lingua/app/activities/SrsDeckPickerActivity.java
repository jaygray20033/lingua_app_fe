package com.lingua.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.lingua.app.R;
import com.lingua.app.api.ApiClient;
import com.lingua.app.api.LinguaApiService;
import com.lingua.app.models.ApiResponse;
import com.lingua.app.models.Deck;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import java.util.ArrayList;
import java.util.List;

/**
 * Lets the user pick a deck before starting an SRS review session.
 * Source: GET /api/srs/decks
 *
 * "Tất cả các deck" option starts the SRS session without a deck filter so the
 * server can mix reviews from every deck. Long-press a deck to delete it
 * (DELETE /api/my-decks/{deckId}).
 */
public class SrsDeckPickerActivity extends AppCompatActivity {

    private LinguaApiService apiService;
    private final List<Deck> decks = new ArrayList<>();
    private DeckAdapter adapter;
    private ProgressBar progressBar;
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_list);

        TextView tvTitle = findViewById(R.id.tvTitle);
        tvTitle.setText("📚 Chọn deck để ôn SRS");

        progressBar = findViewById(R.id.progressBar);
        tvEmpty = findViewById(R.id.tvEmpty);

        RecyclerView rv = findViewById(R.id.recyclerView);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DeckAdapter();
        rv.setAdapter(adapter);

        apiService = ApiClient.getService(this);
        loadDecks();
    }

    private void loadDecks() {
        progressBar.setVisibility(View.VISIBLE);
        apiService.getSrsDecks().enqueue(new Callback<ApiResponse<List<Deck>>>() {
            @Override public void onResponse(Call<ApiResponse<List<Deck>>> c, Response<ApiResponse<List<Deck>>> r) {
                progressBar.setVisibility(View.GONE);
                decks.clear();
                // Always include an "all decks" pseudo entry first
                Deck all = new Deck();
                all.id = 0;
                all.name = "🌐 Tất cả các deck";
                all.description = "Ôn mọi thẻ đến hạn";
                decks.add(all);
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess() && r.body().getData() != null) {
                    decks.addAll(r.body().getData());
                }
                adapter.notifyDataSetChanged();
                tvEmpty.setVisibility(decks.size() <= 1 ? View.VISIBLE : View.GONE);
            }
            @Override public void onFailure(Call<ApiResponse<List<Deck>>> c, Throwable t) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(SrsDeckPickerActivity.this, "Lỗi: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void confirmDeleteDeck(Deck deck) {
        new AlertDialog.Builder(this)
                .setTitle("Xoá deck")
                .setMessage("Bạn có chắc muốn xoá deck \"" + deck.name + "\"?")
                .setPositiveButton("Xoá", (d, w) -> apiService.deleteDeck(deck.id).enqueue(new Callback<ApiResponse<Object>>() {
                    @Override public void onResponse(Call<ApiResponse<Object>> c, Response<ApiResponse<Object>> r) {
                        if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                            Toast.makeText(SrsDeckPickerActivity.this, "Đã xoá", Toast.LENGTH_SHORT).show();
                            loadDecks();
                        } else {
                            Toast.makeText(SrsDeckPickerActivity.this, "Không xoá được", Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override public void onFailure(Call<ApiResponse<Object>> c, Throwable t) {
                        Toast.makeText(SrsDeckPickerActivity.this, "Lỗi: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }))
                .setNegativeButton("Huỷ", null)
                .show();
    }

    private class DeckAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            // Build a simple two-line item programmatically to avoid an extra layout file
            LinearLayout root = new LinearLayout(parent.getContext());
            root.setOrientation(LinearLayout.VERTICAL);
            int pad = (int) (16 * getResources().getDisplayMetrics().density);
            root.setPadding(pad, pad, pad, pad);
            root.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            root.setBackgroundColor(0xFFFFFFFF);

            TextView title = new TextView(parent.getContext());
            title.setId(android.R.id.text1);
            title.setTextSize(18);
            title.setTextColor(0xFF3C3C3C);
            root.addView(title);

            TextView sub = new TextView(parent.getContext());
            sub.setId(android.R.id.text2);
            sub.setTextSize(13);
            sub.setTextColor(0xFFAFAFAF);
            root.addView(sub);

            return new RecyclerView.ViewHolder(root) {};
        }

        @Override public void onBindViewHolder(RecyclerView.ViewHolder h, int pos) {
            Deck d = decks.get(pos);
            ((TextView) h.itemView.findViewById(android.R.id.text1)).setText(
                    (d.flagEmoji != null ? d.flagEmoji + " " : "") + (d.name != null ? d.name : "Deck"));
            String sub = d.description != null ? d.description : "";
            if (d.id > 0) sub += "  ·  " + d.cardCount + " thẻ";
            ((TextView) h.itemView.findViewById(android.R.id.text2)).setText(sub);

            h.itemView.setOnClickListener(v -> {
                Intent i = new Intent(SrsDeckPickerActivity.this, FlashcardActivity.class);
                i.putExtra("deckId", d.id);
                startActivity(i);
                finish();
            });
            h.itemView.setOnLongClickListener(v -> {
                if (d.id > 0) confirmDeleteDeck(d);
                return true;
            });
        }

        @Override public int getItemCount() { return decks.size(); }
    }
}
