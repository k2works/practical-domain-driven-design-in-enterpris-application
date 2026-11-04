package com.example.cargotracker.externaldata.application.internal.queryservices;

import com.example.cargotracker.externaldata.domain.model.aggregates.DataSource;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSourceRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.IngestionRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecordRepository;
import com.example.cargotracker.externaldata.domain.model.valueobjects.UndecidedQuarantine;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 判断を待つ隔離の照会。S-17 の一覧に、判断を待つ隔離を取込の新しい順・ファイルの行の順に {@link #PAGE_SIZE} 件ずつ返す（Bolt 39d。開始準備の
 * 決定 (3)・(4)）。取込の時刻と外部 ID・外部版は受信記録の照会が結んで読み、情報源だけをページの中の異なる情報源 ID ごとに 1 回引く。
 * 絞った取込があるかは取込を 1 回引いて確かめ、なければ絞らない一覧を返して見つからないことを示す。判断を待つ隔離が 0 件ならページの照会と
 * 情報源を引かない。求めた頁が 1 より前なら 1 頁目、最後より後なら最後の頁を返す。件数を数えてから頁を読むまでに件数が減って読んだ頁が
 * 空なら、数え直して 1 回だけ読み直す（Bolt 39d2b）。取込で絞ったときだけ、まとめての却下のリンクのためにその取込の判断を待つキーのない
 * 隔離を数える（Bolt 39d2b）。
 * 組み立ては {@code externaldata.infrastructure.config} の {@code @Bean} が担う。
 */
@Service
public class UndecidedRecordQueryService {

    /** 1 ページの件数（S-16 の結果の画面の隔離の理由の 100 件にそろえる。Bolt 39d 計画の開始準備の決定 (3)）。 */
    public static final int PAGE_SIZE = 100;

    private final ReceivedRecordRepository receivedRecords;
    private final IngestionRepository ingestions;
    private final DataSourceRepository dataSources;

    public UndecidedRecordQueryService(
            ReceivedRecordRepository receivedRecords,
            IngestionRepository ingestions,
            DataSourceRepository dataSources) {
        this.receivedRecords = receivedRecords;
        this.ingestions = ingestions;
        this.dataSources = dataSources;
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
        int total = receivedRecords.countUndecidedQuarantines(filter);
        int page = pageOf(total, requestedPage);
        List<UndecidedQuarantine> quarantines = total == 0 ? List.of() : quarantinesOf(filter, page);
        if (quarantines.isEmpty() && total > 0) {
            // 件数を数えてからページを読むまでに、そのページの隔離がすべて判断された（まとめての却下で起きやすい）。数え直して最後の
            // ページに寄せ、1 回だけ読み直す。読み直しても空なら 0 件にする（「0〜0 件目」を示さない。Bolt 39d2b の開始準備の決定 (8)）
            total = receivedRecords.countUndecidedQuarantines(filter);
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

    private List<UndecidedQuarantine> quarantinesOf(UUID filter, int page) {
        return receivedRecords.undecidedQuarantines(filter, (page - 1) * PAGE_SIZE, PAGE_SIZE);
    }

    /**
     * ID で絞った判断を待つ隔離 1 件（S-17 の判断の画面の中身。Bolt 39d2）。一覧と同じ項目で、隔離でない・判断のある・取込のない受信記録と
     * ないときは空（情報源を引かない）。
     *
     * @param receivedRecordId 受信記録 ID
     */
    @Transactional(readOnly = true)
    public Optional<UndecidedRecordPage.Item> undecidedQuarantine(UUID receivedRecordId) {
        return receivedRecords
                .undecidedQuarantine(receivedRecordId)
                .map(quarantine -> item(
                        quarantine,
                        // 取込は情報源への外部キーを持つので、情報源は必ずある
                        dataSources.findById(quarantine.dataSourceId()).orElseThrow()));
    }

    private static UndecidedRecordPage.Item item(UndecidedQuarantine quarantine, DataSource source) {
        return new UndecidedRecordPage.Item(
                quarantine.receivedRecordId(),
                quarantine.ingestedAt(),
                source.provider(),
                source.recordType(),
                quarantine.lineRange(),
                !quarantine.hasExternalRecord(),
                quarantine.externalId(),
                quarantine.externalVersion(),
                quarantine.note());
    }
}
