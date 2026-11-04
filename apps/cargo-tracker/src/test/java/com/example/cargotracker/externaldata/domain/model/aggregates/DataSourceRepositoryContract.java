package com.example.cargotracker.externaldata.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.externaldata.domain.model.aggregates.DataSource.ApprovalStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

/**
 * 情報源のリポジトリの契約（Bolt 39b の行 6）。メモリの実装と PostgreSQL の実装が継いで同じ約束を確かめる。
 * 情報源の選定・承認は W10 の運用タスクなので、ポートは読み取りだけを持ち、情報源は継ぐ側が {@link #given(DataSource)} で入れる。
 * 件数は差で確かめる（開発データや他のテストの情報源が入っていても崩れない）。提供元の名前は乱数の UUID を含めて自分の行で絞る。
 *
 * <p>{@code @Transactional} は継ぐ側ではなくこのクラスに付ける（前例 {@link ExternalDataRecordRepositoriesContract}）。テストの
 * トランザクションの属性はテストのメソッドを宣言したクラスから引かれ、継ぐ側のクラスに付けても継いだメソッドには効かない。メモリの実装では
 * 効かない。
 */
@Transactional
public abstract class DataSourceRepositoryContract {

    protected abstract DataSourceRepository repository();

    /** 情報源を入れる（ポートの外。メモリなら足し、DB なら行を書く）。 */
    protected abstract void given(DataSource source);

    private static DataSource source(ApprovalStatus status) {
        return new DataSource(UUID.randomUUID(), "契約の提供元-" + UUID.randomUUID(), "VOYAGE", status);
    }

    @Test
    void 行6_IDで情報源を引ける() {
        DataSource approved = source(ApprovalStatus.APPROVED);
        given(approved);

        assertThat(repository().findById(approved.id())).contains(approved);
    }

    @Test
    void 行6_入れていないIDの情報源は引けない() {
        assertThat(repository().findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void 行6_候補と停止中は承認済みに数えない() {
        int before = repository().countApproved();

        given(source(ApprovalStatus.CANDIDATE));
        given(source(ApprovalStatus.SUSPENDED));

        assertThat(repository().countApproved()).isEqualTo(before);
    }

    @Test
    void 行6_承認済みの情報源を数える() {
        int before = repository().countApproved();

        given(source(ApprovalStatus.APPROVED));
        given(source(ApprovalStatus.APPROVED));

        assertThat(repository().countApproved()).isEqualTo(before + 2);
    }

    @Test
    void 行4_承認済みの情報源だけを提供元と原本種別の順に並べる() {
        // Bolt 39c の行 4。候補と停止中は並べない。名前は自分の UUID で絞り、順は ASCII の末尾の文字で決まる組にする（照合順序に左右されない）
        String prefix = "契約の提供元-" + UUID.randomUUID();
        DataSource secondVoyage = new DataSource(UUID.randomUUID(), prefix + "-B", "VOYAGE", ApprovalStatus.APPROVED);
        DataSource firstVoyage = new DataSource(UUID.randomUUID(), prefix + "-A", "VOYAGE", ApprovalStatus.APPROVED);
        DataSource firstMilestone =
                new DataSource(UUID.randomUUID(), prefix + "-A", "MILESTONE", ApprovalStatus.APPROVED);
        given(secondVoyage);
        // 情報源は（提供元、原本種別）で一意なので、候補と停止中は別の提供元にする
        given(new DataSource(UUID.randomUUID(), prefix + "-0", "VOYAGE", ApprovalStatus.CANDIDATE));
        given(firstVoyage);
        given(new DataSource(UUID.randomUUID(), prefix + "-C", "VOYAGE", ApprovalStatus.SUSPENDED));
        given(firstMilestone);

        assertThat(repository().findApproved())
                .filteredOn(source -> source.provider().startsWith(prefix))
                .containsExactly(firstMilestone, firstVoyage, secondVoyage);
    }
}
