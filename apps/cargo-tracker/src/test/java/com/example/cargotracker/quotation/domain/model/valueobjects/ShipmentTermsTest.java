package com.example.cargotracker.quotation.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.Location;
import org.junit.jupiter.api.Test;

class ShipmentTermsTest {

    @Test
    void 出発地と目的地が同じ輸送条件は作れない() {
        ShipmentTerms terms = ShipmentTermsFixture.generalCargo();

        assertThatThrownBy(() -> new ShipmentTerms(
                        terms.consigneeCompanyId(),
                        new Location("JPTYO"),
                        new Location("JPTYO"),
                        terms.arrivalDeadline(),
                        terms.cargo()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
