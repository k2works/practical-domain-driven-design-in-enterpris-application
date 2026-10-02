package com.example.cargotracker.quotation.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import org.junit.jupiter.api.Test;

class ShipmentTermsTest {

    @Test
    void 出発地と目的地が同じ輸送条件は作れない() {
        ShipmentTerms terms = ShipmentTermsFixture.generalCargo();
        CompanyId consignee = terms.consigneeCompanyId();
        Location tokyo = new Location("JPTYO");
        UtcInstant deadline = terms.arrivalDeadline();
        Cargo cargo = terms.cargo();

        assertThatThrownBy(() -> new ShipmentTerms(consignee, tokyo, tokyo, deadline, cargo))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
