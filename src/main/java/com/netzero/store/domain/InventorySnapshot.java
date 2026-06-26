package com.netzero.store.domain;

import com.netzero.common.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "inventory_snapshot")
public class InventorySnapshot extends BaseEntity {

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "day_of_week")
    private String dayOfWeek;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private ItemMaster item;

    private String category;
    private String unit;

    @Column(name = "ordered_qty")
    private BigDecimal orderedQty;

    @Column(name = "opening_stock")
    private BigDecimal openingStock;

    private BigDecimal demand;

    @Column(name = "actual_sales")
    private BigDecimal actualSales;

    private BigDecimal stockout;

    @Column(name = "waste_qty")
    private BigDecimal wasteQty;

    @Column(name = "closing_stock")
    private BigDecimal closingStock;

    @Column(name = "waste_kg")
    private BigDecimal wasteKg;

    @Column(name = "waste_carbon_kg")
    private BigDecimal wasteCarbonKg;

    @Column(name = "waste_cost_krw")
    private BigDecimal wasteCostKrw;

    @Column(name = "last_order_date")
    private LocalDate lastOrderDate;

    public InventorySnapshot() {}

    public Long getStoreId() { return storeId; }
    public void setStoreId(Long storeId) { this.storeId = storeId; }
    public LocalDate getBusinessDate() { return businessDate; }
    public void setBusinessDate(LocalDate businessDate) { this.businessDate = businessDate; }
    public String getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(String dayOfWeek) { this.dayOfWeek = dayOfWeek; }
    public ItemMaster getItem() { return item; }
    public void setItem(ItemMaster item) { this.item = item; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public BigDecimal getOrderedQty() { return orderedQty; }
    public void setOrderedQty(BigDecimal orderedQty) { this.orderedQty = orderedQty; }
    public BigDecimal getOpeningStock() { return openingStock; }
    public void setOpeningStock(BigDecimal openingStock) { this.openingStock = openingStock; }
    public BigDecimal getDemand() { return demand; }
    public void setDemand(BigDecimal demand) { this.demand = demand; }
    public BigDecimal getActualSales() { return actualSales; }
    public void setActualSales(BigDecimal actualSales) { this.actualSales = actualSales; }
    public BigDecimal getStockout() { return stockout; }
    public void setStockout(BigDecimal stockout) { this.stockout = stockout; }
    public BigDecimal getWasteQty() { return wasteQty; }
    public void setWasteQty(BigDecimal wasteQty) { this.wasteQty = wasteQty; }
    public BigDecimal getClosingStock() { return closingStock; }
    public void setClosingStock(BigDecimal closingStock) { this.closingStock = closingStock; }
    public BigDecimal getWasteKg() { return wasteKg; }
    public void setWasteKg(BigDecimal wasteKg) { this.wasteKg = wasteKg; }
    public BigDecimal getWasteCarbonKg() { return wasteCarbonKg; }
    public void setWasteCarbonKg(BigDecimal wasteCarbonKg) { this.wasteCarbonKg = wasteCarbonKg; }
    public BigDecimal getWasteCostKrw() { return wasteCostKrw; }
    public void setWasteCostKrw(BigDecimal wasteCostKrw) { this.wasteCostKrw = wasteCostKrw; }
    public LocalDate getLastOrderDate() { return lastOrderDate; }
    public void setLastOrderDate(LocalDate lastOrderDate) { this.lastOrderDate = lastOrderDate; }
}
