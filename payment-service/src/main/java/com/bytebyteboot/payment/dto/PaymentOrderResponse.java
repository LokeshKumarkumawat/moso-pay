package com.bytebyteboot.payment.dto;
import com.bytebyteboot.payment.model.PaymentOrder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentOrderResponse(
        Long id,
        String razorpayOrderId,
        String razorpayPaymentId,
        BigDecimal amount,
        String currency,
        String receipt,
        PaymentOrder.PaymentStatus status,
        String customerEmail,
        String customerPhone,
        LocalDateTime createdAt,
        String notes
) {
    public static PaymentOrderResponse from(PaymentOrder paymentOrder) {
        return new PaymentOrderResponse(
                paymentOrder.getId(),
                paymentOrder.getRazorpayOrderId(),
                paymentOrder.getRazorpayPaymentId(),
                paymentOrder.getAmount(),
                paymentOrder.getCurrency(),
                paymentOrder.getReceipt(),
                paymentOrder.getStatus(),
                paymentOrder.getCustomerEmail(),
                paymentOrder.getCustomerPhone(),
                paymentOrder.getCreatedAt(),
                paymentOrder.getNotes()
        );
    }
}