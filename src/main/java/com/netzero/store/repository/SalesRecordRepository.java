package com.netzero.store.repository;

import com.netzero.store.domain.SalesRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface SalesRecordRepository extends JpaRepository<SalesRecord, Long> {

    List<SalesRecord> findByStoreIdAndBusinessDateBetween(Long storeId, LocalDate from, LocalDate to);
}
