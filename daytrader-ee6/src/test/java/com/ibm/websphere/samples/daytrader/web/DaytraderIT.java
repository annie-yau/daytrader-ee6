package com.ibm.websphere.samples.daytrader.web;
 
import org.junit.Test;
import static org.junit.Assert.assertTrue;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
 
public class DaytraderIT {
 
    @Test
    public void testDeployment() {
        testEndpoint("/daytrader/index.jsp", "<h1>Hello World!</h1>");
    }
     
    private void testEndpoint(String endpoint, String expectedOutput) {
        String route = System.getProperty("daytrader.app.route");
 
        Response response = sendRequest(route + endpoint, "GET");
        int responseCode = response.getStatus();
        assertTrue("Incorrect response code: " + responseCode,responseCode == 200);
         
        String responseString = response.readEntity(String.class);
        response.close();
        assertTrue("Incorrect response, response is " + responseString,
                    responseString.contains(expectedOutput));
    }
 
    private Response sendRequest(String url, String requestType) {
        Client client = ClientBuilder.newClient();
        
        System.out.println("in sendRequest: url = " + url);
        
        WebTarget target = client.target(url);
        Invocation.Builder invoBuild = target.request();
        Response response = invoBuild.build(requestType).invoke();
        return response;
    }
}
