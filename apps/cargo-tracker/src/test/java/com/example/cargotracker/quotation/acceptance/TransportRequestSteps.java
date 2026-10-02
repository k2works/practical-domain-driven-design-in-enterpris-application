package com.example.cargotracker.quotation.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.quotation.application.internal.commands.SubmitTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commandservices.SubmissionOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.TransportRequestCommandService;
import com.example.cargotracker.quotation.application.internal.queryservices.TransportRequestQueryService;
import com.example.cargotracker.quotation.domain.model.valueobjects.CargoCategory;
import com.example.cargotracker.quotation.domain.model.valueobjects.PackageType;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsInput;
import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations;
import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations.Item;
import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations.Reason;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestStatus;
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
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * 見積りのステップ定義。見積りの入力ポートだけを呼ぶ（AT-05）。
 * 入力はシナリオの語（項目の日本語名と値）で受け取り、輸送条件の入力（ShipmentTermsInput）に直して提出する。
 */
public class TransportRequestSteps {

    private static final CompanyId SHIPPER = new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    private static final UserId SUBMITTER = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000101"));

    /** シナリオで使う荷受人の呼び名。企業マスターができるまでの仮の一覧（確認ポイント 2）と同じ考え方。 */
    private static final Map<String, CompanyId> CONSIGNEES = Map.of(
            "取引先 A", new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000000201")));

    private static final Map<String, Item> ITEMS = Map.of(
            "荷受人", Item.CONSIGNEE,
            "出発地", Item.ORIGIN,
            "目的地", Item.DESTINATION,
            "希望到着期限", Item.ARRIVAL_DEADLINE,
            "貨物種別", Item.CARGO_CATEGORY,
            "荷姿", Item.PACKAGE_TYPE,
            "個数", Item.PACKAGE_COUNT,
            "総重量（kg）", Item.GROSS_WEIGHT_KG,
            "容積（m3）", Item.VOLUME_M3);

    private static final Map<String, Reason> REASONS = Map.of(
            "出発地と同じ", Reason.SAME_AS_ORIGIN,
            "提出時刻以前", Reason.NOT_AFTER_SUBMISSION,
            "0 以下", Reason.NOT_POSITIVE,
            "小数点以下 3 桁を超える", Reason.TOO_MANY_DECIMALS,
            "MVP の対象外", Reason.OUTSIDE_MVP);

    private static final Map<String, CargoCategory> CARGO_CATEGORIES = Map.of(
            "一般", CargoCategory.GENERAL,
            "危険物", CargoCategory.DANGEROUS,
            "冷凍", CargoCategory.REEFER,
            "その他特殊", CargoCategory.OTHER_SPECIAL);

    private static final Map<String, PackageType> PACKAGE_TYPES = Map.of(
            "パレット", PackageType.PALLET,
            "カートン", PackageType.CARTON,
            "クレート", PackageType.CRATE,
            "その他", PackageType.OTHER);

    private final TransportRequestCommandService commandService;
    private final TransportRequestQueryService queryService;
    private final InMemoryTransportRequestRepository repository;
    private final ScenarioContext context;
    private final Map<Item, String> input = new EnumMap<>(Item.class);
    private SubmissionOutcome lastOutcome;

    public TransportRequestSteps(
            TransportRequestCommandService commandService,
            TransportRequestQueryService queryService,
            InMemoryTransportRequestRepository repository,
            ScenarioContext context) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.repository = repository;
        this.context = context;
    }

    @前提("荷主が次の輸送条件を入力している")
    public void 荷主が次の輸送条件を入力している(DataTable table) {
        input.clear();
        table.asMaps().forEach(row -> input.put(item(row.get("項目")), row.get("値")));
    }

    @前提("荷主が必須条件をそろえた輸送条件を入力している")
    public void 荷主が必須条件をそろえた輸送条件を入力している() {
        input.clear();
        input.put(Item.CONSIGNEE, "取引先 A");
        input.put(Item.ORIGIN, "JPTYO");
        input.put(Item.DESTINATION, "NLRTM");
        input.put(Item.ARRIVAL_DEADLINE, "2026-11-02T00:00:00Z");
        input.put(Item.CARGO_CATEGORY, "一般");
        input.put(Item.PACKAGE_TYPE, "パレット");
        input.put(Item.PACKAGE_COUNT, "12");
        input.put(Item.GROSS_WEIGHT_KG, "8400");
        input.put(Item.VOLUME_M3, "32.5");
    }

    @前提("荷主が {string} を {string} に変える")
    public void 荷主が項目を変える(String itemName, String value) {
        input.put(item(itemName), value);
    }

    @前提("荷主が {string} を空にする")
    public void 荷主が項目を空にする(String itemName) {
        input.remove(item(itemName));
    }

    @もし("荷主が入力した輸送条件を提出する")
    public void 荷主が入力した輸送条件を提出する() {
        lastOutcome = commandService.submit(new SubmitTransportRequestCommand(SHIPPER, SUBMITTER, toInput()));
        if (lastOutcome instanceof SubmissionOutcome.Submitted submitted) {
            context.transportRequestId(submitted.transportRequestId().value());
        }
    }

    @ならば("輸送要求は審査中になる")
    public void 輸送要求は審査中になる() {
        assertThat(lastOutcome).isInstanceOf(SubmissionOutcome.Submitted.class);
        assertThat(queryService.findById(new TransportRequestId(context.transportRequestId())))
                .hasValueSatisfying(
                        request -> assertThat(request.status()).isEqualTo(TransportRequestStatus.UNDER_REVIEW));
    }

    @ならば("提出者と提出時刻 {string} が記録される")
    public void 提出者と提出時刻が記録される(String submittedAt) {
        assertThat(queryService.findById(new TransportRequestId(context.transportRequestId())))
                .hasValueSatisfying(request -> {
                    assertThat(request.currentVersion().submittedBy()).isEqualTo(SUBMITTER);
                    assertThat(request.currentVersion().submittedAt())
                            .isEqualTo(new UtcInstant(Instant.parse(submittedAt)));
                });
    }

    @ならば("業務番号 {string} が示される")
    public void 業務番号が示される(String number) {
        assertThat(lastOutcome)
                .isInstanceOfSatisfying(SubmissionOutcome.Submitted.class, submitted ->
                        assertThat(submitted.number().text()).isEqualTo(number));
        assertThat(queryService.findById(new TransportRequestId(context.transportRequestId())))
                .hasValueSatisfying(request -> assertThat(request.number().text()).isEqualTo(number));
    }

    @ならば("提出は受け付けられず輸送要求は作られない")
    public void 提出は受け付けられず輸送要求は作られない() {
        assertThat(lastOutcome).isInstanceOf(SubmissionOutcome.Rejected.class);
        assertThat(repository.count()).isZero();
    }

    @ならば("{string} の不足が示される")
    public void 項目の不足が示される(String itemName) {
        assertThat(violations().has(item(itemName), Reason.MISSING))
                .as("%s の不足: %s", itemName, violations())
                .isTrue();
    }

    @ならば("{string} に誤り {string} が示される")
    public void 項目に誤りが示される(String itemName, String reasonName) {
        assertThat(violations().has(item(itemName), REASONS.get(reasonName)))
                .as("%s の誤り %s: %s", itemName, reasonName, violations())
                .isTrue();
    }

    private SubmissionViolations violations() {
        assertThat(lastOutcome).isInstanceOf(SubmissionOutcome.Rejected.class);
        return ((SubmissionOutcome.Rejected) lastOutcome).violations();
    }

    private ShipmentTermsInput toInput() {
        return new ShipmentTermsInput(
                value(Item.CONSIGNEE, CONSIGNEES::get),
                value(Item.ORIGIN, Location::new),
                value(Item.DESTINATION, Location::new),
                value(Item.ARRIVAL_DEADLINE, text -> new UtcInstant(Instant.parse(text))),
                value(Item.CARGO_CATEGORY, CARGO_CATEGORIES::get),
                value(Item.PACKAGE_TYPE, PACKAGE_TYPES::get),
                value(Item.PACKAGE_COUNT, Integer::valueOf),
                value(Item.GROSS_WEIGHT_KG, BigDecimal::new),
                value(Item.VOLUME_M3, BigDecimal::new));
    }

    private <T> T value(Item item, Function<String, T> parser) {
        String text = input.get(item);
        return text == null ? null : parser.apply(text);
    }

    private static Item item(String itemName) {
        Item item = ITEMS.get(itemName);
        assertThat(item).as("シナリオの項目名: %s", itemName).isNotNull();
        return item;
    }
}
