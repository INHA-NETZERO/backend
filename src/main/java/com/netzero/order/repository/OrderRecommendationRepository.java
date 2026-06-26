package com.netzero.order.repository;

import com.netzero.order.domain.OrderRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface OrderRecommendationRepository extends JpaRepository<OrderRecommendation, Long> {

    Optional<OrderRecommendation> findByStoreIdAndItemIdAndTargetDate(Long storeId, Long itemId, LocalDate targetDate);

    List<OrderRecommendation> findByStoreIdAndTargetDate(Long storeId, LocalDate targetDate);

    Optional<OrderRecommendation> findFirstByStoreIdOrderByTargetDateDesc(Long storeId);
}
