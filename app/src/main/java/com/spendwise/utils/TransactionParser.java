package com.spendwise.utils;

import android.content.Context;
import com.spendwise.R;
import com.spendwise.data.Alias;
import com.spendwise.data.Transaction;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TransactionParser {

    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
        "(?i)(?:rs\\.?|inr|₹)?\\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)"
    );
    private static final Pattern AMOUNT_CONTEXT_PATTERN = Pattern.compile(
        "(?i)(?:rs\\.?|inr|₹)?\\s*([0-9,]+\\.?[0-9]{0,2})(?=\\s*(?:debited|credited|spent|paid|txn|withdrawn|sent|received))"
    );
    private static final Pattern DATE_PATTERN = Pattern.compile(
        "\\b(\\d{2}[-/]\\d{2}(?:[-/]\\d{2,4})?)\\b"
    );
    private static final Pattern INSTRUMENT_ID_PATTERN = Pattern.compile(
        "(?i)(?:card|a/c|account|ac|card ending)\\s*(?:xx|\\*|x)?\\s*(\\d{3,6})"
    );
    private static final Pattern INSTRUMENT_ID_BACKUP_PATTERN = Pattern.compile(
        "(?i)[xX]{0,2}(\\d{4})"
    );
    private static final Pattern VPA_PATTERN = Pattern.compile(
        "([a-zA-Z0-9\\.\\-]+@[a-zA-Z]+)"
    );
    private static final Pattern MERCHANT_PATTERN = Pattern.compile(
        "(?i)(?:to|from|at)\\s+([A-Z0-9\\.\\s]{3,30})"
    );

    private static final String[] TXN_ACTION_KEYWORDS = {
            "debited", "credited", "received", "sent", "spent", "txn", "transaction",
            "purchase", "paid", "withdrawn", "deposit", "transferred"
    };

    public static Transaction parseSms(Context context, String messageBody, String sender, List<Alias> aliases) {
        return parseSms(context, messageBody, sender, aliases, System.currentTimeMillis());
    }

    public static Transaction parseSms(Context context, String messageBody, String sender, List<Alias> aliases, long parsedAtMillis) {
        String normalizedMessage = messageBody == null ? "" : messageBody.trim();
        if (normalizedMessage.isEmpty()) {
            return null;
        }

        if (!isTransactionSms(normalizedMessage)) {
            return null;
        }

        double amount = extractAmount(normalizedMessage);
        if (amount <= 0) return null;

        String type = determineType(normalizedMessage);
        if ("UNKNOWN".equals(type)) {
            return null;
        }

        String merchant = extractMerchant(context, normalizedMessage, aliases);
        String paymentMethod = determinePaymentMethod(normalizedMessage);
        String instrumentType = determineInstrumentType(normalizedMessage);
        String instrumentId = extractInstrumentId(normalizedMessage);
        String date = extractTransactionDate(normalizedMessage, parsedAtMillis);
        float confidenceScore = calculateConfidenceScore(normalizedMessage, sender, amount, merchant, paymentMethod, instrumentType, instrumentId, type);

        String id = UUID.randomUUID().toString();

        return new Transaction(
                id,
                merchant,
                amount,
                date,
                paymentMethod,
                confidenceScore,
                "General",
                type,
                messageBody,
                instrumentType,
                instrumentId,
                "UNKNOWN"
        );
    }

    private static boolean isTransactionSms(String message) {
        String lower = message.toLowerCase(Locale.getDefault());

        if (lower.contains("otp")) {
            return false;
        }
        if (lower.contains("statement")) {
            return false;
        }
        if (lower.contains("alert") && !lower.contains("credited") && !lower.contains("debited")) {
            return false;
        }

        boolean hasTxnKeyword = containsAny(lower, TXN_ACTION_KEYWORDS);
        if (!hasTxnKeyword) {
            return false;
        }

        boolean hasCurrency = lower.contains("rs") || lower.contains("inr") || message.contains("₹");
        if (hasCurrency) {
            return true;
        }

        return AMOUNT_PATTERN.matcher(message).find();
    }

    private static double extractAmount(String message) {
        Matcher contextMatcher = AMOUNT_CONTEXT_PATTERN.matcher(message);
        if (contextMatcher.find()) {
            try {
                return Double.parseDouble(contextMatcher.group(1).replace(",", ""));
            } catch (NumberFormatException e) {
            }
        }

        Matcher matcher = AMOUNT_PATTERN.matcher(message);
        while (matcher.find()) {
            try {
                double value = Double.parseDouble(matcher.group(1).replace(",", ""));
                if (value >= 0) {
                    return value;
                }
            } catch (NumberFormatException e) {
            }
        }
        return 0;
    }

    private static String extractMerchant(Context context, String message, List<Alias> aliases) {
        String aliasFromMessage = AliasResolver.resolveAliasForText(message, aliases);
        if (aliasFromMessage != null) {
            return aliasFromMessage;
        }

        Matcher vpaMatcher = VPA_PATTERN.matcher(message);
        if (vpaMatcher.find()) {
            String vpa = vpaMatcher.group(1).trim();
            String aliasFromVpa = AliasResolver.resolveAliasForText(vpa, aliases);
            if (aliasFromVpa != null) {
                return aliasFromVpa;
            }
            return vpa;
        }

        Matcher partyMatcher = MERCHANT_PATTERN.matcher(message.toUpperCase(Locale.getDefault()));
        while (partyMatcher.find()) {
            String potential = cleanPartyToken(partyMatcher.group(1));
            if (!potential.isEmpty()) {
                return potential;
            }
        }

        return context.getString(R.string.unknown_merchant);
    }

    private static String cleanPartyToken(String token) {
        if (token == null) {
            return "";
        }

        String cleaned = token.trim();
        cleaned = cleaned.replaceAll("(?i)(ON|BY|UPI|REF|TXN).*", "").trim();
        cleaned = cleaned.replaceAll("\\s{2,}", " ").trim();
        if (cleaned.length() < 3) {
            return "";
        }
        return cleaned;
    }

    private static String determineType(String message) {
        String msgLower = message.toLowerCase(Locale.getDefault());
        if (msgLower.contains("credited") || msgLower.contains("received") || msgLower.contains("deposit")) {
            return Transaction.TYPE_INCOME;
        }
        if (msgLower.contains("debited") || msgLower.contains("spent") || msgLower.contains("paid")
                || msgLower.contains("sent") || msgLower.contains("withdrawn")
                || msgLower.contains("purchase") || msgLower.contains("transaction") || msgLower.contains("txn")) {
            return Transaction.TYPE_EXPENSE;
        }
        return "UNKNOWN";
    }

    private static String determinePaymentMethod(String message) {
        String msgLower = message.toLowerCase(Locale.getDefault());
        if (msgLower.contains("upi") || msgLower.contains("vpa")) {
            return "UPI";
        }
        if (msgLower.contains("imps")) {
            return "IMPS";
        }
        if (msgLower.contains("neft")) {
            return "NEFT";
        }
        if (msgLower.contains("rtgs")) {
            return "RTGS";
        }
        if (msgLower.contains("atm")) {
            return "ATM";
        }
        if (msgLower.contains("card")) {
            return "CARD";
        }
        return "UNKNOWN";
    }

    private static String determineInstrumentType(String message) {
        String msgLower = message.toLowerCase(Locale.getDefault());
        if (msgLower.contains("credit card")) {
            return "CREDIT_CARD";
        }
        if (msgLower.contains("debit card")) {
            return "DEBIT_CARD";
        }
        if (msgLower.contains("card")) {
            return "CARD";
        }
        if (msgLower.contains("a/c") || msgLower.contains("account")) {
            return "BANK_ACCOUNT";
        }
        return "UNKNOWN";
    }

    private static String extractInstrumentId(String message) {
        Matcher matcher = INSTRUMENT_ID_PATTERN.matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        }

        Matcher backupMatcher = INSTRUMENT_ID_BACKUP_PATTERN.matcher(message);
        if (backupMatcher.find()) {
            return backupMatcher.group(1);
        }

        return "UNKNOWN";
    }

    private static String extractTransactionDate(String message, long fallbackMillis) {
        Matcher matcher = DATE_PATTERN.matcher(message);
        if (matcher.find()) {
            String rawDate = matcher.group(1);
            String normalized = rawDate.replace('/', '-');

            String[] parts = normalized.split("-");
            if (parts.length == 2) {
                Calendar calendar = Calendar.getInstance();
                calendar.setTimeInMillis(fallbackMillis);
                int year = calendar.get(Calendar.YEAR);
                return String.format(Locale.getDefault(), "%04d-%s-%s", year, parts[1], parts[0]);
            } else if (parts.length == 3) {
                String day = parts[0];
                String month = parts[1];
                String yearStr = parts[2];
                if (yearStr.length() == 2) {
                    yearStr = "20" + yearStr;
                }
                return String.format(Locale.getDefault(), "%s-%s-%s", yearStr, month, day);
            }
        }

        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(fallbackMillis));
    }

    private static float calculateConfidenceScore(
            String message,
            String sender,
            double amount,
            String merchant,
            String paymentMethod,
            String instrumentType,
            String instrumentId,
            String type
    ) {
        float score = 0f;
        String lower = message.toLowerCase(Locale.getDefault());

        if (amount > 0) {
            score += 0.35f;
        }

        if (containsAny(lower, TXN_ACTION_KEYWORDS)) {
            score += 0.20f;
        }

        if (!"Unknown".equalsIgnoreCase(paymentMethod)) {
            score += 0.15f;
        }

        if (!"Unknown".equalsIgnoreCase(instrumentType)) {
            score += 0.05f;
        }

        if (!"UNKNOWN".equalsIgnoreCase(instrumentId)) {
            score += 0.05f;
        }

        if (merchant != null && !merchant.equalsIgnoreCase("Unknown Merchant")) {
            score += 0.15f;
        }

        if ((merchant == null || merchant.equalsIgnoreCase("Unknown Merchant"))
                && "Unknown".equalsIgnoreCase(paymentMethod)
                && "Unknown".equalsIgnoreCase(instrumentType)
                && "UNKNOWN".equalsIgnoreCase(instrumentId)) {
            score -= 0.20f;
        }

        if (DATE_PATTERN.matcher(message).find() || lower.contains("ref") || lower.contains("upi")) {
            score += 0.10f;
        }

        if (sender != null) {
            String senderLower = sender.toLowerCase(Locale.getDefault());
            if (senderLower.contains("bank") || senderLower.contains("hdfc") || senderLower.contains("icici") || senderLower.contains("sbi")) {
                score += 0.05f;
            }
        }

        if (!isTransactionSms(message)) {
            score -= 0.25f;
        }

        if (score < 0f) {
            score = 0f;
        }
        if (score > 1f) {
            score = 1f;
        }
        return score;
    }

    private static boolean containsAny(String text, String[] keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
