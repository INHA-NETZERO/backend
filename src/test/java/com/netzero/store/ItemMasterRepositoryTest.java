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
    void findAllReturnsSixSeededItems() {
        assertThat(repo.findAll()).hasSize(6);
    }

    @Test
    void findByWasteTargetTrueFiltersCorrectly() {
        // 우유, 치즈, 원두, 베이커리 => 4 waste-target items
        var wasteItems = repo.findByWasteTargetTrue();
        assertThat(wasteItems).hasSize(4);
        assertThat(wasteItems).allMatch(i -> i.isWasteTarget());
    }

    @Test
    void findByCategoryReturnsCorrectSubset() {
        var rawMaterials = repo.findByCategory(ItemCategory.원재료);
        // 우유, 치즈, 원두 => 3 items
        assertThat(rawMaterials).hasSize(3);
        assertThat(rawMaterials).allMatch(i -> i.getCategory() == ItemCategory.원재료);

        var beverages = repo.findByCategory(ItemCategory.판매음료);
        // 아메리카노 => 1 item
        assertThat(beverages).hasSize(1);
    }
}
