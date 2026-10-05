package com.example.cargotracker.quotation.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.quotation.domain.model.aggregates.ConcurrentQuotationUpdateException;
import com.example.cargotracker.quotation.domain.model.aggregates.DuplicateQuotationException;
import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.aggregates.QuotationRepository;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.Currency;
import com.example.cargotracker.quotation.domain.model.valueobjects.PricingLine;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationId;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationInput;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationStatus;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotedRequestSummary;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 見積り（`quotation`・`pricing_line`、Q-INV-05・07・17・18）の永続化を PostgreSQL で確かめる。
 * 業務番号はほかのテストとぶつからないよう 2082 年を使う。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class MyBatisQuotationIntegrationTest {

    private static final UtcInstant NOW = new UtcInstant(Instant.parse("2082-01-05T04:00:00Z"));
    private static final UtcInstant APPROVED_AT = new UtcInstant(Instant.parse("2082-01-05T04:30:00Z"));
    private static final UserId STAFF = new UserId(UUID.randomUUID());

    @Autowired
    QuotationRepository repository;

    @Autowired
    TransportRequestRepository transportRequests;

    @Autowired
    JdbcTemplate jdbc;

    private TransportRequestId transportRequest(int sequence) {
        TransportRequest request = TransportRequest.submit(
                new TransportRequestId(UUID.randomUUID()),
                new TransportRequestNumber(2082, sequence),
                new CompanyId(UUID.randomUUID()),
                ShipmentTermsFixture.generalCargo(),
                STAFF,
                NOW);
        transportRequests.save(request);
        return request.id();
    }

    private Quotation calculated(TransportRequestId transportRequestId, int quotationNo) {
        Quotation quotation = Quotation.create(new QuotationId(UUID.randomUUID()), transportRequestId, 1, quotationNo);
        quotation.calculate(QuotationFixture.completeInput(), NOW);
        return quotation;
    }

    @Test
    void 算出した見積りを料金明細と有効期限と経路方針とともに保存し読み出せる() {
        TransportRequestId transportRequestId = transportRequest(1);
        Quotation quotation = calculated(transportRequestId, 1);

        repository.save(quotation);

        assertThat(repository.findByTransportRequestIdAndNo(transportRequestId, 1))
                .hasValueSatisfying(found -> {
                    assertThat(found.id()).isEqualTo(quotation.id());
                    assertThat(found.status()).isEqualTo(QuotationStatus.PENDING_APPROVAL);
                    assertThat(found.transportRequestVersionNo()).isEqualTo(1);
                    assertThat(found.pricingBasis()).hasValueSatisfying(basis -> {
                        assertThat(basis.currency()).isEqualTo(Currency.USD);
                        assertThat(basis.lines())
                                .containsExactly(
                                        new PricingLine("海上運賃", new BigDecimal("3200.00"), "年間契約 2026-A"),
                                        new PricingLine("燃料調整金", new BigDecimal("530.00"), null));
                    });
                    assertThat(found.expiry()).isEqualTo(quotation.expiry());
                    assertThat(found.routePolicy()).hasValueSatisfying(policy -> {
                        assertThat(policy.via()).containsExactly(new Location("SGSIN"));
                        assertThat(policy.departureAt())
                                .isEqualTo(quotation.routePolicy().orElseThrow().departureAt());
                    });
                    assertThat(found.presentedAt()).isEmpty();
                });
        assertThat(jdbc.queryForObject(
                        "SELECT total_amount FROM quotation.quotation WHERE id = ?",
                        BigDecimal.class,
                        quotation.id().value()))
                .isEqualByComparingTo("3730.00");
    }

    @Test
    void 経由地のない経路方針を保存し読み出せる() {
        TransportRequestId transportRequestId = transportRequest(2);
        Quotation quotation = Quotation.create(new QuotationId(UUID.randomUUID()), transportRequestId, 1, 1);
        QuotationInput input = QuotationFixture.completeInput();
        quotation.calculate(
                new QuotationInput(
                        input.lines(),
                        input.currency(),
                        input.expiresAt(),
                        List.of(),
                        input.departureAt(),
                        input.arrivalAt()),
                NOW);

        repository.save(quotation);

        assertThat(repository.findByTransportRequestIdAndNo(transportRequestId, 1))
                .hasValueSatisfying(found ->
                        assertThat(found.routePolicy().orElseThrow().via()).isEmpty());
    }

    @Test
    void 社内承認して提示した見積りは承認者と提示時刻を保存し集約の版が進む() {
        TransportRequestId transportRequestId = transportRequest(3);
        repository.save(calculated(transportRequestId, 1));
        Quotation loaded =
                repository.findByTransportRequestIdAndNo(transportRequestId, 1).orElseThrow();
        loaded.presentInternally(STAFF, APPROVED_AT);

        repository.update(loaded);

        assertThat(repository.findByTransportRequestIdAndNo(transportRequestId, 1))
                .hasValueSatisfying(found -> {
                    assertThat(found.status()).isEqualTo(QuotationStatus.PRESENTED);
                    assertThat(found.approvedBy()).contains(STAFF);
                    assertThat(found.presentedAt()).contains(APPROVED_AT);
                    assertThat(found.aggregateVersion()).isEqualTo(loaded.aggregateVersion() + 1);
                });
    }

    @Test
    void 同じ見積りの版を2つ読んで更新すると後の更新は競合で失敗する() {
        TransportRequestId transportRequestId = transportRequest(4);
        repository.save(calculated(transportRequestId, 1));
        Quotation first =
                repository.findByTransportRequestIdAndNo(transportRequestId, 1).orElseThrow();
        Quotation second =
                repository.findByTransportRequestIdAndNo(transportRequestId, 1).orElseThrow();
        first.presentInternally(STAFF, NOW);
        second.presentInternally(STAFF, NOW);
        repository.update(first);

        assertThatThrownBy(() -> repository.update(second)).isInstanceOf(ConcurrentQuotationUpdateException.class);
    }

    @Test
    void 同じ輸送要求の同じ見積り番号はUKで拒否しドメインの例外にしてトランザクションを続けられる() {
        TransportRequestId transportRequestId = transportRequest(5);
        repository.save(calculated(transportRequestId, 1));
        Quotation duplicated = calculated(transportRequestId, 1);

        assertThatThrownBy(() -> repository.save(duplicated)).isInstanceOf(DuplicateQuotationException.class);
        assertThat(repository.findByTransportRequestId(transportRequestId))
                .as("UK の違反の後も同じトランザクションで読める（セーブポイントに戻す。Bolt 9・10 レビュー R-02）")
                .hasSize(1);
    }

    @Test
    void 見積りは見積り番号の順に返る() {
        TransportRequestId transportRequestId = transportRequest(6);
        Quotation first = calculated(transportRequestId, 1);
        Quotation second = calculated(transportRequestId, 2);
        first.replaceWith(second.id(), NOW);
        repository.save(second);
        repository.save(first);

        assertThat(repository.findByTransportRequestId(transportRequestId))
                .extracting(Quotation::quotationNo)
                .containsExactly(1, 2);
    }

    /** PostgreSQL は制約違反でトランザクションを中断するため、違反ごとに別のテストにする。 */
    @ParameterizedTest
    @CsvSource({
        "PENDING_APPROVAL, , 100.00, USD", // 承認待ちなのに有効期限がない
        "PENDING_APPROVAL, 2082-01-08T09:00:00Z, , USD", // 承認待ちなのに合計がない
        "PENDING_APPROVAL, 2082-01-08T09:00:00Z, 100.00, GBP", // 通貨が候補にない
        "ROUTING_REQUESTED, 2082-01-08T09:00:00Z, 100.00, USD" // Bolt 11 でまだ使わない状態
    })
    void 承認待ち以後の必須の列と通貨と状態はCHECK制約で守る(String status, String expiresAt, String totalAmount, String currency) {
        TransportRequestId transportRequestId = transportRequest(7);
        String insert = "INSERT INTO quotation.quotation (id, transport_request_id, quotation_no,"
                + " transport_request_version_no, status, expires_at, total_amount, currency, route_policy_via,"
                + " route_policy_departure_at, route_policy_arrival_at, version)"
                + " VALUES (?, ?, 1, 1, ?, CAST(? AS TIMESTAMP WITH TIME ZONE), CAST(? AS NUMERIC(15,2)), ?, '',"
                + " TIMESTAMP WITH TIME ZONE '2082-01-10 00:00:00+00', TIMESTAMP WITH TIME ZONE '2082-01-30 00:00:00+00', 0)";
        UUID id = UUID.randomUUID();
        UUID requestId = transportRequestId.value();

        assertThatThrownBy(() -> jdbc.update(insert, id, requestId, status, expiresAt, totalAmount, currency))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 作成中の見積りは有効期限と合計がなくても保存できる() {
        TransportRequestId transportRequestId = transportRequest(8);

        int inserted = jdbc.update(
                "INSERT INTO quotation.quotation (id, transport_request_id, quotation_no, transport_request_version_no,"
                        + " status, version) VALUES (?, ?, 1, 1, 'DRAFT', 0)",
                UUID.randomUUID(),
                transportRequestId.value());

        assertThat(inserted).isEqualTo(1);
    }

    private Quotation presented(TransportRequestId transportRequestId, int quotationNo) {
        Quotation quotation = calculated(transportRequestId, quotationNo);
        quotation.presentInternally(STAFF, APPROVED_AT);
        return quotation;
    }

    /** 再見積りと同じ順に書く: 旧版の更新を先に、新しい見積りの保存を後に（Q-INV-18。Bolt 11）。 */
    private void requote(Quotation old, Quotation replacement, UtcInstant at) {
        old.replaceWith(replacement.id(), at);
        repository.update(old);
        repository.save(replacement);
    }

    @Test
    void 置換済みの見積りは置換先とともに保存し読み出せ置換先のFKはコミットの時に確かめる() {
        TransportRequestId transportRequestId = transportRequest(9);
        repository.save(presented(transportRequestId, 1));
        Quotation old =
                repository.findByTransportRequestIdAndNo(transportRequestId, 1).orElseThrow();
        Quotation replacement = calculated(transportRequestId, 2);

        requote(old, replacement, APPROVED_AT);
        jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE");

        assertThat(repository.findByTransportRequestIdAndNo(transportRequestId, 1))
                .hasValueSatisfying(found -> {
                    assertThat(found.status()).isEqualTo(QuotationStatus.REPLACED);
                    assertThat(found.replacedBy()).contains(replacement.id());
                    assertThat(found.presentedAt()).contains(APPROVED_AT);
                });
        assertThat(repository.findByTransportRequestIdAndNo(transportRequestId, 2))
                .hasValueSatisfying(found -> assertThat(found.status()).isEqualTo(QuotationStatus.PENDING_APPROVAL));
    }

    @Test
    void 有効期限を過ぎて置き換えた見積りは失効として保存し置換先を持たない() {
        TransportRequestId transportRequestId = transportRequest(10);
        repository.save(presented(transportRequestId, 1));
        Quotation old =
                repository.findByTransportRequestIdAndNo(transportRequestId, 1).orElseThrow();

        requote(old, calculated(transportRequestId, 2), new UtcInstant(QuotationFixture.EXPIRES_AT.instant()));

        assertThat(repository.findByTransportRequestIdAndNo(transportRequestId, 1))
                .hasValueSatisfying(found -> {
                    assertThat(found.status()).isEqualTo(QuotationStatus.EXPIRED);
                    assertThat(found.replacedBy()).isEmpty();
                });
    }

    @Test
    void 作成中と承認待ちと提示済みの見積りは輸送要求に1つだけで部分一意インデックスがドメインの例外にする() {
        TransportRequestId transportRequestId = transportRequest(11);
        repository.save(presented(transportRequestId, 1));
        Quotation second = calculated(transportRequestId, 2);

        assertThatThrownBy(() -> repository.save(second)).isInstanceOf(DuplicateQuotationException.class);
        assertThat(repository.findByTransportRequestId(transportRequestId))
                .as("違反の後も同じトランザクションで読める（セーブポイントに戻す）")
                .hasSize(1);
    }

    @Test
    void 置換済みと失効の見積りは1つだけの数に入らない() {
        TransportRequestId transportRequestId = transportRequest(12);
        repository.save(presented(transportRequestId, 1));
        Quotation second = calculated(transportRequestId, 2);
        requote(repository.findByTransportRequestIdAndNo(transportRequestId, 1).orElseThrow(), second, NOW);
        Quotation third = calculated(transportRequestId, 3);
        Quotation loadedSecond =
                repository.findByTransportRequestIdAndNo(transportRequestId, 2).orElseThrow();

        requote(loadedSecond, third, new UtcInstant(QuotationFixture.EXPIRES_AT.instant()));

        assertThat(repository.findByTransportRequestId(transportRequestId))
                .extracting(Quotation::status)
                .containsExactly(QuotationStatus.REPLACED, QuotationStatus.EXPIRED, QuotationStatus.PENDING_APPROVAL);
    }

    /** PostgreSQL は制約違反でトランザクションを中断するため、違反ごとに別のテストにする。 */
    @ParameterizedTest
    @CsvSource({
        "REPLACED, ", // 置換済みなのに置換先がない
        "PRESENTED, replacement", // 置換済みでないのに置換先がある
        "EXPIRED, replacement" // 失効なのに置換先がある
    })
    void 置換先は置換済みのときだけ持つことをCHECK制約で守る(String status, String replacement) {
        TransportRequestId transportRequestId = transportRequest(13);
        Quotation target = calculated(transportRequestId, 1);
        repository.save(target);
        Quotation other = calculated(transportRequestId, 2);
        other.replaceWith(target.id(), NOW);
        repository.save(other);
        UUID replacedBy = replacement == null ? null : other.id().value();
        UUID approver = STAFF.value();
        UUID targetId = target.id().value();
        String update = "UPDATE quotation.quotation SET status = ?, replaced_by_quotation_id = ?,"
                + " internal_approved_by = ?, internal_approved_at = expires_at - INTERVAL '1 day',"
                + " presented_at = expires_at - INTERVAL '1 day' WHERE id = ?";

        assertThatThrownBy(() -> jdbc.update(update, status, replacedBy, approver, targetId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_quotation_replaced");
    }

    @Test
    void 置換先はある見積りでなければならない() {
        TransportRequestId transportRequestId = transportRequest(14);
        Quotation target = calculated(transportRequestId, 1);
        repository.save(target);
        jdbc.update(
                "UPDATE quotation.quotation SET status = 'REPLACED', replaced_by_quotation_id = ? WHERE id = ?",
                UUID.randomUUID(),
                target.id().value());

        assertThatThrownBy(() -> jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_quotation_replaced_by");
    }

    @Test
    void 見積提示済みの見積依頼の最新の見積りを有効期限の近い順に返し見積り作成中の見積依頼は返さない() {
        TransportRequestId quoted = transportRequest(15);
        TransportRequest request = transportRequests.findById(quoted).orElseThrow();
        request.approve(1, STAFF, "根拠", NOW);
        request.markQuotationPresented(1);
        transportRequests.update(request);
        repository.save(presented(quoted, 1));
        Quotation latest = calculated(quoted, 2);
        requote(repository.findByTransportRequestIdAndNo(quoted, 1).orElseThrow(), latest, APPROVED_AT);
        TransportRequestId quoting = transportRequest(16);
        TransportRequest quotingRequest = transportRequests.findById(quoting).orElseThrow();
        quotingRequest.approve(1, STAFF, "根拠", NOW);
        transportRequests.update(quotingRequest);
        repository.save(calculated(quoting, 1));

        assertThat(repository.findLatestOfQuotedRequests())
                .filteredOn(summary -> summary.number().equals(new TransportRequestNumber(2082, 15))
                        || summary.number().equals(new TransportRequestNumber(2082, 16)))
                .containsExactly(new QuotedRequestSummary(
                        new TransportRequestNumber(2082, 15),
                        2,
                        QuotationStatus.PENDING_APPROVAL,
                        QuotationFixture.EXPIRES_AT));
    }
}
