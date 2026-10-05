package com.example.cargotracker.quotation.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 見積有効期限の判定（判定時刻が期限より前だけ有効、同時刻は失効。BR-10、Q-INV-06。Bolt 11）。 */
class QuotationExpiryTest {

    private final QuotationExpiry expiry = new QuotationExpiry(new UtcInstant(Instant.parse("2099-10-08T09:00:00Z")));

    @ParameterizedTest
    @CsvSource({"2099-10-08T08:59:59Z, true", "2099-10-08T09:00:00Z, false", "2099-10-08T09:00:01Z, false"})
    void 判定時刻が期限より前のときだけ有効(String judgedAt, boolean valid) {
        assertThat(expiry.isValidAt(new UtcInstant(Instant.parse(judgedAt)))).isEqualTo(valid);
    }

    @Test
    void 判定時刻がなければ壊れた前提とする() {
        assertThatThrownBy(() -> expiry.isValidAt(null)).isInstanceOf(NullPointerException.class);
    }
}
