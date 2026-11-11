# auth-service

Executable OIDC identity adapter on port 8086. Exposes provider configuration and authenticated identity/session claims; validates issuer/audience/signature. It never handles passwords or invents login/token endpoints. Keycloak performs registration, sign-in, logout, session management, credential recovery and supported MFA. Use Authorization Code + PKCE through the public SPA client.
