# LWP Provider for Keycloak

This project is a small Keycloak authenticator provider that injects a dummy LWP ID into a user's attributes during authentication.

It contains:
- `LwpIdAuthenticator` — the Authenticator implementation (package `cz.cid.lwp`).
- `LwpIdAuthenticatorFactory` — the AuthenticatorFactory that registers the authenticator with Keycloak.

Purpose
-------
This provider is intended as a simple example and a starting point for implementing real logic that looks up and sets a user's LWP ID as a user attribute (attribute key: `userLWPId`).

Quick facts
-----------
- Keycloak tested version: 26.3.5 (provider compiled against `keycloak-core` / `keycloak-server-spi` 26.3.5).
- Artifact: `lwp_provider-1.0-SNAPSHOT.jar` (produced by `mvn package`).

Build
-----
Requirements:
- Java JDK compatible with the configured Maven compiler plugin (see `pom.xml` — currently configured for Java 9).
- Maven 3.x

From the project root run:

```bash
mvn -U clean package
```

This will produce a JAR in `target/` (for example `target/lwp_provider-1.0-SNAPSHOT.jar`).

Install into Keycloak
---------------------
1. Ensure the JAR contains the service registration file `META-INF/services/org.keycloak.authentication.AuthenticatorFactory` that lists the factory FQN. For this project the expected line is:

```
cz.cid.lwp.LwpIdAuthenticatorFactory
```

2. Copy the built JAR to your Keycloak's `providers/` directory (or the location your Keycloak distribution uses for custom providers). Example (on the Keycloak host):

```bash
cp target/lwp_provider-1.0-SNAPSHOT.jar /opt/keycloak/providers/
# Then rebuild/restart Keycloak (depends on distribution):
/opt/keycloak/bin/kc.sh build
systemctl restart keycloak   # or start/stop the server as appropriate
```

For containerized Keycloak follow your distro's instructions for adding custom providers and rebuilding the server image.

Usage in Authentication Flow
----------------------------
After deploying and restarting Keycloak:
- In the Admin Console, go to Realm -> Authentication -> Flows and either add the authenticator to an existing flow or create a new flow.
- Add an execution using the provider display name: `LwpID Authenticator` (display name defined in `LwpIdAuthenticatorFactory#getDisplayType`).

If the execution shows as Disabled or you cannot set it to REQUIRED, check the Troubleshooting section below.

Troubleshooting
---------------
Q: The execution shows as Disabled and I can't set it to REQUIRED in the flow.

Common causes and fixes:

- Service file FQN mismatch
  - Ensure `META-INF/services/org.keycloak.authentication.AuthenticatorFactory` inside the JAR contains exactly:
    `cz.cid.lwp.LwpIdAuthenticatorFactory` — the fully-qualified class name of your factory. If the package or class name differs, update the service file accordingly, repackage and redeploy.

- `requiresUser()` in the Authenticator
  - The authenticator currently implements `requiresUser()`; Keycloak will treat certain executions differently depending on this value. If your authenticator operates on an authenticated user (reads/sets attributes), it should return `true`. In `LwpIdAuthenticator` change:
    ```java
    @Override
    public boolean requiresUser() { return true; }
    ```
    Rebuild and redeploy after this change.

- Requirement choices not advertised by the factory
  - `LwpIdAuthenticatorFactory#getRequirementChoices()` should include `REQUIRED` (and others as needed). This factory already exposes the common choices.

- Split package warning / package name collision
  - If you see warnings about split packages (e.g., `Detected a split package usage`), it means the same Java package appears in multiple JARs on the server. Choose a unique package name (for example include your organization domain) and rebuild to avoid conflicts with other installed providers.

- Maven build warnings about plugin versions
  - The POM should declare plugin versions. This project already sets `maven-compiler-plugin` version in `pom.xml`. If you see warnings, ensure your `pom.xml` contains explicit plugin versions.

Validation checklist
--------------------
- Built JAR contains `META-INF/services/org.keycloak.authentication.AuthenticatorFactory` with the correct factory FQN.
- Authenticator's `requiresUser()` returns `true` if it depends on an authenticated user.
- `LwpIdAuthenticatorFactory#getRequirementChoices()` includes `REQUIRED` so the execution can be set as required in the Admin Console.
- No package collisions with other providers on the Keycloak server.

Development notes and next steps
-------------------------------
- Replace the dummy mapping (`dummyDb`) with real data lookup (database, REST API, LDAP, etc.).
- Add configuration properties to the factory if you need configurable endpoints/credentials.
- Add unit tests covering the factory and authenticator behavior.

License
-------
Put your license here or keep it private as needed.

Contact
-------
For questions about this provider implementation, update or open an issue in the repository with details of Keycloak version and server logs.
