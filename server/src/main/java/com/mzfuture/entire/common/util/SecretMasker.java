package com.mzfuture.entire.common.util;

import java.util.regex.Pattern;

/// Masks credentials before text is logged or returned to clients.
///
/// Git auth URLs embed the access token as userinfo (`https://<token>@host/...`), and JGit copies that URL
/// verbatim into its exception messages, so any message derived from a git failure must go through here.
public final class SecretMasker {

    /// `scheme://userinfo@` → `scheme://***@`
    private static final Pattern URL_USERINFO = Pattern.compile("(?i)\\b([a-z][a-z0-9+.-]*://)[^/\\s@'\"]+@");

    /// Well-known token formats that might appear outside a URL (GitHub, GitLab)
    private static final Pattern KNOWN_TOKENS = Pattern.compile(
            "\\b(?:github_pat_[A-Za-z0-9_]+|gh[pousr]_[A-Za-z0-9]+|glpat-[A-Za-z0-9_-]+)");

    private static final String MASK = "***";

    private SecretMasker() {
    }

    public static String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String masked = URL_USERINFO.matcher(text).replaceAll("$1" + MASK + "@");
        return KNOWN_TOKENS.matcher(masked).replaceAll(MASK);
    }
}
