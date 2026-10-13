package org.cloudfoundry.identity.uaa.authentication;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import jakarta.servlet.http.HttpServletRequest;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class UaaAuthenticationDetailsTest {

    /**
     * {@code new UaaAuthenticationDetails(true, "login", "127.0.0.1", "session-1")}, Java-serialized by the
     * version of this class without {@code servletPath} and without an explicit {@code serialVersionUID}.
     */
    private static final String SERIALIZED_BEFORE_SERVLET_PATH =
            "rO0ABXNyAEVvcmcuY2xvdWRmb3VuZHJ5LmlkZW50aXR5LnVhYS5hdXRoZW50aWNhdGlvbi5VYWFBdXRoZW50aWNhdGlvbkRldGFpbHNC"
                    + "XogB+IMu8wIACVoABmFkZE5ld1oAEmlzQXV0aG9yaXphdGlvblNldEwAFGF1dGhlbnRpY2F0aW9uTWV0aG9kdAASTGphdmEv"
                    + "bGFuZy9TdHJpbmc7TAAIY2xpZW50SWRxAH4AAUwACWxvZ2luSGludHQAO0xvcmcvY2xvdWRmb3VuZHJ5L2lkZW50aXR5L3Vh"
                    + "YS9hdXRoZW50aWNhdGlvbi9VYWFMb2dpbkhpbnQ7TAAGb3JpZ2lucQB+AAFMAAxwYXJhbWV0ZXJNYXB0AA9MamF2YS91dGls"
                    + "L01hcDtMAAtyZXF1ZXN0UGF0aHEAfgABTAAJc2Vzc2lvbklkcQB+AAF4cAEAcHQABWxvZ2lucHQACTEyNy4wLjAuMXB0AAd1"
                    + "bmtub3dudAAJc2Vzc2lvbi0x";

    @Test
    void deserializesInstancesPersistedBeforeServletPathWasAdded() throws Exception {
        byte[] bytes = Base64.getDecoder().decode(SERIALIZED_BEFORE_SERVLET_PATH);
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            UaaAuthenticationDetails details = (UaaAuthenticationDetails) in.readObject();
            assertThat(details.isAddNew()).isTrue();
            assertThat(details.getClientId()).isEqualTo("login");
            assertThat(details.getOrigin()).isEqualTo("127.0.0.1");
            assertThat(details.getSessionId()).isEqualTo("session-1");
            assertThat(details.getServletPath()).isNull();
        }
    }

    @Test
    void toStringDoesNotContainSessionId() {
        UaaAuthenticationDetails details = new UaaAuthenticationDetails(false, "clientid", "origin", "1234");
        String toString = details.toString();
        assertThat(toString).contains("sessionId=<SESSION>");
    }

    @Test
    void buildValidAuthenticationDetails() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("clientId", "a");
        UaaAuthenticationDetails details = new UaaAuthenticationDetails(request);
        assertThat(details.getClientId()).isEqualTo("a");
    }

    @Test
    void buildWithoutAuthenticationDetails() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        UaaAuthenticationDetails details = new UaaAuthenticationDetails(request);
        assertThat(details.getClientId()).isNull();
    }

    @Test
    void noLoginHint() {
        HttpServletRequest request = new MockHttpServletRequest();

        UaaAuthenticationDetails details = new UaaAuthenticationDetails(request, "cliendId");
        assertThat(details.getLoginHint()).isNull();
    }

    @Test
    void publicTokenRequest() {
        HttpServletRequest request = new MockHttpServletRequest("POST", "/oauth/token");

        UaaAuthenticationDetails details = new UaaAuthenticationDetails(request, "cliendId");
        details.setAuthenticationMethod("none");
        assertThat(details.getLoginHint()).isNull();
        assertThat(details.isAuthorizationSet()).isFalse();
        assertThat(details.getRequestPath()).isEqualTo("/oauth/token");
        assertThat(details.getAuthenticationMethod()).isEqualTo("none");
    }

    @Test
    void savesRequestParameters() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("key", "value");

        UaaAuthenticationDetails details = new UaaAuthenticationDetails(request, null);
        assertThat(details.getParameterMap().get("key")[0]).isEqualTo("value");
    }

    @Test
    void doesNotSaveUsernamePasswordRequestParameters() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        String[] filteredKeys = {"Username", "username", "Password", "password", "Passcode", "passcode"};
        for (String key : filteredKeys) {
            request.addParameter(key, "value");
        }

        UaaAuthenticationDetails details = new UaaAuthenticationDetails(request, null);
        for (String key : filteredKeys) {
            assertThat(details.getParameterMap()).doesNotContainKey(key);
        }
    }
}
