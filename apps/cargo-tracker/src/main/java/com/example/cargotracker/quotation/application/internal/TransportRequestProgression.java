package com.example.cargotracker.quotation.application.internal;

import com.example.cargotracker.quotation.domain.model.aggregates.ConcurrentTransportRequestUpdateException;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestStatus;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiPredicate;
import org.slf4j.Logger;

/**
 * 見積りのイベントと予約の通知を受けて輸送要求を先の状態へ進める（DE-03・DE-16・DE-21・DE-04 の listener と、DE-07 を受ける公開 API の
 * 実装に共通。Bolt 23 で eventhandlers から移した）。
 * 読み込み、進め、変えたときだけ保存する。再配信のほかに状態を変えなかったときは、結果整合が崩れた手がかりとして警告のログを残す
 * （Bolt 9・10 レビュー R-06）。
 *
 * <p>これらの listener は別々のトランザクションで同じ輸送要求を並行して更新し得る（提示の直後の詳細経路設計の依頼、割り当ての直後の
 * 承認）。保存が楽観ロックの競合で失敗したら、同じトランザクションで読み直してやり直す。進める操作は冪等で先へだけ進めるため、
 * 先にほかの listener が進めていれば何もしない。競合が上限の回数続いたら例外を投げ、トランザクションごと戻して発行の記録を未完了に
 * 残す（再配信に任せる。ADR-014。Bolt 22 の割り込み）。
 */
public final class TransportRequestProgression {

    /** 楽観ロックの競合で読み直してやり直す上限の回数（最初の試みを含む）。 */
    public static final int MAX_ATTEMPTS = 3;

    private final TransportRequestRepository repository;
    private final Logger log;
    private final String eventName;
    private final TransportRequestStatus target;
    private final String targetName;
    private final BiPredicate<TransportRequest, Integer> advance;

    /**
     * @param log 警告を残す listener のロガー
     * @param eventName ログに出すイベントの番号（例: DE-03）
     * @param target 進める先の状態
     * @param targetName ログに出す進める先の状態の名前（例: 見積提示済み）
     * @param advance 輸送要求を見積りの対象の版で進める操作（状態を変えたら true）
     */
    public TransportRequestProgression(
            TransportRequestRepository repository,
            Logger log,
            String eventName,
            TransportRequestStatus target,
            String targetName,
            BiPredicate<TransportRequest, Integer> advance) {
        this.repository = repository;
        this.log = log;
        this.eventName = eventName;
        this.target = target;
        this.targetName = targetName;
        this.advance = advance;
    }

    /**
     * 輸送要求を見積りの対象の版で進める。
     *
     * @return 進めた結果
     */
    public Result advance(UUID transportRequestId, UUID quotationId, int quotedVersionNo) {
        for (int attempt = 1; ; attempt++) {
            try {
                return advanceOnce(transportRequestId, quotationId, quotedVersionNo);
            } catch (ConcurrentTransportRequestUpdateException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw e;
                }
            }
        }
    }

    private Result advanceOnce(UUID transportRequestId, UUID quotationId, int quotedVersionNo) {
        Optional<TransportRequest> found = repository.findById(new TransportRequestId(transportRequestId));
        if (found.isEmpty()) {
            log.warn("{} の輸送要求が見つからない: 輸送要求 {}、見積り {}", eventName, transportRequestId, quotationId);
            return Result.NOT_FOUND;
        }
        TransportRequest request = found.get();
        if (advance.test(request, quotedVersionNo)) {
            repository.update(request);
            return Result.ADVANCED;
        }
        if (request.hasReached(target, quotedVersionNo)) {
            return Result.ALREADY_REACHED;
        }
        log.warn(
                "{} で輸送要求を{}にしなかった: 輸送要求 {}、見積り {} の版 {}、現在の版 {}、状態 {}",
                eventName,
                targetName,
                transportRequestId,
                quotationId,
                quotedVersionNo,
                request.currentVersion().versionNo(),
                request.status());
        return Result.NOT_ADVANCED;
    }

    /** 進めた結果。 */
    public enum Result {
        /** 進めた。 */
        ADVANCED,
        /** すでにその状態まで進んでいた（再配信・遅れて届いたイベント。何もしなかった）。 */
        ALREADY_REACHED,
        /** 業務の理由で進めなかった（警告のログを残した）。 */
        NOT_ADVANCED,
        /** 輸送要求が見つからない（警告のログを残した）。 */
        NOT_FOUND
    }
}
