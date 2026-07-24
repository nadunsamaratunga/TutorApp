package com.tutorapp.model;

public class BankPayment extends Payment {
    private String accountNumber;
    private String receiptImage;

    public BankPayment(double amount, String accountNumber, String receiptImage) {
        super(amount);
        this.accountNumber = accountNumber;
        this.receiptImage = receiptImage;
    }

    private BankPayment(int paymentId, double amount, PaymentStatus status,
                         String accountNumber, String receiptImage) {
        super(paymentId, amount, status);
        this.accountNumber = accountNumber;
        this.receiptImage = receiptImage;
    }

    //Rebuilds a BankPayment that already exists in the database, preserving its original id and status. 
    public static BankPayment restore(int paymentId, double amount, PaymentStatus status,
                                       String accountNumber, String receiptImage) {
        return new BankPayment(paymentId, amount, status, accountNumber, receiptImage);
    }

    // Simplified for this project: we only ask for the bank credentials above, then immediately mark the payment as successful.
     
    @Override
    public boolean processPayment() {
        setStatus(PaymentStatus.SUCCESS);
        return true;
    }

    @Override
    public String getMethodName() { return "Bank Transfer"; }

    public String getAccountNumber() { return accountNumber; }
    public String getReceiptImage() { return receiptImage; }
}
