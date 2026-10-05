package com.example.cargotracker.quotation.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.quotation.application.internal.commands.ResubmitTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commandservices.ResubmissionOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.TransportRequestCommandService;
import com.example.cargotracker.quotation.application.internal.queryservices.DocumentFile;
import com.example.cargotracker.quotation.application.internal.queryservices.StaffTransportRequestQueryService;
import com.example.cargotracker.quotation.application.internal.queryservices.TransportRequestQueryService;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.valueobjects.DocumentMediaType;
import com.example.cargotracker.quotation.domain.model.valueobjects.DocumentType;
import com.example.cargotracker.quotation.domain.model.valueobjects.RequiredDocument;
import com.example.cargotracker.quotation.domain.model.valueobjects.RequiredDocumentAttachment;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTerms;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsInput;
import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations.Item;
import com.example.cargotracker.quotation.domain.model.valueobjects.SubmissionViolations.Reason;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.acceptance.ScenarioContext;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import io.cucumber.java.ja.前提;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 必要書類（US-01 AC1・AC2、Q-INV-16）のステップ定義。見積りの入力ポートだけを呼ぶ（AT-05）。
 * 書類の中身は、形式の先頭のバイトに、指定の大きさまで埋め草を足して作る。
 */
public class RequiredDocumentSteps {

    /** 提出したのと同じ荷主（TransportRequestSteps と同じ値）。 */
    private static final CompanyId SHIPPER = new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    private static final CompanyId OTHER_SHIPPER =
            new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000000002"));

    private static final UserId SUBMITTER = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000101"));

    private static final Map<String, DocumentType> TYPES = Map.of(
            "商業送り状", DocumentType.COMMERCIAL_INVOICE,
            "梱包明細", DocumentType.PACKING_LIST,
            "その他", DocumentType.OTHER);

    /** 中身の呼び名と、その先頭のバイト。テキストはどの形式の署名にも当たらない。 */
    private static final Map<String, byte[]> HEADS = Map.of(
            "PDF",
            "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII),
            "PNG",
            new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A},
            "JPEG",
            new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0},
            "テキスト",
            "これは PDF ではない".getBytes(StandardCharsets.UTF_8));

    private static final Map<String, Item> ITEMS = Map.of(
            "商業送り状", Item.COMMERCIAL_INVOICE,
            "梱包明細", Item.PACKING_LIST,
            "その他の書類", Item.OTHER_DOCUMENTS);

    private static final Map<String, Reason> REASONS = Map.of(
            "形式が対象外", Reason.UNSUPPORTED_FORMAT,
            "大きすぎる", Reason.TOO_LARGE,
            "多すぎる", Reason.TOO_MANY);

    private final TransportRequestCommandService commandService;
    private final TransportRequestQueryService queryService;
    private final StaffTransportRequestQueryService staffQueryService;
    private final RequiredDocumentAttachments attachments;
    private final InMemoryRequiredDocumentStorage storage;
    private final ScenarioContext context;
    private ResubmissionOutcome lastResubmission;

    public RequiredDocumentSteps(
            TransportRequestCommandService commandService,
            TransportRequestQueryService queryService,
            StaffTransportRequestQueryService staffQueryService,
            RequiredDocumentAttachments attachments,
            InMemoryRequiredDocumentStorage storage,
            ScenarioContext context) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.staffQueryService = staffQueryService;
        this.attachments = attachments;
        this.storage = storage;
        this.context = context;
    }

    @前提("荷主が次の書類を添付する")
    public void 荷主が次の書類を添付する(DataTable table) {
        table.asMaps()
                .forEach(row -> attachments.add(new RequiredDocumentAttachment(
                        TYPES.get(row.get("種類")),
                        row.get("ファイル名"),
                        content(row.get("中身"), Integer.parseInt(row.get("大きさ（KB）")) * 1024))));
    }

    @ならば("版 {int} に書類が {int} 件残る")
    public void 版に書類が残る(int versionNo, int count) {
        assertThat(current().currentVersion().versionNo()).isEqualTo(versionNo);
        assertThat(current().currentVersion().terms().documents()).hasSize(count);
    }

    @ならば("荷主が版 {int} の書類 {int} を取得すると {string} の形式 {string} の添付と同じ中身が返る")
    public void 荷主が取得すると同じ中身が返る(int versionNo, int documentNo, String fileName, String mediaType) {
        assertSameContent(
                queryService.findDocument(current().number(), SHIPPER, versionNo, documentNo), fileName, mediaType);
    }

    @ならば("営業担当者が版 {int} の書類 {int} を取得すると {string} の形式 {string} の添付と同じ中身が返る")
    public void 営業担当者が取得すると同じ中身が返る(int versionNo, int documentNo, String fileName, String mediaType) {
        assertSameContent(
                staffQueryService.findDocument(current().number(), versionNo, documentNo), fileName, mediaType);
    }

    @ならば("他社の荷主が版 {int} の書類 {int} を取得しても見つからない")
    public void 他社の荷主は取得できない(int versionNo, int documentNo) {
        assertThat(queryService.findDocument(current().number(), OTHER_SHIPPER, versionNo, documentNo))
                .isEmpty();
    }

    @ならば("荷主が版 {int} の書類 {int} を取得しても見つからない")
    public void 荷主は取得できない(int versionNo, int documentNo) {
        assertThat(queryService.findDocument(current().number(), SHIPPER, versionNo, documentNo))
                .isEmpty();
    }

    @もし("荷主が目的地を {string} のまま選んだ書類で出し直す")
    public void 選んだ書類で出し直す(String destination) {
        ShipmentTerms terms = current().currentVersion().terms();
        assertThat(terms.destination().unLocode()).isEqualTo(destination);
        ShipmentTermsInput input = new ShipmentTermsInput(
                terms.consigneeCompanyId(),
                terms.origin(),
                terms.destination(),
                terms.arrivalDeadline(),
                terms.cargo().category(),
                terms.cargo().packageType(),
                terms.cargo().packageCount(),
                terms.cargo().grossWeightKg(),
                terms.cargo().volumeM3());
        lastResubmission = commandService.resubmit(new ResubmitTransportRequestCommand(
                current().number(), SHIPPER, SUBMITTER, input, attachments.takePending()));
    }

    @ならば("版 {int} の書類は {string} と {string} になる")
    public void 版の書類の一覧(int versionNo, String first, String second) {
        assertThat(lastResubmission).isInstanceOf(ResubmissionOutcome.Resubmitted.class);
        assertThat(current().currentVersion().versionNo()).isEqualTo(versionNo);
        assertThat(current().currentVersion().terms().documents())
                .extracting(RequiredDocument::fileName)
                .containsExactly(first, second);
    }

    @ならば("版 {int} の書類は次のとおりになる")
    public void 版の書類は次のとおり(int versionNo, DataTable table) {
        assertThat(lastResubmission).isInstanceOf(ResubmissionOutcome.Resubmitted.class);
        assertThat(current().currentVersion().versionNo()).isEqualTo(versionNo);
        assertThat(current().currentVersion().terms().documents())
                .extracting(document -> document.documentNo() + ":" + document.fileName())
                .containsExactlyElementsOf(table.asMaps().stream()
                        .map(row -> row.get("書類番号") + ":" + row.get("ファイル名"))
                        .toList());
    }

    @ならば("出し直しは受け付けられず {string} に誤り {string} が示される")
    public void 出し直しは受け付けられない(String item, String reason) {
        assertThat(lastResubmission)
                .isInstanceOfSatisfying(
                        ResubmissionOutcome.Invalid.class,
                        invalid -> assertThat(invalid.violations().has(ITEMS.get(item), REASONS.get(reason)))
                                .as("%s に %s: %s", item, reason, invalid.violations())
                                .isTrue());
    }

    @ならば("保存された書類は {int} 件のままである")
    public void 保存された書類の件数(int count) {
        assertThat(storage.count()).isEqualTo(count);
    }

    private void assertSameContent(Optional<DocumentFile> found, String fileName, String mediaType) {
        RequiredDocumentAttachment attached = attachments.attached(fileName);
        assertThat(found).hasValueSatisfying(file -> {
            assertThat(file.document().fileName()).isEqualTo(fileName);
            assertThat(file.document().mediaType()).isEqualTo(DocumentMediaType.valueOf(mediaType));
            assertThat(Arrays.equals(file.content(), attached.content())).isTrue();
        });
    }

    private TransportRequest current() {
        return queryService
                .findByNumber(TransportRequestNumber.parse(context.transportRequestNumber()), SHIPPER)
                .orElseThrow();
    }

    private static byte[] content(String kind, int size) {
        byte[] head = HEADS.get(kind);
        byte[] content = Arrays.copyOf(head, Math.max(size, head.length));
        Arrays.fill(content, head.length, content.length, (byte) 'x');
        return content;
    }
}
