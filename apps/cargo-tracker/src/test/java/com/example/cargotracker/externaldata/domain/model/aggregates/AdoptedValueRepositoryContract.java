package com.example.cargotracker.externaldata.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.externaldata.domain.model.aggregates.AdoptedValue.TargetType;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

/**
 * 採用値のリポジトリの契約（Bolt 39b3 の行 1・15）。メモリと H2 と PostgreSQL の実装が継いで同じ約束を確かめる。採用値は受信記録への外部キーを
 * 持つので、継ぐ側が {@link #givenReceivedRecord()} で採用の受信記録を用意する（ポートの外）。
 *
 * <p>{@code @Transactional} は継ぐ側ではなくこのクラスに付ける（テストのトランザクションの属性はテストのメソッドを宣言したクラスから
 * 引かれる。Bolt 39b の T-231）。書いた行はロールバックで消える。メモリの実装では効かない。
 */
@Transactional
public abstract class AdoptedValueRepositoryContract {

    /** マイクロ秒までの時刻（DB の精度）。 */
    private static final Instant PUBLISHED_AT = Instant.parse("2026-11-03T01:02:03.123456Z");

    protected abstract AdoptedValueRepository adoptedValues();

    /** 採用の受信記録を用意し、その ID を返す（ポートの外。採用値の外部キーの先）。 */
    protected abstract UUID givenReceivedRecord();

    /** 航海の値の要約（寄港ごとに「港;到着;出発」を「|」でつなぐ。{@code VoyageValues#summary()} の形。T-230）。 */
    private static final String VOYAGE_SUMMARY =
            "JPTYO;;2026-11-19T23:00:00.000000Z|USLAX;2026-12-01T14:00:00.000000Z;";

    /** 主要実績の値の要約（「種類;場所;発生時刻」。{@code MilestoneValues#summary()} の形。T-230）。 */
    private static final String MILESTONE_SUMMARY = "DEPARTURE;JPTYO;2026-11-19T23:00:00.000000Z";

    private static AdoptedValue adoptedValue(UUID receivedRecordId, TargetType targetType, String targetKey) {
        return new AdoptedValue(
                UUID.randomUUID(),
                receivedRecordId,
                targetType,
                targetKey,
                targetType == TargetType.VOYAGE ? VOYAGE_SUMMARY : MILESTONE_SUMMARY,
                "2026-11-03-001",
                PUBLISHED_AT);
    }

    @Test
    void 行1_採用値を追記すると受信記録IDで引け公開した時刻をマイクロ秒まで往復する() {
        AdoptedValue voyage = adoptedValue(givenReceivedRecord(), TargetType.VOYAGE, "V2611E");
        AdoptedValue milestone = adoptedValue(givenReceivedRecord(), TargetType.MILESTONE, "CTABCDEFGH2345");

        adoptedValues().add(voyage);
        adoptedValues().add(milestone);

        assertThat(adoptedValues().findByReceivedRecordId(voyage.receivedRecordId()))
                .contains(voyage);
        assertThat(adoptedValues().findByReceivedRecordId(milestone.receivedRecordId()))
                .contains(milestone);
    }

    @Test
    void 行1_採用値のない受信記録IDでは空() {
        assertThat(adoptedValues().findByReceivedRecordId(givenReceivedRecord()))
                .isEmpty();
    }

    @Test
    void 行15_同じ受信記録への2つ目の採用値の追加は名前付きの例外() {
        UUID receivedRecordId = givenReceivedRecord();
        AdoptedValueRepository repository = adoptedValues();
        repository.add(adoptedValue(receivedRecordId, TargetType.VOYAGE, "V2611E"));
        AdoptedValue again = adoptedValue(receivedRecordId, TargetType.VOYAGE, "V2611E");

        // PostgreSQL は一意制約の違反でテストのトランザクションを中断するので、違反の後に引かない
        assertThatThrownBy(() -> repository.add(again)).isInstanceOf(DuplicateAdoptedValueException.class);
    }

    @Test
    void 行15_採用値のリポジトリは追記と受信記録IDでの照会だけを持ち更新と削除を持たない() {
        assertThat(Arrays.stream(AdoptedValueRepository.class.getDeclaredMethods())
                        .map(Method::getName))
                .containsExactlyInAnyOrder("add", "findByReceivedRecordId");
    }
}
