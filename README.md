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


Contact
-------
For questions about this provider implementation, update or open an issue in the repository with details of Keycloak version and server logs.
