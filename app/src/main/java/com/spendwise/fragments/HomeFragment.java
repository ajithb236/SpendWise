package com.spendwise.fragments;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.spendwise.MainActivity;
import com.spendwise.R;
import com.spendwise.SpendWiseApp;
import com.spendwise.adapters.TransactionAdapter;
import com.spendwise.data.Transaction;
import com.spendwise.db.AppDatabase;
import com.spendwise.dialogs.TransactionDetailActivity;
import com.spendwise.utils.BudgetPrefs;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HomeFragment extends Fragment {

    private AppDatabase db;
    private TransactionAdapter adapter;
    private int chartMonths = 6;
    private List<Transaction> allTransactions = new ArrayList<>();
    private BarChart barChart;
    private TextView tvStreakChip;
    private final SimpleDateFormat[] supportedDateFormats = new SimpleDateFormat[] {
            new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()),
            new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()),
            new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()),
            new SimpleDateFormat("dd-MM-yy", Locale.getDefault()),
            new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()),
            new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()),
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()),
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()),
            new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.ENGLISH)
    };

        private static final Pattern YMD_PATTERN = Pattern.compile("(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})");
        private static final Pattern DMY_PATTERN = Pattern.compile("(\\d{1,2})[-/](\\d{1,2})[-/](\\d{2,4})");

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        db = SpendWiseApp.getDatabase();

        TextView tvBalance = view.findViewById(R.id.tv_total_balance);
        TextView tvIncome = view.findViewById(R.id.tv_monthly_income);
        TextView tvExpenses = view.findViewById(R.id.tv_monthly_expenses);
        RecyclerView rvRecent = view.findViewById(R.id.rv_recent_transactions);
        barChart = view.findViewById(R.id.bar_chart);
        tvStreakChip = view.findViewById(R.id.tv_streak_chip);
        TextView btnSeeAll = view.findViewById(R.id.btn_see_all);
        MaterialButtonToggleGroup togglePeriod = view.findViewById(R.id.toggle_period);

        rvRecent.setLayoutManager(new LinearLayoutManager(getContext()));
        rvRecent.setNestedScrollingEnabled(false);
        adapter = new TransactionAdapter(new ArrayList<>(), (tx) ->
            startActivity(new Intent(getContext(), TransactionDetailActivity.class)
                .putExtra("txId", tx.getId())));
        rvRecent.setAdapter(adapter);

        db.transactionDao().getAll().observe(getViewLifecycleOwner(), transactions -> {
            this.allTransactions = transactions != null ? transactions : new ArrayList<>();
            updateDashboard(tvBalance, tvIncome, tvExpenses);
            updateStreakChip();
            updateRecentTransactions();
            setupBarChart(barChart, this.allTransactions);
        });

        togglePeriod.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btn_1m) chartMonths = 1;
                else if (checkedId == R.id.btn_3m) chartMonths = 3;
                else if (checkedId == R.id.btn_6m) chartMonths = 6;
                setupBarChart(barChart, allTransactions);
            }
        });

        btnSeeAll.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).navigateTo(R.id.nav_transactions);
            }
        });

        tvStreakChip.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).navigateToSettingsBudget();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        updateStreakChip();
    }

    private void updateStreakChip() {
        if (tvStreakChip == null || getContext() == null) {
            return;
        }

        double budget = BudgetPrefs.getDailyBudget(requireContext());
        if (budget <= 0) {
            tvStreakChip.setText("🔥 Set budget");
            return;
        }

        int streak = calculateStreak(allTransactions, budget);
        tvStreakChip.setText("🔥 " + streak);
    }

    private int calculateStreak(List<Transaction> transactions, double dailyBudget) {
        if (transactions == null || transactions.isEmpty()) {
            return 0;
        }

        Map<Long, Double> dailyNetExpense = new HashMap<>();
        long earliestDay = Long.MAX_VALUE;

        for (Transaction tx : transactions) {
            long day = resolveDayMillis(tx.getDate());
            if (day <= 0) {
                continue;
            }

            earliestDay = Math.min(earliestDay, day);
            boolean isIncome = Transaction.TYPE_INCOME.equalsIgnoreCase(tx.getType());
            double signed = isIncome ? -tx.getAmount() : tx.getAmount();
            dailyNetExpense.put(day, dailyNetExpense.getOrDefault(day, 0.0) + signed);
        }

        if (earliestDay == Long.MAX_VALUE) {
            return 0;
        }

        Calendar cursor = Calendar.getInstance();
        zeroTime(cursor);
        Calendar stop = Calendar.getInstance();
        stop.setTimeInMillis(earliestDay);
        zeroTime(stop);

        int streak = 0;
        while (!cursor.before(stop)) {
            long day = cursor.getTimeInMillis();
            double netExpense = dailyNetExpense.getOrDefault(day, 0.0);
            if (netExpense > dailyBudget) {
                break;
            }
            streak++;
            cursor.add(Calendar.DAY_OF_YEAR, -1);
        }

        return streak;
    }

    private long resolveDayMillis(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return -1L;
        }

        String trimmed = dateStr.trim();

        if (trimmed.matches("\\d{10,13}")) {
            try {
                long epoch = Long.parseLong(trimmed);
                if (trimmed.length() == 10) {
                    epoch *= 1000L;
                }
                Calendar c = Calendar.getInstance();
                c.setTimeInMillis(epoch);
                zeroTime(c);
                return c.getTimeInMillis();
            } catch (NumberFormatException ignored) {
            }
        }

        Matcher ymdMatcher = YMD_PATTERN.matcher(trimmed);
        if (ymdMatcher.find()) {
            try {
                int year = Integer.parseInt(ymdMatcher.group(1));
                int month = Integer.parseInt(ymdMatcher.group(2)) - 1;
                int day = Integer.parseInt(ymdMatcher.group(3));
                Calendar c = Calendar.getInstance();
                c.setLenient(false);
                c.set(year, month, day, 0, 0, 0);
                c.set(Calendar.MILLISECOND, 0);
                return c.getTimeInMillis();
            } catch (Exception ignored) {
            }
        }

        Matcher dmyMatcher = DMY_PATTERN.matcher(trimmed);
        if (dmyMatcher.find()) {
            try {
                int day = Integer.parseInt(dmyMatcher.group(1));
                int month = Integer.parseInt(dmyMatcher.group(2)) - 1;
                int year = Integer.parseInt(dmyMatcher.group(3));
                if (year < 100) {
                    year += 2000;
                }
                Calendar c = Calendar.getInstance();
                c.setLenient(false);
                c.set(year, month, day, 0, 0, 0);
                c.set(Calendar.MILLISECOND, 0);
                return c.getTimeInMillis();
            } catch (Exception ignored) {
            }
        }

        for (SimpleDateFormat format : supportedDateFormats) {
            try {
                Date date = format.parse(trimmed);
                if (date != null) {
                    Calendar c = Calendar.getInstance();
                    c.setTime(date);
                    zeroTime(c);
                    return c.getTimeInMillis();
                }
            } catch (ParseException ignored) {
            }
        }

        return -1L;
    }

    private void zeroTime(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
    }

    private int[] resolveYearMonth(String dateStr) {
        Calendar now = Calendar.getInstance();
        int fallbackYear = now.get(Calendar.YEAR);
        int fallbackMonth = now.get(Calendar.MONTH);

        if (dateStr == null || dateStr.trim().isEmpty()) {
            return new int[] { fallbackYear, fallbackMonth };
        }

        String trimmed = dateStr.trim();

        if (trimmed.matches("\\d{10,13}")) {
            try {
                long epoch = Long.parseLong(trimmed);
                if (trimmed.length() == 10) {
                    epoch *= 1000L;
                }
                Calendar c = Calendar.getInstance();
                c.setTimeInMillis(epoch);
                return new int[] { c.get(Calendar.YEAR), c.get(Calendar.MONTH) };
            } catch (NumberFormatException ignored) {
            }
        }

        Matcher ymdMatcher = YMD_PATTERN.matcher(trimmed);
        if (ymdMatcher.find()) {
            try {
                int year = Integer.parseInt(ymdMatcher.group(1));
                int month = Integer.parseInt(ymdMatcher.group(2)) - 1;
                if (month >= 0 && month <= 11) {
                    return new int[] { year, month };
                }
            } catch (NumberFormatException ignored) {
            }
        }

        Matcher dmyMatcher = DMY_PATTERN.matcher(trimmed);
        if (dmyMatcher.find()) {
            try {
                int month = Integer.parseInt(dmyMatcher.group(2)) - 1;
                int year = Integer.parseInt(dmyMatcher.group(3));
                if (year < 100) {
                    year += 2000;
                }
                if (month >= 0 && month <= 11) {
                    return new int[] { year, month };
                }
            } catch (NumberFormatException ignored) {
            }
        }

        for (SimpleDateFormat format : supportedDateFormats) {
            try {
                Date date = format.parse(trimmed);
                if (date != null) {
                    Calendar c = Calendar.getInstance();
                    c.setTime(date);
                    return new int[] { c.get(Calendar.YEAR), c.get(Calendar.MONTH) };
                }
            } catch (ParseException ignored) {
            }
        }

        return new int[] { fallbackYear, fallbackMonth };
    }

    private void updateDashboard(TextView tvBalance, TextView tvIncome, TextView tvExpenses) {
        double balance = 0;
        double totalIn = 0;
        double totalOut = 0;

        for (Transaction tx : allTransactions) {
            boolean isIncome = Transaction.TYPE_INCOME.equalsIgnoreCase(tx.getType());
            
            if (isIncome) {
                balance += tx.getAmount();
                totalIn += tx.getAmount();
            } else {
                balance -= tx.getAmount();
                totalOut += tx.getAmount();
            }
        }

        tvBalance.setText(formatCurrency(balance));
        tvIncome.setText("↙ " + formatCurrencyShort(totalIn));
        tvExpenses.setText("↗ " + formatCurrencyShort(totalOut));
    }

    private void updateRecentTransactions() {
        if (allTransactions == null) return;
        List<Transaction> recent = allTransactions.size() > 5
            ? allTransactions.subList(0, 5)
            : allTransactions;
        adapter.updateTransactions(recent);
    }

    private void setupBarChart(BarChart chart, List<Transaction> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            chart.clear();
            chart.setNoDataText("No transaction data yet");
            chart.setNoDataTextColor(Color.parseColor("#8B9BBF"));
            chart.invalidate();
            return;
        }

        int[] anchor = getChartAnchorYearMonth(transactions);
        List<BarEntry> stackedEntries = new ArrayList<>();
        String[] labels = new String[chartMonths];
        double maxMonthTotal = 0;

        for (int i = 0; i < chartMonths; i++) {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.YEAR, anchor[0]);
            cal.set(Calendar.MONTH, anchor[1]);
            cal.set(Calendar.DAY_OF_MONTH, 1);
            cal.add(Calendar.MONTH, -(chartMonths - 1 - i));
            int year = cal.get(Calendar.YEAR);
            int month = cal.get(Calendar.MONTH);
            labels[i] = new SimpleDateFormat("MMM", Locale.getDefault()).format(cal.getTime());

            double inc = 0, exp = 0;
            for (Transaction tx : transactions) {
                int[] ym = resolveYearMonth(tx.getDate());
                if (ym[0] == year && ym[1] == month) {
                    if (Transaction.TYPE_INCOME.equalsIgnoreCase(tx.getType())) {
                        inc += tx.getAmount();
                    } else {
                        exp += tx.getAmount();
                    }
                }
            }
            stackedEntries.add(new BarEntry(i, new float[] { (float) inc, (float) exp }));
            maxMonthTotal = Math.max(maxMonthTotal, inc + exp);
        }

        BarDataSet stackSet = new BarDataSet(stackedEntries, "Income / Expenses");
        stackSet.setColors(
                Color.parseColor("#00C896"),
                Color.parseColor("#FF5A5A")
        );
        stackSet.setStackLabels(new String[] { "Income", "Expenses" });
        stackSet.setDrawValues(false);

        BarData data = new BarData(stackSet);
        data.setBarWidth(0.55f);
        chart.setData(data);
        
        chart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        chart.getXAxis().setGranularity(1f);
        chart.getXAxis().setAxisMinimum(0f);
        chart.getXAxis().setAxisMaximum(chartMonths);
        chart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        chart.getXAxis().setTextColor(Color.parseColor("#8B9BBF"));
        chart.getXAxis().setDrawGridLines(false);

        chart.getAxisLeft().setTextColor(Color.parseColor("#8B9BBF"));
        chart.getAxisLeft().setGridColor(Color.parseColor("#243050"));
        chart.getAxisLeft().setAxisMinimum(0f);
        if (maxMonthTotal <= 0) {
            chart.getAxisLeft().setAxisMaximum(1f);
        } else {
            chart.getAxisLeft().resetAxisMaximum();
        }
        chart.getAxisRight().setEnabled(false);
        chart.getDescription().setEnabled(false);
        chart.getLegend().setTextColor(Color.parseColor("#8B9BBF"));
        chart.notifyDataSetChanged();
        
        chart.animateY(1000);
        chart.invalidate();
    }

    private int[] getLatestYearMonth(List<Transaction> transactions) {
        Calendar now = Calendar.getInstance();
        int bestYear = now.get(Calendar.YEAR);
        int bestMonth = now.get(Calendar.MONTH);

        for (Transaction tx : transactions) {
            int[] ym = resolveYearMonth(tx.getDate());
            if (isAfterCurrentMonth(ym[0], ym[1])) {
                continue;
            }
            if (ym[0] > bestYear || (ym[0] == bestYear && ym[1] > bestMonth)) {
                bestYear = ym[0];
                bestMonth = ym[1];
            }
        }

        return new int[] { bestYear, bestMonth };
    }

    private int[] getChartAnchorYearMonth(List<Transaction> transactions) {
        if (chartMonths == 1) {
            Calendar now = Calendar.getInstance();
            return new int[] { now.get(Calendar.YEAR), now.get(Calendar.MONTH) };
        }
        return getLatestYearMonth(transactions);
    }

    private boolean isAfterCurrentMonth(int year, int month) {
        Calendar now = Calendar.getInstance();
        int currentYear = now.get(Calendar.YEAR);
        int currentMonth = now.get(Calendar.MONTH);
        return year > currentYear || (year == currentYear && month > currentMonth);
    }

    private String formatCurrency(double v) { return String.format(Locale.getDefault(), "₹%,.2f", v); }
    private String formatCurrencyShort(double v) {
        if (v >= 1000) return String.format(Locale.getDefault(), "₹%.1fK", v / 1000);
        return String.format(Locale.getDefault(), "₹%.0f", v);
    }
}
