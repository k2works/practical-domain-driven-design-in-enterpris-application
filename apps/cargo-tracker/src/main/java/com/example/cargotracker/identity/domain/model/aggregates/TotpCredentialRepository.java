package com.example.cargotracker.identity.domain.model.aggregates;

import com.example.cargotracker.identity.domain.model.valueobjects.EncryptedSecret;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Optional;

/**
 * TOTP の資格のリポジトリ（送信ポート）。実装は infrastructure に置く。
 * 刻みの使用は、読んでから書く形にせず、1 文の条件付きの更新で原子的に判定する（IA-INV-10、ADR-011）。
 */
public interface TotpCredentialRepository {

    /**
     * 登録した資格を保存する。同じ利用者の資格が既にあれば {@link DuplicateTotpEnrollmentException}（表の主キー。同時の登録は
     * 1 つだけが通る。Bolt 31b。再登録は {@link #replace}）。
     */
    void add(TotpCredential credential);

    /**
     * 利用者の資格を、再登録した資格で置き換える（Bolt 33b）。保存されている秘密が再登録の前に読んだ秘密のままのときだけ書き、書けたら
     * true（同じ古い秘密を読んだ同時の 2 つの再登録は 1 つだけが通る）。最後に使った刻みは、保存されている値と渡した値の大きい方に
     * する（読んだ後に古い秘密の要求が刻みを使っても戻さない。IA-INV-10）。資格のない利用者は書かずに false。
     *
     * @param expectedSecret 再登録の前に読んだ資格の暗号化した秘密
     */
    boolean replace(TotpCredential credential, EncryptedSecret expectedSecret);

    Optional<TotpCredential> findByUserId(UserId userId);

    /**
     * 保存されている秘密が照合で読んだ秘密と同じで、最後に使った刻みより後の刻みのときだけ、その刻みを最後に使った刻みとして書く。
     * 書けたら true。同じ刻みの同時の使用は 1 つだけが true になる。照合の後に資格を置き換えていたら false（古い秘密のコードを
     * 通さない。Bolt 33b）。資格のない利用者は false（失敗へ倒す。T-42）。
     *
     * @param secret 照合に使った資格の暗号化した秘密
     */
    boolean useStep(UserId userId, EncryptedSecret secret, long step);

    /**
     * システム管理者の解除（Bolt 38c。US-18 AC9）。保存されている資格の登録の時刻が、確認の画面で読んだ時刻のときだけ資格を消し、消せたら
     * true。確認の後に本人が登録し直した資格（登録の時刻が変わる）と、資格のない利用者は消さずに false（同時の 2 つの解除は 1 つだけが
     * true になる）。
     *
     * @param expectedEnrolledAt 確認の画面で読んだ登録の時刻
     */
    boolean remove(UserId userId, UtcInstant expectedEnrolledAt);
}
