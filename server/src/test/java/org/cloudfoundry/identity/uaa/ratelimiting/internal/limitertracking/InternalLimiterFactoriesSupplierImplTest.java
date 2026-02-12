package org.cloudfoundry.identity.uaa.ratelimiting.internal.limitertracking;

import org.cloudfoundry.identity.uaa.ratelimiting.AbstractExceptionTestSupport;
import org.cloudfoundry.identity.uaa.ratelimiting.core.CompoundKey;
import org.cloudfoundry.identity.uaa.ratelimiting.core.config.LimiterMapping;
import org.cloudfoundry.identity.uaa.ratelimiting.core.config.RequestsPerWindowSecs;
import org.cloudfoundry.identity.uaa.ratelimiting.core.http.CallerIdSupplierByType;
import org.cloudfoundry.identity.uaa.ratelimiting.internal.common.InternalLimiterFactory;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

@SuppressWarnings("SameParameterValue")
class InternalLimiterFactoriesSupplierImplTest extends AbstractExceptionTestSupport {
    CallerIdSupplierByType callerIdSupplier = Mockito.mock(CallerIdSupplierByType.class);

    @Test
    void factoriesSupplier_toString() {
        List<LimiterMapping> limiterMappings = List.of(
                LimiterMapping.builder().name("N1").withCallerCredentialsID("2r/1s").pathSelector("startsWith:/F-35B").build(),
                LimiterMapping.builder().name("N2").withCallerRemoteAddressID("4r/2s").pathSelectors("contains:F-22", "equals:/F-35").build(),
                LimiterMapping.builder().name("N3").withCallerRemoteAddressID("2r/1s").pathSelectors("equals:/F-22", "contains:F-35B").build(),
                LimiterMapping.builder().name("N4").withoutCallerID("4r/2s").global("50r/s").pathSelector("startsWith:/F-22").build(),
                LimiterMapping.builder().name("Others").global("150r/5s").pathSelector("other").build(),
                LimiterMapping.builder().name("All").global("100r/3s").pathSelector("All").build());

        InternalLimiterFactoriesSupplierImpl fs = new InternalLimiterFactoriesSupplierImpl(null, null, limiterMappings);
        checkFactoryCollections(fs,
                "   Equals:",
                "      /F-35 -> N2:RemoteAddressID @ 4r/2s",
                "      /F-22 -> N3:RemoteAddressID @ 2r/s",
                "   StartsWith:",
                "      /F-35B -> N1:CredentialsID @ 2r/s",
                "      /F-22 -> N4:",
                "         NoID @ 4r/2s",
                "         Global @ 50r/s",
                "   Contains:",
                "      F-35B -> N3:RemoteAddressID @ 2r/s",
                "      F-22 -> N2:RemoteAddressID @ 4r/2s",
                "   Other:",
                "      Others:Global @ 150r/5s",
                "   All:",
                "      All:Global @ 100r/3s",
                "");
    }

    @Test
    void factoriesSupplier_pathPattern_matching() {
        LimiterMapping pathPatternMapping = LimiterMapping.builder()
                .name("PathPatternScim")
                .withCallerCredentialsID("100r/s")
                .pathSelector("pathPattern:/Users/*")
                .pathSelector("pathPattern:/Groups/{id}")
                .build();
        LimiterMapping equalsMapping = LimiterMapping.builder()
                .name("Exact")
                .withCallerRemoteAddressID("10r/s")
                .pathSelector("equals:/Users/exact")
                .build();
        LimiterMapping other = LimiterMapping.builder().name("Others").global("150r/5s").pathSelector("other").build();
        List<LimiterMapping> limiterMappings = List.of(equalsMapping, pathPatternMapping, other);

        InternalLimiterFactoriesSupplierImpl fs = new InternalLimiterFactoriesSupplierImpl(null, null, limiterMappings);

        // equals wins over pathPattern when path matches exactly
        assertThat(fs.getPathBasedLimiterMappings("/Users/exact")).isEqualTo(equalsMapping);
        // pathPattern /Users/* matches paths with one segment after /Users
        assertThat(fs.getPathBasedLimiterMappings("/Users/123")).isEqualTo(pathPatternMapping);
        assertThat(fs.getPathBasedLimiterMappings("/Users/abc")).isEqualTo(pathPatternMapping);
        // pathPattern /Groups/{id} matches one segment after /Groups
        assertThat(fs.getPathBasedLimiterMappings("/Groups/xyz")).isEqualTo(pathPatternMapping);
        // pathPattern does not match (no segment after /Groups, or unrelated path)
        assertThat(fs.getPathBasedLimiterMappings("/Groups")).isEqualTo(other);
        assertThat(fs.getPathBasedLimiterMappings("/other")).isEqualTo(other);
    }

    @Test
    void factoriesSupplier_pathPattern_invalidPattern_throws() {
        assertThatThrownBy(() -> LimiterMapping.builder()
                .name("BadPattern")
                .global("1r/s")
                .pathSelector("pathPattern:/foo/**/bar")
                .build())
                .hasMessageContaining("BadPattern")
                .hasMessageContaining("/foo/**/bar")
                .hasMessageContaining("**"); // PatternParseException message (e.g. "** pattern elements should be placed at the start or end")
    }

    @Test
    void factoriesSupplier_pathPattern_toString_and_pathsCount() {
        LimiterMapping pathPatternMapping = LimiterMapping.builder()
                .name("PathPatternN")
                .global("50r/s")
                .pathSelector("pathPattern:/api/**")
                .build();
        LimiterMapping other = LimiterMapping.builder().name("Others").global("150r/5s").pathSelector("other").build();
        List<LimiterMapping> limiterMappings = List.of(pathPatternMapping, other);

        InternalLimiterFactoriesSupplierImpl fs = new InternalLimiterFactoriesSupplierImpl(null, null, limiterMappings);

        assertThat(fs.getPathBasedLimiterMappings("/api/v1/foo")).isEqualTo(pathPatternMapping);
        assertThat(fs.toString()).contains("PathPattern:");
        assertThat(fs.toString()).contains("/api/**");
        assertThat(fs.toString()).contains("PathPatternN");
    }

    @Test
    void factoriesSupplier_pathPattern_zonePath_matching() {
        LimiterMapping loginPage = LimiterMapping.builder()
                .name("LoginPage")
                .withCallerRemoteAddressID("50r/1s")
                .pathSelector("pathPattern:/z/{subdomain}/login")
                .build();
        LimiterMapping loginDo = LimiterMapping.builder()
                .name("LoginDo")
                .withCallerRemoteAddressID("50r/s")
                .pathSelector("pathPattern:/z/{subdomain}/login.do")
                .build();
        LimiterMapping oauthToken = LimiterMapping.builder()
                .name("AuthToken")
                .withCallerRemoteAddressID("50r/s")
                .pathSelector("pathPattern:/z/{subdomain}/oauth/token")
                .build();
        LimiterMapping scim = LimiterMapping.builder()
                .name("SCIM")
                .withCallerCredentialsID("500r/s")
                .pathSelector("pathPattern:/z/{subdomain}/Users/**")
                .pathSelector("pathPattern:/z/{subdomain}/Groups/**")
                .build();
        LimiterMapping other = LimiterMapping.builder().name("Others").global("150r/5s").pathSelector("other").build();
        List<LimiterMapping> limiterMappings = List.of(loginPage, loginDo, oauthToken, scim, other);

        InternalLimiterFactoriesSupplierImpl fs = new InternalLimiterFactoriesSupplierImpl(null, null, limiterMappings);

        assertThat(fs.getPathBasedLimiterMappings("/z/myzone/login")).isEqualTo(loginPage);
        assertThat(fs.getPathBasedLimiterMappings("/z/other-zone/login")).isEqualTo(loginPage);
        assertThat(fs.getPathBasedLimiterMappings("/z/myzone/login.do")).isEqualTo(loginDo);
        assertThat(fs.getPathBasedLimiterMappings("/z/zone1/oauth/token")).isEqualTo(oauthToken);
        assertThat(fs.getPathBasedLimiterMappings("/z/zone1/Users/123")).isEqualTo(scim);
        assertThat(fs.getPathBasedLimiterMappings("/z/zone1/Users/abc/xyz")).isEqualTo(scim);
        assertThat(fs.getPathBasedLimiterMappings("/z/zone1/Groups/xyz")).isEqualTo(scim);
        assertThat(fs.getPathBasedLimiterMappings("/z/zone1/Groups/id/extra")).isEqualTo(scim);
        assertThat(fs.getPathBasedLimiterMappings("/login")).isEqualTo(other);
        assertThat(fs.getPathBasedLimiterMappings("/z/myzone/other")).isEqualTo(other);
    }

    @Test
    void factoriesSupplier_validate_Ordered_Map() {
        LimiterMapping n1 = LimiterMapping.builder().name("N1").global("2r/1s").pathSelectors("equals:/F-22", "equals:/F-35A", "equals:/F-35B", "equals:/F-35C", "equals:/F-35I").build();
        LimiterMapping n2 = LimiterMapping.builder().name("N2").global("4r/2s").withoutCallerID("1r/5s").pathSelectors("startsWith:/F-35", "startsWith:/F-22").build();
        LimiterMapping all = LimiterMapping.builder().name("All").global("100r/3s").pathSelector("all").build();
        List<LimiterMapping> limiterMappings = List.of(n1, n2, all);

        InternalLimiterFactoriesSupplierImpl fs = new InternalLimiterFactoriesSupplierImpl(null, null, limiterMappings);
        checkFactoryCollections(fs,
                "   Equals:",
                "      /F-22 -> N1:Global @ 2r/s",
                "      /F-35A -> N1:Global @ 2r/s",
                "      /F-35B -> N1:Global @ 2r/s",
                "      /F-35C -> N1:Global @ 2r/s",
                "      /F-35I -> N1:Global @ 2r/s",
                "   StartsWith:",
                "      /F-22 -> N2:",
                "         NoID @ 1r/5s",
                "         Global @ 4r/2s",
                "      /F-35 -> N2:",
                "         NoID @ 1r/5s",
                "         Global @ 4r/2s",
                "   All:",
                "      All:Global @ 100r/3s",
                "");
        // 1 - non Global !all
        // 2 - non Global all
        // 3 - Global !all
        // 4 - Global all
        check(fs, "/A-10", Expected.from(all).withGlobal()); // only 1 factory : 4 - Global all
        check(fs, "/F-35I", // 2 factories returned
                Expected.from(n1).withGlobal(), // 3 - Global !all
                Expected.from(all).withGlobal()); // 4 - Global all
        check(fs, "/F-22/42986123", // 3 factories returned
                Expected.from(n2).withNoID(), // 1 - non Global !all
                Expected.from(n2).withGlobal(), // 3 - Global !all
                Expected.from(all).withGlobal()); // 4 - Global all
    }

    static final class Expected {
        private final LimiterMapping limiterMapping;
        private String callerID;
        private String windowType;
        private RequestsPerWindowSecs requestsPerWindow;

        static Expected from(LimiterMapping limiterMapping) {
            return new Expected(limiterMapping);
        }

        Expected withGlobal() {
            return withCannedCallerID(WindowType.GLOBAL);
        }

        Expected withNoID() {
            return withCannedCallerID(WindowType.NON_GLOBAL.NoID);
        }

        private Expected withCannedCallerID(WindowType windowType) {
            callerID = windowType.cannedCallerID();
            this.windowType = windowType.windowType();
            requestsPerWindow = windowType.extractRequestsPerWindowFrom(limiterMapping);
            return this;
        }

        private Expected(LimiterMapping limiterMapping) {
            this.limiterMapping = limiterMapping;
        }

        CompoundKey compoundKey() {
            return CompoundKey.from(limiterMapping.name(), windowType, callerID);
        }
    }

    private void check(InternalLimiterFactoriesSupplierImpl fs, String path, Expected... orderedExpectedData) {
        LinkedHashMap<CompoundKey, InternalLimiterFactory> map = fs.internalFactoryMapFor(callerIdSupplier, path);
        Iterator<CompoundKey> keys = map.keySet().iterator(); // ordered set
        for (int i = 0; i < orderedExpectedData.length; i++) {
            Expected expected = orderedExpectedData[i];
            if (!keys.hasNext()) {
                fail("expected " + orderedExpectedData.length + " factories, but got only " + i + ": " + map);
            }
            CompoundKey key = keys.next();
            InternalLimiterFactoryImpl factory = (InternalLimiterFactoryImpl) map.get(key);
            assertThat(key).as("key mismatch on expected[" + i + "]").isEqualTo(expected.compoundKey());
            assertThat(factory.getRequestsPerWindow()).as("RequestsPerWindow mismatch on expected[" + i + "]").isEqualTo(expected.requestsPerWindow);
            assertThat(factory.getName()).as("name mismatch on expected[" + i + "]").isEqualTo(expected.limiterMapping.name());
            assertThat(factory.getWindowType()).as("windowType mismatch on expected[" + i + "]").isEqualTo(expected.windowType);
        }
        if (keys.hasNext()) {
            fail("expected " + orderedExpectedData.length + " factories, but got " + map.size() + ": " + map);
        }
    }

    private void checkFactoryCollections(InternalLimiterFactoriesSupplierImpl fs, String... lines) {
        String lfsString = fs.toString();
        StringBuilder sb = new StringBuilder().append("InternalLimiterFactoriesSupplier:");
        for (String line : lines) {
            sb.append('\n').append(line);
        }
        assertThat(lfsString).isEqualTo(sb.toString());
    }
}