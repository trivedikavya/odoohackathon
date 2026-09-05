package com.urbanfurniture.accounting.transaction.payment;

public enum PaymentDirection {
    /** Money in - a customer settling an invoice. */
    RECEIVE,
    /** Money out - us settling a vendor bill. */
    PAY
}
