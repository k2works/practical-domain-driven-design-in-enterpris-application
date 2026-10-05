package com.example.cargotracker.quotation.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShipmentTermsTest {

    private static RequiredDocument document(int documentNo, DocumentType type) {
        return new RequiredDocument(
                documentNo, type, "d.pdf", DocumentMediaType.PDF, 10, "0".repeat(64), "quotation/x/" + documentNo);
    }

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

    @Test
    void 種類ごとの上限までの書類は持てる() {
        List<RequiredDocument> documents = List.of(
                document(1, DocumentType.COMMERCIAL_INVOICE),
                document(2, DocumentType.PACKING_LIST),
                document(3, DocumentType.OTHER),
                document(4, DocumentType.OTHER),
                document(5, DocumentType.OTHER));

        assertThat(ShipmentTermsFixture.generalCargo().withDocuments(documents).documents())
                .hasSize(5);
    }

    @Test
    void 種類ごとの上限を超える書類は持てない() {
        ShipmentTerms terms = ShipmentTermsFixture.generalCargo();
        List<RequiredDocument> twoInvoices =
                List.of(document(1, DocumentType.COMMERCIAL_INVOICE), document(2, DocumentType.COMMERCIAL_INVOICE));
        List<RequiredDocument> fourOthers = List.of(
                document(1, DocumentType.OTHER),
                document(2, DocumentType.OTHER),
                document(3, DocumentType.OTHER),
                document(4, DocumentType.OTHER));

        assertThatThrownBy(() -> terms.withDocuments(twoInvoices)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> terms.withDocuments(fourOthers)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 同じ書類番号の書類は持てない() {
        ShipmentTerms terms = ShipmentTermsFixture.generalCargo();
        List<RequiredDocument> duplicated =
                List.of(document(1, DocumentType.COMMERCIAL_INVOICE), document(1, DocumentType.PACKING_LIST));

        assertThatThrownBy(() -> terms.withDocuments(duplicated)).isInstanceOf(IllegalArgumentException.class);
    }
}
