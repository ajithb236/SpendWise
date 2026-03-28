package com.spendwise.adapters;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.spendwise.R;
import com.spendwise.data.Instrument;
import com.spendwise.dialogs.AccountDetailActivity;
import com.spendwise.dialogs.EditInstrumentActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class InstrumentAdapter extends RecyclerView.Adapter<InstrumentAdapter.ViewHolder> {

    private List<Instrument> instruments = new ArrayList<>();
    private Map<String, Double> netByInstrumentId = new HashMap<>();
    private Context context;

    public InstrumentAdapter(Context context) {
        this.context = context;
    }

    public void setInstruments(List<Instrument> instruments) {
        this.instruments = instruments;
        notifyDataSetChanged();
    }

    public void setNetByInstrumentId(Map<String, Double> netByInstrumentId) {
        this.netByInstrumentId = netByInstrumentId != null ? netByInstrumentId : new HashMap<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_account, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Instrument instrument = instruments.get(position);
        holder.bind(instrument);
    }

    @Override
    public int getItemCount() {
        return instruments.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvAccountName;
        TextView tvBankName;
        TextView tvBalance;
        View container;

        ViewHolder(View itemView) {
            super(itemView);
            tvAccountName = itemView.findViewById(R.id.tv_account_name);
            tvBankName = itemView.findViewById(R.id.tv_bank_name);
            tvBalance = itemView.findViewById(R.id.tv_balance);
            container = itemView;
        }

        void bind(Instrument instrument) {
            tvAccountName.setText(instrument.getNickname());
            String bankInfo = instrument.getBankName();
            if (instrument.getInstrumentIdMasked() != null && !instrument.getInstrumentIdMasked().equalsIgnoreCase("UNKNOWN")) {
                bankInfo += " (" + instrument.getInstrumentIdMasked() + ")";
            }
            tvBankName.setText(bankInfo);

            double net = netByInstrumentId.getOrDefault(instrument.getId(), 0.0);
            tvBalance.setText(String.format(Locale.getDefault(), "₹%,.2f", net));

            container.setOnClickListener(v -> {
                Intent intent = new Intent(context, AccountDetailActivity.class);
                intent.putExtra("accountId", instrument.getId());
                context.startActivity(intent);
            });

            container.setOnLongClickListener(v -> {
                Intent intent = new Intent(context, EditInstrumentActivity.class);
                intent.putExtra("instrument_id", instrument.getId());
                context.startActivity(intent);
                return true;
            });
        }
    }
}
