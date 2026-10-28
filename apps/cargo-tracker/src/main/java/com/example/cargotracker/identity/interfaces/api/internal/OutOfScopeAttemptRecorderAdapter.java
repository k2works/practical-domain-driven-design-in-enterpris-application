package com.example.cargotracker.identity.interfaces.api.internal;

import com.example.cargotracker.identity.application.internal.commandservices.AssignedScopeDenialService;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.OutOfScopeAttemptRecorder;
import com.example.cargotracker.shared.domain.ScopedResourceKind;
import org.springframework.stereotype.Component;

/**
 * 担当範囲の外の試行の記録のポートのインバウンドアダプター（Bolt 38f、ADR-019）。業務のコンテキストの画面の層の部品から呼ばれ、
 * アクセス・監査の記録のサービスに委ねる。トランザクションの約束（外側の中では拒み、自分のトランザクションで書く）はサービスが守る。
 */
@Component
public class OutOfScopeAttemptRecorderAdapter implements OutOfScopeAttemptRecorder {

    private final AssignedScopeDenialService denialService;

    public OutOfScopeAttemptRecorderAdapter(AssignedScopeDenialService denialService) {
        this.denialService = denialService;
    }

    @Override
    public void recordOutOfScope(AuthenticatedActor actor, ScopedResourceKind kind, String resourceId) {
        denialService.recordDenied(actor, kind, resourceId);
    }
}
