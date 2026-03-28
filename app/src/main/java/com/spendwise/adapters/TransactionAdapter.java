package com.spendwise.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.spendwise.R;
import com.spendwise.data.Transaction;
import com.spendwise.utils.DateDisplayUtil;

import java.util.List;
import java.util.Locale;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.ViewHolder> {

    public interface OnClickListener { void onClick(Transaction tx); }

    private List<Transaction> transactions;
    private OnClickListener listener;

    public TransactionAdapter(List<Transaction> transactions, OnClickListener listener) {
        this.transactions = transactions;
        this.listener = listener;
    }

    public void updateTransactions(List<Transaction> newData) {
        this.transactions = newData;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transaction, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        Transaction tx = transactions.get(position);

        h.tvMerchant.setText(tx.getMerchantName());
        String category = tx.getCategory() != null ? tx.getCategory() : "Uncategorized";
        String date = DateDisplayUtil.toIndianDisplayDate(tx.getDate());
        h.tvCategoryDate.setText(date.isEmpty() ? category : (category + " · " + date));

        h.tvAmount.setText(String.format(Locale.getDefault(), "₹%.2f", tx.getAmount()));

        if (Transaction.TYPE_INCOME.equalsIgnoreCase(tx.getType())) {
            h.tvAmount.setTextColor(Color.parseColor("#4CAF50"));
            if (h.ivIcon != null) h.ivIcon.setImageResource(android.R.drawable.stat_sys_download_done);
        } else {
            h.tvAmount.setTextColor(Color.parseColor("#F44336"));
            if (h.ivIcon != null) h.ivIcon.setImageResource(android.R.drawable.stat_sys_upload_done);
        }
        
        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(tx);
        });
    }

    @Override
    public int getItemCount() {
        return transactions != null ? transactions.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvMerchant, tvCategoryDate, tvAmount;
        ImageView ivIcon;

        ViewHolder(View view) {
            super(view);
            tvMerchant = view.findViewById(R.id.tv_merchant);
            tvCategoryDate = view.findViewById(R.id.tv_category_date);
            tvAmount = view.findViewById(R.id.tv_amount);
            ivIcon = view.findViewById(R.id.iv_category_icon);
        }
    }
}
