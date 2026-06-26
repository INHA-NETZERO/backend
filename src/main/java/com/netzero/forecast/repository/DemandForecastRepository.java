package com.netzero.forecast.repository;

import com.netzero.forecast.domain.DemandForecast;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DemandForecastRepository extends JpaRepository<DemandForecast, Long> {

    Optional<DemandForecast> findByStoreIdAndItemIdAndTargetDate(Long storeId, Long itemId, LocalDate targetDate);

    List<DemandForecast> findByStoreIdAndItemIdInAndTargetDate(Long storeId, List<Long> itemIds, LocalDate targetDate);
}
