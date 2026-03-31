package com.spendwise.utils;

import android.content.Context;
import android.util.Log;
import com.spendwise.R;
import com.spendwise.db.AppDatabase;
import com.spendwise.data.Alias;
import com.spendwise.data.Transaction;
import com.spendwise.ml.BertInferenceEngine;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HybridInferenceManager {

    private static final String TAG = "HybridInference";
    private final BertInferenceEngine bertEngine;
    private static final float CONFIDENCE_THRESHOLD = 0.95f;
    private static final int REQUIRED_EVIDENCE_SIGNALS = 3;
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("([0-9,]+(?:\\.[0-9]{1,2})?)");
    private static final Pattern DATE_PATTERN = Pattern.compile("\\b(\\d{2}[-/]\\d{2}(?:[-/]\\d{2,4})?)\\b");
    private static final Pattern INSTRUMENT_ID_PATTERN = Pattern.compile("(?i)(?:card|a/c|account|ac|ending)\\s*(?:xx|\\*|x)?\\s*(\\d{3,6})");

    public HybridInferenceManager(Context context) throws IOException {
        this.bertEngine = new BertInferenceEngine(context);
    }

    public Transaction parseSms(Context context, String rawSms, String sender, List<Alias> aliases) {
        return parseSms(context, rawSms, sender, aliases, System.currentTimeMillis());
    }

    public Transaction parseSms(Context context, String rawSms, String sender, List<Alias> aliases, long parsedAtMillis) {
        if (rawSms == null || rawSms.trim().isEmpty()) {
            Log.w(TAG, "Parser used: NONE (empty SMS body)");
            return null;
        }

        Transaction regexResult = TransactionParser.parseSms(context, rawSms, sender, aliases, parsedAtMillis);

        String regexDecision = null;
        if (regexResult != null && shouldAcceptRegexResult(context, rawSms, regexResult, aliases)) {
            Log.w(TAG, "Parser used: REGEX_PRIMARY, confidence=" + regexResult.getConfidenceScore());
            return regexResult;
        } else if (regexResult != null) {
            regexDecision = buildRegexRejectionReason(context, rawSms, regexResult, aliases);
        }

        if (regexResult != null) {
            Log.w(TAG, "Regex result rejected, trying BERT. " + regexDecision);
        }

        Log.w(TAG, "BERT attempt started");
        BertInferenceEngine.InferenceResult result = bertEngine.infer(rawSms);
        Log.w(TAG, "BERT output: meanConfidence=" + result.getMeanConfidence() + ", entities=" + result.getEntities());
        Transaction bertResult = mapBertToTransaction(context, rawSms, result, aliases, parsedAtMillis);
        if (bertResult != null) {
            Log.w(TAG, "Parser used: BERT_FALLBACK, confidence=" + bertResult.getConfidenceScore());
            return bertResult;
        }

        if (regexResult != null) {
            Log.w(TAG, "Parser used: REGEX_FALLBACK_AFTER_BERT_MISS, confidence=" + regexResult.getConfidenceScore());
        } else {
            Log.w(TAG, "Parser used: NONE (both REGEX and BERT returned null)");
        }

        return regexResult;
    }

    private boolean shouldAcceptRegexResult(Context context, String rawSms, Transaction regexResult, List<Alias> aliases) {
        if (regexResult.getConfidenceScore() < CONFIDENCE_THRESHOLD) {
            return false;
        }

        if (!isKnownMerchant(context, regexResult.getMerchantName(), aliases)) {
            return false;
        }

        int evidenceSignals = 0;

        String unknownMerchant = context.getString(R.string.unknown_merchant);
        String merchant = regexResult.getMerchantName();
        if (merchant != null && !merchant.trim().isEmpty() && !merchant.equalsIgnoreCase(unknownMerchant)) {
            evidenceSignals++;
        }

        String paymentMethod = regexResult.getPaymentMethod();
        if (paymentMethod != null && !"UNKNOWN".equalsIgnoreCase(paymentMethod)) {
            evidenceSignals++;
        }

        String instrumentType = regexResult.getInstrumentType();
        String instrumentId = regexResult.getInstrumentId();
        if ((instrumentType != null && !"UNKNOWN".equalsIgnoreCase(instrumentType))
                || (instrumentId != null && !"UNKNOWN".equalsIgnoreCase(instrumentId))) {
            evidenceSignals++;
        }

        String lower = rawSms == null ? "" : rawSms.toLowerCase(Locale.getDefault());
        if (lower.contains("upi") || lower.contains("imps") || lower.contains("neft")
                || lower.contains("rtgs") || lower.contains("card") || lower.contains("ref")) {
            evidenceSignals++;
        }

        return evidenceSignals >= REQUIRED_EVIDENCE_SIGNALS;
    }

    private String buildRegexRejectionReason(Context context, String rawSms, Transaction regexResult, List<Alias> aliases) {
        StringBuilder reason = new StringBuilder();

        if (regexResult.getConfidenceScore() < CONFIDENCE_THRESHOLD) {
            reason.append("confidence=")
                    .append(regexResult.getConfidenceScore())
                    .append(" < ")
                    .append(CONFIDENCE_THRESHOLD)
                    .append("; ");
        }

        if (!isKnownMerchant(context, regexResult.getMerchantName(), aliases)) {
            reason.append("merchant unseen in aliases/history; ");
        }

        int evidenceSignals = 0;

        String unknownMerchant = context.getString(R.string.unknown_merchant);
        String merchant = regexResult.getMerchantName();
        if (merchant != null && !merchant.trim().isEmpty() && !merchant.equalsIgnoreCase(unknownMerchant)) {
            evidenceSignals++;
        } else {
            reason.append("merchant weak; ");
        }

        String paymentMethod = regexResult.getPaymentMethod();
        if (paymentMethod != null && !"UNKNOWN".equalsIgnoreCase(paymentMethod)) {
            evidenceSignals++;
        } else {
            reason.append("payment method unknown; ");
        }

        String instrumentType = regexResult.getInstrumentType();
        String instrumentId = regexResult.getInstrumentId();
        if ((instrumentType != null && !"UNKNOWN".equalsIgnoreCase(instrumentType))
                || (instrumentId != null && !"UNKNOWN".equalsIgnoreCase(instrumentId))) {
            evidenceSignals++;
        } else {
            reason.append("instrument weak; ");
        }

        String lower = rawSms == null ? "" : rawSms.toLowerCase(Locale.getDefault());
        boolean hasTxnRail = lower.contains("upi") || lower.contains("imps") || lower.contains("neft")
                || lower.contains("rtgs") || lower.contains("card") || lower.contains("ref");
        if (hasTxnRail) {
            evidenceSignals++;
        } else {
            reason.append("txn rail hint missing; ");
        }

        reason.append("evidence=")
                .append(evidenceSignals)
                .append("/")
                .append(REQUIRED_EVIDENCE_SIGNALS);

        return reason.toString();
    }

    private boolean isKnownMerchant(Context context, String merchantName, List<Alias> aliases) {
        String normalizedMerchant = normalizeMerchant(merchantName);
        if (normalizedMerchant.isEmpty()) {
            return false;
        }

        String unknownMerchant = normalizeMerchant(context.getString(R.string.unknown_merchant));
        if (normalizedMerchant.equals(unknownMerchant)) {
            return false;
        }

        Set<String> knownMerchants = new HashSet<>();

        if (aliases != null) {
            for (Alias alias : aliases) {
                if (alias == null) {
                    continue;
                }
                knownMerchants.add(normalizeMerchant(alias.getOriginalName()));
                knownMerchants.add(normalizeMerchant(alias.getAliasName()));
            }
        }

        List<Transaction> existingTransactions = AppDatabase.getInstance(context).transactionDao().getAllForLookup();
        for (Transaction txn : existingTransactions) {
            if (txn == null) {
                continue;
            }
            knownMerchants.add(normalizeMerchant(txn.getMerchantName()));
        }

        knownMerchants.remove("");
        return knownMerchants.contains(normalizedMerchant);
    }

    private String normalizeMerchant(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.getDefault()).replaceAll("\\s+", " ");
    }

    private Transaction mapBertToTransaction(Context context, String rawSms, BertInferenceEngine.InferenceResult result, List<Alias> aliases, long parsedAtMillis) {
        Map<String, String> entities = result.getEntities();

        double amount = parseAmount(entities.get("AMOUNT"));
        if (amount <= 0) {
            amount = parseAmount(rawSms);
        }
        if (amount <= 0) {
            Log.w(TAG, "BERT mapping failed: amount not found or invalid");
            return null;
        }

        String type = inferTransactionType(rawSms);
        if ("UNKNOWN".equals(type)) {
            Log.w(TAG, "BERT mapping failed: transaction type unknown");
            return null;
        }

        String merchant = resolveMerchant(context, entities.get("MERCHANT"), rawSms, aliases);
        String date = normalizeDate(entities.get("DATE"), parsedAtMillis);
        String paymentMethod = inferPaymentMethod(rawSms, entities.get("INSTRUMENT_TYPE"));
        String instrumentType = normalizeInstrumentType(entities.get("INSTRUMENT_TYPE"), rawSms);
        String instrumentId = resolveInstrumentId(entities.get("ACCOUNT"), rawSms);

        float confidence = Math.max(0.55f, Math.min(0.95f, result.getMeanConfidence()));

        Log.w(TAG, "BERT mapping succeeded: amount=" + amount + ", type=" + type + ", merchant=" + merchant);

            return new Transaction(
                    UUID.randomUUID().toString(),
                    merchant,
                    amount,
                    date,
                    paymentMethod,
                    confidence,
                    "Uncategorized",
                    type,
                    rawSms,
                    instrumentType,
                    instrumentId,
                    "UNKNOWN"
            );
    }

    private double parseAmount(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0.0;
        }
        Matcher matcher = NUMERIC_PATTERN.matcher(value.replace("₹", "").replace("INR", "").replace("Rs", ""));
        if (matcher.find()) {
            try {
                return Double.parseDouble(matcher.group(1).replace(",", ""));
            } catch (NumberFormatException ignored) {
                return 0.0;
            }
        }
        return 0.0;
    }

    private String inferTransactionType(String message) {
        String lower = message.toLowerCase(Locale.getDefault());
        if (lower.contains("credited") || lower.contains("received") || lower.contains("deposit")) {
            return Transaction.TYPE_INCOME;
        }
        if (lower.contains("debited") || lower.contains("spent") || lower.contains("paid")
                || lower.contains("sent") || lower.contains("withdrawn") || lower.contains("purchase")) {
            return Transaction.TYPE_EXPENSE;
        }
        return "UNKNOWN";
    }

    private String inferPaymentMethod(String message, String instrumentEntity) {
        String lower = message.toLowerCase(Locale.getDefault());
        String entity = instrumentEntity == null ? "" : instrumentEntity.toLowerCase(Locale.getDefault());

        if (lower.contains("upi") || lower.contains("vpa") || entity.contains("upi")) return "UPI";
        if (lower.contains("imps")) return "IMPS";
        if (lower.contains("neft")) return "NEFT";
        if (lower.contains("rtgs")) return "RTGS";
        if (lower.contains("atm")) return "ATM";
        if (lower.contains("card") || entity.contains("card")) return "CARD";
        return "UNKNOWN";
    }

    private String normalizeInstrumentType(String instrumentEntity, String message) {
        String lowerEntity = instrumentEntity == null ? "" : instrumentEntity.toLowerCase(Locale.getDefault());
        String lowerMessage = message.toLowerCase(Locale.getDefault());

        if (lowerEntity.contains("credit") || lowerMessage.contains("credit card")) return "CREDIT_CARD";
        if (lowerEntity.contains("debit") || lowerMessage.contains("debit card")) return "DEBIT_CARD";
        if (lowerEntity.contains("card") || lowerMessage.contains("card")) return "CARD";
        if (lowerEntity.contains("account") || lowerMessage.contains("account") || lowerMessage.contains("a/c")) return "BANK_ACCOUNT";
        return "UNKNOWN";
    }

    private String resolveInstrumentId(String instrumentHint, String message) {
        if (instrumentHint != null) {
            Matcher accountMatcher = NUMERIC_PATTERN.matcher(instrumentHint);
            if (accountMatcher.find()) {
                return accountMatcher.group(1).replace(",", "");
            }
        }

        Matcher matcher = INSTRUMENT_ID_PATTERN.matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "UNKNOWN";
    }

    private String normalizeDate(String dateEntity, long fallbackMillis) {
        if (dateEntity == null || dateEntity.trim().isEmpty()) {
            return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(fallbackMillis));
        }

        String normalized = dateEntity.trim().replace('/', '-');
        if (normalized.matches("\\d{4}-\\d{1,2}-\\d{1,2}")) {
            String[] ymd = normalized.split("-");
            return String.format(Locale.getDefault(), "%04d-%02d-%02d",
                    Integer.parseInt(ymd[0]), Integer.parseInt(ymd[1]), Integer.parseInt(ymd[2]));
        }

        String[] parts = normalized.split("-");
        if (parts.length == 2) {
            int year = Calendar.getInstance().get(Calendar.YEAR);
            return String.format(Locale.getDefault(), "%04d-%s-%s", year, parts[1], parts[0]);
        }
        if (parts.length == 3) {
            String day = parts[0];
            String month = parts[1];
            String year = parts[2].length() == 2 ? "20" + parts[2] : parts[2];
            return String.format(Locale.getDefault(), "%s-%s-%s", year, month, day);
        }
        if (DATE_PATTERN.matcher(normalized).find()) {
            return normalized;
        }
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(fallbackMillis));
    }

    private String resolveMerchant(Context context, String merchantEntity, String rawSms, List<Alias> aliases) {
        String aliasFromEntity = AliasResolver.resolveAliasForText(merchantEntity, aliases);
        if (aliasFromEntity != null) {
            return aliasFromEntity;
        }

        String aliasFromSms = AliasResolver.resolveAliasForText(rawSms, aliases);
        if (aliasFromSms != null) {
            return aliasFromSms;
        }

        if (merchantEntity != null) {
            String cleaned = merchantEntity.trim();
            if (!cleaned.isEmpty()) {
                return cleaned;
            }
        }
        return context.getString(R.string.unknown_merchant);
    }

    public void cleanup() {
        bertEngine.close();
    }
}
