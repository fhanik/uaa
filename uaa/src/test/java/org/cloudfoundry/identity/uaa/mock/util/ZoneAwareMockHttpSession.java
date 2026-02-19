package org.cloudfoundry.identity.uaa.mock.util;

import org.cloudfoundry.identity.uaa.zone.ZoneContextPathSessionRequestWrapper;
import org.springframework.mock.web.MockHttpSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link MockHttpSession} that pre-creates the subsession attribute map used by
 * {@link org.cloudfoundry.identity.uaa.zone.ZoneContextPathSessionFilter}. Attributes
 * set on this session (e.g. {@code SPRING_SECURITY_CONTEXT_KEY}) are stored inside the
 * subsession map so the filter's {@link org.cloudfoundry.identity.uaa.zone.ZonePathHttpSession}
 * can find them.
 * <p>
 * This makes the session filter invisible to tests: they set attributes as usual and the
 * filter sees them in the correct subsession namespace.
 */
public class ZoneAwareMockHttpSession extends MockHttpSession {

    private final Map<String, Object> subsessionAttributes;

    /**
     * Creates a session with a "default" subsession namespace (empty context path).
     */
    public ZoneAwareMockHttpSession() {
        this("");
    }

    /**
     * Creates a session with a subsession namespace for the given context path.
     * For example, {@code new ZoneAwareMockHttpSession("/uaa")} creates the subsession
     * under the {@code /uaa} context path key.
     */
    public ZoneAwareMockHttpSession(String contextPath) {
        String key = (contextPath != null) ? contextPath : "";
        this.subsessionAttributes = new ConcurrentHashMap<>();
        super.setAttribute(
                ZoneContextPathSessionRequestWrapper.attributeNameForContextPath(key),
                this.subsessionAttributes
        );
    }

    /**
     * Creates a session scoped to the given zone resolution mode.
     * <ul>
     *   <li>{@link ZoneResolutionMode#SUBDOMAIN}: normal {@link MockHttpSession} behavior,
     *       no subsession namespacing.</li>
     *   <li>{@link ZoneResolutionMode#ZONE_PATH}: subsession under context path
     *       {@code /z/{subdomain}}.</li>
     * </ul>
     */
    public ZoneAwareMockHttpSession(ZoneResolutionMode mode, String subdomain) {
        if (mode == ZoneResolutionMode.ZONE_PATH && subdomain != null && !subdomain.isEmpty()) {
            this.subsessionAttributes = new ConcurrentHashMap<>();
            super.setAttribute(
                    ZoneContextPathSessionRequestWrapper.attributeNameForContextPath("/z/" + subdomain),
                    this.subsessionAttributes
            );
        } else {
            this.subsessionAttributes = null;
        }
    }

    @Override
    public void setAttribute(String name, Object value) {
        if (subsessionAttributes != null
                && !name.startsWith(ZoneContextPathSessionRequestWrapper.ATTRIBUTE_NAME_PREFIX)) {
            if (value != null) {
                subsessionAttributes.put(name, value);
            } else {
                subsessionAttributes.remove(name);
            }
        } else {
            super.setAttribute(name, value);
        }
    }

    @Override
    public Object getAttribute(String name) {
        if (subsessionAttributes != null
                && !name.startsWith(ZoneContextPathSessionRequestWrapper.ATTRIBUTE_NAME_PREFIX)) {
            return subsessionAttributes.get(name);
        }
        return super.getAttribute(name);
    }

    @Override
    public void removeAttribute(String name) {
        if (subsessionAttributes != null
                && !name.startsWith(ZoneContextPathSessionRequestWrapper.ATTRIBUTE_NAME_PREFIX)) {
            subsessionAttributes.remove(name);
        } else {
            super.removeAttribute(name);
        }
    }
}
