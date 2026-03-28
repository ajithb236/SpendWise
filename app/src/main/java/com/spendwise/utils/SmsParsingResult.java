package com.spendwise.utils;

public class SmsParsingResult {
    public double amount;
    public String merchant;
    public String type;
    public String category;
    public String description;
    public boolean isValid;
    public float confidence;

    public SmsParsingResult() {
        this.isValid = false;
        this.confidence = 0f;
        this.type = "expense";
    }

    public SmsParsingResult(double amount, String merchant, String type, 
                           String category, String description, float confidence) {
        this.amount = amount;
        this.merchant = merchant;
        this.type = type;
        this.category = category;
        this.description = description;
        this.confidence = confidence;
        this.isValid = amount > 0 && merchant != null && !merchant.isEmpty();
    }
}
