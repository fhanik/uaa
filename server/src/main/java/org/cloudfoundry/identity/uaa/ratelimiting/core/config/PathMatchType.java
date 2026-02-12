package org.cloudfoundry.identity.uaa.ratelimiting.core.config;

import java.util.function.Function;

import org.cloudfoundry.identity.uaa.ratelimiting.util.StringUtilities;
import org.springframework.web.util.pattern.PathPatternParser;
import org.springframework.web.util.pattern.PatternParseException;

public enum PathMatchType {
    Equals( path -> path.startsWith("/") ? null : "must start with a slash ('/')" ), //NOSONAR Keep Camelcase, as those are exposed to the yml configuration file
    StartsWith( path -> path.startsWith("/") ? null : "must start with a slash ('/')" ), //NOSONAR
    Contains( path -> !path.isEmpty() ? null : "must not be empty" ), //NOSONAR
    PathPattern( PathMatchType::pathPatternUnacceptable ), //NOSONAR path is a Spring PathPattern (e.g. /Users/*, /api/**)
    Other( path -> path.isEmpty() ? null : "must be empty" ), //NOSONAR
    All( path -> path.isEmpty() ? null : "must be empty" ); //NOSONAR

    private static final PathPatternParser PATH_PATTERN_PARSER = new PathPatternParser();

    private static String pathPatternUnacceptable(String path) {
        if (path == null || !path.startsWith("/")) {
            return "must start with a slash ('/')";
        }
        try {
            PATH_PATTERN_PARSER.parse(path);
            return null;
        } catch (PatternParseException e) {
            return e.getMessage();
        }
    }

    private final Function<String, String> pathChecker;

    PathMatchType(Function<String, String> pathChecker) {
        this.pathChecker = pathChecker;
    }

    public String pathUnacceptable(String path) {
        return pathChecker.apply(path != null ? path : "");
    }

    public static String options() {
        return StringUtilities.options(values());
    }
}
