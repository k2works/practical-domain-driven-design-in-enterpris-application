package com.example.cargotracker.routing.interfaces.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.AuthenticatedActorFixture;
import com.example.cargotracker.shared.domain.OutOfScopeAttemptRecorder;
import com.example.cargotracker.shared.domain.ScopedResourceKind;
import com.example.cargotracker.shared.domain.UserId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 経路設計の社内の画面の担当範囲の外への試行に応える部品の単体テスト（Bolt 38g の行 3a・7。部品がポートを呼ばないと、呼び出しの見張りの
 * 規則は空のまま通るため。38f の {@code StaffOutOfScopeResponderTest} の写し）。
 */
class RoutingOutOfScopeResponderTest {

    @Test
    void 担当範囲の外の試行をポートで1回記録して404を返す() {
        OutOfScopeAttemptRecorder recorder = mock(OutOfScopeAttemptRecorder.class);
        AuthenticatedActor actor = AuthenticatedActorFixture.routeDesignerHandling(new UserId(UUID.randomUUID()));

        ResponseStatusException exception =
                new RoutingOutOfScopeResponder(recorder).recordAndNotFound(actor, "RC-2026-0001");

        then(recorder).should().recordOutOfScope(actor, ScopedResourceKind.ROUTING_CASE, "RC-2026-0001");
        then(recorder).shouldHaveNoMoreInteractions();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
