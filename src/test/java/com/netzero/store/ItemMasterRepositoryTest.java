package com.netzero.store;

import com.netzero.store.domain.ItemCategory;
import com.netzero.store.repository.ItemMasterRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ItemMasterRepositoryTest {

    @Autowired
    ItemMasterRepository repo;

    @Test
    void seedLoadedAndFindByName() {
        var milk = repo.findByName("우유").orElseThrow();
        assertThat(milk.getEfProd()).isEqualByComparingTo("3.0");
        assertThat(milk.isWasteTarget()).isTrue();
    }

    @Test
    void findAllReturnsSeededItems() {
        assertThat(repo.findAll()).hasSize(28);
    }

    @Test
    void findByWasteTargetTrueFiltersCorrectly() {
        // 완제품 9 + 원재료 9 = 18 waste-target items
        var wasteItems = repo.findByWasteTargetTrueOrderByCategoryAscNameAsc();
        assertThat(wasteItems).hasSize(18);
        assertThat(wasteItems).allMatch(i -> i.isWasteTarget());
    }

    @Test
    void findByCategoryReturnsCorrectSubset() {
        var rawMaterials = repo.findByCategoryOrderByNameAsc(ItemCategory.원재료);
        // R01~R09 => 9 items
        assertThat(rawMaterials).hasSize(9);
        assertThat(rawMaterials).allMatch(i -> i.getCategory() == ItemCategory.원재료);

        var beverages = repo.findByCategoryOrderByNameAsc(ItemCategory.판매음료);
        // D01~D07 => 7 items
        assertThat(beverages).hasSize(7);
    }
}
