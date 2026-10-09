package com.example.cargotracker.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CommandIdTest {

    @Test
    void UUIDのコマンドIDを作れる() {
        UUID value = UUID.fromString("00000000-0000-0000-0000-000000000001");

        assertThat(new CommandId(value).value()).isEqualTo(value);
    }

    @Test
    void 値のないコマンドIDは作れない() {
        assertThatThrownBy(() -> new CommandId(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void 新しいコマンドIDは発行のたびに違う() {
        assertThat(CommandId.random()).isNotEqualTo(CommandId.random());
    }
}
