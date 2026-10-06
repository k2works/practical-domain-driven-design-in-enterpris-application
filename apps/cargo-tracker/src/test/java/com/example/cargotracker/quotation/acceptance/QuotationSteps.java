package com.example.cargotracker.quotation.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.quotation.application.internal.commands.CalculateQuotationCommand;
import com.example.cargotracker.quotation.application.internal.commands.PresentQuotationCommand;
import com.example.cargotracker.quotation.application.internal.commands.RequoteQuotationCommand;
import com.example.cargotracker.quotation.application.internal.commandservices.CalculationOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.PresentationOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.QuotationCommandService;
import com.example.cargotracker.quotation.application.internal.commandservices.RequotationOutcome;
import com.example.cargotracker.quotation.application.internal.queryservices.QuotationQueryService;
import com.example.cargotracker.quotation.application.internal.queryservices.StaffQuotationQueryService;
import com.example.cargotracker.quotation.domain.events.QuotationPresented;
import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.valueobjects.Currency;
import com.example.cargotracker.quotation.domain.model.valueobjects.PricingLine;
import com.example.cargotracker.quotation.domain.model.valueobjects.PricingLineInput;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationInput;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationStatus;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationViolations.Item;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationViolations.Reason;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.acceptance.DeferredEventDelivery;
import com.example.cargotracker.shared.acceptance.ScenarioContext;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import io.cucumber.java.ja.前提;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 見積りの算出と提示（US-03 AC1〜AC3、Q-INV-05・17・18）のステップ定義。見積りの入力ポートだけを呼ぶ（AT-05）。
 * 社内承認者は、審査の判断者と同じく仮の営業担当者とする。
 */
public class QuotationSteps {

    /** 提出したのと同じ荷主（TransportRequestSteps と同じ値）。 */
    private static final CompanyId SHIPPER = new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    private static final CompanyId OTHER_SHIPPER =
            new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000000002"));

    private static final UserId APPROVER = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000301"));

    private static final Map<String, QuotationStatus> STATUSES = Map.of(
            "承認待ち", QuotationStatus.PENDING_APPROVAL,
            "提示済み", QuotationStatus.PRESENTED,
            "失効", QuotationStatus.EXPIRED,
            "置換済み", QuotationStatus.REPLACED,
            "詳細設計依頼済み", QuotationStatus.ROUTING_REQUESTED);

    private static final Map<String, Item> ITEMS = Map.of(
            "料金明細", Item.PRICING_LINES,
            "通貨", Item.CURRENCY,
            "有効期限", Item.EXPIRES_AT);

    private static final Map<String, Reason> REASONS =
            Map.of("不足", Reason.MISSING, "有効期限が算出の時刻以前", Reason.NOT_AFTER_CALCULATION);

    private static final Map<String, QuotationRejection> REJECTIONS = Map.of(
            "見積りがすでにある", QuotationRejection.ALREADY_QUOTED,
            "失効している", QuotationRejection.EXPIRED,
            "置換済み", QuotationRejection.REPLACED,
            "詳細設計依頼済み", QuotationRejection.ROUTING_REQUESTED);

    /** 標準の見積りの有効期限（有効期限を書かない前提の文で使う）。 */
    private static final String STANDARD_EXPIRES_AT = "2026-10-08T09:00:00Z";

    private final QuotationCommandService commandService;
    private final QuotationQueryService queryService;
    private final StaffQuotationQueryService staffQueryService;
    private final DeferredEventDelivery delivery;
    private final ScenarioContext context;
    private final List<PricingLineInput> lines = new ArrayList<>();
    private Currency currency;
    private UtcInstant expiresAt;
    private List<Location> via = List.of();
    private UtcInstant departureAt;
    private UtcInstant arrivalAt;
    private CalculationOutcome lastCalculation;
    private PresentationOutcome lastPresentation;
    private RequotationOutcome lastRequotation;

    public QuotationSteps(
            QuotationCommandService commandService,
            QuotationQueryService queryService,
            StaffQuotationQueryService staffQueryService,
            DeferredEventDelivery delivery,
            ScenarioContext context) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.staffQueryService = staffQueryService;
        this.delivery = delivery;
        this.context = context;
    }

    @前提("営業担当者が見積りに次の料金明細を入れる")
    public void 料金明細を入れる(DataTable table) {
        lines.clear();
        table.asMaps()
                .forEach(row -> lines.add(new PricingLineInput(
                        row.get("内容"), new BigDecimal(row.get("金額")), blankToNull(row.get("参照した契約条件")))));
    }

    @前提("営業担当者が見積りに通貨 {string} と有効期限 {string} を入れる")
    public void 通貨と有効期限を入れる(String currencyCode, String expires) {
        currency = Currency.valueOf(currencyCode);
        expiresAt = new UtcInstant(Instant.parse(expires));
    }

    @前提("営業担当者が見積りに経由地 {string} と概算の出発 {string} と概算の到着 {string} を入れる")
    public void 経路方針を入れる(String viaCodes, String departure, String arrival) {
        via = viaCodes.isBlank()
                ? List.of()
                : Arrays.stream(viaCodes.split(","))
                        .map(String::strip)
                        .map(Location::new)
                        .toList();
        departureAt = new UtcInstant(Instant.parse(departure));
        arrivalAt = new UtcInstant(Instant.parse(arrival));
    }

    @前提("営業担当者が標準の見積りを算出して社内承認して提示している")
    public void 標準の見積りを提示している() {
        有効期限を決めた標準の見積りを提示している(STANDARD_EXPIRES_AT);
    }

    @前提("営業担当者が有効期限 {string} の標準の見積りを算出して社内承認して提示している")
    public void 有効期限を決めた標準の見積りを提示している(String expires) {
        有効期限を決めた標準の見積りを算出している(expires);
        社内承認して提示する(1);
    }

    @前提("営業担当者が有効期限 {string} の標準の見積りを算出している")
    public void 有効期限を決めた標準の見積りを算出している(String expires) {
        lines.clear();
        lines.add(new PricingLineInput("海上運賃", new BigDecimal("3200.00"), "年間契約 2026-A"));
        lines.add(new PricingLineInput("燃料調整金", new BigDecimal("530.00"), null));
        通貨と有効期限を入れる("USD", expires);
        経路方針を入れる("SGSIN", "2026-10-10T00:00:00Z", "2026-10-30T09:00:00Z");
        見積りを算出する();
        assertThat(lastCalculation).isInstanceOf(CalculationOutcome.Calculated.class);
    }

    @もし("営業担当者が見積りを算出する")
    public void 見積りを算出する() {
        lastCalculation = commandService.calculate(new CalculateQuotationCommand(
                number(), new QuotationInput(List.copyOf(lines), currency, expiresAt, via, departureAt, arrivalAt)));
    }

    @もし("営業担当者が見積り {int} を社内承認して提示する")
    public void 社内承認して提示する(int quotationNo) {
        assertThat(commandService.present(new PresentQuotationCommand(number(), quotationNo, APPROVER)))
                .isInstanceOf(PresentationOutcome.Presented.class);
    }

    @もし("営業担当者が見積り {int} の社内承認と提示を試みる")
    public void 社内承認と提示を試みる(int quotationNo) {
        lastPresentation = commandService.present(new PresentQuotationCommand(number(), quotationNo, APPROVER));
    }

    @ならば("見積りの提示の結果は {string} である")
    public void 提示の結果(String result) {
        if ("提示した".equals(result)) {
            assertThat(lastPresentation).isInstanceOf(PresentationOutcome.Presented.class);
        } else {
            assertThat(lastPresentation).isEqualTo(new PresentationOutcome.Rejected(REJECTIONS.get(result)));
        }
    }

    @もし("営業担当者が見積り {int} を再見積りする")
    public void 再見積りする(int quotationNo) {
        lastRequotation = commandService.requote(new RequoteQuotationCommand(
                number(),
                quotationNo,
                new QuotationInput(List.copyOf(lines), currency, expiresAt, via, departureAt, arrivalAt)));
    }

    @ならば("再見積りは受け付けられず {string} と示される")
    public void 再見積りは受け付けられない(String reason) {
        assertThat(lastRequotation).isEqualTo(new RequotationOutcome.Rejected(REJECTIONS.get(reason)));
    }

    @ならば("見積り {int} は {string} に {string} として扱われる")
    public void 判定時刻での有効性(int quotationNo, String at, String validity) {
        assertThat(staffQueryService.find(number(), quotationNo))
                .hasValueSatisfying(quotation -> assertThat(quotation.isExpiredAt(new UtcInstant(Instant.parse(at))))
                        .isEqualTo("失効".equals(validity)));
    }

    @ならば("見積り {int} は {string} になり見積り {int} に置き換えられている")
    public void 置き換えられている(int quotationNo, String status, int replacementNo) {
        Quotation replacement = staffQueryService.find(number(), replacementNo).orElseThrow();
        assertThat(staffQueryService.find(number(), quotationNo)).hasValueSatisfying(quotation -> {
            assertThat(quotation.status()).isEqualTo(STATUSES.get(status));
            assertThat(quotation.replacedBy()).contains(replacement.id());
        });
    }

    @ならば("荷主が照会する見積りはない")
    public void 荷主が照会する見積りはない() {
        assertThat(queryService.findVisible(number(), SHIPPER)).isEmpty();
    }

    @ならば("見積り {int} はない")
    public void 見積りはない(int quotationNo) {
        assertThat(staffQueryService.find(number(), quotationNo)).isEmpty();
    }

    @ならば("荷主が照会する見積りは次のとおりである")
    public void 荷主が照会する見積り(DataTable table) {
        assertThat(queryService.findVisible(number(), SHIPPER))
                .extracting(quotation -> quotation.quotationNo() + ":" + quotation.status())
                .containsExactlyElementsOf(table.asMaps().stream()
                        .map(row -> row.get("見積り番号") + ":" + STATUSES.get(row.get("状態")))
                        .toList());
    }

    @ならば("見積り {int} は {string} になる")
    public void 見積りの状態(int quotationNo, String status) {
        assertThat(staffQueryService.find(number(), quotationNo))
                .hasValueSatisfying(quotation -> assertThat(quotation.status()).isEqualTo(STATUSES.get(status)));
    }

    @ならば("見積り {int} は {string} になり、提示時刻 {string} と承認者が残る")
    public void 提示時刻と承認者が残る(int quotationNo, String status, String presentedAt) {
        assertThat(staffQueryService.find(number(), quotationNo)).hasValueSatisfying(quotation -> {
            assertThat(quotation.status()).isEqualTo(STATUSES.get(status));
            assertThat(quotation.presentedAt()).contains(new UtcInstant(Instant.parse(presentedAt)));
            assertThat(quotation.approvedBy()).contains(APPROVER);
        });
    }

    @ならば("見積り {int} を提示したイベントが発行される")
    public void 提示したイベントが発行される(int quotationNo) {
        assertThat(delivery.published())
                .filteredOn(QuotationPresented.class::isInstance)
                .map(QuotationPresented.class::cast)
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.quotationNo()).isEqualTo(quotationNo);
                    assertThat(event.transportRequestId()).isEqualTo(context.transportRequestId());
                    assertThat(event.transportRequestVersionNo()).isEqualTo(1);
                });
    }

    @ならば("荷主が見積りを照会すると料金明細 {int} 行と合計 {string} {string} と有効期限 {string} が示される")
    public void 荷主に料金根拠と有効期限が示される(int lineCount, String total, String currencyCode, String expires) {
        assertThat(shipperQuotation()).hasValueSatisfying(quotation -> {
            assertThat(quotation.pricingBasis().orElseThrow().lines())
                    .hasSize(lineCount)
                    .extracting(PricingLine::description)
                    .containsExactly("海上運賃", "燃料調整金");
            assertThat(quotation.pricingBasis().orElseThrow().total()).isEqualByComparingTo(total);
            assertThat(quotation.pricingBasis().orElseThrow().currency()).isEqualTo(Currency.valueOf(currencyCode));
            assertThat(quotation.expiry().orElseThrow().expiresAt()).isEqualTo(new UtcInstant(Instant.parse(expires)));
        });
    }

    @ならば("荷主が照会した見積りの経路方針は経由地 {string} と概算の出発 {string} と概算の到着 {string} である")
    public void 荷主に経路方針が示される(String viaCode, String departure, String arrival) {
        assertThat(shipperQuotation()).hasValueSatisfying(quotation -> {
            assertThat(quotation.routePolicy().orElseThrow().via()).containsExactly(new Location(viaCode));
            assertThat(quotation.routePolicy().orElseThrow().departureAt())
                    .isEqualTo(new UtcInstant(Instant.parse(departure)));
            assertThat(quotation.routePolicy().orElseThrow().arrivalAt())
                    .isEqualTo(new UtcInstant(Instant.parse(arrival)));
        });
    }

    @ならば("他社の荷主には見積りが見えない")
    public void 他社の荷主には見えない() {
        assertThat(queryService.findVisible(number(), OTHER_SHIPPER)).isEmpty();
    }

    @ならば("荷主が見積りを照会しても見つからない")
    public void 荷主には見つからない() {
        assertThat(shipperQuotation()).isEmpty();
    }

    @ならば("見積りは算出されず {string} に誤り {string} が示される")
    @ならば("見積りの {string} に誤り {string} が示される")
    public void 算出の誤りが示される(String item, String reason) {
        assertThat(lastCalculation)
                .isInstanceOfSatisfying(
                        CalculationOutcome.Invalid.class,
                        invalid -> assertThat(invalid.violations().has(ITEMS.get(item), REASONS.get(reason)))
                                .as("%s に %s: %s", item, reason, invalid.violations())
                                .isTrue());
    }

    @ならば("見積りは作られない")
    public void 見積りは作られない() {
        assertThat(staffQueryService.find(number(), 1)).isEmpty();
    }

    @ならば("見積りの算出の結果は {string} である")
    public void 算出の結果(String result) {
        if ("承認待ち".equals(result)) {
            見積りの状態(1, result);
        } else {
            算出の誤りが示される("有効期限", result);
        }
    }

    @ならば("見積りは作られず {string} と示される")
    public void 見積りは作られず理由が示される(String reason) {
        assertThat(lastCalculation).isEqualTo(new CalculationOutcome.Rejected(REJECTIONS.get(reason)));
        assertThat(staffQueryService.find(number(), 2)).isEmpty();
    }

    private Optional<Quotation> shipperQuotation() {
        return queryService.findVisible(number(), SHIPPER).stream().findFirst();
    }

    private TransportRequestNumber number() {
        return TransportRequestNumber.parse(context.transportRequestNumber());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
