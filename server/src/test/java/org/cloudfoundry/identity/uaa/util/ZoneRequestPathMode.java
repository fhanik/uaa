package org.cloudfoundry.identity.uaa.util;

import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Request path mode: default path or zone path {@code /z/{subdomain}/...} where subdomain is arbitrary.
 * Used by tests to parameterize behavior for zone path-based requests.
 */
public enum ZoneRequestPathMode {
    DEFAULT(""),
    ZONE_PATH("test-zone");

    private final String subdomain;

    ZoneRequestPathMode(String subdomain) {
        this.subdomain = subdomain;
    }

    public String getSubdomain() {
        return subdomain;
    }

    public String redirectPrefix() {
        return subdomain.isEmpty() ? "" : "/z/" + subdomain;
    }

    public void applyRequestPath(MockHttpServletRequest request, String pathSuffix) {
        if (subdomain.isEmpty()) {
            request.setRequestURI(pathSuffix);
            request.setServletPath(pathSuffix);
        } else {
            String path = "/z/" + subdomain + pathSuffix;
            request.setRequestURI(path);
            request.setServletPath(path);
        }
    }
}
