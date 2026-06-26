package com.netzero.carbon.dto;

/**
 * Immutable result of carbon accounting computation.
 *
 * @param wasteAvoidedKg Total waste avoided in kilograms (wasteAvoidedQty * kgPerUnit)
 * @param guaranteedSavingKg Guaranteed carbon saving from waste avoidance (wasteAvoidedKg * efWaste)
 * @param potentialSavingKg Potential carbon saving including production efficiency (wasteAvoidedKg * (efProd + efWaste))
 */
public record CarbonResult(
    double wasteAvoidedKg,
    double guaranteedSavingKg,
    double potentialSavingKg) {}
