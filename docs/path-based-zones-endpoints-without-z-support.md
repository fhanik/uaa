# Endpoints Without `/z/{subdomain}/` Support (Discovery)

This document lists endpoints that do **not** yet have a dual path mapping for `/z/{subdomain}/...`. Security config may already allow `/z/*/path` in some cases; the **controller** (or filter) still only maps the non-zone path. Tests that hit these paths are listed so you can extend them with zone-path permutations or add new tests when adding `/z/` support.

**Legend:**
- **Controller has /z/?** – Controller (or endpoint class) has a second path variant like `/z/{subdomain}/...`.
- **Security has /z/*/?** – At least one security filter chain or requestMatcher includes a `/z/*/...` (or `/z/{subdomain}/...`) pattern for this path.
- **Tests** – Test classes or test methods that perform requests to these paths (get/post/put/delete to the path). These are the tests that may need zone-path parameterization or new cases when you add `/z/` support.

---

## Table of Contents

1.  ✅ [Reset / forgot password (UI)](#1-reset--forgot-password-ui)
2.  ✅ [Change password (UI)](#2-change-password-ui)
3.  ✅ [Change email / verify email (UI)](#3-change-email--verify-email-ui)
4.  ✅ [Force password change (UI)](#4-force-password-change-ui)
5.  ✅ [Logged out (UI)](#5-logged-out-ui)
6.  ✅ [Home and error pages (UI)](#6-home-and-error-pages-ui)
7.  ✅ [Session (UI)](#7-session-ui)
8.  ❌ [Invitations (UI + API)](#8-invitations-ui--api)
9.  ❌ [Profile (UI)](#9-profile-ui)
10. ❌ [Passcode (API / UI)](#10-passcode-api--ui)
11. ❌ [OAuth / token / client admin (API)](#11-oauth--token--client-admin-api-not-yet-covered-by-z)
12. ❌ [Authenticate (API)](#12-authenticate-api)
13. ❌ [Disable User Management and Rate Limiter](#12-authenticate-api)
14. ❌ [Zone Switching - Path Aware Zone Sessions](#13-zone-switching---path-aware-zone-sessions)
15. ❌ [HTML Content - Pages and Emails](#summary-high-level)
16. ❌ [Summary (high-level)](#summary-high-level)
17. ✅ [Pull Request](https://github.com/cloudfoundry/uaa/pull/3730)
18. ✅ [Feature Branch](https://github.com/fhanik/uaa/tree/feature/path-based-zones)
---

## 1. Reset / forgot password (UI)

| Endpoint(s) | Controller / Class | Controller has /z/? | Security has /z/*/? | Tests that touch these endpoints |
|-------------|--------------------|---------------------|----------------------|-----------------------------------|
| `/forgot_password` | ResetPasswordController | No | Yes (LoginSecurityConfiguration login form chain) | ResetPasswordControllerMockMvcTests, ResetPasswordControllerTest, LoginMockMvcTests (forgot_password.do, links) |
| `/forgot_password.do` | ResetPasswordController | No | Yes | Same as above |
| `/email_sent` | ResetPasswordController | No | Yes (noSecurityEndpoints has `/z/*/email_sent`) | ResetPasswordControllerTest, AccountsControllerMockMvcTests (accounts/email_sent) |
| `/reset_password` (HEAD, GET with `code`) | ResetPasswordController | No | Yes (login form chain) | ResetPasswordControllerMockMvcTests, ResetPasswordControllerTest, ResetPasswordAuthenticationEntryPointTests (forward) |
| `/reset_password.do` | ResetPasswordController | No | Yes (login form + ResetPasswordAuthenticationFilter) | ResetPasswordControllerMockMvcTests, ResetPasswordControllerTest, ResetPasswordAuthenticationFilterTest |

**Note:** Security already has `/z/*/forgot_password`, `/z/*/reset_password**`, etc. in LoginSecurityConfiguration. The **controller** still only declares the single path (e.g. `@GetMapping("/forgot_password")`). Adding `/z/{subdomain}/...` to the controller mappings is the remaining work.

---

## 2. Change password (UI)

| Endpoint(s) | Controller / Class | Controller has /z/? | Security has /z/*/? | Tests that touch these endpoints |
|-------------|--------------------|---------------------|----------------------|-----------------------------------|
| `/change_password` | ChangePasswordController | No | No (chain is `/password_*` only) | LoginMockMvcTests (get/change_password, post/change_password.do) |
| `/change_password.do` | ChangePasswordController | No | No | Same as above |

**Note:** LoginSecurityConfiguration has a separate chain for `/password_*` with no `/z/*/` variant. Both controller and security need updates for zone path.

---

## 3. Change email / verify email (UI)

| Endpoint(s) | Controller / Class | Controller has /z/? | Security has /z/*/? | Tests that touch these endpoints |
|-------------|--------------------|---------------------|----------------------|-----------------------------------|
| `/change_email` | ChangeEmailController | No | No (chain is `/email_*` only) | LoginMockMvcTests, ChangeEmailControllerTest |
| `/change_email.do` | ChangeEmailController | No | No | Same as above |
| `/verify_email` | ChangeEmailController | No | No | ChangeEmailControllerTest |

**Note:** LoginSecurityConfiguration has a separate chain for `/email_*` with no `/z/*/` variant. Both controller and security need updates.

---

## 4. Force password change (UI)

| Endpoint(s) | Controller / Class | Controller has /z/? | Security has /z/*/? | Tests that touch these endpoints |
|-------------|--------------------|---------------------|----------------------|-----------------------------------|
| `/force_password_change`, `/force_password_change/` | ForcePasswordChangeController | No | Yes (login form chain) | ForcePasswordChangeControllerTest, ForcePasswordChangeControllerMockMvcTest, UaaAuthenticationFailureHandlerTests (redirect + applyRequestPath) |
| `/force_password_change_completed` | No controller mapping (redirect target; PasswordChangeUiRequiredFilter uses path) | N/A | No (not in noSecurityEndpoints with /z/) | ForcePasswordChangeControllerMockMvcTest (get), PasswordChangeUiRequiredFilterTest (setPathInfo) |

**Note:** Security already has `/z/*/force_password_change/**`. Controller has no `/z/` variant. `force_password_change_completed` is a redirect target and filter path; no explicit `@GetMapping` found—may be served as view or by default. If it gets a controller, it will need `/z/` support too.

---

## 5. Logged out (UI)

| Endpoint(s) | Controller / Class | Controller has /z/? | Security has /z/*/? | Tests that touch these endpoints |
|-------------|--------------------|---------------------|----------------------|-----------------------------------|
| `/logged_out` | LoggedOutEndpoint | No | No (noSecurityEndpoints has `/logged_out` but no `/z/*/logged_out`) | Indirect (logout flows redirect here) |

**Note:** SpringServletXmlSecurityConfiguration noSecurityEndpoints includes `/logged_out` only; no `/z/*/logged_out`. Controller has single path.

---

## 6. Home and error pages (UI)

| Endpoint(s) | Controller / Class | Controller has /z/? | Security has /z/*/? | Tests that touch these endpoints |
|-------------|--------------------|---------------------|----------------------|-----------------------------------|
| `/`, `/home` | HomeController | No | No | LoginMockMvcTests (get("/")), HomeControllerViewTests (get("/home")), IdentityZoneEndpointsMockMvcTests (homeRedirect link) |
| `/error500` | HomeController | No | No (noSecurityEndpoints has `/error**`) | — |
| `/saml_error` | HomeController | No | No (noSecurityEndpoints has `/saml_error`) | — |
| `/oauth_error` | HomeController | No | No | — |
| `/rejected` | HomeController | No | No (noSecurityEndpoints has `/rejected`) | — |

**Note:** noSecurityEndpoints does not add `/z/*/` for these. Controller has no `/z/` variants.

---

## 7. Session (UI)

| Endpoint(s) | Controller / Class | Controller has /z/? | Security has /z/*/? | Tests that touch these endpoints |
|-------------|--------------------|---------------------|----------------------|-----------------------------------|
| `/session` | SessionController | No | No (noSecurityEndpoints has `/session` but no `/z/*/session`) | SessionControllerIntegrationTests |
| `/session_management` | SessionController | No | No | SessionControllerIntegrationTests |

---

## 8. Invitations (UI + API)

| Endpoint(s) | Controller / Class | Controller has /z/? | Security has /z/*/? | Tests that touch these endpoints |
|-------------|--------------------|---------------------|----------------------|-----------------------------------|
| `/invitations/accept` | InvitationsController | No | No (LoginSecurityConfiguration has /invitations/accept without /z/) | InvitationsEndpointMockMvcTests, InvitationsControllerTest, InvitationsServiceMockMvcTests, AbstractLdapMockMvcTest |
| `/invitations/accept.do` | InvitationsController | No | No | Same as above |
| `/invitations/accept_enterprise.do` | InvitationsController | No | No | InvitationsControllerTest, AbstractLdapMockMvcTest |
| `/invitations/sent`, `/invitations/new`, `/invitations/new.do` | InvitationsController | No | No | InvitationsControllerTest (if any hit these) |
| `/invite_users`, `/invite_users/` | InvitationsEndpoint (API) | No | No (LoginSecurityConfiguration /invite_users/** has no /z/) | InvitationsEndpointMockMvcTests |

---

## 9. Profile (UI)

| Endpoint(s) | Controller / Class | Controller has /z/? | Security has /z/*/? | Tests that touch these endpoints |
|-------------|--------------------|---------------------|----------------------|-----------------------------------|
| `/profile`, `/profile/` | ProfileController | No | No (login form chain has no /z/*/ for profile) | ProfileControllerMockMvcTests, LoginMockMvcTests (redirect:profile), InvitationsServiceMockMvcTests |

---

## 10. Passcode (API / UI)

| Endpoint(s) | Controller / Class | Controller has /z/? | Security has /z/*/? | Tests that touch these endpoints |
|-------------|--------------------|---------------------|----------------------|-----------------------------------|
| `/passcode` | PasscodeEndpoint | No | No (OauthEndpointSecurityConfiguration passcode matcher has no /z/) | PasscodeMockMvcTests, TokenMvcMockTests (get("/passcode")), AbstractLdapMockMvcTest, LoginInfoEndpointTests (prompt text) |

---

## 11. OAuth / token / client admin (API – not yet covered by /z/)

| Endpoint(s) | Controller / Class | Controller has /z/? | Security has /z/*/? | Tests that touch these endpoints |
|-------------|--------------------|---------------------|----------------------|-----------------------------------|
| `/oauth/confirm_access` | AccessController | No | No | — |
| `/oauth/error` | AccessController | No | No | — |
| `/oauth/token/revoke/user/{userId}` etc. | TokenRevocationEndpoint | No | No (OauthEndpointSecurityConfiguration /oauth/token/revoke/** has no /z/) | — |
| `/check_token` | CheckTokenEndpoint | No | No | — |
| `/introspect` | IntrospectEndpoint | No | No | — |
| `/oauth/clients/**` | ClientAdminEndpoints, ClientMetadataAdminEndpoints | No | No (ClientAdminSecurityConfiguration has no /z/) | — |
| `/identity-providers/**` | IdentityProviderEndpoints | No | No (IdentityZoneSecurityConfiguration has no /z/) | — |
| `/identity-zones/**` | — | No | No | IdentityZoneEndpointsMockMvcTests (already parameterized for zone path in tests) |
| `/Codes/**` | CodeStoreEndpoints | No | No | — |
| `/email_verifications`, `/email_changes` | ChangeEmailEndpoints (SCIM) | No | No | — |
| `/RateLimitingStatus/**` | RateLimitStatusController | No | No | — |
| `/saml/metadata`, `/saml/metadata/` | SamlMetadataEndpoint | No | No (secFilterOpenSamlEndPoints has no /z/) | — |

---

## 12. Authenticate (API)

| Endpoint(s) | Controller / Class | Controller has /z/? | Security has /z/*/? | Tests that touch these endpoints |
|-------------|--------------------|---------------------|----------------------|-----------------------------------|
| `/authenticate`, `/authenticate/` | RemoteAuthenticationEndpoint | No | No (LoginSecurityConfiguration authenticate chain has no /z/) | LoginMockMvcTests (post("/authenticate")) |

---

## 13. Disable User Management and Rate Limiter

Should filter all the same URLs when the zone is path based.

---

## 14. Zone Switching - Path Aware Zone Sessions

Once steps 1-12 are completed, the system will work for a single session.
Switching zones by changing the /z/ zone path, will cause the SessionResetFilter
to kick in and redirect the user to the default zone login page.

There is a decision to be made at this point, do we support multiple zone sessions when using paths?
If so, there will be a session implementation, very much like the one IdentityZoneResolving/Switching filters
that allows the same server side session hold attributes for multiple zones at the same time

---

## 15. HTML Content - Pages and Emails

See [logged_out.html](server/src/main/resources/templates/web/logged_out.html) for how to handle HTML
Self Explanatory 

## Summary (high-level)

- **UI endpoints most likely to need `/z/` next:** reset_password, forgot_password, change_password, change_email, verify_email, force_password_change (and _completed), logged_out, home, session, invitations (accept flow), profile, passcode. Security already has `/z/*/` for several of these (forgot_password, reset_password, force_password_change, create_account, login, etc.); the **controller** mappings are what’s missing.
- **Security chains that don’t yet have `/z/*/`:** `/password_*`, `/email_*`, noSecurityEndpoints for `/session`, `/session_management`, `/logged_out`, `/`, `/home`, `/error**`, `/saml_error`, `/oauth_error`, `/rejected`; invitations and invite_users; profile; passcode; OAuth confirm_access/error; token revoke; check_token; introspect; client admin; identity-providers; identity-zones; Codes; RateLimitingStatus; SAML metadata; authenticate.
- **Tests:** The “Tests that touch these endpoints” column lists the test classes/methods that perform requests to the given path. When you add `/z/{subdomain}/` support for an endpoint, parameterize those tests with `ZoneResolutionMode` (or equivalent) or add dedicated zone-path tests so both default and `/z/` paths are covered.

