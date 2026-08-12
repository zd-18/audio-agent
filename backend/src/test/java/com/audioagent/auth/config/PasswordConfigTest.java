package com.audioagent.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordConfigTest {

    @Test
    void bcryptHashNeverEqualsPlaintextAndCanBeVerified() {
        PasswordEncoder encoder = new PasswordConfig().passwordEncoder();
        String plaintext = "secure-pass";
        String encoded = encoder.encode(plaintext);

        assertNotEquals(plaintext, encoded);
        assertTrue(encoder.matches(plaintext, encoded));
    }
}
