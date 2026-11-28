package com.example.cargotracker.quotation.interfaces.web;

import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.OutOfScopeAttemptRecorder;
import com.example.cargotracker.shared.domain.ScopedResourceKind;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * 社内の画面の担当範囲の外への確定の試行に応える部品（Bolt 38f の決定 (10)、SEC-12、ADR-019）。試行をアクセス・監査の記録に残し、
 * 404 を返す（担当範囲の外の輸送要求があることを知らせない）。記録のポートを呼ぶのはこの部品だけで、トランザクションの外で呼ぶ
 * （ポートは外側のトランザクションの中では拒む。ArchUnit の規則が守る）。照会（GET）は記録せずに 404 にする。
 */
@Component
public class StaffOutOfScopeResponder {

    private final OutOfScopeAttemptRecorder recorder;

    public StaffOutOfScopeResponder(OutOfScopeAttemptRecorder recorder) {
        this.recorder = recorder;
    }

    /**
     * 担当範囲の外の試行を記録し、返す 404 を作る。
     *
     * @param resourceId 業務番号（見積りは「業務番号/見積り番号」）
     */
    public ResponseStatusException recordAndNotFound(
            AuthenticatedActor actor, ScopedResourceKind kind, String resourceId) {
        recorder.recordOutOfScope(actor, kind, resourceId);
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
