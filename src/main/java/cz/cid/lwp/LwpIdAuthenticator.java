package cz.cid.lwp;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.UserModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;

public class LwpIdAuthenticator implements Authenticator {

    private static final Logger logger = LoggerFactory.getLogger(LwpIdAuthenticator.class);

    private static CosmosClient cosmosClient = null;
    private static CosmosContainer cosmosContainer = null;

    private static void initCosmos() {
        logger.debug("initCosmos: checking COSMOS configuration");
        if (cosmosClient != null && cosmosContainer != null) return;

        String endpoint = "https://mobidriverdb.documents.azure.com:443/";
        String key = "6VWHXGXL0HkNU3M9mxTpDUbjvRB9WfeCzDRYvP9YCL8Mz5GEo37iDsPvotT26SMyGZ5CtknbvEMurju0n7SnyA==";
        String dbName = "MobiDriver";
        String containerName = "UserPKP";


        try {
            cosmosClient = new CosmosClientBuilder()
                    .endpoint(endpoint)
                    .key(key)
                    .buildClient();

            CosmosDatabase database = cosmosClient.getDatabase(dbName);
            cosmosContainer = database.getContainer(containerName);
            logger.info("initCosmos: initialized Cosmos container {} in database {}", containerName, dbName);
        } catch (Exception e) {
            logger.warn("initCosmos: failed to initialize Cosmos client/container", e);
            // if something goes wrong, keep cosmosContainer null and fall back
            cosmosContainer = null;
        }
    }

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        UserModel user = context.getUser();

        if (user == null) {
            logger.warn("authenticate: user is null in context");
            context.failure(org.keycloak.authentication.AuthenticationFlowError.UNKNOWN_USER);
            return;
        }

        String username = user.getUsername();
        logger.info("authenticate: start for username={}", username);

        // Try to locate EmployeeId from user attributes or auth session
        String employeeId = findEmployeeId(context, user);
        // Try to locate CompanyId (string) from user attributes or auth session
        logger.info("EMPLOYEE ID : {}", employeeId);


        String lwpId = null;

        if (employeeId == null) {
            logger.warn("authenticate: no employeeId found for user='{}' - skipping Cosmos lookup", username);
        } else {
            initCosmos();
            if (cosmosContainer == null) {
                logger.warn("authenticate: cosmosContainer is null after init - will use fallback for user='{}'", username);
            } else {
                try {
                    // Prepare EmployeeId for SQL: if non-numeric, quote and escape
                    String queryEmployee = employeeId;
                    if (!employeeId.matches("\\d+")) {
                        String escapedEmp = employeeId.replace("'", "''");
                        queryEmployee = "'" + escapedEmp + "'";
                    }
                    // Query only by EmployeeId (company filter removed)
                    String query = String.format("SELECT * FROM c WHERE c.Item.EmployeeId = %s", queryEmployee);
                    logger.info("authenticate: running Cosmos query: {}", query);
                    Iterable<?> itemsObj = cosmosContainer.queryItems(query, new CosmosQueryRequestOptions(), Object.class);

                    boolean anyDoc = false;
                    if (itemsObj != null) {
                        for (Object it : itemsObj) {
                            anyDoc = true;
                            if (it instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> doc = (Map<String, Object>) it;
                                Object docId = doc.get("id");
                                logger.debug("authenticate: examining document id={}", docId);
                                Object headerObj = doc.get("Header");
                                if (headerObj instanceof Map) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> header = (Map<String, Object>) headerObj;
                                    Object userLwpObj = header.get("UserLWPId");
                                    Object companyIdFound = header.get("CompanyId");
                                    logger.debug("authenticate: document header CompanyId(doc)='{}', UserLWPId(doc)='{}'", companyIdFound, userLwpObj);
                                    if (userLwpObj != null) {
                                        lwpId = userLwpObj.toString();
                                        logger.info("authenticate: found UserLWPId='{}' in Cosmos for employeeId='{}' (doc id={})", lwpId, employeeId, docId);
                                        // set companyId attribute on Keycloak user if present
                                        if (companyIdFound != null) {
                                            try {
                                                String compVal = companyIdFound.toString();
                                                user.setAttribute("companyId", Collections.singletonList(compVal));
                                                logger.info("authenticate: set user attribute companyId='{}' for user='{}'", compVal, username);
                                            } catch (Exception e) {
                                                logger.warn("authenticate: failed to set companyId attribute for user='{}'", username, e);
                                            }
                                        }
                                        break;
                                    } else {
                                        logger.debug("authenticate: document id={} has no UserLWPId in Header", docId);
                                    }
                                } else {
                                    logger.debug("authenticate: document id={} has no Header object (headerObj={})", docId, headerObj);
                                }
                            } else {
                                logger.debug("authenticate: query returned non-map item: {}", it);
                            }
                        }
                    } else {
                        logger.debug("authenticate: query returned null iterable for employeeId='{}'", employeeId);
                    }

                    if (!anyDoc) {
                        logger.info("authenticate: no documents returned for employeeId='{}' ", employeeId);
                    }
                } catch (Exception e) {
                    logger.warn("authenticate: error querying Cosmos for employeeId='{}'", employeeId, e);
                    // ignore and fallback
                    lwpId = null;
                }
            }
        }

        if (lwpId == null) {
            //lwpId = dummyDb.getOrDefault(username, "LWP-UNKNOWN");
            logger.info("authenticate: using fallback LWP id='{}' for user='{}'", lwpId, username);
        }

        user.setAttribute("userLWPId", Collections.singletonList(lwpId));
        logger.debug("authenticate: set user attribute userLWPId='{}' for user='{}'", lwpId, username);
        logger.info("authenticate: final resolved lwpId='{}' for user='{}'", lwpId, username);

        context.success();
    }

    private String findEmployeeId(AuthenticationFlowContext context, UserModel user) {
        // Try common attribute names on the Keycloak user
        String emp = user.getFirstAttribute("employeeid");
        if (emp != null) {
            logger.debug("findEmployeeId: found via user.getFirstAttribute('employeeId') => '{}'", emp);
            return emp.trim();
        }
        emp = user.getFirstAttribute("employeeId");
        if (emp != null) {
            logger.debug("findEmployeeId: found via user.getFirstAttribute('EmployeeId') => '{}'", emp);
            return emp.trim();
        }

        // Try authentication session notes (if the claim was preserved there)
        try {
            Object session = context.getAuthenticationSession();
            if (session != null) {
                logger.debug("findEmployeeId: checking authentication session notes via reflection");
                try {
                    java.lang.reflect.Method mClientNote = session.getClass().getMethod("getClientNote", String.class);
                    Object val = mClientNote.invoke(session, "employeeId");
                    if (val instanceof String) {
                        logger.debug("findEmployeeId: found via session.getClientNote('employeeId') => '{}'", val);
                        return ((String) val).trim();
                    }
                } catch (NoSuchMethodException ignored) {
                    logger.debug("findEmployeeId: session.getClientNote method not present");
                }

                try {
                    java.lang.reflect.Method mClientNote2 = session.getClass().getMethod("getClientNote", String.class);
                    Object val = mClientNote2.invoke(session, "EmployeeId");
                    if (val instanceof String) {
                        logger.debug("findEmployeeId: found via session.getClientNote('EmployeeId') => '{}'", val);
                        return ((String) val).trim();
                    }
                } catch (NoSuchMethodException ignored) {}

                try {
                    java.lang.reflect.Method mAuthNote = session.getClass().getMethod("getAuthNote", String.class);
                    Object val = mAuthNote.invoke(session, "employeeId");
                    if (val instanceof String) {
                        logger.debug("findEmployeeId: found via session.getAuthNote('employeeId') => '{}'", val);
                        return ((String) val).trim();
                    }
                } catch (NoSuchMethodException ignored) {}

                try {
                    java.lang.reflect.Method mAuthNote2 = session.getClass().getMethod("getAuthNote", String.class);
                    Object val = mAuthNote2.invoke(session, "EmployeeId");
                    if (val instanceof String) {
                        logger.debug("findEmployeeId: found via session.getAuthNote('EmployeeId') => '{}'", val);
                        return ((String) val).trim();
                    }
                } catch (NoSuchMethodException ignored) {}
            } else {
                logger.debug("findEmployeeId: authentication session is null");
            }
        } catch (Exception e) {
            logger.debug("findEmployeeId: exception while inspecting authentication session", e);
        }

        // If still null, try realm/user attribute mapping for claim name "employeeId" (case-insensitive)
        Map<String, java.util.List<String>> attrs = user.getAttributes();
        if (attrs != null) {
            for (Map.Entry<String, java.util.List<String>> e : attrs.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase("employeeId")) {
                    java.util.List<String> vals = e.getValue();
                    if (vals != null && !vals.isEmpty()) {
                        logger.debug("findEmployeeId: found via user.getAttributes() key='{}' => '{}'", e.getKey(), vals.get(0));
                        return vals.get(0).trim();
                    }
                }
            }
        }

        logger.debug("findEmployeeId: employeeId not found in attributes or session");
        return null;
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
