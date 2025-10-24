package cz.cid.lwp;

import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.UserModel;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DummyLwpIdAuthenticator implements Authenticator {

    // Dummy „databáze“ username → LwpID
    private static final Map<String, String> dummyDb = new HashMap<>();

    static {
        dummyDb.put("jlisnik@svelkyemail.onmicrosoft.com", "5686");
        dummyDb.put("jplandor@svelkyemail.onmicrosoft.com", "4578");
        dummyDb.put("ext_test@awt.eu", "1234");

    }

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        UserModel user = context.getUser();

        if (user == null) {
            context.failure(org.keycloak.authentication.AuthenticationFlowError.UNKNOWN_USER);
            return;
        }

        String username = user.getUsername();

        String lwpId = dummyDb.getOrDefault(username, "LWP-UNKNOWN");

        user.setAttribute("userLWPId", Collections.singletonList(lwpId));

        context.success();
    }

    @Override
    public void action(AuthenticationFlowContext context) {

    }

    @Override
    public boolean requiresUser() { return false; }

    @Override
    public boolean configuredFor(org.keycloak.models.KeycloakSession session, org.keycloak.models.RealmModel realm, UserModel user) { return true; }

    @Override
    public void setRequiredActions(org.keycloak.models.KeycloakSession session, org.keycloak.models.RealmModel realm, UserModel user) {

    }

    @Override
    public void close() {

    }
}
