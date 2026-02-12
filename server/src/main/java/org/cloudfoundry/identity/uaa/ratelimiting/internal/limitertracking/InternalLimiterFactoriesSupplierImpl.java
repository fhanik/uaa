package org.cloudfoundry.identity.uaa.ratelimiting.internal.limitertracking;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import org.cloudfoundry.identity.uaa.ratelimiting.core.CompoundKey;
import org.cloudfoundry.identity.uaa.ratelimiting.core.LoggingOption;
import org.cloudfoundry.identity.uaa.ratelimiting.core.config.LimiterMapping;
import org.cloudfoundry.identity.uaa.ratelimiting.core.config.PathMatchType;
import org.cloudfoundry.identity.uaa.ratelimiting.core.config.PathSelector;
import org.cloudfoundry.identity.uaa.ratelimiting.core.config.RequestsPerWindowSecs;
import org.cloudfoundry.identity.uaa.ratelimiting.core.config.exception.RateLimitingConfigException;
import org.cloudfoundry.identity.uaa.ratelimiting.core.http.AuthorizationCredentialIdExtractor;
import org.cloudfoundry.identity.uaa.ratelimiting.core.http.CallerIdSupplierByType;
import org.cloudfoundry.identity.uaa.ratelimiting.core.http.CallerIdSupplierByTypeFactory;
import org.cloudfoundry.identity.uaa.ratelimiting.core.http.RequestInfo;
import org.cloudfoundry.identity.uaa.ratelimiting.internal.common.CallerIdSupplierByTypeFactoryFactory;
import org.cloudfoundry.identity.uaa.ratelimiting.internal.common.InternalLimiterFactoriesSupplier;
import org.cloudfoundry.identity.uaa.ratelimiting.internal.common.InternalLimiterFactory;

public class InternalLimiterFactoriesSupplierImpl implements InternalLimiterFactoriesSupplier {
    static final String TO_STRING_INDENT = "   ";

    private static final PathPatternParser PATH_PATTERN_PARSER = new PathPatternParser();

    private final Map<String, LimiterMapping> pathEqualsToLimiterMappings = new LinkedHashMap<>();
    private final List<PathPatternToLimiterMapping> pathPatternLimiterMappings;
    private final PathFragmentToLimiterMappings pathStartsWithLimiterMappings;
    private final PathFragmentToLimiterMappings pathContainsLimiterMappings;
    private final LimiterMapping pathOtherLimiterMapping;
    private final LimiterMapping allLimiterMapping;
    private final int limiterMappings;
    private final LoggingOption loggingOption;

    // public for testing
    public final CallerIdSupplierByTypeFactory callerIdSupplierByTypeFactory;

    @Override
    @NotNull
    public LoggingOption getLoggingOption() {
        return loggingOption;
    }

    @Override
    public boolean isSupplierNOOP() {
        return false;
    }

    @Override
    public String getCallerCredentialsIdSupplierDescription() {
        return callerIdSupplierByTypeFactory == null ? "Nope" : callerIdSupplierByTypeFactory.getCallerCredentialsIdSupplierDescription();
    }

    @Override
    public int getLimiterMappings() {
        return limiterMappings;
    }

    @Override
    public LinkedHashMap<CompoundKey, InternalLimiterFactory> factoryMapFor(RequestInfo info) {
        return internalFactoryMapFor(callerIdSupplierByTypeFactory.from(info), info.getServletPath());
    }

    // package friendly for testing
    LinkedHashMap<CompoundKey, InternalLimiterFactory> internalFactoryMapFor(CallerIdSupplierByType callerIdSupplierByType, String servletPath) {
        return mapFrom(callerIdSupplierByType,
                getPathBasedLimiterMappings(servletPath), allLimiterMapping);
    }

    // package friendly for testing
    LimiterMapping getPathBasedLimiterMappings(String servletPath) { // Method shows how the search algorithm works!
        if ((servletPath == null) || servletPath.isEmpty()) {
            return pathOtherLimiterMapping;
        }
        LimiterMapping pathLimiterMappings = pathEqualsToLimiterMappings.get(servletPath);        // 1st - Equals
        if (pathLimiterMappings == null) { pathLimiterMappings = getPathPatternMatch(servletPath); } // 2nd - PathPattern
        if (pathLimiterMappings == null) { pathLimiterMappings = pathStartsWithLimiterMappings.get(servletPath); } // 3rd - StartsWith
        if (pathLimiterMappings == null) { pathLimiterMappings = pathContainsLimiterMappings.get(servletPath); }   // 4th - Contains
        if (pathLimiterMappings == null) { pathLimiterMappings = pathOtherLimiterMapping; }  // 5th - Other
        return pathLimiterMappings;
    }

    private LimiterMapping getPathPatternMatch(String servletPath) {
        PathContainer pathContainer = PathContainer.parsePath(servletPath);
        for (PathPatternToLimiterMapping mapping : pathPatternLimiterMappings) {
            if (mapping.pattern.matches(pathContainer)) {
                return mapping.limiterMapping;
            }
        }
        return null;
    }

    /**
     * Code that creates the compoundKey to factories in a consistent (and hopefully best) order (least likely to have lock contention to most likely).
     */
    // package friendly for testing
    static LinkedHashMap<CompoundKey, InternalLimiterFactory> mapFrom(CallerIdSupplierByType callerIdSupplierByType,
            LimiterMapping propsPath, LimiterMapping propsAll) {
        LinkedHashMap<CompoundKey, InternalLimiterFactory> map = new LinkedHashMap<>();
        WindowType.NON_GLOBAL.addBestTo(map, propsPath, callerIdSupplierByType); // non Globals first
        WindowType.NON_GLOBAL.addBestTo(map, propsAll, callerIdSupplierByType);
        WindowType.GLOBAL.addTo(map, propsPath, callerIdSupplierByType); // then Globals
        WindowType.GLOBAL.addTo(map, propsAll, callerIdSupplierByType);
        return map;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder().append("InternalLimiterFactoriesSupplier:");
        appendTo(sb, PathMatchType.Equals, pathEqualsToLimiterMappings);
        appendTo(sb, PathMatchType.PathPattern, pathPatternLimiterMappings);
        appendTo(sb, PathMatchType.StartsWith, pathStartsWithLimiterMappings);
        appendTo(sb, PathMatchType.Contains, pathContainsLimiterMappings);
        appendTo(sb, PathMatchType.Other, pathOtherLimiterMapping);
        appendTo(sb, PathMatchType.All, allLimiterMapping);
        return sb.append('\n').toString();
    }

    /**
     * Constructor
     *
     * @param credentialIdExtractor null defaults to Client (caller's) IP only
     * @param limiterMappings       if empty it throws an error
     * @throws RateLimitingConfigException if any of the TypeLimiterMappings are bad or collide with another or basic filtering is not included!
     */
    public InternalLimiterFactoriesSupplierImpl(AuthorizationCredentialIdExtractor credentialIdExtractor,
            LoggingOption loggingOption, Collection<LimiterMapping> limiterMappings) {
        callerIdSupplierByTypeFactory = CallerIdSupplierByTypeFactoryFactory.from(credentialIdExtractor);
        this.loggingOption = LoggingOption.deNull(loggingOption);
        int countLimiterMappings = 0;

        List<PathPatternToLimiterMapping> pathPatternMappings = new ArrayList<>();
        List<PathFragmentToLimiterMapping> ptfStartsWiths = new ArrayList<>();
        List<PathFragmentToLimiterMapping> ptfContains = new ArrayList<>();
        LimiterMapping pathOtherLimiterMappingInternal = null;
        LimiterMapping allLimiterMappings = null;
        for (LimiterMapping limiterMapping : limiterMappings) {
            if (limiterMapping != null) {
                countLimiterMappings++;
                List<PathSelector> selectors = limiterMapping.pathSelectors();
                for (PathSelector selector : selectors) {
                    PathMatchType pmType = selector.getType();

                    switch (pmType) {
                        case Equals:
                            pathEqualsToLimiterMappings.put(selector.getPath(), limiterMapping);
                            break;
                        case PathPattern:
                            try {
                                PathPattern pattern = PATH_PATTERN_PARSER.parse(selector.getPath());
                                pathPatternMappings.add(new PathPatternToLimiterMapping(pattern, selector.getPath(), limiterMapping));
                            } catch (Exception e) {
                                throw new RateLimitingConfigException(
                                        "Invalid pathPattern in limiterMapping '" + limiterMapping.name() + "': '" + selector.getPath() + "' - " + e.getMessage(), e);
                            }
                            break;
                        case StartsWith:
                            ptfStartsWiths.add(new PathFragmentToLimiterMapping( selector.getPath(), limiterMapping ));
                            break;
                        case Contains:
                            ptfContains.add(new PathFragmentToLimiterMapping( selector.getPath(), limiterMapping ));
                            break;
                        case Other:
                            pathOtherLimiterMappingInternal = limiterMapping;
                            break;
                        case All:
                            allLimiterMappings = limiterMapping;
                            break;
                        default:
                            throw new RateLimitingConfigException( "Unexpected PathMatchType '" + pmType + "' on: " + limiterMapping.name() );
                    }
                }
            }
        }
        this.pathPatternLimiterMappings = List.copyOf(pathPatternMappings);
        pathStartsWithLimiterMappings = new PathFragmentToLimiterMappings( String::startsWith, ptfStartsWiths );
        pathContainsLimiterMappings = new PathFragmentToLimiterMappings( String::contains, ptfContains );
        this.pathOtherLimiterMapping = pathOtherLimiterMappingInternal;
        this.allLimiterMapping = allLimiterMappings;
        this.limiterMappings = countLimiterMappings;
    }

    @SuppressWarnings("SameParameterValue")
    private static void appendTo(StringBuilder sb, PathMatchType type, Map<String, LimiterMapping> pathLimiterMappings) {
        appendPathMatchType(sb, type, !pathLimiterMappings.isEmpty());
        for (Map.Entry<String, LimiterMapping> entry : pathLimiterMappings.entrySet()) {
            appendLimiterMappingsWithPath(sb, entry.getKey(), entry.getValue());
        }
    }

    private static void appendTo(StringBuilder sb, PathMatchType type, List<PathPatternToLimiterMapping> mappings) {
        appendPathMatchType(sb, type, !mappings.isEmpty());
        for (PathPatternToLimiterMapping mapping : mappings) {
            appendLimiterMappingsWithPath(sb, mapping.patternString, mapping.limiterMapping);
        }
    }

    private static void appendTo(StringBuilder sb, PathMatchType type, PathFragmentToLimiterMappings mappings) {
        appendPathMatchType(sb, type, !mappings.isEmpty());
        mappings.stream().forEach(mapping -> appendLimiterMappingsWithPath(sb, mapping.getPathFragment(), mapping.getLimiterMapping()));
    }

    @SuppressWarnings("SameParameterValue")
    private static void appendTo(StringBuilder sb, PathMatchType type, LimiterMapping limiterMapping) {
        if (limiterMapping != null) {
            appendPathMatchType(sb, type, true);
            appendLimiterMappingsWithPath(sb, null, limiterMapping);
        }
    }

    private static void appendPathMatchType(StringBuilder sb, PathMatchType type, boolean atLeastOne) {
        if (atLeastOne) {
            sb.append('\n').append(TO_STRING_INDENT).append(type).append(':');
        }
    }

    private static void appendLimiterMappingsWithPath(StringBuilder sb, String path, LimiterMapping limiterMapping) {
        sb.append('\n').append(TO_STRING_INDENT).append(TO_STRING_INDENT);
        if (path != null) {
            sb.append(path).append(" -> ");
        }
        sb.append(limiterMapping.name()).append(":");
        boolean multiple = limiterMapping.limitsCount() > 1;
        for (WindowType type : WindowType.ALL_WINDOW_TYPES) {
            appendLimits(sb, multiple, type.windowType(), type.extractRequestsPerWindowFrom(limiterMapping));
        }
    }

    private static void appendLimits(StringBuilder sb, boolean multiple, String limitsName, RequestsPerWindowSecs limits) {
        if (limits != null) {
            if (multiple) {
                sb.append('\n').append(TO_STRING_INDENT).append(TO_STRING_INDENT).append(TO_STRING_INDENT);
            }
            sb.append(limitsName).append(" @ ").append(limits);
        }
    }

    private record PathPatternToLimiterMapping(PathPattern pattern, String patternString, LimiterMapping limiterMapping) {
    }
}
