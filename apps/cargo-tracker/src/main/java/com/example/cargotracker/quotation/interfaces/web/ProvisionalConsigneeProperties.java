package com.example.cargotracker.quotation.interfaces.web;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 企業マスターができるまでの仮の荷受人の一覧（Bolt 4 の確認ポイント 2）。荷主はこの一覧から荷受人を選ぶ。
 * US-16（利用者と権限の管理）の Bolt で企業マスターに置き換え、このクラスを消す。
 *
 * @param companies 荷受人の企業
 */
@ConfigurationProperties("cargotracker.provisional-consignees")
public record ProvisionalConsigneeProperties(List<Company> companies) {

    public ProvisionalConsigneeProperties {
        companies = companies == null ? List.of() : List.copyOf(companies);
    }

    /** 一覧にある企業 ID なら、その企業を返す。 */
    public Optional<Company> find(UUID id) {
        return companies.stream().filter(company -> company.id().equals(id)).findFirst();
    }

    /**
     * 仮の荷受人の企業。
     *
     * @param id 企業 ID
     * @param name 画面に出す名前
     */
    public record Company(UUID id, String name) {

        public Company {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
        }
    }
}
