package com.bytebyteboot.payment.repository;

import com.bytebyteboot.payment.model.PaymentOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;
import java.time.LocalDateTime;

@Repository
public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {

    Optional<PaymentOrder> findByRazorpayOrderId(String razorpayOrderId);

    Optional<PaymentOrder> findByRazorpayPaymentId(String razorpayPaymentId);

    List<PaymentOrder> findByStatus(PaymentOrder.PaymentStatus status);

    List<PaymentOrder> findByCustomerEmail(String customerEmail);

    @Query("SELECT p FROM PaymentOrder p WHERE p.createdAt BETWEEN ?1 AND ?2")
    List<PaymentOrder> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    boolean existsByRazorpayOrderId(String razorpayOrderId);
}