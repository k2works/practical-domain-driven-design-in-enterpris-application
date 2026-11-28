package com.example.cargotracker.shared.annotation.ddd;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 中核ドメインの中心の概念（開発戦略の Living Documentation。Bolt 25）。生成する設計ドキュメント（JIG）で、読み手が最初に見る型を
 * 示すためだけに付ける。業務の規則は持たず、テストでも検査しない。付けるのは貨物予約と追跡記録のような、コンテキストの中心の集約ルートに限る。
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CoreConcept {}
