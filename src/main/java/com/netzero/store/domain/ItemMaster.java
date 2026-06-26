package com.netzero.store.domain;

import com.netzero.common.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "item_master")
public class ItemMaster extends BaseEntity {

    private String name;

    @Enumerated(EnumType.STRING)
    private ItemCategory category;

    @Column(name = "waste_target")
    private boolean wasteTarget;

    private String unit;

    @Column(name = "shelf_life_days")
    private Integer shelfLifeDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_condition")
    private StorageCondition storageCondition;

    @Column(name = "kg_per_unit")
    private BigDecimal kgPerUnit;

    @Column(name = "ef_prod")
    private BigDecimal efProd;

    @Column(name = "ef_waste")
    private BigDecimal efWaste;

    @Column(name = "purchase_price")
    private BigDecimal purchasePrice;

    @Column(name = "price_unit")
    private String priceUnit;

    private String note;

    protected ItemMaster() {}

    public String getName() { return name; }
    public ItemCategory getCategory() { return category; }
    public boolean isWasteTarget() { return wasteTarget; }
    public String getUnit() { return unit; }
    public Integer getShelfLifeDays() { return shelfLifeDays; }
    public StorageCondition getStorageCondition() { return storageCondition; }
    public BigDecimal getKgPerUnit() { return kgPerUnit; }
    public BigDecimal getEfProd() { return efProd; }
    public BigDecimal getEfWaste() { return efWaste; }
    public BigDecimal getPurchasePrice() { return purchasePrice; }
    public String getPriceUnit() { return priceUnit; }
    public String getNote() { return note; }
}
