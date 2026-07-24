package com.tutorapp.model;

public class CardPayment extends Payment {
    private String cardNumber;
    private String holderName;
    private String expiryDate;

    public CardPayment(double amount, String cardNumber, String holderName, String expiryDate) {
        super(amount);
        this.cardNumber = cardNumber;
        this.holderName = holderName;
        this.expiryDate = expiryDate;
    }

    private CardPayment(int paymentId, double amount, PaymentStatus status,
                         String cardNumber, String holderName, String expiryDate) {
        super(paymentId, amount, status);
        this.cardNumber = cardNumber;
        this.holderName = holderName;
        this.expiryDate = expiryDate;
    }

    // Rebuilds a CardPayment that already exists in the database, preserving its original id and status. 
    public static CardPayment restore(int paymentId, double amount, PaymentStatus status,
                                       String maskedCardNumber, String holderName, String expiryDate) {
        return new CardPayment(paymentId, amount, status, maskedCardNumber, holderName, expiryDate);
    }

    //Simplified for this project: we only ask for the card credentials above,then immediately mark the payment as successful (no real card network call is made).
    
    @Override
    public boolean processPayment() {
        setStatus(PaymentStatus.SUCCESS);
        return true;
    }

    @Override
    public String getMethodName() { return "Card Payment"; }

    public String getCardNumber() { return cardNumber; }
    public String getHolderName() { return holderName; }
    public String getExpiryDate() { return expiryDate; }

    // Masked card number for safe display in the UI. 
    public String getMaskedCardNumber() {
        if (cardNumber == null || cardNumber.length() < 4) return "****";
        return "**** **** **** " + cardNumber.substring(cardNumber.length() - 4);
    }
}
