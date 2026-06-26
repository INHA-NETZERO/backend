package com.netzero.store.repository;

import com.netzero.store.domain.ItemCategory;
import com.netzero.store.domain.ItemMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ItemMasterRepository extends JpaRepository<ItemMaster, Long> {

    Optional<ItemMaster> findByName(String name);

    List<ItemMaster> findAllByOrderByCategoryAscNameAsc();

    List<ItemMaster> findByWasteTargetTrueOrderByCategoryAscNameAsc();

    List<ItemMaster> findByCategoryOrderByNameAsc(ItemCategory category);
}
