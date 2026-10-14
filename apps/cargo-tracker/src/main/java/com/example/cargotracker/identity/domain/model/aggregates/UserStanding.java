package com.example.cargotracker.identity.domain.model.aggregates;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.util.Objects;

/**
 * 利用者の立場。利用者とその所属企業の組（Bolt 32c、IA-INV-04）。request ごとの確かめで、今の役割と利用状態・企業の有効性を
 * 1 回で読んだ結果。判定はログインと同じ {@link User#authenticationRejection(Company)} で行う。読み取り専用の組で、保存には
 * 使わない（2 つの集約の変更はそれぞれのリポジトリで行う）。{@link User} が password のハッシュを欠かせないので読み出しには
 * 含めるが、主体には写さない。
 *
 * @param user 利用者
 * @param company 所属企業
 */
@ValueObject
public record UserStanding(User user, Company company) {

    public UserStanding {
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(company, "company");
        if (!user.companyId().equals(company.id())) {
            throw new IllegalArgumentException("利用者の所属企業と組にする");
        }
    }
}
