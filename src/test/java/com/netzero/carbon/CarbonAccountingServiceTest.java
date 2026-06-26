package com.netzero.carbon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import com.netzero.carbon.service.CarbonAccountingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CarbonAccountingServiceTest {

  private CarbonAccountingService svc;

  @BeforeEach
  void setUp() {
    svc = new CarbonAccountingService();
  }

  @Test
  void milk() {
    // Milk example: qty 11.95, kg 1.03, efProd 3.0, efWaste 0.2, wasteTarget=true
    // Expected: kg=12.31, guaranteed=2.46, potential=39.4
    var r = svc.compute(11.95, 1.03, 3.0, 0.2, true);
    assertThat(r.wasteAvoidedKg()).isCloseTo(12.31, offset(0.01));
    assertThat(r.guaranteedSavingKg()).isCloseTo(2.46, offset(0.01));
    assertThat(r.potentialSavingKg()).isCloseTo(39.4, offset(0.1));
  }

  @Test
  void nonWasteTargetIsZero() {
    // When wasteTarget=false, all values should be zero
    assertThat(svc.compute(5, 0, 0, 0, false).potentialSavingKg()).isZero();
  }
}
