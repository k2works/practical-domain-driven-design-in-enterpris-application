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
import com.example.cargotracker.quotation.domain.model.valueobjects.AssignedRoute;
import com.example.cargotracker.quotation.domain.model.valueobjects.AssignedRouteLeg;
import com.example.cargotracker.quotation.domain.model.valueobjects.AwaitingBookingSummary;
import com.example.cargotracker.quotation.domain.model.valueobjects.Currency;
import com.example.cargotracker.quotation.domain.model.valueobjects.PricingLine;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationId;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationInput;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationStatus;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotedRequestSummary;
import com.example.cargotracker.quotation.domain.model.valueobjects.RoutingRequestedSummary;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipperApproval;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipperLatestQuotationSummary;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestStatus;
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
        "PENDING_APPROVAL, , 100.00, USD, ck_quotation_calculated", // 承認待ちなのに有効期限がない
        "PENDING_APPROVAL, 2082-01-08T09:00:00Z, , USD, ck_quotation_calculated", // 承認待ちなのに合計がない
        "PENDING_APPROVAL, 2082-01-08T09:00:00Z, 100.00, GBP, ck_quotation_currency", // 通貨が候補にない
        "BOOKED, 2082-01-08T09:00:00Z, 100.00, USD, ck_quotation_status" // 見積りの状態にない値（荷主の承認済みは Bolt 20 で足した）
    })
    void 承認待ち以後の必須の列と通貨と状態はCHECK制約で守る(
            String status, String expiresAt, String totalAmount, String currency, String constraint) {
        TransportRequestId transportRequestId = transportRequest(7);
        String insert = "INSERT INTO quotation.quotation (id, transport_request_id, quotation_no,"
                + " transport_request_version_no, status, expires_at, total_amount, currency, route_policy_via,"
                + " route_policy_departure_at, route_policy_arrival_at, version)"
                + " VALUES (?, ?, 1, 1, ?, CAST(? AS TIMESTAMP WITH TIME ZONE), CAST(? AS NUMERIC(15,2)), ?, '',"
                + " TIMESTAMP WITH TIME ZONE '2082-01-10 00:00:00+00', TIMESTAMP WITH TIME ZONE '2082-01-30 00:00:00+00', 0)";
        UUID id = UUID.randomUUID();
        UUID requestId = transportRequestId.value();

        assertThatThrownBy(() -> jdbc.update(insert, id, requestId, status, expiresAt, totalAmount, currency))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(constraint);
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
        old.requoteInto(replacement, at);
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

    // 荷主の回答（US-24 AC1、Q-INV-09・18。Bolt 12）

    private static final UserId RESPONDENT = new UserId(UUID.randomUUID());

    private Quotation routingRequested(TransportRequestId transportRequestId) {
        repository.save(presented(transportRequestId, 1));
        Quotation quotation =
                repository.findByTransportRequestIdAndNo(transportRequestId, 1).orElseThrow();
        quotation.requestRouteDesign(RESPONDENT, APPROVED_AT);
        repository.update(quotation);
        return quotation;
    }

    @Test
    void 詳細設計依頼済みの見積りは回答者と回答時刻とともに保存し読み出せる() {
        TransportRequestId transportRequestId = transportRequest(17);

        routingRequested(transportRequestId);

        assertThat(repository.findByTransportRequestIdAndNo(transportRequestId, 1))
                .hasValueSatisfying(found -> {
                    assertThat(found.status()).isEqualTo(QuotationStatus.ROUTING_REQUESTED);
                    assertThat(found.respondedBy()).contains(RESPONDENT);
                    assertThat(found.respondedAt()).contains(APPROVED_AT);
                });
        assertThat(jdbc.queryForObject(
                        "SELECT shipper_response FROM quotation.quotation WHERE transport_request_id = ?",
                        String.class,
                        transportRequestId.value()))
                .isEqualTo("PROCEED");
    }

    @Test
    void 詳細設計依頼済みの見積りも1つだけの数に入り部分一意インデックスがドメインの例外にする() {
        TransportRequestId transportRequestId = transportRequest(18);
        routingRequested(transportRequestId);
        Quotation second = calculated(transportRequestId, 2);

        assertThatThrownBy(() -> repository.save(second)).isInstanceOf(DuplicateQuotationException.class);
    }

    /** PostgreSQL は制約違反でトランザクションを中断するため、違反ごとに別のテストにする。 */
    @ParameterizedTest
    @CsvSource({
        "ROUTING_REQUESTED, , ck_quotation_responded", // 詳細設計依頼済みなのに回答がない
        "PRESENTED, PROCEED_ONLY, ck_quotation_responded", // 回答の列がそろっていない
        "ROUTING_REQUESTED, DECLINED, ck_quotation_shipper_response" // 回答の値は進むだけ（辞退は AC2）
    })
    void 回答の列はそろって値を持ち詳細設計依頼済みなら必須で回答の値をCHECK制約で守る(String status, String response, String constraint) {
        TransportRequestId transportRequestId = transportRequest(19);
        Quotation target = presented(transportRequestId, 1);
        repository.save(target);
        boolean complete = "DECLINED".equals(response);
        String shipperResponse = response == null ? null : complete ? "DECLINED" : "PROCEED";
        UUID respondedBy = complete ? RESPONDENT.value() : null;
        String update = "UPDATE quotation.quotation SET status = ?, shipper_response = ?, responded_by = ?,"
                + " responded_at = CASE WHEN ? THEN presented_at END WHERE id = ?";
        UUID targetId = target.id().value();

        assertThatThrownBy(() -> jdbc.update(update, status, shipperResponse, respondedBy, complete, targetId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(constraint);
    }

    @Test
    void 回答と再見積りが同じ版を読んで更新すると後の更新は競合で失敗する() {
        TransportRequestId transportRequestId = transportRequest(20);
        repository.save(presented(transportRequestId, 1));
        Quotation forShipper =
                repository.findByTransportRequestIdAndNo(transportRequestId, 1).orElseThrow();
        Quotation forStaff =
                repository.findByTransportRequestIdAndNo(transportRequestId, 1).orElseThrow();
        forShipper.requestRouteDesign(RESPONDENT, APPROVED_AT);
        repository.update(forShipper);
        forStaff.replaceWith(new QuotationId(UUID.randomUUID()), APPROVED_AT);

        assertThatThrownBy(() -> repository.update(forStaff)).isInstanceOf(ConcurrentQuotationUpdateException.class);
        assertThat(repository
                        .findByTransportRequestIdAndNo(transportRequestId, 1)
                        .orElseThrow()
                        .status())
                .isEqualTo(QuotationStatus.ROUTING_REQUESTED);
    }

    @Test
    void 経路設計中の見積依頼の詳細設計依頼済みの見積りを依頼時刻の古い順に返し輸送要求は経路設計中で保存できる() {
        TransportRequestId later = transportRequest(21);
        TransportRequestId earlier = transportRequest(22);
        TransportRequestId notYetRouting = transportRequest(23);
        for (TransportRequestId id : List.of(later, earlier, notYetRouting)) {
            TransportRequest request = transportRequests.findById(id).orElseThrow();
            request.approve(1, STAFF, "根拠", NOW);
            request.markQuotationPresented(1);
            transportRequests.update(request);
            repository.save(presented(id, 1));
        }
        UtcInstant laterAt = new UtcInstant(Instant.parse("2082-01-06T02:00:00Z"));
        UtcInstant earlierAt = new UtcInstant(Instant.parse("2082-01-06T01:00:00Z"));
        respond(later, laterAt);
        respond(earlier, earlierAt);
        respond(notYetRouting, earlierAt);
        for (TransportRequestId id : List.of(later, earlier)) {
            TransportRequest request = transportRequests.findById(id).orElseThrow();
            request.markRoutingRequested(1);
            transportRequests.update(request);
        }

        assertThat(transportRequests.findById(earlier).orElseThrow().status())
                .isEqualTo(TransportRequestStatus.ROUTING);
        assertThat(repository.findRoutingRequestedSummaries())
                .filteredOn(summary -> summary.number().year() == 2082)
                .containsExactly(
                        new RoutingRequestedSummary(
                                new TransportRequestNumber(2082, 22),
                                1,
                                QuotationStatus.ROUTING_REQUESTED,
                                earlierAt,
                                QuotationFixture.EXPIRES_AT,
                                null),
                        new RoutingRequestedSummary(
                                new TransportRequestNumber(2082, 21),
                                1,
                                QuotationStatus.ROUTING_REQUESTED,
                                laterAt,
                                QuotationFixture.EXPIRES_AT,
                                null));
    }

    private void respond(TransportRequestId transportRequestId, UtcInstant at) {
        Quotation quotation =
                repository.findByTransportRequestIdAndNo(transportRequestId, 1).orElseThrow();
        quotation.requestRouteDesign(RESPONDENT, at);
        repository.update(quotation);
    }

    // 経路版の割当てと荷主の承認（Bolt 20、R-INV-11、Q-INV-10）

    private static final UserId SHIPPER_USER = new UserId(UUID.randomUUID());
    private static final UtcInstant CONFIRMED_AT = new UtcInstant(Instant.parse("2082-01-07T05:00:00Z"));
    private static final AssignedRoute ROUTE = new AssignedRoute(
            "RC-2082-0001",
            1,
            CONFIRMED_AT,
            List.of(
                    new AssignedRouteLeg(
                            "V-201",
                            new Location("JPTYO"),
                            new Location("SGSIN"),
                            new UtcInstant(Instant.parse("2099-10-10T00:00:00Z")),
                            new UtcInstant(Instant.parse("2099-10-20T00:00:00Z"))),
                    new AssignedRouteLeg(
                            "V-301",
                            new Location("SGSIN"),
                            new Location("NLRTM"),
                            new UtcInstant(Instant.parse("2099-10-20T12:00:00Z")),
                            new UtcInstant(Instant.parse("2099-10-31T00:00:00Z")))));

    private Quotation awaitingApproval(TransportRequestId transportRequestId) {
        routingRequested(transportRequestId);
        Quotation quotation =
                repository.findByTransportRequestIdAndNo(transportRequestId, 1).orElseThrow();
        quotation.assignRoute(ROUTE, CONFIRMED_AT);
        repository.update(quotation);
        return quotation;
    }

    @Test
    void 荷主承認待ちの見積りは割り当てた経路と区間とともに保存しIDでも読み出せる() {
        TransportRequestId transportRequestId = transportRequest(24);
        Quotation quotation = awaitingApproval(transportRequestId);

        assertThat(repository.findById(quotation.id())).hasValueSatisfying(found -> {
            assertThat(found.status()).isEqualTo(QuotationStatus.AWAITING_SHIPPER_APPROVAL);
            assertThat(found.assignedRoute()).contains(ROUTE);
            assertThat(found.shipperApproval()).isEmpty();
        });
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM quotation.assigned_route_leg WHERE quotation_id = ?",
                        Integer.class,
                        quotation.id().value()))
                .isEqualTo(2);
    }

    @Test
    void 承認済みの見積りは承認者と承認時刻とともに保存し区間は重ねて書かない() {
        TransportRequestId transportRequestId = transportRequest(25);
        awaitingApproval(transportRequestId);
        Quotation quotation =
                repository.findByTransportRequestIdAndNo(transportRequestId, 1).orElseThrow();
        UtcInstant approvedAt = new UtcInstant(Instant.parse("2082-01-07T06:00:00Z"));
        quotation.approveByShipper(SHIPPER_USER, approvedAt);

        repository.update(quotation);

        assertThat(repository.findById(quotation.id())).hasValueSatisfying(found -> {
            assertThat(found.status()).isEqualTo(QuotationStatus.APPROVED);
            assertThat(found.shipperApproval()).contains(new ShipperApproval(SHIPPER_USER, approvedAt));
            assertThat(found.assignedRoute()).contains(ROUTE);
        });
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM quotation.assigned_route_leg WHERE quotation_id = ?",
                        Integer.class,
                        quotation.id().value()))
                .isEqualTo(2);
    }

    @Test
    void 荷主承認待ちと承認済みの見積りも1つだけの数に入る() {
        TransportRequestId transportRequestId = transportRequest(26);
        awaitingApproval(transportRequestId);
        Quotation second = calculated(transportRequestId, 2);

        assertThatThrownBy(() -> repository.save(second)).isInstanceOf(DuplicateQuotationException.class);
    }

    @Test
    void ない見積りのIDでは見つからない() {
        assertThat(repository.findById(new QuotationId(UUID.randomUUID()))).isEmpty();
    }

    /** PostgreSQL は制約違反でトランザクションを中断するため、違反ごとに別のテストにする。 */
    @ParameterizedTest
    @CsvSource({
        "AWAITING_SHIPPER_APPROVAL, , , ck_quotation_route_assigned", // 荷主承認待ちなのに経路版がない
        "ROUTING_REQUESTED, RC-2082-0001, , ck_quotation_route_assigned", // 経路版の列がそろっていない
        "APPROVED, RC-2082-0001, 1, ck_quotation_shipper_approved" // 承認済みなのに荷主承認がない
    })
    void 経路版の参照と荷主承認の列はそろって値を持ち状態に応じて必須をCHECK制約で守る(
            String status, String caseNumber, Integer versionNo, String constraint) {
        TransportRequestId transportRequestId = transportRequest(27);
        Quotation target = routingRequested(transportRequestId);
        String update = "UPDATE quotation.quotation SET status = ?, routing_case_number = ?, route_version_no = ?,"
                + " route_confirmed_at = CASE WHEN ? THEN responded_at END WHERE id = ?";
        boolean confirmedAt = versionNo != null;
        UUID targetId = target.id().value();

        assertThatThrownBy(() -> jdbc.update(update, status, caseNumber, versionNo, confirmedAt, targetId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(constraint);
    }

    @Test
    void 輸送要求は荷主承認待ちと予約待ちで保存できる() {
        TransportRequestId transportRequestId = transportRequest(28);
        TransportRequest request =
                transportRequests.findById(transportRequestId).orElseThrow();
        request.approve(1, STAFF, "根拠", NOW);
        request.markRoutingRequested(1);
        request.markAwaitingApproval(1);
        transportRequests.update(request);
        assertThat(transportRequests.findById(transportRequestId).orElseThrow().status())
                .isEqualTo(TransportRequestStatus.AWAITING_APPROVAL);

        TransportRequest awaiting =
                transportRequests.findById(transportRequestId).orElseThrow();
        awaiting.markReadyToBook(1);
        transportRequests.update(awaiting);

        assertThat(transportRequests.findById(transportRequestId).orElseThrow().status())
                .isEqualTo(TransportRequestStatus.READY_TO_BOOK);
    }

    @Test
    void 経路設計中の表には荷主承認待ちの見積依頼も状態とともに返し荷主が承認した見積りは予約の確定待ちに移す() {
        TransportRequestId awaiting = transportRequest(29);
        TransportRequestId approved = transportRequest(30);
        for (TransportRequestId id : List.of(awaiting, approved)) {
            TransportRequest request = transportRequests.findById(id).orElseThrow();
            request.approve(1, STAFF, "根拠", NOW);
            request.markRoutingRequested(1);
            request.markAwaitingApproval(1);
            transportRequests.update(request);
            awaitingApproval(id);
        }
        Quotation toApprove =
                repository.findByTransportRequestIdAndNo(approved, 1).orElseThrow();
        toApprove.approveByShipper(SHIPPER_USER, CONFIRMED_AT);
        repository.update(toApprove);
        TransportRequest ready = transportRequests.findById(approved).orElseThrow();
        ready.markReadyToBook(1);
        transportRequests.update(ready);

        assertThat(repository.findRoutingRequestedSummaries())
                .filteredOn(summary -> summary.number().year() == 2082)
                .extracting(summary -> summary.number().sequence() + ":" + summary.status())
                .containsExactly("29:AWAITING_SHIPPER_APPROVAL");
        assertThat(repository.findAwaitingBookingSummaries())
                .filteredOn(summary -> summary.number().year() == 2082)
                .extracting(summary -> summary.number().sequence())
                .contains(30);
    }

    // 受付一覧（S-02）の予約の確定待ちの表（US-04、Bolt 20 レビュー D-78。Bolt 23b）

    private static final UtcInstant SHIPPER_APPROVED_AT = new UtcInstant(Instant.parse("2082-01-07T06:00:00Z"));

    private void approved(TransportRequestId transportRequestId) {
        awaitingApproval(transportRequestId);
        Quotation quotation =
                repository.findByTransportRequestIdAndNo(transportRequestId, 1).orElseThrow();
        quotation.approveByShipper(SHIPPER_USER, SHIPPER_APPROVED_AT);
        repository.update(quotation);
    }

    private void advanceTransportRequest(TransportRequestId transportRequestId, TransportRequestStatus target) {
        TransportRequest request =
                transportRequests.findById(transportRequestId).orElseThrow();
        request.approve(1, STAFF, "根拠", NOW);
        request.markRoutingRequested(1);
        if (target != TransportRequestStatus.ROUTING) {
            request.markAwaitingApproval(1);
        }
        if (target == TransportRequestStatus.READY_TO_BOOK) {
            request.markReadyToBook(1);
        } else if (target == TransportRequestStatus.BOOKED) {
            request.markBooked(1);
        }
        transportRequests.update(request);
    }

    @Test
    void 予約の確定待ちは荷主が承認した見積りを有効期限の近い順に並べ予約確定済みの輸送要求を出さない() {
        TransportRequestId notYetDelivered = transportRequest(31);
        approved(notYetDelivered);
        advanceTransportRequest(notYetDelivered, TransportRequestStatus.ROUTING);
        TransportRequestId readyToBook = transportRequest(32);
        approved(readyToBook);
        advanceTransportRequest(readyToBook, TransportRequestStatus.READY_TO_BOOK);
        UtcInstant sooner = new UtcInstant(Instant.parse("2099-10-01T00:00:00Z"));
        jdbc.update(
                "UPDATE quotation.quotation SET expires_at = ? WHERE transport_request_id = ?",
                java.sql.Timestamp.from(sooner.instant()),
                readyToBook.value());
        TransportRequestId booked = transportRequest(33);
        approved(booked);
        advanceTransportRequest(booked, TransportRequestStatus.BOOKED);
        TransportRequestId awaiting = transportRequest(34);
        awaitingApproval(awaiting);
        advanceTransportRequest(awaiting, TransportRequestStatus.AWAITING_APPROVAL);

        assertThat(repository.findAwaitingBookingSummaries())
                .as("DE-21・DE-04 の配信を待たずに見積りの承認済みを正にする（輸送要求が経路設計中のままでも出す）。有効期限の近い順")
                .filteredOn(summary -> summary.number().year() == 2082)
                .containsExactly(
                        new AwaitingBookingSummary(
                                new TransportRequestNumber(2082, 32),
                                1,
                                QuotationStatus.APPROVED,
                                "RC-2082-0001",
                                1,
                                SHIPPER_APPROVED_AT,
                                sooner),
                        new AwaitingBookingSummary(
                                new TransportRequestNumber(2082, 31),
                                1,
                                QuotationStatus.APPROVED,
                                "RC-2082-0001",
                                1,
                                SHIPPER_APPROVED_AT,
                                QuotationFixture.EXPIRES_AT));
        assertThat(repository.findRoutingRequestedSummaries())
                .as("承認済みは予約の確定待ちの表に移し、荷主承認待ちは経路の確定の時刻を持つ")
                .filteredOn(summary ->
                        summary.number().year() == 2082 && summary.number().sequence() >= 31)
                .extracting(RoutingRequestedSummary::number, RoutingRequestedSummary::routeConfirmedAt)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(new TransportRequestNumber(2082, 34), CONFIRMED_AT));
    }

    // 荷主の承認の後に失効した見積りの再見積り（D-85。Bolt 35b）

    @Test
    void 承認済みで失効した見積りの再見積りは旧版を失効にし新しい見積りは引き継いだ経路と区間を保存して提示と承認ができる() {
        TransportRequestId transportRequestId = transportRequest(35);
        approved(transportRequestId);
        Quotation old =
                repository.findByTransportRequestIdAndNo(transportRequestId, 1).orElseThrow();
        UtcInstant expired = QuotationFixture.EXPIRES_AT;
        Quotation replacement = calculatedAfterExpiry(transportRequestId, 2);

        requote(old, replacement, expired);
        jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE");

        assertThat(repository.findByTransportRequestIdAndNo(transportRequestId, 1))
                .hasValueSatisfying(found -> {
                    assertThat(found.status()).isEqualTo(QuotationStatus.EXPIRED);
                    assertThat(found.replacedBy()).isEmpty();
                    assertThat(found.shipperApproval())
                            .contains(new ShipperApproval(SHIPPER_USER, SHIPPER_APPROVED_AT));
                });
        Quotation saved = repository.findById(replacement.id()).orElseThrow();
        assertThat(saved.status()).isEqualTo(QuotationStatus.PENDING_APPROVAL);
        assertThat(saved.assignedRoute()).contains(ROUTE);
        assertThat(saved.respondedBy()).contains(RESPONDENT);
        assertThat(saved.respondedAt()).contains(APPROVED_AT);
        assertThat(legs(replacement.id())).as("新しい見積りの ID で区間の写しを持つ").isEqualTo(2);
        assertThat(legs(old.id())).as("旧版の区間は残る").isEqualTo(2);

        saved.presentInternally(STAFF, expired);
        repository.update(saved);
        Quotation presented = repository.findById(replacement.id()).orElseThrow();
        assertThat(presented.status()).isEqualTo(QuotationStatus.AWAITING_SHIPPER_APPROVAL);
        presented.approveByShipper(SHIPPER_USER, expired);
        repository.update(presented);

        assertThat(repository.findById(replacement.id())).hasValueSatisfying(found -> {
            assertThat(found.status()).isEqualTo(QuotationStatus.APPROVED);
            assertThat(found.assignedRoute()).contains(ROUTE);
        });
        assertThat(legs(replacement.id())).as("区間は重ねて書かない").isEqualTo(2);
    }

    @Test
    void 経路を引き継いだ見積りを提示の前にもう一度再見積りすると次の見積りも経路と区間を保存する() {
        TransportRequestId transportRequestId = transportRequest(36);
        approved(transportRequestId);
        UtcInstant expired = QuotationFixture.EXPIRES_AT;
        Quotation second = calculatedAfterExpiry(transportRequestId, 2);
        requote(repository.findByTransportRequestIdAndNo(transportRequestId, 1).orElseThrow(), second, expired);
        Quotation third = calculatedAfterExpiry(transportRequestId, 3);

        requote(repository.findById(second.id()).orElseThrow(), third, expired);
        jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE");

        assertThat(repository.findById(second.id())).hasValueSatisfying(found -> {
            assertThat(found.status()).isEqualTo(QuotationStatus.REPLACED);
            assertThat(found.replacedBy()).contains(third.id());
        });
        assertThat(repository.findById(third.id())).hasValueSatisfying(found -> {
            assertThat(found.status()).isEqualTo(QuotationStatus.PENDING_APPROVAL);
            assertThat(found.assignedRoute()).contains(ROUTE);
            assertThat(found.respondedBy()).contains(RESPONDENT);
        });
        assertThat(legs(third.id())).isEqualTo(2);
    }

    /** 旧版の有効期限の時刻に、その 1 日後を有効期限として算出した見積り。 */
    private Quotation calculatedAfterExpiry(TransportRequestId transportRequestId, int quotationNo) {
        UtcInstant expired = QuotationFixture.EXPIRES_AT;
        Quotation quotation = Quotation.create(new QuotationId(UUID.randomUUID()), transportRequestId, 1, quotationNo);
        quotation.calculate(
                new QuotationInput(
                        QuotationFixture.completeInput().lines(),
                        QuotationFixture.completeInput().currency(),
                        new UtcInstant(expired.instant().plusSeconds(86_400)),
                        List.of(),
                        new UtcInstant(expired.instant().plusSeconds(172_800)),
                        new UtcInstant(expired.instant().plusSeconds(259_200))),
                expired);
        return quotation;
    }

    @Test
    void 予約の確定待ちは承認の後に失効した見積りを再見積りした後の経路を持つ承認待ちの見積りを荷主の承認時刻なしで出す() {
        TransportRequestId readyToBook = transportRequest(37);
        approved(readyToBook);
        advanceTransportRequest(readyToBook, TransportRequestStatus.READY_TO_BOOK);
        requote(
                repository.findByTransportRequestIdAndNo(readyToBook, 1).orElseThrow(),
                calculatedAfterExpiry(readyToBook, 2),
                QuotationFixture.EXPIRES_AT);
        TransportRequestId awaitingApproval = transportRequest(38);
        approved(awaitingApproval);
        advanceTransportRequest(awaitingApproval, TransportRequestStatus.AWAITING_APPROVAL);
        requote(
                repository.findByTransportRequestIdAndNo(awaitingApproval, 1).orElseThrow(),
                calculatedAfterExpiry(awaitingApproval, 2),
                QuotationFixture.EXPIRES_AT);
        TransportRequestId withoutRoute = transportRequest(39);
        repository.save(presented(withoutRoute, 1));
        advanceTransportRequest(withoutRoute, TransportRequestStatus.READY_TO_BOOK);
        requote(
                repository.findByTransportRequestIdAndNo(withoutRoute, 1).orElseThrow(),
                calculated(withoutRoute, 2),
                NOW);
        TransportRequestId presented = transportRequest(40);
        approved(presented);
        advanceTransportRequest(presented, TransportRequestStatus.READY_TO_BOOK);
        Quotation next = calculatedAfterExpiry(presented, 2);
        requote(
                repository.findByTransportRequestIdAndNo(presented, 1).orElseThrow(),
                next,
                QuotationFixture.EXPIRES_AT);
        Quotation saved = repository.findById(next.id()).orElseThrow();
        saved.presentInternally(STAFF, QuotationFixture.EXPIRES_AT);
        repository.update(saved);
        UtcInstant nextExpires =
                new UtcInstant(QuotationFixture.EXPIRES_AT.instant().plusSeconds(86_400));

        assertThat(repository.findAwaitingBookingSummaries())
                .as("経路を持つ承認待ち（再見積りの後で提示の前）は、輸送要求が予約待ちでも荷主承認待ちでも出す（D-85。Bolt 35c）。"
                        + "経路を持たない承認待ちと、提示した後の見積り（荷主承認待ち）は出さない")
                .filteredOn(summary ->
                        summary.number().year() == 2082 && summary.number().sequence() >= 37)
                .containsExactlyInAnyOrder(
                        new AwaitingBookingSummary(
                                new TransportRequestNumber(2082, 37),
                                2,
                                QuotationStatus.PENDING_APPROVAL,
                                "RC-2082-0001",
                                1,
                                null,
                                nextExpires),
                        new AwaitingBookingSummary(
                                new TransportRequestNumber(2082, 38),
                                2,
                                QuotationStatus.PENDING_APPROVAL,
                                "RC-2082-0001",
                                1,
                                null,
                                nextExpires));
        assertThat(repository.findRoutingRequestedSummaries())
                .as("提示した見積り 2（荷主承認待ち）は、DE-24 で輸送要求が予約待ちから戻る前でも経路設計中・荷主承認待ちの表に出す")
                .filteredOn(summary ->
                        summary.number().year() == 2082 && summary.number().sequence() >= 37)
                .extracting(
                        RoutingRequestedSummary::number,
                        RoutingRequestedSummary::quotationNo,
                        RoutingRequestedSummary::status)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                        new TransportRequestNumber(2082, 40), 2, QuotationStatus.AWAITING_SHIPPER_APPROVAL));
    }

    @Test
    void 荷主の見積依頼の一覧には見積依頼ごとに荷主に見える最新の見積りを自社の分だけ返す() {
        CompanyId shipper = new CompanyId(UUID.randomUUID());
        TransportRequestId approvedRequest = transportRequest(41, shipper);
        approved(approvedRequest);
        advanceTransportRequest(approvedRequest, TransportRequestStatus.READY_TO_BOOK);
        TransportRequestId notPresented = transportRequest(42, shipper);
        approved(notPresented);
        advanceTransportRequest(notPresented, TransportRequestStatus.READY_TO_BOOK);
        requote(
                repository.findByTransportRequestIdAndNo(notPresented, 1).orElseThrow(),
                calculatedAfterExpiry(notPresented, 2),
                QuotationFixture.EXPIRES_AT);
        TransportRequestId represented = transportRequest(43, shipper);
        approved(represented);
        advanceTransportRequest(represented, TransportRequestStatus.READY_TO_BOOK);
        Quotation next = calculatedAfterExpiry(represented, 2);
        requote(
                repository.findByTransportRequestIdAndNo(represented, 1).orElseThrow(),
                next,
                QuotationFixture.EXPIRES_AT);
        Quotation saved = repository.findById(next.id()).orElseThrow();
        saved.presentInternally(STAFF, QuotationFixture.EXPIRES_AT);
        repository.update(saved);
        TransportRequestId awaiting = transportRequest(44, shipper);
        awaitingApproval(awaiting);
        advanceTransportRequest(awaiting, TransportRequestStatus.AWAITING_APPROVAL);
        TransportRequestId quoting = transportRequest(46, shipper);
        repository.save(calculated(quoting, 1));
        TransportRequestId otherShipper = transportRequest(45);
        approved(otherShipper);
        advanceTransportRequest(otherShipper, TransportRequestStatus.READY_TO_BOOK);
        UtcInstant nextExpires =
                new UtcInstant(QuotationFixture.EXPIRES_AT.instant().plusSeconds(86_400));

        assertThat(repository.findLatestVisibleForShipper(shipper))
                .as("提示の前の見積り 2 は荷主に見えないので数えない。見積りを提示していない見積依頼と他社の見積依頼は出さない。" + "輸送要求の状態では絞らない（選び方は画面。D-85。Bolt 35d）")
                .containsExactly(
                        new ShipperLatestQuotationSummary(
                                new TransportRequestNumber(2082, 41),
                                QuotationStatus.APPROVED,
                                QuotationFixture.EXPIRES_AT,
                                SHIPPER_APPROVED_AT,
                                APPROVED_AT,
                                APPROVED_AT),
                        new ShipperLatestQuotationSummary(
                                new TransportRequestNumber(2082, 42),
                                QuotationStatus.EXPIRED,
                                QuotationFixture.EXPIRES_AT,
                                SHIPPER_APPROVED_AT,
                                APPROVED_AT,
                                APPROVED_AT),
                        new ShipperLatestQuotationSummary(
                                new TransportRequestNumber(2082, 43),
                                QuotationStatus.AWAITING_SHIPPER_APPROVAL,
                                nextExpires,
                                null,
                                QuotationFixture.EXPIRES_AT,
                                APPROVED_AT),
                        new ShipperLatestQuotationSummary(
                                new TransportRequestNumber(2082, 44),
                                QuotationStatus.AWAITING_SHIPPER_APPROVAL,
                                QuotationFixture.EXPIRES_AT,
                                null,
                                APPROVED_AT,
                                APPROVED_AT));
        assertThat(repository.findLatestVisibleForShipper(shipper))
                .as("回答を引き継いだ見積り 2 だけが再承認の見積り（通常の流れは提示と回答が同時刻か回答が後）")
                .extracting(ShipperLatestQuotationSummary::isPresentedForReapproval)
                .containsExactly(false, false, true, false);
    }

    private TransportRequestId transportRequest(int sequence, CompanyId shipper) {
        TransportRequest request = TransportRequest.submit(
                new TransportRequestId(UUID.randomUUID()),
                new TransportRequestNumber(2082, sequence),
                shipper,
                ShipmentTermsFixture.generalCargo(),
                STAFF,
                NOW);
        transportRequests.save(request);
        return request.id();
    }

    private int legs(QuotationId quotationId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM quotation.assigned_route_leg WHERE quotation_id = ?",
                Integer.class,
                quotationId.value());
    }
}
