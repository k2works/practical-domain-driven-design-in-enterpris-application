package com.example.cargotracker.booking.interfaces.web;

import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.OutOfScopeAttemptRecorder;
import com.example.cargotracker.shared.domain.ScopedResourceKind;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * 予約の社内の画面の担当範囲の外への本予約の確定の試行に応える部品（Bolt 38f2。SEC-12、ADR-019）。試行を見積り（確定の対象。予約は
 * まだない）としてアクセス・監査の記録に残し、404 を返す（担当範囲の外の見積りがあることを知らせない）。記録のポートを呼ぶのは予約の側では
 * この部品だけで、トランザクションの外で呼ぶ（ポートは外側のトランザクションの中では拒む。ArchUnit の規則が守る）。照会（GET）は記録せずに
 * 404 にする。
 */
@Component
public class BookingOutOfScopeResponder {

    private final OutOfScopeAttemptRecorder recorder;

    public BookingOutOfScopeResponder(OutOfScopeAttemptRecorder recorder) {
        this.recorder = recorder;
    }

    /**
     * 担当範囲の外の本予約の確定の試行を記録し、返す 404 を作る。
     *
     * @param actor 試行した操作者
     * @param transportRequestNumber 見積りの公開 API が返す正規の業務番号（生の入力を ID にしない）
     * @param quotationNo 見積り番号
     */
    public ResponseStatusException recordAndNotFound(
            AuthenticatedActor actor, String transportRequestNumber, int quotationNo) {
        recorder.recordOutOfScope(actor, ScopedResourceKind.QUOTATION, transportRequestNumber + "/" + quotationNo);
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
