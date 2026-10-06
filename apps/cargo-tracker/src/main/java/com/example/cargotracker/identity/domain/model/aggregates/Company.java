package com.example.cargotracker.identity.domain.model.aggregates;

import com.example.cargotracker.identity.domain.model.valueobjects.CompanyKind;
import com.example.cargotracker.shared.annotation.ddd.AggregateRoot;
import com.example.cargotracker.shared.domain.CompanyId;
import java.util.Objects;

/**
 * 企業。荷主・荷受人・A 社などの利用者の所属組織。無効な企業の利用者は認証できない（IA-INV-09）。
 * 企業の登録と有効・無効の切り替えは企業マスター（US-16）で作る。
 */
@AggregateRoot
public final class Company {

    private final CompanyId id;
    private final String name;
    private final CompanyKind kind;
    private final boolean active;

    private Company(CompanyId id, String name, CompanyKind kind, boolean active) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.active = active;
    }

    /** 企業を組み立てる（登録とリポジトリの復元が使う）。 */
    public static Company of(CompanyId id, String name, CompanyKind kind, boolean active) {
        return new Company(id, name, kind, active);
    }

    public CompanyId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public CompanyKind kind() {
        return kind;
    }

    public boolean active() {
        return active;
    }
}
