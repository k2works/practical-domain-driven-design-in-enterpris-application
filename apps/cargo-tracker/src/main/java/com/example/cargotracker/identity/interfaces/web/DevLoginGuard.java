package com.example.cargotracker.identity.interfaces.web;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 入力済みの設定（cargotracker.dev-login.*）が dev の外で入っていたら起動させない（守りの 2 層目）。
 * Red の骨組み: まだ確かめない。
 */
@Component
public class DevLoginGuard {

    public DevLoginGuard(DevLoginProperties properties, Environment environment) {
        // Red の骨組み
    }
}
