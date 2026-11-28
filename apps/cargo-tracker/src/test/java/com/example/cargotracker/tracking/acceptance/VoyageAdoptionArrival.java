package com.example.cargotracker.tracking.acceptance;

import com.example.cargotracker.tracking.domain.model.valueobjects.PublicationOrder;
import com.example.cargotracker.tracking.domain.model.valueobjects.VoyagePortCall;
import java.util.List;

/**
 * 航海の採用値（DE-12）が追跡に届くこと（業務ルール層の受入シナリオ。Bolt 39g4b の行 16。前例 {@link ExternalMilestoneArrival}）。ステップ定義は他のコンテキストの
 * イベントに依存しないので（AT-05）、追跡の言葉の値で受け、DE-12 への組み立てと listener の呼び出しは受入テストの組み立て（{@code AcceptanceTestConfiguration}）が受け持つ。
 */
@FunctionalInterface
public interface VoyageAdoptionArrival {

    void arrive(String voyageNumber, List<VoyagePortCall> portCalls, PublicationOrder order);
}
