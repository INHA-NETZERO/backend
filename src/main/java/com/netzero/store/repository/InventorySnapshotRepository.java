package com.netzero.store.repository;

import com.netzero.store.domain.InventorySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InventorySnapshotRepository extends JpaRepository<InventorySnapshot, Long> {

    Optional<InventorySnapshot> findByStoreIdAndItem_IdAndBusinessDate(Long storeId, Long itemId, LocalDate date);

    List<InventorySnapshot> findByStoreIdAndBusinessDate(Long storeId, LocalDate date);
}
