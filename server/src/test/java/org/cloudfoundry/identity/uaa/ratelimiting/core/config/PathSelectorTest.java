package org.cloudfoundry.identity.uaa.ratelimiting.core.config;

import org.cloudfoundry.identity.uaa.ratelimiting.core.config.exception.RateLimitingConfigException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class PathSelectorTest {
    private static final String NAME = "login";

    @Test
    void pathMatchType() {
        assertThat(PathSelector.pathMatchType("")).as("empty").isNull();
        assertThat(PathSelector.pathMatchType("FRED")).as("FRED").isNull();
        for (PathMatchType value : PathMatchType.values()) {
            assertThat(PathSelector.pathMatchType(value.toString())).as("unchanged Case " + value).isEqualTo(value);
            assertThat(PathSelector.pathMatchType(value.toString().toLowerCase())).as("lower Case " + value).isEqualTo(value);
            assertThat(PathSelector.pathMatchType(value.toString().toUpperCase())).as("upper Case " + value).isEqualTo(value);
        }
    }

    @Test
    void parse() {
        checkNull(null);
        checkNull("");
        checkNull("   ");

        checkException(1, "fred");
        checkException(2, "fred:");
        checkException(3, "fred:/login");
        checkException(11, "equals");
        checkException(12, "equals:");
        checkException(13, "equals:login");
        checkException(21, "StartsWith");
        checkException(22, "StartsWith:");
        checkException(23, "StartsWith:login");
        checkException(21, "Contains");
        checkException(22, "Contains:");
        checkException(23, "Contains:  "); // w/ extraneous spaces ignored
        checkException(31, "Other:login");
        checkException(41, "All:login");

        checkOK("equals  :  /login", PathMatchType.Equals, "/login"); // w/ extraneous spaces ignored
        checkOK("StartsWith:/login", PathMatchType.StartsWith, "/login");
        checkOK("Contains:/login", PathMatchType.Contains, "/login");
        checkOK("pathPattern:/Users/*", PathMatchType.PathPattern, "/Users/*");
        checkOK("pathPattern:/Users/{id}", PathMatchType.PathPattern, "/Users/{id}");
        checkOK("pathPattern:/oauth/**", PathMatchType.PathPattern, "/oauth/**");
        checkOK("pathPattern:/z/{subdomain}/login", PathMatchType.PathPattern, "/z/{subdomain}/login");
        checkOK("pathPattern:/z/{subdomain}/Users/**", PathMatchType.PathPattern, "/z/{subdomain}/Users/**");
        checkOK("Other", PathMatchType.Other, "");
        checkOK("Other:", PathMatchType.Other, "");
        checkOK("All", PathMatchType.All, "");
        checkOK("All:", PathMatchType.All, "");
    }

    @Test
    void parse_pathPattern_invalid() {
        checkException(0, "pathPattern:");
        checkException(0, "pathPattern:  ");
        checkException(0, "pathPattern:login");
        checkException(0, "pathPattern:Users/123");
    }

    private PathSelector check(int offsetIndex, String selectorStr) {
        return PathSelector.parse(selectorStr, offsetIndex, NAME);
    }

    private void checkException(int offsetIndex, String selectorStr) {
        PathSelector ps;
        try {
            ps = check(offsetIndex, selectorStr);
        } catch (RateLimitingConfigException e) {
            String msg = e.getMessage();
            String startsWithFragment = NAME + "'s PathSelector[" + offsetIndex + "]";
            String containsFragment = " in '" + selectorStr.trim() + "'";
            if (!msg.startsWith(startsWithFragment) || !msg.contains(containsFragment)) {
                fail("Message \"" + msg + "\" did not:\n" +
                        "  startWith: \"" + startsWithFragment + "\"\n" +
                        "  & contain: \"" + containsFragment + "\"");
            }
            return;
        }
        assertThat(ps).as("null from '" + selectorStr + "'").isNotNull();
        fail("from '" + selectorStr + "' did NOT expect: '" + ps + "'");
    }

    private void checkNull(String selectorStr) {
        PathSelector ps = check(0, selectorStr);
        assertThat(ps).as("expected null from '" + selectorStr + "', but got: " + ps).isNull();
    }

    private void checkOK(String selectorStr, PathMatchType pathMatchType, String path) {
        PathSelector ps = check(0, selectorStr);
        assertThat(ps).as("null from '" + selectorStr + "'").isNotNull();
        assertThat(ps.getType()).as("type from '" + selectorStr + "'").isEqualTo(pathMatchType);
        assertThat(ps.getPath()).as("path from '" + selectorStr + "'").isEqualTo(path);
    }

    @Test
    void listFrom() {
        checkException(null);
        checkException(List.of());
        checkException(List.of("", "  "));

        List<PathSelector> ps = PathSelector.listFrom(NAME, List.of(
                "equals:/login",
                "StartsWith:/login",
                "Contains:/login",
                "pathPattern:/Users/*",
                "Other",
                "All"));
        assertThat(ps).hasSize(6);
        checkOK(ps, 0, PathMatchType.Equals, "/login");
        checkOK(ps, 1, PathMatchType.StartsWith, "/login");
        checkOK(ps, 2, PathMatchType.Contains, "/login");
        checkOK(ps, 3, PathMatchType.PathPattern, "/Users/*");
        checkOK(ps, 4, PathMatchType.Other, "");
        checkOK(ps, 5, PathMatchType.All, "");
    }

    private void checkOK(List<PathSelector> selectors, int offsetIndex, PathMatchType type, String path) {
        PathSelector ps = selectors.get(offsetIndex);
        assertThat(ps).as("null from offsetIndex: " + offsetIndex).isNotNull();
        assertThat(ps.getType()).as("type from offsetIndex: " + offsetIndex).isEqualTo(type);
        assertThat(ps.getPath()).as("path from offsetIndex: " + offsetIndex).isEqualTo(path);
    }

    private void checkException(List<String> pathSelectors) {
        List<PathSelector> ps;
        try {
            ps = PathSelector.listFrom(NAME, pathSelectors);
        } catch (RateLimitingConfigException e) {
            String msg = e.getMessage();
            String startsWithFragment = "No pathSelectors";
            if (!msg.startsWith(startsWithFragment)) {
                fail("Message \"" + msg + "\" did not:\n" +
                        "  startWith: \"" + startsWithFragment + "\"");
            }
            return;
        }
        assertThat(ps).as("null from: " + pathSelectors).isNotNull();
        fail("from '" + pathSelectors + "' did NOT expect: '" + ps + "'");
    }
}