package com.netzero.store.repository;

import com.netzero.store.domain.OrderPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderPolicyRepository extends JpaRepository<OrderPolicy, Long> {

    @Query("SELECT op FROM OrderPolicy op WHERE op.store.id = :storeId AND op.item.id = :itemId")
    Optional<OrderPolicy> findByStoreIdAndItemId(@Param("storeId") Long storeId, @Param("itemId") Long itemId);

    @Query("SELECT op FROM OrderPolicy op WHERE op.store.id = :storeId")
    List<OrderPolicy> findByStoreId(@Param("storeId") Long storeId);
}
