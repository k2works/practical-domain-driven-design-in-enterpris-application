package com.example.cargotracker.quotation.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.aggregates.QuotationRepository;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.AssignedRoute;
import com.example.cargotracker.quotation.domain.model.valueobjects.AssignedRouteLeg;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationId;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.quotation.interfaces.api.BookableQuotationQuery;
import com.example.cargotracker.quotation.interfaces.api.BookableQuotationRequest;
import com.example.cargotracker.quotation.interfaces.api.BookableQuotationResult;
import com.example.cargotracker.quotation.interfaces.api.RouteRedesignNotification;
import com.example.cargotracker.quotation.interfaces.api.RouteRedesignNotificationReceipt;
import com.example.cargotracker.quotation.interfaces.api.RouteRedesignNotificationRequest;
import com.example.cargotracker.shared.OverlappingTransactions;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.AuthenticatedActorFixture;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 本予約の確定用の照会（見積りの行を押さえる）と再設計要の印を付ける更新が、どちらの順でも直列になることを PostgreSQL で確かめる
 * （本予約の確定との届く順 I・J、状態の組み合わせの表の行 13・14。US-07 AC5、ADR-016。Bolt 37e2）。後の操作が先の接続を待っていることは
 * {@code pg_blocking_pids} で確かめる。テストの間でデータを共有しないよう、クラスに @Transactional を付けず、業務番号は 2090 年を使う。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MyBatisQuotationBookingLockIntegrationTest {

    private static final UserId STAFF = new UserId(UUID.randomUUID());
    private static final UserId SHIPPER_USER = new UserId(UUID.randomUUID());
    private static final UtcInstant COMMITTED_AT = at("2090-01-09T01:00:00Z");
    private static final UtcInstant REQUIRED_AT = at("2090-01-09T00:30:00Z");

    @Autowired
    BookableQuotationQuery bookableQuotationQuery;

    @Autowired
    RouteRedesignNotification routeRedesignNotification;

    @Autowired
    QuotationRepository repository;

    @Autowired
    TransportRequestRepository transportRequests;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Test
    void 確定用の照会の後に印を付ける更新は確定のコミットまで待ってから付く() {
        // 届く順 I（行 13）
        String number = transactionTemplate.execute(status -> approved(1, "RC-2090-0001"));

        List<Object> results = OverlappingTransactions.run(
                transactionTemplate,
                () -> bookableQuotationQuery.findForConfirmation(request(number)),
                () -> routeRedesignNotification.notifyRedesignRequired(redesign("RC-2090-0001")));

        assertThat(results.get(0)).isInstanceOf(BookableQuotationResult.Bookable.class);
        assertThat(results.get(1)).isEqualTo(new RouteRedesignNotificationReceipt.Marked(1));
    }

    @Test
    void 印を付ける更新の後の確定用の照会は印のコミットまで待って再設計要を読む() {
        // 届く順 J（行 14）
        String number = transactionTemplate.execute(status -> approved(2, "RC-2090-0002"));

        List<Object> results = OverlappingTransactions.run(
                transactionTemplate,
                () -> routeRedesignNotification.notifyRedesignRequired(redesign("RC-2090-0002")),
                () -> bookableQuotationQuery.findForConfirmation(request(number)));

        assertThat(results.get(1))
                .isEqualTo(new BookableQuotationResult.NotBookable(
                        BookableQuotationResult.NotBookable.ROUTE_REDESIGN_REQUIRED));
    }

    @Test
    void 同じトランザクションで先に見積りを読んでいても確定用の照会は印のコミットを読む() {
        // 届く順 J（行 14）。先の読み込みはロックせずに印の前の見積りを読む。確定用の照会がセッションのキャッシュを使えば印を見落とす
        String number = transactionTemplate.execute(status -> approved(3, "RC-2090-0003"));

        List<Object> results = OverlappingTransactions.run(
                transactionTemplate,
                () -> routeRedesignNotification.notifyRedesignRequired(redesign("RC-2090-0003")),
                () -> List.of(
                        bookableQuotationQuery.find(request(number)),
                        bookableQuotationQuery.findForConfirmation(request(number))));

        List<?> second = (List<?>) results.get(1);
        assertThat(second.get(0)).as("先の読み込みは印の前").isInstanceOf(BookableQuotationResult.Bookable.class);
        assertThat(second.get(1))
                .isEqualTo(new BookableQuotationResult.NotBookable(
                        BookableQuotationResult.NotBookable.ROUTE_REDESIGN_REQUIRED));
    }

    @Test
    void 確定用の照会は呼び手のトランザクションの外では例外にする() {
        // ロックを呼び手のトランザクションの終わりまで持つため（MANDATORY）。外で呼ぶと文の終わりでロックが外れる
        String number = transactionTemplate.execute(status -> approved(4, "RC-2090-0004"));

        BookableQuotationRequest request = request(number);

        assertThatThrownBy(() -> bookableQuotationQuery.findForConfirmation(request))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    /** 荷主企業（どの見積りも同じ。営業担当者が担当する。Bolt 38f2）。 */
    private static final CompanyId SHIPPER = new CompanyId(UUID.randomUUID());

    private static final AuthenticatedActor OPERATOR =
            AuthenticatedActorFixture.salesHandling(new UserId(UUID.randomUUID()), SHIPPER);

    private static BookableQuotationRequest request(String number) {
        return new BookableQuotationRequest(number, 1, COMMITTED_AT, OPERATOR);
    }

    private static RouteRedesignNotificationRequest redesign(String routingCaseNumber) {
        return new RouteRedesignNotificationRequest(routingCaseNumber, 1, REQUIRED_AT);
    }

    /** 輸送要求を作り、経路版を割り当てて荷主が承認した見積り 1 を保存する。業務番号の表記を返す。 */
    private String approved(int sequence, String routingCaseNumber) {
        TransportRequest request = TransportRequest.submit(
                new TransportRequestId(UUID.randomUUID()),
                new TransportRequestNumber(2090, sequence),
                SHIPPER,
                ShipmentTermsFixture.generalCargo(),
                STAFF,
                at("2090-01-05T01:00:00Z"));
        transportRequests.save(request);
        Quotation quotation = Quotation.create(new QuotationId(UUID.randomUUID()), request.id(), 1, 1);
        quotation.calculate(QuotationFixture.completeInput(), at("2090-01-05T04:00:00Z"));
        quotation.presentInternally(STAFF, at("2090-01-05T04:30:00Z"));
        quotation.requestRouteDesign(SHIPPER_USER, at("2090-01-06T02:00:00Z"));
        quotation.assignRoute(route(routingCaseNumber), at("2090-01-07T05:00:00Z"));
        quotation.approveByShipper(SHIPPER_USER, at("2090-01-08T03:00:00Z"));
        quotation.clearDomainEvents();
        repository.save(quotation);
        return request.number().text();
    }

    private static AssignedRoute route(String routingCaseNumber) {
        return new AssignedRoute(
                routingCaseNumber,
                1,
                at("2090-01-07T05:00:00Z"),
                List.of(new AssignedRouteLeg(
                        "V-201",
                        new Location("JPTYO"),
                        new Location("NLRTM"),
                        at("2099-10-10T00:00:00Z"),
                        at("2099-10-31T00:00:00Z"))));
    }

    private static UtcInstant at(String instant) {
        return new UtcInstant(Instant.parse(instant));
    }
}
