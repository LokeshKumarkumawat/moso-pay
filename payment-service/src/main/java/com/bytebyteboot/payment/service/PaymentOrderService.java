package com.bytebyteboot.payment.service;

import com.bytebyteboot.payment.config.RazorpayProperties;
import com.bytebyteboot.payment.dto.PaymentOrderRequest;
import com.bytebyteboot.payment.dto.PaymentVerificationRequest;
import com.bytebyteboot.payment.exception.PaymentException;
import com.bytebyteboot.payment.model.PaymentOrder;
import com.bytebyteboot.payment.dto.PaymentOrderResponse;
import com.bytebyteboot.payment.repository.PaymentOrderRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class PaymentOrderService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentOrderService.class);

    private final RazorpayClient razorpayClient;
    private final PaymentOrderRepository paymentOrderRepository;
    private final RazorpayProperties razorpayProperties;

    @Autowired
    public PaymentOrderService(RazorpayClient razorpayClient,
                          PaymentOrderRepository paymentOrderRepository,
                          RazorpayProperties razorpayProperties) {
        this.razorpayClient = razorpayClient;
        this.paymentOrderRepository = paymentOrderRepository;
        this.razorpayProperties = razorpayProperties;
    }

    public PaymentOrderResponse createOrder(PaymentOrderRequest request) {
        try {
            logger.info("Creating Razorpay order for amount: {}", request.amount());

            int amountInPaise = request.amount().multiply(java.math.BigDecimal.valueOf(100)).intValue();

            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amountInPaise);
            orderRequest.put("currency", request.currency());
            orderRequest.put("receipt", request.receipt());

            if (request.notes() != null) {
                JSONObject notes = new JSONObject();
                notes.put("notes", request.notes());
                orderRequest.put("notes", notes);
            }

            Order razorpayOrder = razorpayClient.orders.create(orderRequest);

            // Save to database
            PaymentOrder paymentOrder = new PaymentOrder(
                    razorpayOrder.get("id"),
                    request.amount(),
                    request.currency(),
                    request.receipt()
            );
            paymentOrder.setCustomerEmail(request.customerEmail());
            paymentOrder.setCustomerPhone(request.customerPhone());
            paymentOrder.setNotes(request.notes());

            PaymentOrder savedOrder = paymentOrderRepository.save(paymentOrder);

            return PaymentOrderResponse.from(savedOrder);

        } catch (RazorpayException e) {
            logger.error("Error creating Razorpay order: {}", e.getMessage(), e);
            throw new PaymentException("Failed to create payment order", e);
        }
    }

    public PaymentOrderResponse verifyPayment(PaymentVerificationRequest request) {
        try {
            logger.info("Verifying payment for order: {}", request.razorpayOrderId());

            // Verify signature
            if (!verifySignature(request.razorpayOrderId(),
                    request.razorpayPaymentId(),
                    request.razorpaySignature())) {
                throw new PaymentException("Invalid payment signature");
            }

            // Update payment order
            PaymentOrder paymentOrder = paymentOrderRepository
                    .findByRazorpayOrderId(request.razorpayOrderId())
                    .orElseThrow(() -> new PaymentException("Payment order not found"));

            paymentOrder.setRazorpayPaymentId(request.razorpayPaymentId());
            paymentOrder.setRazorpaySignature(request.razorpaySignature());
            paymentOrder.setStatus(PaymentOrder.PaymentStatus.PAID);

            PaymentOrder updatedOrder = paymentOrderRepository.save(paymentOrder);

            logger.info("Payment verified successfully for order: {}", request.razorpayOrderId());
            return PaymentOrderResponse.from(updatedOrder);

        } catch (Exception e) {
            logger.error("Error verifying payment: {}", e.getMessage(), e);
            throw new PaymentException("Payment verification failed", e);
        }
    }

    private boolean verifySignature(String orderId, String paymentId, String signature) {
        try {
            String data = orderId + "|" + paymentId;
            String generatedSignature = calculateHMAC(data, razorpayProperties.getKeySecret());
            return signature.equals(generatedSignature);
        } catch (Exception e) {
            logger.error("Error verifying signature: {}", e.getMessage(), e);
            return false;
        }
    }

    private String calculateHMAC(String data, String key)
            throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }


    @Transactional(readOnly = true)
    public PaymentOrderResponse getOrder(String orderId) {
        PaymentOrder paymentOrder = paymentOrderRepository
                .findByRazorpayOrderId(orderId)
                .orElseThrow(() -> new PaymentException("Payment order not found"));

        return PaymentOrderResponse.from(paymentOrder);
    }

    @Transactional(readOnly = true)
    public List<PaymentOrderResponse> getAllOrders() {
        return paymentOrderRepository.findAll()
                .stream()
                .map(PaymentOrderResponse::from)
                .toList();
    }

    public PaymentOrderResponse cancelOrder(String orderId) {
        PaymentOrder paymentOrder = paymentOrderRepository
                .findByRazorpayOrderId(orderId)
                .orElseThrow(() -> new PaymentException("Payment order not found"));

        if (paymentOrder.getStatus() == PaymentOrder.PaymentStatus.PAID) {
            throw new PaymentException("Cannot cancel a paid order");
        }

        paymentOrder.setStatus(PaymentOrder.PaymentStatus.CANCELLED);
        PaymentOrder updatedOrder = paymentOrderRepository.save(paymentOrder);

        return PaymentOrderResponse.from(updatedOrder);
    }

    public void handleWebhook(String payload, String signature) {
        try {
            // Verify webhook signature
            if (!verifyWebhookSignature(payload, signature)) {
                throw new PaymentException("Invalid webhook signature");
            }

            JSONObject webhookData = new JSONObject(payload);
            String event = webhookData.getString("event");
            JSONObject paymentEntity = webhookData.getJSONObject("payload")
                    .getJSONObject("payment")
                    .getJSONObject("entity");

            String orderId = paymentEntity.getString("order_id");

            Optional<PaymentOrder> optionalOrder = paymentOrderRepository.findByRazorpayOrderId(orderId);
            if (optionalOrder.isPresent()) {
                PaymentOrder paymentOrder = optionalOrder.get();

                switch (event) {
                    case "payment.captured" -> {
                        paymentOrder.setStatus(PaymentOrder.PaymentStatus.PAID);
                        paymentOrder.setRazorpayPaymentId(paymentEntity.getString("id"));
                    }
                    case "payment.failed" -> paymentOrder.setStatus(PaymentOrder.PaymentStatus.FAILED);
                    default -> logger.warn("Unhandled webhook event: {}", event);
                }

                paymentOrderRepository.save(paymentOrder);
                logger.info("Processed webhook event: {} for order: {}", event, orderId);
            }

        } catch (Exception e) {
            logger.error("Error processing webhook: {}", e.getMessage(), e);
            throw new PaymentException("Webhook processing failed", e);
        }
    }

    private boolean verifyWebhookSignature(String payload, String signature) {
        try {
            String generatedSignature = calculateHMAC(payload, razorpayProperties.getWebhookSecret());
            return signature.equals(generatedSignature);
        } catch (Exception e) {
            logger.error("Error verifying webhook signature: {}", e.getMessage(), e);
            return false;
        }
    }


}
