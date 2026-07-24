package com.tutorapp.model;

import java.util.concurrent.atomic.AtomicInteger;

// Abstract Payment. Per project requirements this is a simplified, classroom-style payment flow: subclasses only need to collect the relevant credentials, and processPayment() always finalizes the payment as SUCCESS (no real gateway integration).
 
public abstract class Payment {
    private static final AtomicInteger COUNTER = new AtomicInteger(1);

    private final int paymentId;
    private final double amount;
    private PaymentStatus status;

    protected Payment(double amount) {
        this.paymentId = COUNTER.getAndIncrement();
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
    }

    //Used when reconstructing a payment that already exists in the database. 
    protected Payment(int paymentId, double amount, PaymentStatus status) {
        this.paymentId = paymentId;
        this.amount = amount;
        this.status = status;
    }

    // Ensures the next auto-assigned id won't collide with one already loaded from the database. 
    public static void bumpCounterPast(int usedId) {
        COUNTER.updateAndGet(v -> Math.max(v, usedId + 1));
    }

    // Processes the payment using whatever credentials the subclass collected. 
    public abstract boolean processPayment();

    public int getPaymentId() { return paymentId; }
    public double getAmount() { return amount; }
    public PaymentStatus getStatus() { return status; }
    protected void setStatus(PaymentStatus status) { this.status = status; }

    // Human readable label for the payment method, used in the UI.
    public abstract String getMethodName();
}
