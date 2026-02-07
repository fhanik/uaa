package org.cloudfoundry.identity.uaa.util;

import org.cloudfoundry.identity.uaa.zone.IdentityZoneHolder;
import org.cloudfoundry.identity.uaa.zone.MultitenancyFixture;
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

    /** Path prefix for redirects/links: "" for DEFAULT, "/z/test-zone" for ZONE_PATH. */
    public String redirectPrefix() {
        return subdomain.isEmpty() ? "" : "/z/" + subdomain;
    }

    /** Sets IdentityZoneHolder so it matches this mode. ZONE_PATH uses a test zone; DEFAULT leaves current zone. */
    public void setZone() {
        if (this == ZONE_PATH) {
            IdentityZoneHolder.set(MultitenancyFixture.identityZone("test-zone-id", subdomain));
        }
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
