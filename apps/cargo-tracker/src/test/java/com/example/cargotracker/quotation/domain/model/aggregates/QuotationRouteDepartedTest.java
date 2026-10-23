package com.example.cargotracker.quotation.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.quotation.domain.model.valueobjects.AssignedRoute;
import com.example.cargotracker.quotation.domain.model.valueobjects.AssignedRouteLeg;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationId;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 割り当てた経路の最初の区間が出発した見積り（US-07 AC5、D-88。Bolt 37e4 の状態の組み合わせの行 1〜7）。荷主の承認と本予約の照会は、
 * 最初の区間の出発予定が判定時刻と同時刻か前なら出発済みで拒む（経路設計の確定の {@code ALREADY_DEPARTED} と同じ境界）。再見積りを
 * 受け付けるかどうかは変えず、承認済みの期限内の拒否の理由だけを出発済みにする。再設計要の印もあれば再設計要を返す。
 */
class QuotationRouteDepartedTest {

    /** 最初の区間の出発予定（有効期限 2099-10-08T09:00Z より前）。 */
    private static final UtcInstant DEPARTURE = at("2099-09-01T00:00:00Z");

    private static final String CASE = "RC-2026-0001";

    private final TransportRequestId transportRequestId = new TransportRequestId(UUID.randomUUID());
    private final UserId salesRep = new UserId(UUID.randomUUID());
    private final UserId shipperUser = new UserId(UUID.randomUUID());

    @Test
    void 最初の区間の出発の後は荷主が承認できない() {
        // 行 1
        assertThat(awaitingApproval().approvalRejectionAt(plus(DEPARTURE, Duration.ofHours(1))))
                .contains(QuotationRejection.ROUTE_ALREADY_DEPARTED);
    }

    @Test
    void 最初の区間の出発と同時刻は出発済みとして荷主が承認できない() {
        // 行 2（境界。経路設計の ALREADY_DEPARTED と同じ）
        assertThat(awaitingApproval().approvalRejectionAt(DEPARTURE))
                .contains(QuotationRejection.ROUTE_ALREADY_DEPARTED);
    }

    @Test
    void 最初の区間の出発の前なら荷主は承認できる() {
        // 行 3
        assertThat(awaitingApproval().approvalRejectionAt(plus(DEPARTURE, Duration.ofSeconds(-1))))
                .isEmpty();
    }

    @Test
    void 最初の区間の出発と同時刻か後のcommit時刻では本予約に使えず前なら使える() {
        // 行 4
        Quotation approved = approved();

        assertThat(approved.bookingRejectionAt(DEPARTURE)).contains(QuotationRejection.ROUTE_ALREADY_DEPARTED);
        assertThat(approved.bookingRejectionAt(plus(DEPARTURE, Duration.ofSeconds(-1))))
                .isEmpty();
    }

    @Test
    void 再設計要の印もあれば再設計要を返す() {
        // 行 5（再設計要を先に見る。Bolt 37e3 の文言と見本を変えない）
        UtcInstant departed = plus(DEPARTURE, Duration.ofHours(1));

        assertThat(marked(awaitingApproval()).approvalRejectionAt(departed))
                .contains(QuotationRejection.ROUTE_REDESIGN_REQUIRED);
        assertThat(marked(approved()).bookingRejectionAt(departed))
                .contains(QuotationRejection.ROUTE_REDESIGN_REQUIRED);
    }

    @Test
    void 出発済みの承認済みは有効期限の後に再見積りできる() {
        // 行 6（D-88。再見積りの引き継ぎはそのまま）
        assertThat(approved().requoteRejectionAt(QuotationFixture.EXPIRES_AT)).isEmpty();
    }

    @Test
    void 出発済みの承認済みの期限内の再見積りは出発済みの理由で拒む() {
        // 行 7（受け付けるかどうかは変えない。理由だけを出発済みにする。開始準備の決定 (4)）
        assertThat(approved().requoteRejectionAt(plus(DEPARTURE, Duration.ofHours(1))))
                .contains(QuotationRejection.ROUTE_ALREADY_DEPARTED);
        assertThat(approved().requoteRejectionAt(plus(DEPARTURE, Duration.ofSeconds(-1))))
                .contains(QuotationRejection.APPROVED_AND_STILL_VALID);
    }

    private Quotation awaitingApproval() {
        Quotation quotation = Quotation.create(new QuotationId(UUID.randomUUID()), transportRequestId, 1, 1);
        quotation.calculate(QuotationFixture.completeInput(), at("2026-10-05T04:00:00Z"));
        quotation.presentInternally(salesRep, at("2026-10-05T04:30:00Z"));
        quotation.requestRouteDesign(shipperUser, at("2026-10-06T02:00:00Z"));
        quotation.assignRoute(route(), at("2026-10-08T02:30:00Z"));
        quotation.clearDomainEvents();
        return quotation;
    }

    private Quotation approved() {
        Quotation quotation = awaitingApproval();
        quotation.approveByShipper(shipperUser, at("2026-10-08T03:00:00Z"));
        quotation.clearDomainEvents();
        return quotation;
    }

    private static Quotation marked(Quotation quotation) {
        quotation.markRouteRedesignRequired(CASE, 1, at("2026-10-08T04:00:00Z"));
        quotation.clearDomainEvents();
        return quotation;
    }

    private static AssignedRoute route() {
        return new AssignedRoute(
                CASE,
                1,
                at("2026-10-08T02:00:00Z"),
                List.of(
                        new AssignedRouteLeg(
                                "V-101",
                                new Location("JPTYO"),
                                new Location("SGSIN"),
                                DEPARTURE,
                                at("2099-09-11T00:00:00Z")),
                        new AssignedRouteLeg(
                                "V-201",
                                new Location("SGSIN"),
                                new Location("NLRTM"),
                                at("2099-09-11T12:00:00Z"),
                                at("2099-09-30T00:00:00Z"))));
    }

    private static UtcInstant plus(UtcInstant instant, Duration duration) {
        return new UtcInstant(instant.instant().plus(duration));
    }

    private static UtcInstant at(String instant) {
        return new UtcInstant(Instant.parse(instant));
    }
}
