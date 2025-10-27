package cz.cid.lwp;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosQueryRequestOptions;

import java.util.Map;
import java.util.Scanner;

public class LwpCosmosTest {
    public static void main(String[] args) {
        String employeeId = null;
        String companyId = null;
        if (args != null && args.length > 0) {
            employeeId = args[0];
            if (args.length > 1) companyId = args[1];
        } else {
            System.out.print("Enter EmployeeId: ");
            Scanner sc = new Scanner(System.in);
            employeeId = sc.nextLine();
            System.out.print("Enter CompanyId (optional): ");
            companyId = sc.nextLine();
            sc.close();
            if (companyId != null && companyId.trim().isEmpty()) companyId = null;
        }

        if (employeeId == null || employeeId.trim().isEmpty()) {
            System.err.println("EmployeeId is required");
            System.exit(1);
        }
        employeeId = employeeId.trim();
        if (companyId != null) companyId = companyId.trim();

        String endpoint = "https://mobidriverdb.documents.azure.com:443/";
        String key = "6VWHXGXL0HkNU3M9mxTpDUbjvRB9WfeCzDRYvP9YCL8Mz5GEo37iDsPvotT26SMyGZ5CtknbvEMurju0n7SnyA==";
        String dbName = "MobiDriver";
        String containerName = "UserPKP";


        CosmosClient client = null;
        try {
            client = new CosmosClientBuilder()
                    .endpoint(endpoint)
                    .key(key)
                    .buildClient();

            CosmosDatabase database = client.getDatabase(dbName);
            CosmosContainer container = database.getContainer(containerName);

            // prepare employeeId for SQL: if non-numeric, quote and escape
            String queryEmployee = employeeId;
            if (!employeeId.matches("\\d+")) {
                String escapedEmp = employeeId.replace("'", "''");
                queryEmployee = "'" + escapedEmp + "'";
            }
            // prepare company filter if provided (CompanyId is a string)
            String companyFilter = "";
            if (companyId != null && !companyId.isEmpty()) {
                String escapedCompany = companyId.replace("'", "''");
                companyFilter = " AND c.Header.CompanyId = '" + escapedCompany + "'";
            }

            String query = String.format("SELECT * FROM c WHERE c.Item.EmployeeId = %s%s", queryEmployee, companyFilter);
            Iterable<?> itemsObj = container.queryItems(query, new CosmosQueryRequestOptions(), Object.class);

            boolean found = false;
            if (itemsObj != null) {
                for (Object it : itemsObj) {
                    if (it instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> doc = (Map<String, Object>) it;
                        Object headerObj = doc.get("Header");
                        if (headerObj instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> header = (Map<String, Object>) headerObj;
                            Object userLwp = header.get("UserLWPId");
                            Object companyIdFound = header.get("CompanyId");
                            System.out.println("EmployeeId: " + employeeId + " -> UserLWPId: " + userLwp + ", CompanyId (doc): " + companyIdFound);
                            found = true;
                        } else {
                            System.out.println("Document Header is not a JSON object: " + headerObj);
                        }
                    } else {
                        System.out.println("Query returned non-map item: " + it);
                    }
                }
            } else {
                System.out.println("Query returned non-iterable result: " + itemsObj);
            }

            if (!found) {
                System.out.println("No document found for EmployeeId=" + employeeId);
            }

        } catch (Exception e) {
            System.err.println("Error querying Cosmos DB:");
            e.printStackTrace();
            System.exit(3);
        } finally {
            if (client != null) {
                try { client.close(); } catch (Exception ignored) {}
            }
        }
    }
}
