package com.example.cargotracker.externaldata.application.internal.queryservices;

import com.example.cargotracker.externaldata.domain.model.aggregates.AdoptedValue;
import com.example.cargotracker.externaldata.domain.model.aggregates.AdoptedValueRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSource;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSourceRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.IngestionRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecord;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecordRepository;
import com.example.cargotracker.externaldata.domain.model.rules.DiscrepancyRule;
import com.example.cargotracker.externaldata.domain.model.valueobjects.Classification;
import com.example.cargotracker.externaldata.domain.model.valueobjects.ExternalIdAdoptedValue;
import com.example.cargotracker.externaldata.domain.model.valueobjects.ReceivedValue;
import com.example.cargotracker.externaldata.domain.model.valueobjects.RecordValues;
import com.example.cargotracker.externaldata.domain.model.valueobjects.UndecidedRecord;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 判断を待つ隔離・差異の照会。S-17 の一覧に、判断を待つ隔離と差異を取込の新しい順・ファイルの行の順に {@link #PAGE_SIZE} 件ずつ返す（Bolt 39d。開始準備の
 * 決定 (3)・(4)）。取込の時刻と外部 ID・外部版は受信記録の照会が結んで読み、情報源だけをページの中の異なる情報源 ID ごとに 1 回引く。
 * 絞った取込があるかは取込を 1 回引いて確かめ、なければ絞らない一覧を返して見つからないことを示す。判断を待つ隔離が 0 件ならページの照会と
 * 情報源を引かない。求めた頁が 1 より前なら 1 頁目、最後より後なら最後の頁を返す。件数を数えてから頁を読むまでに件数が減って読んだ頁が
 * 空なら、数え直して 1 回だけ読み直す（Bolt 39d2b）。取込で絞ったときだけ、まとめての却下のリンクのためにその取込の判断を待つキーのない
 * 隔離を数える（Bolt 39d2b）。判断の画面の中身とは別に、内容の違う原本の受け取った値とこの外部版の採用値を返す（航海は Bolt 39d3、主要実績と
 * 読めない値は Bolt 39d3c）。一覧の照会は値も採用値も読まない。
 * 組み立ては {@code externaldata.infrastructure.config} の {@code @Bean} が担う。
 */
@Service
public class UndecidedRecordQueryService {

    /** 1 ページの件数（S-16 の結果の画面の隔離の理由の 100 件にそろえる。Bolt 39d 計画の開始準備の決定 (3)）。 */
    public static final int PAGE_SIZE = 100;

    private static final Logger LOG = LoggerFactory.getLogger(UndecidedRecordQueryService.class);

    private final ReceivedRecordRepository receivedRecords;
    private final IngestionRepository ingestions;
    private final DataSourceRepository dataSources;
    private final AdoptedValueRepository adoptedValues;

    public UndecidedRecordQueryService(
            ReceivedRecordRepository receivedRecords,
            IngestionRepository ingestions,
            DataSourceRepository dataSources,
            AdoptedValueRepository adoptedValues) {
        this.receivedRecords = receivedRecords;
        this.ingestions = ingestions;
        this.dataSources = dataSources;
        this.adoptedValues = adoptedValues;
    }

    /**
     * 隔離・差異の判断の画面の値（Bolt 39d3 の開始準備の決定 (5)・(6)、航海と主要実績は Bolt 39d3c の開始準備の決定 (4)、差異は Bolt 39h2 の決定 (5)で受け取った値だけ）。値を返すのは、分類が
     * 隔離か差異で外部原本を持つ受信記録（差異はこの外部版の採用値を引かない）。キーのない隔離は受信記録も採用値も引かずに空、ない受信記録・隔離でない受信記録は採用値を引かずに空。
     * 受け取った値は判断の画面の中身の原本種別で、採用値は採用値の対象の種類で型で分ける入口を呼ぶ（原本種別は判断の画面の中身から受け、取込と
     * 情報源を読み直さない）。入口が要約か原本種別を読めない（{@link IllegalArgumentException}）ときは、その側を読めない印にして WARN のログに
     * ID・原本種別・例外の型だけを残す（【業務の判断】(k)。要約の中身と例外の文は取込ファイルから来た値を含むので書かない）。値の列がないことを
     * 読めないことより先に判定する。引くのは受信記録の ID の照会 1 回、この外部版の採用値の照会 1 回、採用値があればその受信記録の ID の照会
     * 1 回（採用値の取得時刻）で、読めない値でも変わらない。
     *
     * @param quarantine 判断の画面の中身（{@link #undecidedRecord} の値）
     */
    @Transactional(readOnly = true)
    public Optional<UndecidedRecordValues> undecidedRecordValues(UndecidedRecordPage.Item quarantine) {
        if (quarantine.keyless()) {
            return Optional.empty();
        }
        return receivedRecords
                .findById(quarantine.receivedRecordId())
                .filter(received -> (received.classification() == Classification.QUARANTINED
                                || received.classification() == Classification.DISCREPANCY)
                        && received.externalRecordId() != null)
                .map(received -> valuesOf(received, quarantine));
    }

    private UndecidedRecordValues valuesOf(ReceivedRecord quarantine, UndecidedRecordPage.Item item) {
        String recordType = item.recordType();
        ReceivedValue value = quarantine.value();
        // 値の列がない（Bolt 39d3 より前の隔離）ときは入口を呼ばず、読めない印を立てない
        Optional<RecordValues> received = value == null
                ? Optional.empty()
                : readable(recordType, value.targetKey(), value.summary(), () -> "受信記録 " + quarantine.id());
        // 差異の外部原本（その外部版）は採用値を持たないので、この外部版の採用値は引かない（Bolt 39h2 の決定 (5)）
        UndecidedRecordValues.Adopted adopted = quarantine.classification() == Classification.DISCREPANCY
                ? null
                : adoptedValues
                        .currentOf(quarantine.externalRecordId())
                        .map(this::adoptedOf)
                        .orElse(null);
        // この外部 ID の採用値（差異の規則が比べる相手。警告の判定と領域の選び方は差異の規則と同じ定義を使う。Bolt 39h3 の決定 (2)・(4)）
        List<ExternalIdAdoptedValue> sameExternalId =
                adoptedValues.findByExternalId(item.provider(), recordType, item.externalId());
        boolean reversed = DiscrepancyRule.isVersionReversed(quarantine.acquiredAt(), sameExternalId);
        UndecidedRecordValues.Adopted partner = DiscrepancyRule.partnerOf(quarantine.acquiredAt(), sameExternalId)
                // 逆転でなく判断する受信記録と同じ外部版なら「この外部版の採用値」の領域が同じ値を示すので出さない
                .filter(candidate -> reversed || !candidate.externalVersion().equals(item.externalVersion()))
                .map(this::partnerOf)
                .orElse(null);
        return new UndecidedRecordValues(
                received.orElse(null),
                value != null && received.isEmpty(),
                quarantine.acquiredAt(),
                adopted,
                partner,
                reversed,
                reversed && quarantine.classification() == Classification.QUARANTINED);
    }

    private UndecidedRecordValues.Adopted partnerOf(ExternalIdAdoptedValue partner) {
        // 取得時刻・公開した時刻・外部版は一覧の値が持つので、値の要約だけを採用値の受信記録 ID で引く
        AdoptedValue adopted =
                adoptedValues.findByReceivedRecordId(partner.receivedRecordId()).orElseThrow();
        Optional<RecordValues> values = readable(
                adopted.targetType().name(),
                adopted.targetKey(),
                adopted.valueSummary(),
                () -> "採用値 " + adopted.id() + "（受信記録 " + adopted.receivedRecordId() + "）");
        return new UndecidedRecordValues.Adopted(
                values.orElse(null),
                values.isEmpty(),
                partner.externalVersion(),
                partner.publishedAt(),
                partner.acquiredAt());
    }

    private UndecidedRecordValues.Adopted adoptedOf(AdoptedValue adopted) {
        // 採用値は受信記録への外部キーを持ち、採用の受信記録は外部原本と取得時刻を持つ
        ReceivedRecord adoptedRecord =
                receivedRecords.findById(adopted.receivedRecordId()).orElseThrow();
        Optional<RecordValues> values = readable(
                adopted.targetType().name(),
                adopted.targetKey(),
                adopted.valueSummary(),
                () -> "採用値 " + adopted.id() + "（受信記録 " + adopted.receivedRecordId() + "）");
        return new UndecidedRecordValues.Adopted(
                values.orElse(null),
                values.isEmpty(),
                adopted.infoVersion(),
                adopted.publishedAt(),
                adoptedRecord.acquiredAt());
    }

    /**
     * 型で分ける入口で要約から値に戻す。入口の {@link IllegalArgumentException} だけを受けて空にし、WARN のログを残す（try は入口の呼び出し
     * だけを囲む。ほかの例外は受けない）。
     */
    private static Optional<RecordValues> readable(
            String recordType, String targetKey, String summary, Supplier<String> subject) {
        try {
            return Optional.of(RecordValues.ofSummary(recordType, targetKey, summary));
        } catch (IllegalArgumentException e) {
            LOG.warn(
                    "判断の画面の値を読めませんでした: {}、原本種別 {}、{}",
                    subject.get(),
                    printable(recordType),
                    e.getClass().getName());
            return Optional.empty();
        }
    }

    /**
     * ログに書く原本種別（表を直に書き換えて入りうる文字列なので、改行などの制御文字・行と段落の区切り・書式の文字を置き換える。ログの偽造を
     * 防ぐ。判定は表示名の規則 {@code DisplayNameRule} の見分けにくい文字と同じ）。
     */
    private static String printable(String text) {
        StringBuilder printable = new StringBuilder(text.length());
        text.codePoints().forEach(c -> printable.appendCodePoint(isUnprintable(c) ? '_' : c));
        return printable.toString();
    }

    private static boolean isUnprintable(int codePoint) {
        int type = Character.getType(codePoint);
        return Character.isISOControl(codePoint)
                || type == Character.FORMAT
                || type == Character.LINE_SEPARATOR
                || type == Character.PARAGRAPH_SEPARATOR;
    }

    /**
     * 判断を待つ隔離の 1 ページ。
     *
     * @param ingestionIdOrNull 絞る取込 ID（{@code null} なら絞らない）
     * @param requestedPage 求めた頁（1 から）
     */
    @Transactional(readOnly = true)
    public UndecidedRecordPage page(UUID ingestionIdOrNull, int requestedPage) {
        boolean notFound = ingestionIdOrNull != null
                && ingestions.findById(ingestionIdOrNull).isEmpty();
        UUID filter = notFound ? null : ingestionIdOrNull;
        int total = receivedRecords.countUndecidedRecords(filter);
        int page = pageOf(total, requestedPage);
        List<UndecidedRecord> quarantines = total == 0 ? List.of() : quarantinesOf(filter, page);
        if (quarantines.isEmpty() && total > 0) {
            // 件数を数えてからページを読むまでに、そのページの隔離がすべて判断された（まとめての却下で起きやすい）。数え直して最後の
            // ページに寄せ、1 回だけ読み直す。読み直しても空なら 0 件にする（「0〜0 件目」を示さない。Bolt 39d2b の開始準備の決定 (8)）
            total = receivedRecords.countUndecidedRecords(filter);
            page = pageOf(total, page);
            quarantines = total == 0 ? List.of() : quarantinesOf(filter, page);
        }
        if (quarantines.isEmpty()) {
            return new UndecidedRecordPage(List.of(), 0, 1, 0, filter, notFound, 0);
        }
        int pageCount = pageCountOf(total);
        int keylessCount = filter == null ? 0 : receivedRecords.countUndecidedKeylessQuarantines(filter);
        Map<UUID, DataSource> sources = new HashMap<>();
        List<UndecidedRecordPage.Item> items = quarantines.stream()
                .map(quarantine -> item(
                        quarantine,
                        // 取込は情報源への外部キーを持つので、情報源は必ずある
                        sources.computeIfAbsent(
                                quarantine.dataSourceId(),
                                id -> dataSources.findById(id).orElseThrow())))
                .toList();
        return new UndecidedRecordPage(items, total, page, pageCount, filter, notFound, keylessCount);
    }

    /**
     * 1 つの取込の形式の誤りのまとめての却下の画面の中身（Bolt 39d2b）。取込の時刻と情報源と、その取込の判断を待つキーのない隔離の件数。
     * 取込がなければ空（数えず、情報源を引かない）。件数が 0 でも返す（画面が一覧に戻って知らせる）。
     *
     * @param ingestionId 取込 ID
     */
    @Transactional(readOnly = true)
    public Optional<KeylessRejectionTarget> keylessRejectionTarget(UUID ingestionId) {
        return ingestions.findById(ingestionId).map(ingestion -> {
            // 取込は情報源への外部キーを持つので、情報源は必ずある
            DataSource source = dataSources.findById(ingestion.dataSourceId()).orElseThrow();
            return new KeylessRejectionTarget(
                    ingestion.id(),
                    ingestion.ingestedAt(),
                    source.provider(),
                    source.recordType(),
                    receivedRecords.countUndecidedKeylessQuarantines(ingestion.id()));
        });
    }

    /** 件数と求めた頁から示す頁（1 より前なら 1、最後より後なら最後。件数が 0 なら 1）。 */
    private static int pageOf(int total, int requestedPage) {
        return Math.clamp(requestedPage, 1, Math.max(1, pageCountOf(total)));
    }

    private static int pageCountOf(int total) {
        return (total + PAGE_SIZE - 1) / PAGE_SIZE;
    }

    private List<UndecidedRecord> quarantinesOf(UUID filter, int page) {
        return receivedRecords.undecidedRecords(filter, (page - 1) * PAGE_SIZE, PAGE_SIZE);
    }

    /**
     * ID で絞った判断を待つ隔離 1 件（S-17 の判断の画面の中身。Bolt 39d2）。一覧と同じ項目で、隔離でない・判断のある・取込のない受信記録と
     * ないときは空（情報源を引かない）。
     *
     * @param receivedRecordId 受信記録 ID
     */
    @Transactional(readOnly = true)
    public Optional<UndecidedRecordPage.Item> undecidedRecord(UUID receivedRecordId) {
        return receivedRecords
                .undecidedRecord(receivedRecordId)
                .map(quarantine -> item(
                        quarantine,
                        // 取込は情報源への外部キーを持つので、情報源は必ずある
                        dataSources.findById(quarantine.dataSourceId()).orElseThrow()));
    }

    private static UndecidedRecordPage.Item item(UndecidedRecord quarantine, DataSource source) {
        return new UndecidedRecordPage.Item(
                quarantine.receivedRecordId(),
                quarantine.ingestionId(),
                quarantine.ingestedAt(),
                source.provider(),
                source.recordType(),
                quarantine.lineRange(),
                quarantine.classification(),
                quarantine.externalId(),
                quarantine.externalVersion(),
                quarantine.note());
    }
}
