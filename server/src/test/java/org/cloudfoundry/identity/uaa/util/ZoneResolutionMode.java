package org.cloudfoundry.identity.uaa.util;

import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.util.StringUtils;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Mode for resolving identity zone in MockMvc tests: by host subdomain or by path prefix {@code /z/{subdomain}/}.
 * Mirrors {@code org.cloudfoundry.identity.uaa.mock.util.MockMvcUtils.ZoneResolutionMode} for use in server module tests.
 */
public enum ZoneResolutionMode {
    SUBDOMAIN {
        @Override
        public MockHttpServletRequestBuilder createRequestBuilder(String subdomain, HttpMethod method, String contextPath, String pathSuffix) {
            if (StringUtils.hasText(subdomain)) {
                return requestBuilderForMethod(method, contextPath + pathSuffix)
                        .with(new SetServerNameRequestPostProcessor(subdomain + ".localhost"));
            } else {
                return requestBuilderForMethod(method, contextPath + pathSuffix);
            }
        }

        @Override
        public String getServletPath(String subdomain, String pathSuffix) {
            return pathSuffix;
        }
    },
    ZONE_PATH {
        @Override
        public MockHttpServletRequestBuilder createRequestBuilder(String subdomain, HttpMethod method, String contextPath, String pathSuffix) {
            if (StringUtils.hasText(subdomain)) {
                return requestBuilderForMethod(method, contextPath + "/z/{subdomain}" + pathSuffix, subdomain);
            } else {
                return requestBuilderForMethod(method, contextPath + pathSuffix);
            }
        }

        @Override
        public String getServletPath(String subdomain, String pathSuffix) {
            if (StringUtils.hasText(subdomain)) {
                return "/z/" + subdomain + pathSuffix;
            } else {
                return pathSuffix;
            }
        }
    };

    public MockHttpServletRequestBuilder createRequestBuilder(String subdomain, HttpMethod method, String pathSuffix) {
        return createRequestBuilder(subdomain, method, "", pathSuffix);
    }

    public abstract MockHttpServletRequestBuilder createRequestBuilder(
            String subdomain,
            HttpMethod method,
            String contextPath,
            String pathSuffix
    );

    public abstract String getServletPath(String subdomain, String pathSuffix);

    public static MockHttpServletRequestBuilder requestBuilderForMethod(HttpMethod method, String path, Object... pathVars) {
        if (method == HttpMethod.GET) {
            return get(path, pathVars);
        }
        if (method == HttpMethod.POST) {
            return post(path, pathVars);
        }
        if (method == HttpMethod.PUT) {
            return put(path, pathVars);
        }
        if (method == HttpMethod.DELETE) {
            return delete(path, pathVars);
        }
        if (method == HttpMethod.OPTIONS) {
            return options(path, pathVars);
        }
        if (method == HttpMethod.PATCH) {
            return patch(path, pathVars);
        }
        throw new IllegalArgumentException("Unsupported method: " + method);
    }
}
