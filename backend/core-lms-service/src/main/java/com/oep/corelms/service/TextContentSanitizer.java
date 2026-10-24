package com.oep.corelms.service;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;

/** Applies a small rich-text allowlist to untrusted lesson TEXT content. */
public final class TextContentSanitizer {
    private static final PolicyFactory POLICY = new HtmlPolicyBuilder()
            .allowElements("p", "br", "hr", "div", "span", "ul", "ol", "li", "blockquote",
                    "pre", "code", "b", "strong", "i", "em", "u", "s", "h1", "h2", "h3", "h4", "h5", "h6", "a")
            .allowAttributes("href").onElements("a")
            .allowUrlProtocols("http", "https", "mailto")
            .requireRelNofollowOnLinks()
            .toFactory();

    private TextContentSanitizer() {}

    public static String sanitize(String untrustedHtml) {
        return untrustedHtml == null ? null : POLICY.sanitize(untrustedHtml);
    }
}
