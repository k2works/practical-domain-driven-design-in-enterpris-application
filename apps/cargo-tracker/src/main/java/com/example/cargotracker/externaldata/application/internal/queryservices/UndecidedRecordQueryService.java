package com.example.cargotracker.externaldata.application.internal.queryservices;

import com.example.cargotracker.externaldata.domain.model.aggregates.DataSource;
import com.example.cargotracker.externaldata.domain.model.aggregates.DataSourceRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.IngestionRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecordRepository;
import com.example.cargotracker.externaldata.domain.model.valueobjects.UndecidedQuarantine;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 判断を待つ隔離の照会。S-17 の一覧に、判断を待つ隔離を取込の新しい順・ファイルの行の順に {@link #PAGE_SIZE} 件ずつ返す（Bolt 39d。開始準備の
 * 決定 (3)・(4)）。取込の時刻と外部 ID・外部版は受信記録の照会が結んで読み、情報源だけをページの中の異なる情報源 ID ごとに 1 回引く。
 * 絞った取込があるかは取込を 1 回引いて確かめ、なければ絞らない一覧を返して見つからないことを示す。判断を待つ隔離が 0 件ならページの照会と
 * 情報源を引かない。求めた頁が 1 より前なら 1 頁目、最後より後なら最後の頁を返す。
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
        int pageCount = (total + PAGE_SIZE - 1) / PAGE_SIZE;
        int page = Math.clamp(requestedPage, 1, Math.max(1, pageCount));
        if (total == 0) {
            return new UndecidedRecordPage(List.of(), 0, page, 0, filter, notFound);
        }
        List<UndecidedQuarantine> quarantines =
                receivedRecords.undecidedQuarantines(filter, (page - 1) * PAGE_SIZE, PAGE_SIZE);
        Map<UUID, DataSource> sources = new HashMap<>();
        List<UndecidedRecordPage.Item> items = quarantines.stream()
                .map(quarantine -> item(
                        quarantine,
                        // 取込は情報源への外部キーを持つので、情報源は必ずある
                        sources.computeIfAbsent(
                                quarantine.dataSourceId(),
                                id -> dataSources.findById(id).orElseThrow())))
                .toList();
        return new UndecidedRecordPage(items, total, page, pageCount, filter, notFound);
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
