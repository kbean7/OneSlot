package com.reservation.payment.repository;

import com.reservation.payment.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    boolean existsByPaymentId(Long paymentId);

    Optional<Refund> findByPaymentId(Long paymentId);

    List<Refund> findByPaymentIdIn(List<Long> paymentIds);
}
