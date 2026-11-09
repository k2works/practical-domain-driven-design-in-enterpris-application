package com.example.cargotracker.externaldata.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.externaldata.domain.model.aggregates.AdoptedValue.TargetType;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

    /** 外部 ID・外部版の外部原本を用意し、その ID を返す（ポートの外。この外部版の採用値の照会の絞り込み。Bolt 39d3）。 */
    protected abstract UUID givenExternalRecord(String externalId, String externalVersion);

    /** その外部原本を指す採用の受信記録を用意し、その ID を返す（ポートの外。Bolt 39d3）。 */
    protected abstract UUID givenReceivedRecordOf(UUID externalRecordId);

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
    void 行15_採用値のリポジトリは追記と受信記録IDでの照会とこの外部版の採用値の照会だけを持ち更新と削除を持たない() {
        assertThat(Arrays.stream(AdoptedValueRepository.class.getDeclaredMethods())
                        .map(Method::getName))
                .containsExactlyInAnyOrder("add", "findByReceivedRecordId", "currentOf");
    }

    // --- Bolt 39d3 の行 8（この外部版の採用値。開始準備の決定 (5)、【業務の判断】(j)、T-245）。並びは書く順を逆にしたデータで確かめる ---

    private static AdoptedValue adoptedValueAt(UUID id, UUID receivedRecordId, Instant publishedAt) {
        return new AdoptedValue(id, receivedRecordId, TargetType.VOYAGE, "V2611E", VOYAGE_SUMMARY, "3", publishedAt);
    }

    @Test
    void Bolt39d3_行8_この外部版の採用値は同じ外部原本の採用値のうち公開した時刻の最も新しいもので別の外部版とほかの外部原本を含めない() {
        UUID thisVersion = givenExternalRecord("V2611E", "3");
        UUID newerVersion = givenExternalRecord("V2611E", "4");
        UUID otherVoyage = givenExternalRecord("V2612E", "3");
        AdoptedValue newest =
                adoptedValueAt(UUID.randomUUID(), givenReceivedRecordOf(thisVersion), PUBLISHED_AT.plusSeconds(7200));
        AdoptedValue oldest = adoptedValueAt(UUID.randomUUID(), givenReceivedRecordOf(thisVersion), PUBLISHED_AT);
        AdoptedValue ofNewerVersion = adoptedValueAt(
                UUID.randomUUID(), givenReceivedRecordOf(newerVersion), PUBLISHED_AT.plusSeconds(10_800));
        AdoptedValue ofOtherVoyage =
                adoptedValueAt(UUID.randomUUID(), givenReceivedRecordOf(otherVoyage), PUBLISHED_AT.plusSeconds(14_400));

        // 新しい方を先に書く（書く順に頼らず公開した時刻で選ぶ。T-245）
        adoptedValues().add(newest);
        adoptedValues().add(oldest);
        adoptedValues().add(ofNewerVersion);
        adoptedValues().add(ofOtherVoyage);

        assertThat(adoptedValues().currentOf(thisVersion)).contains(newest);
        assertThat(adoptedValues().currentOf(newerVersion)).contains(ofNewerVersion);
        assertThat(adoptedValues().currentOf(otherVoyage)).contains(ofOtherVoyage);
    }

    @Test
    void Bolt39d3_行8_同じ公開した時刻の採用値は採用値IDの順の最後() {
        UUID externalRecordId = givenExternalRecord("V2611E", "3");
        // ID は先頭の桁だけを変え、符号付き・符号なしのどちらで比べても文字列の表現と同じ順にする
        AdoptedValue last = adoptedValueAt(
                UUID.fromString("70000000-0000-0000-0000-000000000000"),
                givenReceivedRecordOf(externalRecordId),
                PUBLISHED_AT);
        AdoptedValue first = adoptedValueAt(
                UUID.fromString("20000000-0000-0000-0000-000000000000"),
                givenReceivedRecordOf(externalRecordId),
                PUBLISHED_AT);

        adoptedValues().add(last);
        adoptedValues().add(first);

        assertThat(adoptedValues().currentOf(externalRecordId)).contains(last);
    }

    @ParameterizedTest(name = "小さい方から書く {0}")
    @ValueSource(booleans = {true, false})
    void Bolt39d4_行23_同じ公開した時刻の採用値は符号で違うUUIDでも符号なしの順の最後を返す(boolean lowFirst) {
        // Bolt 39d4 の決定 (12)。上の位が符号で違う 2 つの UUID（符号付きで比べると 80000000 が先になる組）。書く順を両方にする（T-245）
        UUID externalRecordId = givenExternalRecord("V2611E", "3");
        AdoptedValue low = adoptedValueAt(
                UUID.fromString("7fffffff-0000-0000-0000-000000000000"),
                givenReceivedRecordOf(externalRecordId),
                PUBLISHED_AT);
        AdoptedValue high = adoptedValueAt(
                UUID.fromString("80000000-0000-0000-0000-000000000000"),
                givenReceivedRecordOf(externalRecordId),
                PUBLISHED_AT);

        adoptedValues().add(lowFirst ? low : high);
        adoptedValues().add(lowFirst ? high : low);

        assertThat(adoptedValues().currentOf(externalRecordId)).contains(high);
    }

    @Test
    void Bolt39d3_行8_採用値のない外部原本のこの外部版の採用値は空() {
        UUID externalRecordId = givenExternalRecord("V2611E", "3");

        assertThat(adoptedValues().currentOf(externalRecordId)).isEmpty();
        assertThat(adoptedValues().currentOf(UUID.randomUUID())).isEmpty();
    }
}
