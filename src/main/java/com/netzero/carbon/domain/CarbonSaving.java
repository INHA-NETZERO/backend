package com.netzero.carbon.domain;

import com.netzero.common.BaseEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "carbon_saving")
public class CarbonSaving extends BaseEntity {

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Column(name = "waste_avoided_kg", precision = 12, scale = 3)
    private BigDecimal wasteAvoidedKg;

    @Column(name = "guaranteed_saving_kg", precision = 12, scale = 3)
    private BigDecimal guaranteedSavingKg;

    @Column(name = "potential_saving_kg", precision = 12, scale = 3)
    private BigDecimal potentialSavingKg;

    @Column(name = "ef_prod_snapshot", precision = 8, scale = 3)
    private BigDecimal efProdSnapshot;

    @Column(name = "ef_waste_snapshot", precision = 8, scale = 3)
    private BigDecimal efWasteSnapshot;

    public CarbonSaving() {}

    public Long getStoreId() { return storeId; }
    public void setStoreId(Long storeId) { this.storeId = storeId; }

    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }

    public LocalDate getTargetDate() { return targetDate; }
    public void setTargetDate(LocalDate targetDate) { this.targetDate = targetDate; }

    public BigDecimal getWasteAvoidedKg() { return wasteAvoidedKg; }
    public void setWasteAvoidedKg(BigDecimal wasteAvoidedKg) { this.wasteAvoidedKg = wasteAvoidedKg; }

    public BigDecimal getGuaranteedSavingKg() { return guaranteedSavingKg; }
    public void setGuaranteedSavingKg(BigDecimal guaranteedSavingKg) { this.guaranteedSavingKg = guaranteedSavingKg; }

    public BigDecimal getPotentialSavingKg() { return potentialSavingKg; }
    public void setPotentialSavingKg(BigDecimal potentialSavingKg) { this.potentialSavingKg = potentialSavingKg; }

    public BigDecimal getEfProdSnapshot() { return efProdSnapshot; }
    public void setEfProdSnapshot(BigDecimal efProdSnapshot) { this.efProdSnapshot = efProdSnapshot; }

    public BigDecimal getEfWasteSnapshot() { return efWasteSnapshot; }
    public void setEfWasteSnapshot(BigDecimal efWasteSnapshot) { this.efWasteSnapshot = efWasteSnapshot; }
}
