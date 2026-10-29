package com.example.cargotracker.routing.interfaces.web;

import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.OutOfScopeAttemptRecorder;
import com.example.cargotracker.shared.domain.ScopedResourceKind;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * 経路設計の社内の画面の担当範囲の外への状態を変える試行（候補の算出・確定）に応える部品（Bolt 38g の決定 (3)、SEC-12、ADR-019。38f の
 * {@code StaffOutOfScopeResponder} の写し。routing は quotation の画面の層に依存できない）。試行をアクセス・監査の記録に残し、404 を返す
 * （担当範囲の外の案件があることを知らせない）。記録のポートを呼ぶのはこの部品だけで、トランザクションの外で呼ぶ（ArchUnit の名前の規則と
 * 閉じる規則が守る）。照会（GET）は記録せずに 404 にする。
 */
@Component
public class RoutingOutOfScopeResponder {

    private final OutOfScopeAttemptRecorder recorder;

    public RoutingOutOfScopeResponder(OutOfScopeAttemptRecorder recorder) {
        this.recorder = recorder;
    }

    /**
     * 担当範囲の外の試行を記録し、返す 404 を作る。記録の種類はこの部品の中で ROUTING_CASE に固める（呼び手が別のコンテキストの種類を渡せないように。38f2 の {@code BookingOutOfScopeResponder} と同じ形）。
     *
     * @param resourceId 案件番号（正規の表記）
     */
    public ResponseStatusException recordAndNotFound(AuthenticatedActor actor, String resourceId) {
        recorder.recordOutOfScope(actor, ScopedResourceKind.ROUTING_CASE, resourceId);
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
