package com.example.cargotracker.identity.infrastructure.crypto;

import com.example.cargotracker.identity.domain.model.aggregates.CommonPasswordList;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * よく使われる password の一覧の実装（Bolt 33a、SEC-05）。同梱の一覧（{@code classpath:security/common-passwords.txt}。出典と抜き出し方は
 * 同じ置き場所の NOTICE）を起動のときに読み、小文字の集合にして照合する。読めない・空のときは起動を止める（照合を黙って空にしない）。
 */
@Component
public final class BundledCommonPasswordList implements CommonPasswordList {

    /** 同梱の一覧の置き場所。 */
    static final String LOCATION = "security/common-passwords.txt";

    private final Set<String> passwords;

    public BundledCommonPasswordList() {
        this(new ClassPathResource(LOCATION));
    }

    BundledCommonPasswordList(Resource resource) {
        this.passwords = read(resource);
    }

    private static Set<String> read(Resource resource) {
        Set<String> read = new HashSet<>();
        try (InputStream in = resource.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isEmpty()) {
                    read.add(line.toLowerCase(Locale.ROOT));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("よく使われる password の一覧を読めない: " + resource.getDescription(), e);
        }
        if (read.isEmpty()) {
            throw new IllegalStateException("よく使われる password の一覧が空: " + resource.getDescription());
        }
        return Set.copyOf(read);
    }

    @Override
    public boolean contains(String password) {
        return password != null && passwords.contains(password.toLowerCase(Locale.ROOT));
    }

    /** 一覧の語数（確かめ用）。 */
    int size() {
        return passwords.size();
    }
}
