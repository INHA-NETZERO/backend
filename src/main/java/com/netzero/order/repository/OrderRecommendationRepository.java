package com.netzero.order.repository;

import com.netzero.order.domain.OrderRecommendation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface OrderRecommendationRepository extends JpaRepository<OrderRecommendation, Long> {

    Optional<OrderRecommendation> findByStoreIdAndItemIdAndTargetDate(Long storeId, Long itemId, LocalDate targetDate);

    List<OrderRecommendation> findByStoreIdAndTargetDate(Long storeId, LocalDate targetDate);

    Optional<OrderRecommendation> findFirstByStoreIdOrderByTargetDateDesc(Long storeId);

    @Query("SELECT DISTINCT r.targetDate FROM OrderRecommendation r " +
           "WHERE r.storeId = :storeId AND r.actualQuantity IS NOT NULL " +
           "ORDER BY r.targetDate DESC")
    Page<LocalDate> findConfirmedDatesByStoreId(@Param("storeId") Long storeId, Pageable pageable);

    @Query("SELECT r FROM OrderRecommendation r " +
           "WHERE r.storeId = :storeId AND r.targetDate = :date")
    List<OrderRecommendation> findAllByStoreIdAndTargetDate(
            @Param("storeId") Long storeId, @Param("date") LocalDate date);
}
