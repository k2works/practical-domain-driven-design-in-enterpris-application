package spike.totp;

import java.time.Instant;
import java.util.List;

@org.springframework.stereotype.Component
/** 骨組み（Red）: スパイクの利用者（荷主担当者 1 人）。TOTP の秘密・回復コード・使ったコード・失敗の回数を持つ。 */
public class SpikeUsers {

    public static final String SHIPPER = "shipper@example.com";
    public static final String PASSWORD = "correct horse battery staple";

    public void reset() {}

    public String currentCode(String username, Instant at) {
        return "";
    }

    public List<String> recoveryCodes(String username) {
        return List.of("");
    }
}
