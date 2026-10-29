package com.example.cargotracker.tracking.interfaces.web;

import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.OutOfScopeAttemptRecorder;
import com.example.cargotracker.shared.domain.ScopedResourceKind;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * 追跡の社内の画面の担当範囲の外へのマイルストーンの登録の試行に応える部品（Bolt 38g の決定 (3)、SEC-12、ADR-019。38f の
 * {@code StaffOutOfScopeResponder} の写し。tracking は quotation の画面の層に依存できない）。試行をアクセス・監査の記録に残し、404 を返す
 * （担当範囲の外の追跡記録があることを知らせない）。記録のポートを呼ぶのはこの部品だけで、トランザクションの外で呼ぶ（ArchUnit の名前の規則と
 * 閉じる規則が守る）。照会（GET）は記録せずに 404 にする。
 */
@Component
public class TrackingOutOfScopeResponder {

    private final OutOfScopeAttemptRecorder recorder;

    public TrackingOutOfScopeResponder(OutOfScopeAttemptRecorder recorder) {
        this.recorder = recorder;
    }

    /**
     * 担当範囲の外の試行を記録し、返す 404 を作る。記録の種類はこの部品の中で TRACKING_RECORD に固める（呼び手が別のコンテキストの種類を渡せないように。38f2 の {@code BookingOutOfScopeResponder} と同じ形）。
     *
     * @param resourceId 追跡番号（形式を確かめた値）
     */
    public ResponseStatusException recordAndNotFound(AuthenticatedActor actor, String resourceId) {
        recorder.recordOutOfScope(actor, ScopedResourceKind.TRACKING_RECORD, resourceId);
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
