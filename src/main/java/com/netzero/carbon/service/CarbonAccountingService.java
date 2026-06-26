package com.netzero.carbon.service;

import com.netzero.carbon.dto.CarbonResult;

/**
 * Pure function service for carbon accounting calculations.
 *
 * Computes carbon savings based on waste avoidance and production/waste efficiency factors.
 * Separates guaranteed savings (from waste avoidance) from potential savings (including production efficiency).
 *
 * No Spring beans, no database access — pure arithmetic algorithm.
 */
public class CarbonAccountingService {

  /**
   * Compute carbon savings from waste avoidance.
   *
   * @param wasteAvoidedQty Quantity of waste avoided (units depend on context)
   * @param kgPerUnit Conversion factor from units to kilograms
   * @param efProd Efficiency factor for production (kg CO2e per kg of material)
   * @param efWaste Efficiency factor for waste/disposal (kg CO2e per kg of waste)
   * @param wasteTarget Whether this item is a waste reduction target; if false, all values are zero
   * @return CarbonResult containing wasteAvoidedKg, guaranteedSavingKg, and potentialSavingKg
   */
  public CarbonResult compute(
      double wasteAvoidedQty,
      double kgPerUnit,
      double efProd,
      double efWaste,
      boolean wasteTarget) {

    // If not a waste target, return all zeros
    if (!wasteTarget) {
      return new CarbonResult(0, 0, 0);
    }

    // Calculate waste avoided in kilograms
    double wasteAvoidedKg = wasteAvoidedQty * kgPerUnit;

    // Guaranteed saving: waste avoidance multiplied by waste efficiency factor
    double guaranteedSavingKg = wasteAvoidedKg * efWaste;

    // Potential saving: waste avoidance multiplied by sum of production and waste efficiency factors
    double potentialSavingKg = wasteAvoidedKg * (efProd + efWaste);

    return new CarbonResult(wasteAvoidedKg, guaranteedSavingKg, potentialSavingKg);
  }
}
