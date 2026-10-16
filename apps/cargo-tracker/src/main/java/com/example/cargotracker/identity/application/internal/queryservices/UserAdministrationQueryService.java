package com.example.cargotracker.identity.application.internal.queryservices;

import com.example.cargotracker.identity.domain.model.aggregates.UserRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.UserDetail;
import com.example.cargotracker.identity.domain.model.valueobjects.UserSummary;
import com.example.cargotracker.shared.domain.UserId;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 利用者の管理の照会。S-20 の一覧と詳細を読む（Bolt 34。US-16 の一部）。`password_hash` を読まない射影で受ける。34b までは、
 * すべての企業の利用者を返す（管理範囲は Bolt 34b。確認ポイント 3）。
 */
@Service
public class UserAdministrationQueryService {

    private final UserRepository users;

    public UserAdministrationQueryService(UserRepository users) {
        this.users = users;
    }

    /** 一覧の行を、所属企業の名前・表示名の順に返す。 */
    public List<UserSummary> list() {
        return users.findSummaries();
    }

    /** 詳細を返す。利用者がいなければ空。 */
    public Optional<UserDetail> detail(UserId id) {
        return users.findDetail(id);
    }
}
