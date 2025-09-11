package com.bytebyteboot.payment.controller;

import com.bytebyteboot.payment.dto.PaymentOrderRequest;
import com.bytebyteboot.payment.dto.PaymentOrderResponse;
import com.bytebyteboot.payment.dto.PaymentVerificationRequest;
import com.bytebyteboot.payment.service.PaymentOrderService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
@CrossOrigin(origins = "*")
public class PaymentOrderController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentOrderController.class);

    private final PaymentOrderService paymentOrderService;

    @Autowired
    public PaymentOrderController(PaymentOrderService paymentOrderService) {
        this.paymentOrderService = paymentOrderService;
    }

    @PostMapping("/create-order")
    public ResponseEntity<PaymentOrderResponse> createOrder(@Valid @RequestBody PaymentOrderRequest request) {
        logger.info("Received request to create payment order for amount: {}", request.amount());
        PaymentOrderResponse response = paymentOrderService.createOrder(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/verify")
    public ResponseEntity<PaymentOrderResponse> verifyPayment(@Valid @RequestBody PaymentVerificationRequest request) {
        logger.info("Received request to verify payment for order: {}", request.razorpayOrderId());
        PaymentOrderResponse response = paymentOrderService.verifyPayment(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<PaymentOrderResponse> getOrder(@PathVariable String orderId) {
        logger.info("Received request to get order: {}", orderId);
        PaymentOrderResponse response = paymentOrderService.getOrder(orderId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/orders")
    public ResponseEntity<List<PaymentOrderResponse>> getAllOrders() {
        logger.info("Received request to get all orders");
        List<PaymentOrderResponse> response = paymentOrderService.getAllOrders();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/cancel/{orderId}")
    public ResponseEntity<PaymentOrderResponse> cancelOrder(@PathVariable String orderId) {
        logger.info("Received request to cancel order: {}", orderId);
        PaymentOrderResponse response = paymentOrderService.cancelOrder(orderId);
        return ResponseEntity.ok(response);
    }
}
