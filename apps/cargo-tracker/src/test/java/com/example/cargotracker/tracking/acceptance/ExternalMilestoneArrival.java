package com.example.cargotracker.tracking.acceptance;

import com.example.cargotracker.tracking.domain.model.valueobjects.ExternalMilestone;

/**
 * 外部原本の主要実績が追跡に届くこと（業務ルール層の受入シナリオ。Bolt 39g の行 46）。ステップ定義は他のコンテキストのイベントに依存しないので（AT-05）、追跡の言葉の値で受け、
 * DE-13 への組み立てと listener の呼び出しは受入テストの組み立て（{@code AcceptanceTestConfiguration}）が受け持つ。
 */
@FunctionalInterface
public interface ExternalMilestoneArrival {

    void arrive(ExternalMilestone milestone);
}
