package com.example.cargotracker.quotation.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.quotation.acceptance.InMemoryQuotationRepository;
import com.example.cargotracker.quotation.domain.model.aggregates.ConcurrentQuotationUpdateException;
import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.valueobjects.AssignedRoute;
import com.example.cargotracker.quotation.domain.model.valueobjects.AssignedRouteLeg;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationId;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationInput;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 再設計要の伝達の入力ポート（US-07 AC5、Q-INV-10。Bolt 37e）。同じ経路版を割り当てた見積りすべてに印を付け、照会し直して増えた見積りにも
 * 付ける。状態の組み合わせの表の行 15〜17 と、再見積りとの届く順の C・E。メモリ上のリポジトリの照会と読み込みの直後に、再見積りや別の
 * 更新を挟んで順を起こす。
 */
class RouteRedesignServiceTest {

    private static final String CASE = "RC-2026-0001";
    private static final UtcInstant EXPIRES = QuotationFixture.EXPIRES_AT;
    private static final UtcInstant REDESIGN_AT = at("2026-10-08T07:00:00Z");

    private final TransportRequestId transportRequestId = new TransportRequestId(UUID.randomUUID());
    private final UserId salesRep = new UserId(UUID.randomUUID());
    private final UserId shipperUser = new UserId(UUID.randomUUID());

    @Test
    void 再見積りの旧版と新しい見積りの両方に付ける() {
        // 行 15
        InMemoryQuotationRepository repository = new InMemoryQuotationRepository();
        Quotation[] pair = requoted(repository);

        RouteRedesignMarking marking = new RouteRedesignService(repository).markRedesignRequired(CASE, 1, REDESIGN_AT);

        assertThat(marking).isEqualTo(new RouteRedesignMarking(2, 2));
        assertMarked(repository, pair[0].id());
        assertMarked(repository, pair[1].id());
    }

    @Test
    void 片方だけ付いていれば新しく付けた数は1() {
        // 行 16
        InMemoryQuotationRepository repository = new InMemoryQuotationRepository();
        Quotation[] pair = requoted(repository);
        Quotation old = repository.findById(pair[0].id()).orElseThrow();
        old.markRouteRedesignRequired(CASE, 1, at("2026-10-08T06:00:00Z"));
        repository.update(old);

        RouteRedesignMarking marking = new RouteRedesignService(repository).markRedesignRequired(CASE, 1, REDESIGN_AT);

        assertThat(marking).isEqualTo(new RouteRedesignMarking(2, 1));
        assertThat(repository
                        .findById(pair[0].id())
                        .orElseThrow()
                        .assignedRoute()
                        .orElseThrow()
                        .redesignRequiredAt())
                .as("最初の時刻のまま")
                .isEqualTo(at("2026-10-08T06:00:00Z"));
    }

    @Test
    void その経路版を割り当てた見積りがなければ何もしない() {
        // 行 17
        InMemoryQuotationRepository repository = new InMemoryQuotationRepository();
        Quotation approved = approved();
        repository.save(approved);

        assertThat(new RouteRedesignService(repository).markRedesignRequired(CASE, 2, REDESIGN_AT))
                .isEqualTo(new RouteRedesignMarking(0, 0));
        assertThat(repository
                        .findById(approved.id())
                        .orElseThrow()
                        .assignedRoute()
                        .orElseThrow()
                        .redesignRequired())
                .isFalse();
    }

    @Test
    void 照会の後に再見積りが終わっても照会し直して新しい見積りにも付ける() {
        // 届く順 C: 照会は再見積りの前（旧版だけが当たる）、旧版を読むのは再見積りの後
        Quotation approved = approved();
        Quotation next = calculatedNext();
        InMemoryQuotationRepository repository = new InMemoryQuotationRepository() {
            private boolean requoted;

            @Override
            public synchronized List<QuotationId> findIdsByAssignedRoute(String routingCaseNumber, int routeVersionNo) {
                List<QuotationId> ids = super.findIdsByAssignedRoute(routingCaseNumber, routeVersionNo);
                if (!requoted) {
                    requoted = true;
                    Quotation old = super.findById(approved.id()).orElseThrow();
                    old.requoteInto(next, EXPIRES);
                    super.update(old);
                    super.save(next);
                }
                return ids;
            }
        };
        repository.save(approved);

        RouteRedesignMarking marking = new RouteRedesignService(repository).markRedesignRequired(CASE, 1, REDESIGN_AT);

        assertThat(marking).isEqualTo(new RouteRedesignMarking(2, 2));
        assertMarked(repository, approved.id());
        assertMarked(repository, next.id());
    }

    @Test
    void 読み込んだ後に見積りが更新されていれば競合の例外にする() {
        // 届く順 E: 印の操作が旧版を読んだ後、再見積りなどが旧版を先に保存する（listener が戻して再配信でやり直す）
        Quotation approved = approved();
        InMemoryQuotationRepository repository = new InMemoryQuotationRepository() {
            @Override
            public synchronized Optional<Quotation> findById(QuotationId id) {
                Optional<Quotation> loaded = super.findById(id);
                super.findById(id).ifPresent(super::update);
                return loaded;
            }
        };
        repository.save(approved);
        RouteRedesignService service = new RouteRedesignService(repository);

        assertThatThrownBy(() -> service.markRedesignRequired(CASE, 1, REDESIGN_AT))
                .isInstanceOf(ConcurrentQuotationUpdateException.class);
    }

    private static void assertMarked(InMemoryQuotationRepository repository, QuotationId id) {
        assertThat(repository
                        .findById(id)
                        .orElseThrow()
                        .assignedRoute()
                        .orElseThrow()
                        .redesignRequiredAt())
                .isEqualTo(REDESIGN_AT);
    }

    /** 承認の後に失効した見積り 1 を再見積りし、旧版（失効）と新しい見積り 2（経路を引き継いだ承認待ち）を保存する。 */
    private Quotation[] requoted(InMemoryQuotationRepository repository) {
        Quotation approved = approved();
        repository.save(approved);
        Quotation old = repository.findById(approved.id()).orElseThrow();
        Quotation next = calculatedNext();
        old.requoteInto(next, EXPIRES);
        repository.update(old);
        repository.save(next);
        return new Quotation[] {old, next};
    }

    private Quotation approved() {
        Quotation quotation = Quotation.create(new QuotationId(UUID.randomUUID()), transportRequestId, 1, 1);
        quotation.calculate(QuotationFixture.completeInput(), at("2026-10-05T04:00:00Z"));
        quotation.presentInternally(salesRep, at("2026-10-05T04:30:00Z"));
        quotation.requestRouteDesign(shipperUser, at("2026-10-06T02:00:00Z"));
        quotation.assignRoute(route(), at("2026-10-08T02:30:00Z"));
        quotation.approveByShipper(shipperUser, at("2026-10-08T03:00:00Z"));
        quotation.clearDomainEvents();
        return quotation;
    }

    /** 旧版の有効期限（EXPIRES）より後を有効期限にした、輸送要求の版 1 の承認待ちの見積り 2。 */
    private Quotation calculatedNext() {
        QuotationInput input = QuotationFixture.completeInput();
        Quotation next = Quotation.create(new QuotationId(UUID.randomUUID()), transportRequestId, 1, 2);
        next.calculate(
                new QuotationInput(
                        input.lines(),
                        input.currency(),
                        at("2099-12-01T00:00:00Z"),
                        input.via(),
                        input.departureAt(),
                        input.arrivalAt()),
                at("2026-10-05T04:00:00Z"));
        return next;
    }

    private static AssignedRoute route() {
        return new AssignedRoute(
                CASE,
                1,
                at("2026-10-08T02:00:00Z"),
                List.of(new AssignedRouteLeg(
                        "V-381",
                        new Location("JPTYO"),
                        new Location("NLRTM"),
                        at("2099-10-12T03:00:00Z"),
                        at("2099-10-30T09:00:00Z"))));
    }

    private static UtcInstant at(String instant) {
        return new UtcInstant(Instant.parse(instant));
    }
}
