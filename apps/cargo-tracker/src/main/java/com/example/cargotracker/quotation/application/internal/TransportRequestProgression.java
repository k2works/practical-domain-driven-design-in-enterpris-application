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
 *
 * <p>再見積りの提示で輸送要求を荷主承認待ちへ戻す DE-24 は「先へだけ進める」の前提を変えるので、届く順の入れ替わりを次の形で守る
 * （ADR-018。Bolt 35b2）。(1) 輸送要求を読んだ後、進める前に、イベントの見積りを読む判定の口（{@link QuotationGate}）を呼び、
 * やり直しのたびに呼び直す（輸送要求を先に読むので、見積りを読んだ後にほかの listener が輸送要求を書けば、保存がぶつかる）。
 * (2) 「届いていても書く」（{@link #writingWhenReached}）の listener は、すでに届いていても保存して版を上げ、先に読んで後から書く
 * 側を必ずぶつける。指定のない呼び手（DE-03・DE-16・DE-07）は今のまま。
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
    private final Guard guard;

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
        this(
                repository,
                log,
                eventName,
                target,
                targetName,
                advance,
                new Guard((request, versionNo) -> request.hasReached(target, versionNo), QuotationGate.OPEN, false));
    }

    private TransportRequestProgression(
            TransportRequestRepository repository,
            Logger log,
            String eventName,
            TransportRequestStatus target,
            String targetName,
            BiPredicate<TransportRequest, Integer> advance,
            Guard guard) {
        this.repository = repository;
        this.log = log;
        this.eventName = eventName;
        this.target = target;
        this.targetName = targetName;
        this.advance = advance;
        this.guard = guard;
    }

    /** 輸送要求を読んだ後、進める前に、イベントの見積りを読んで判定する（やり直しのたびに呼び直す。ADR-018。Bolt 35b2）。 */
    public TransportRequestProgression checking(QuotationGate quotationGate) {
        return new TransportRequestProgression(
                repository, log, eventName, target, targetName, advance, guard.withGate(quotationGate));
    }

    /** すでに届いていても保存して版を上げる（先に読んで後から書く側を楽観ロックでぶつける。ADR-018。Bolt 35b2）。 */
    public TransportRequestProgression writingWhenReached() {
        return new TransportRequestProgression(
                repository, log, eventName, target, targetName, advance, guard.writing());
    }

    /** すでに届いているかの判定を替える（既定は {@link TransportRequest#hasReached}。戻す操作は「ちょうどその状態か」で見る）。 */
    public TransportRequestProgression reachedWhen(BiPredicate<TransportRequest, Integer> reachedWhen) {
        return new TransportRequestProgression(
                repository, log, eventName, target, targetName, advance, guard.withReached(reachedWhen));
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
        QuotationGate.Verdict verdict = guard.gate().check(quotationId);
        if (verdict == QuotationGate.Verdict.SETTLED) {
            return Result.ALREADY_REACHED;
        }
        if (verdict != QuotationGate.Verdict.PROCEED) {
            log.warn(
                    "{} で輸送要求を{}にしなかった（見積りが{}）: 輸送要求 {}、見積り {}",
                    eventName,
                    targetName,
                    verdict.description(),
                    transportRequestId,
                    quotationId);
            return Result.NOT_ADVANCED;
        }
        if (advance.test(request, quotedVersionNo)) {
            repository.update(request);
            return Result.ADVANCED;
        }
        if (guard.reached().test(request, quotedVersionNo)) {
            if (guard.writeWhenReached()) {
                repository.update(request);
            }
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

    /** 届く順の守り（届いているかの判定、進める前の見積りの判定、届いていても書くか。ADR-018。Bolt 35b2）。 */
    private record Guard(BiPredicate<TransportRequest, Integer> reached, QuotationGate gate, boolean writeWhenReached) {

        Guard withGate(QuotationGate newGate) {
            return new Guard(reached, newGate, writeWhenReached);
        }

        Guard writing() {
            return new Guard(reached, gate, true);
        }

        Guard withReached(BiPredicate<TransportRequest, Integer> newReached) {
            return new Guard(newReached, gate, writeWhenReached);
        }
    }

    /** 進めた結果。 */
    public enum Result {
        /** 進めた。 */
        ADVANCED,
        /**
         * 進める必要がない（すでにその状態まで進んでいた再配信・遅れて届いたイベント、または見積りが承認済みでその見積りのイベントが
         * 進める）。状態は変えない（届いていても書く指定なら版だけ上げる）。
         */
        ALREADY_REACHED,
        /** 業務の理由で進めなかった（警告のログを残した）。 */
        NOT_ADVANCED,
        /** 輸送要求が見つからない（警告のログを残した）。 */
        NOT_FOUND
    }
}
