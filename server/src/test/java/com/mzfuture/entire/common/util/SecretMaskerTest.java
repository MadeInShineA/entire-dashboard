package com.mzfuture.entire.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class SecretMaskerTest {

    @Test
    void masksTokenInUrlUserInfo() {
        String msg = "https://github_pat_ABC123_def@github.com/owner/repo.git: git-upload-pack not permitted on "
                + "'https://github_pat_ABC123_def@github.com/owner/repo.git/'";
        String masked = SecretMasker.mask(msg);
        assertEquals("https://***@github.com/owner/repo.git: git-upload-pack not permitted on "
                + "'https://***@github.com/owner/repo.git/'", masked);
    }

    @Test
    void masksUsernameAndTokenUserInfo() {
        assertEquals("https://***@gitlab.example.com/g/p.git",
                SecretMasker.mask("https://oauth2:glpat-xyz@gitlab.example.com/g/p.git"));
    }

    @Test
    void masksBareKnownTokens() {
        String masked = SecretMasker.mask("token ghp_abcDEF123 and github_pat_11AA_bb and glpat-Zz_9");
        assertFalse(masked.contains("ghp_"));
        assertFalse(masked.contains("github_pat_"));
        assertFalse(masked.contains("glpat-"));
    }

    @Test
    void leavesPlainTextAndUrlsWithoutUserInfoAlone() {
        String text = "Failed to fetch https://github.com/owner/repo.git; user@example.com";
        assertEquals(text, SecretMasker.mask(text));
        assertNull(SecretMasker.mask(null));
    }
}
