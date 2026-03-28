package com.spendwise.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.spendwise.R;
import com.spendwise.SpendWiseApp;
import com.spendwise.data.Transaction;
import com.spendwise.db.AppDatabase;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AnalyticsFragment extends Fragment {

    private static final int[] CAT_COLORS = {
        0xFF00C896, 0xFF5B8FF9, 0xFFFF5A5A, 0xFFF7B731,
        0xFFA55EEA, 0xFFFF8C00, 0xFF20C997, 0xFF868E96
    };

    private AppDatabase db;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_analytics, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        db = SpendWiseApp.getDatabase();

        TextView tvMonth = view.findViewById(R.id.tv_month_label);
        TextView tvIncome = view.findViewById(R.id.tv_analytics_income);
        TextView tvExpense = view.findViewById(R.id.tv_analytics_expense);
        TextView tvSaved = view.findViewById(R.id.tv_analytics_saved);
        TextView tvSavingsRate = view.findViewById(R.id.tv_savings_rate);
        TextView tvSavingsHint = view.findViewById(R.id.tv_savings_hint);
        ProgressBar progressSavings = view.findViewById(R.id.progress_savings);
        PieChart pieChart = view.findViewById(R.id.pie_chart);
        BarChart barChart = view.findViewById(R.id.analytics_bar_chart);

        tvMonth.setText(new SimpleDateFormat("MMMM yyyy", Locale.US).format(Calendar.getInstance().getTime()));

        db.transactionDao().getAll().observe(getViewLifecycleOwner(), transactions -> {
            List<Transaction> safeTransactions = transactions != null ? transactions : Collections.emptyList();

            double income = 0;
            double expense = 0;
            Calendar currentMonth = Calendar.getInstance();
            currentMonth.set(Calendar.DAY_OF_MONTH, 1);
            currentMonth.set(Calendar.HOUR_OF_DAY, 0);
            currentMonth.set(Calendar.MINUTE, 0);
            currentMonth.set(Calendar.SECOND, 0);
            currentMonth.set(Calendar.MILLISECOND, 0);
            long monthStart = currentMonth.getTimeInMillis();

            for (Transaction tx : safeTransactions) {
                long txDate = parseDate(tx.getDate());
                if (txDate >= monthStart) {
                    if (Transaction.TYPE_INCOME.equalsIgnoreCase(tx.getType())) {
                        income += tx.getAmount();
                    } else {
                        expense += tx.getAmount();
                    }
                }
            }

            double saved = Math.max(0, income - expense);
            double savingsRate = income > 0 ? (saved / income) * 100 : 0;

            tvIncome.setText(fmt(income));
            tvExpense.setText(fmt(expense));
            tvSaved.setText(fmt(saved));
            tvSavingsRate.setText(String.format(Locale.US, "%.0f%%", savingsRate));
            tvSavingsRate.setTextColor(savingsRate >= 20 ? Color.parseColor("#00C896") : Color.parseColor("#FF5A5A"));
            progressSavings.setProgress((int) Math.min(savingsRate, 100));
            tvSavingsHint.setText(savingsRate >= 20 ? "Great job! You're on track." : "Aim for 20%+ savings rate");

            setupPieChart(pieChart, safeTransactions);
            setupBarChart(barChart, safeTransactions);
        });
    }

    private void setupPieChart(PieChart chart, List<Transaction> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            chart.clear();
            chart.setNoDataText("No expense data this month");
            chart.setNoDataTextColor(Color.parseColor("#8B9BBF"));
            chart.invalidate();
            return;
        }

        Calendar startOfMonth = Calendar.getInstance();
        startOfMonth.set(Calendar.DAY_OF_MONTH, 1);
        startOfMonth.set(Calendar.HOUR_OF_DAY, 0);
        startOfMonth.set(Calendar.MINUTE, 0);
        startOfMonth.set(Calendar.SECOND, 0);
        startOfMonth.set(Calendar.MILLISECOND, 0);
        long startMs = startOfMonth.getTimeInMillis();

        Map<String, Double> byCategory = new HashMap<>();
        for (Transaction tx : transactions) {
            long txDate = parseDate(tx.getDate());
            if (Transaction.TYPE_EXPENSE.equalsIgnoreCase(tx.getType()) && txDate >= startMs) {
                String category = tx.getCategory() != null && !tx.getCategory().trim().isEmpty()
                        ? tx.getCategory().trim()
                        : "Uncategorized";
                byCategory.put(category, byCategory.getOrDefault(category, 0.0) + tx.getAmount());
            }
        }

        List<PieEntry> entries = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        int colorIdx = 0;
        for (Map.Entry<String, Double> e : byCategory.entrySet()) {
            entries.add(new PieEntry(e.getValue().floatValue(), e.getKey()));
            colors.add(CAT_COLORS[colorIdx++ % CAT_COLORS.length]);
        }

        if (entries.isEmpty()) {
            chart.setNoDataText("No expense data this month");
            chart.setNoDataTextColor(Color.parseColor("#8B9BBF"));
        } else {
            PieDataSet ds = new PieDataSet(entries, "");
            ds.setColors(colors);
            ds.setSliceSpace(2f);
            ds.setValueTextColor(Color.WHITE);
            ds.setValueTextSize(11f);
            PieData data = new PieData(ds);
            chart.setData(data);
        }

        chart.setUsePercentValues(true);
        chart.getDescription().setEnabled(false);
        chart.setDrawHoleEnabled(true);
        chart.setHoleColor(Color.parseColor("#1A2540"));
        chart.setHoleRadius(50f);
        chart.setTransparentCircleRadius(55f);
        chart.setTransparentCircleColor(Color.parseColor("#1A2540"));
        chart.setCenterText("Expenses");
        chart.setCenterTextColor(Color.parseColor("#8B9BBF"));
        chart.setCenterTextSize(12f);
        chart.setBackgroundColor(Color.TRANSPARENT);
        chart.getLegend().setTextColor(Color.parseColor("#8B9BBF"));
        chart.getLegend().setWordWrapEnabled(true);
        chart.animateY(800);
        chart.invalidate();
    }

    private void setupBarChart(BarChart chart, List<Transaction> transactions) {
        if (transactions == null) {
            transactions = Collections.emptyList();
        }

        String[] monthLabels = new String[6];
        List<BarEntry> incomeEntries = new ArrayList<>();
        List<BarEntry> expenseEntries = new ArrayList<>();

        for (int i = 0; i < 6; i++) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.MONTH, -(5 - i));
            int yr = cal.get(Calendar.YEAR);
            int mo = cal.get(Calendar.MONTH);
            monthLabels[i] = new SimpleDateFormat("MMM", Locale.US).format(cal.getTime());

            double income = 0, expense = 0;
            for (Transaction tx : transactions) {
                long txDate = parseDate(tx.getDate());
                if (txDate == 0) {
                    continue;
                }
                Calendar tc = Calendar.getInstance();
                tc.setTimeInMillis(txDate);
                if (tc.get(Calendar.YEAR) == yr && tc.get(Calendar.MONTH) == mo) {
                    if (Transaction.TYPE_INCOME.equalsIgnoreCase(tx.getType())) {
                        income += tx.getAmount();
                    } else {
                        expense += tx.getAmount();
                    }
                }
            }
            incomeEntries.add(new BarEntry(i, (float) income));
            expenseEntries.add(new BarEntry(i, (float) expense));
        }

        BarDataSet incomeSet = new BarDataSet(incomeEntries, "Income");
        incomeSet.setColor(Color.parseColor("#00C896"));
        incomeSet.setDrawValues(false);
        BarDataSet expenseSet = new BarDataSet(expenseEntries, "Expenses");
        expenseSet.setColor(Color.parseColor("#FF5A5A"));
        expenseSet.setDrawValues(false);

        BarData data = new BarData(incomeSet, expenseSet);
        float groupSpace = 0.06f;
        float barSpace = 0.02f;
        float barWidth = 0.45f;
        data.setBarWidth(barWidth);
        chart.setData(data);
        float groupWidth = data.getGroupWidth(groupSpace, barSpace);
        chart.getXAxis().setAxisMinimum(0f);
        chart.getXAxis().setAxisMaximum(0f + groupWidth * monthLabels.length);
        chart.groupBars(0f, groupSpace, barSpace);

        XAxis xAxis = chart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(monthLabels));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(Color.parseColor("#8B9BBF"));
        xAxis.setGridColor(Color.parseColor("#243050"));
        xAxis.setCenterAxisLabels(true);
        xAxis.setGranularity(1f);
        xAxis.setDrawGridLines(false);

        chart.getAxisLeft().setTextColor(Color.parseColor("#8B9BBF"));
        chart.getAxisLeft().setGridColor(Color.parseColor("#243050"));
        chart.getAxisRight().setEnabled(false);
        chart.getDescription().setEnabled(false);
        chart.getLegend().setTextColor(Color.parseColor("#8B9BBF"));
        chart.setBackgroundColor(Color.TRANSPARENT);
        chart.animateY(600);
        chart.invalidate();
    }

    private String fmt(double v) {
        return String.format(Locale.getDefault(), "₹%,.0f", v);
    }

    private long parseDate(String dateStr) {
        try {
            return dateFormat.parse(dateStr).getTime();
        } catch (ParseException | NullPointerException e) {
            return 0;
        }
    }
}
