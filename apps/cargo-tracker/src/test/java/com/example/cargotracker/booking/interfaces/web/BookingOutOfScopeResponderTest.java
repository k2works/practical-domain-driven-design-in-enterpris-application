package com.example.cargotracker.booking.interfaces.web;

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
 * 予約の社内の画面の担当範囲の外への確定の試行に応える部品の単体テスト（Bolt 38f2。行 8、V4・V12）。ArchUnit の呼び出しの見張りは、
 * 部品がほかのメソッドを呼ばないと空のまま通るので、ここで記録の値を確かめる。
 */
class BookingOutOfScopeResponderTest {

    @Test
    void 担当範囲の外の本予約の確定を見積りとしてポートで1回記録して404を返す() {
        OutOfScopeAttemptRecorder recorder = mock(OutOfScopeAttemptRecorder.class);
        AuthenticatedActor actor = AuthenticatedActorFixture.salesHandling(new UserId(UUID.randomUUID()));

        ResponseStatusException exception =
                new BookingOutOfScopeResponder(recorder).recordAndNotFound(actor, "TR-2026-0001", 2);

        then(recorder).should().recordOutOfScope(actor, ScopedResourceKind.QUOTATION, "TR-2026-0001/2");
        then(recorder).shouldHaveNoMoreInteractions();
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
