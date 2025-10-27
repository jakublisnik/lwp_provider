package cz.cid.lwp;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.UserModel;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class LwpIdAuthenticator implements Authenticator {

    // Dummy „databáze“ username → LwpID (fallback)
    private static final Map<String, String> dummyDb = new HashMap<>();

    static {
        dummyDb.put("jlisnik@svelkyemail.onmicrosoft.com", "5686");
        dummyDb.put("jplandor@svelkyemail.onmicrosoft.com", "4578");
        dummyDb.put("ext_test@awt.eu", "1234");

    }

    // Cosmos DB client and container (lazy init)
    private static CosmosClient cosmosClient = null;
    private static CosmosContainer cosmosContainer = null;

    private static void initCosmos() {
        if (cosmosClient != null && cosmosContainer != null) return;

        String endpoint = System.getenv("COSMOS_ENDPOINT");
        String key = System.getenv("COSMOS_KEY");
        String dbName = System.getenv("COSMOS_DATABASE");
        String containerName = System.getenv("COSMOS_CONTAINER");

        if (endpoint == null || key == null || dbName == null || containerName == null) {
            // missing configuration -> will fallback to dummyDb
            return;
        }

        try {
            cosmosClient = new CosmosClientBuilder()
                    .endpoint(endpoint)
                    .key(key)
                    .buildClient();

            CosmosDatabase database = cosmosClient.getDatabase(dbName);
            cosmosContainer = database.getContainer(containerName);
        } catch (Exception e) {
            // if something goes wrong, keep cosmosContainer null and fall back
            cosmosContainer = null;
        }
    }

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        UserModel user = context.getUser();

        if (user == null) {
            context.failure(org.keycloak.authentication.AuthenticationFlowError.UNKNOWN_USER);
            return;
        }

        String username = user.getUsername();

        // Try to locate EmployeeId from user attributes or auth session
        String employeeId = findEmployeeId(context, user);

        String lwpId = null;

        if (employeeId != null) {
            initCosmos();
            if (cosmosContainer != null) {
                try {
                    // Query full document where Item.EmployeeId matches
                    String query = String.format("SELECT * FROM c WHERE c.Item.EmployeeId = %s", employeeId);
                    Object itemsObj = cosmosContainer.queryItems(query, new CosmosQueryRequestOptions(), Object.class);

                    if (itemsObj instanceof Iterable) {
                        for (Object it : (Iterable<?>) itemsObj) {
                            if (it instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> doc = (Map<String, Object>) it;
                                Object headerObj = doc.get("Header");
                                if (headerObj instanceof Map) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> header = (Map<String, Object>) headerObj;
                                    Object userLwpObj = header.get("UserLWPId");
                                    if (userLwpObj != null) {
                                        lwpId = userLwpObj.toString();
                                        break;
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // ignore and fallback
                    lwpId = null;
                }
            }
        }

        // Fallback to dummy DB or default unknown
        if (lwpId == null) {
            lwpId = dummyDb.getOrDefault(username, "LWP-UNKNOWN");
        }

        user.setAttribute("userLWPId", Collections.singletonList(lwpId));

        context.success();
    }

    private String findEmployeeId(AuthenticationFlowContext context, UserModel user) {
        // Try common attribute names on the Keycloak user
        String emp = user.getFirstAttribute("employeeId");
        if (emp == null) emp = user.getFirstAttribute("EmployeeId");

        // Try authentication session notes (if the claim was preserved there)
        if (emp == null) {
            try {
                Object session = context.getAuthenticationSession();
                if (session != null) {
                    // use reflection to avoid direct dependency on AuthenticationSessionModel type
                    try {
                        java.lang.reflect.Method mClientNote = session.getClass().getMethod("getClientNote", String.class);
                        Object val = mClientNote.invoke(session, "employeeId");
                        if (val instanceof String) emp = (String) val;
                    } catch (NoSuchMethodException ignored) {
                    }

                    if (emp == null) {
                        try {
                            java.lang.reflect.Method mClientNote2 = session.getClass().getMethod("getClientNote", String.class);
                            Object val = mClientNote2.invoke(session, "EmployeeId");
                            if (val instanceof String) emp = (String) val;
                        } catch (NoSuchMethodException ignored) {
                        }
                    }

                    if (emp == null) {
                        try {
                            java.lang.reflect.Method mAuthNote = session.getClass().getMethod("getAuthNote", String.class);
                            Object val = mAuthNote.invoke(session, "employeeId");
                            if (val instanceof String) emp = (String) val;
                        } catch (NoSuchMethodException ignored) {
                        }
                    }

                    if (emp == null) {
                        try {
                            java.lang.reflect.Method mAuthNote2 = session.getClass().getMethod("getAuthNote", String.class);
                            Object val = mAuthNote2.invoke(session, "EmployeeId");
                            if (val instanceof String) emp = (String) val;
                        } catch (NoSuchMethodException ignored) {
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // If still null, try realm/user attribute mapping for claim name "employeeId" (case-insensitive)
        if (emp == null) {
            Map<String, java.util.List<String>> attrs = user.getAttributes();
            if (attrs != null) {
                for (Map.Entry<String, java.util.List<String>> e : attrs.entrySet()) {
                    if (e.getKey() != null && e.getKey().equalsIgnoreCase("employeeId")) {
                        java.util.List<String> vals = e.getValue();
                        if (vals != null && !vals.isEmpty()) {
                            emp = vals.get(0);
                            break;
                        }
                    }
                }
            }
        }

        // Trim/cleanup
        if (emp != null) emp = emp.trim();
        return emp;
    }

    @Override
    public void action(AuthenticationFlowContext context) {

    }

    @Override
    public boolean requiresUser() { return true; }

    @Override
    public boolean configuredFor(org.keycloak.models.KeycloakSession session, org.keycloak.models.RealmModel realm, UserModel user) { return true; }

    @Override
    public void setRequiredActions(org.keycloak.models.KeycloakSession session, org.keycloak.models.RealmModel realm, UserModel user) {

    }

    @Override
    public void close() {
        if (cosmosClient != null) {
            try {
                cosmosClient.close();
            } catch (Exception ignored) {
            }
            cosmosClient = null;
            cosmosContainer = null;
        }
    }
}

