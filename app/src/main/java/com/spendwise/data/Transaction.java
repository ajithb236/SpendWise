package com.spendwise.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.io.Serializable;

@Entity(tableName = "transactions")
public class Transaction implements Serializable {
    public static final String TYPE_EXPENSE = "Expense";
    public static final String TYPE_INCOME = "Income";

    @PrimaryKey
    @NonNull
    private String id;
    private String merchantName;
    private double amount;
    private String date;
    private String paymentMethod;
    private float confidenceScore;
    private String category;
    private String type;
    private String rawSms;
    private String instrumentType;
    private String instrumentId;
    private String instrumentRefId;

    public Transaction(
            @NonNull String id,
            String merchantName,
            double amount,
            String date,
            String paymentMethod,
            float confidenceScore,
            String category,
            String type,
            String rawSms,
            String instrumentType,
            String instrumentId,
            String instrumentRefId
    ) {
        this.id = id;
        this.merchantName = merchantName;
        this.amount = amount;
        this.date = date;
        this.paymentMethod = paymentMethod;
        this.confidenceScore = confidenceScore;
        this.category = category;
        this.type = type;
        this.rawSms = rawSms;
        this.instrumentType = instrumentType;
        this.instrumentId = instrumentId;
        this.instrumentRefId = instrumentRefId;
    }

    @Ignore
    public Transaction(
            @NonNull String id,
            String merchantName,
            double amount,
            String date,
            String paymentMethod,
            float confidenceScore,
            String category,
            String type,
            String rawSms
    ) {
        this(id, merchantName, amount, date, paymentMethod, confidenceScore, category, type, rawSms, "UNKNOWN", "UNKNOWN", "UNKNOWN");
    }

    @NonNull
    public String getId() { return id; }
    public String getMerchantName() { return merchantName; }
    public double getAmount() { return amount; }
    public String getDate() { return date; }
    public String getPaymentMethod() { return paymentMethod; }
    public float getConfidenceScore() { return confidenceScore; }
    public String getCategory() { return category; }
    public String getType() { return type; }
    public String getRawSms() { return rawSms; }
    public String getInstrumentType() { return instrumentType; }
    public String getInstrumentId() { return instrumentId; }
    public String getInstrumentRefId() { return instrumentRefId; }

    public void setDate(String date) { this.date = date; }
    public void setType(String type) { this.type = type; }
    public void setCategory(String category) { this.category = category; }
    public void setInstrumentRefId(String instrumentRefId) { this.instrumentRefId = instrumentRefId; }
}
