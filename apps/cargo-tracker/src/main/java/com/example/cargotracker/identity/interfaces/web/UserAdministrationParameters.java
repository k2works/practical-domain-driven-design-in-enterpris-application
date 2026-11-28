package com.example.cargotracker.identity.interfaces.web;

import com.example.cargotracker.identity.domain.model.valueobjects.SuspensionReason;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * S-20 の controller が共有する、送られた値の解釈（Bolt 34f。Bolt 38b3b から利用者の登録も）。ID の誤りは 404（いない利用者と同じ）、知らない名前と数でない版は
 * 400。状態を持たない。
 */
final class UserAdministrationParameters {

    private UserAdministrationParameters() {}

    /** 利用者 ID。形の誤った ID は 404。 */
    static UserId userId(String id) {
        try {
            return new UserId(UUID.fromString(id));
        } catch (IllegalArgumentException _) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    /** 企業 ID（Bolt 38b3b。利用者の登録）。形の誤った ID は 404（いない企業と同じ）。 */
    static CompanyId companyId(String id) {
        try {
            return new CompanyId(UUID.fromString(id));
        } catch (IllegalArgumentException _) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    /** 送られた役割の名前を役割にする。なければ空、知らない名前は 400。 */
    static Set<Role> roles(List<String> roles) {
        if (roles == null) {
            return Set.of();
        }
        try {
            return roles.stream().map(Role::valueOf).collect(Collectors.toUnmodifiableSet());
        } catch (IllegalArgumentException _) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
    }

    /** 送られた担当企業の ID を企業 ID にする（Bolt 38e）。なければ空、形の誤りと重複は 400。 */
    static Set<CompanyId> companies(List<String> companies) {
        if (companies == null) {
            return Set.of();
        }
        try {
            Set<CompanyId> parsed = companies.stream()
                    .map(id -> new CompanyId(UUID.fromString(id)))
                    .collect(Collectors.toUnmodifiableSet());
            if (parsed.size() != companies.size()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            }
            return parsed;
        } catch (IllegalArgumentException _) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
    }

    /** 送られた停止の理由の名前を理由にする。空や未指定は理由なし（停止なら判定が拒む）、知らない名前は 400。 */
    static Optional<SuspensionReason> suspensionReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(SuspensionReason.valueOf(reason));
        } catch (IllegalArgumentException _) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
    }

    /** 送られた版を数にする。ない・数でなければ 400。 */
    static long version(String version) {
        if (version == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        try {
            return Long.parseLong(version);
        } catch (NumberFormatException _) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
    }
}
