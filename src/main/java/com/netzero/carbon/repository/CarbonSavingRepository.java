package com.netzero.carbon.repository;

import com.netzero.carbon.domain.CarbonSaving;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CarbonSavingRepository extends JpaRepository<CarbonSaving, Long> {

    Optional<CarbonSaving> findByStoreIdAndItemIdAndTargetDate(Long storeId, Long itemId, LocalDate targetDate);

    List<CarbonSaving> findByStoreIdAndTargetDate(Long storeId, LocalDate targetDate);

    List<CarbonSaving> findByStoreIdAndTargetDateBetween(Long storeId, LocalDate from, LocalDate to);

    List<CarbonSaving> findByStoreIdOrderByTargetDateDesc(Long storeId);
}
