
package com.ibm.websphere.samples.daytrader;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;


import com.fasterxml.jackson.databind.ObjectMapper;
//Jackson
//import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.websphere.samples.daytrader.messaging.MessagingServices;
import com.ibm.websphere.samples.daytrader.messaging.PingResponse;

public class MessagingAction implements MessagingServices {

	private static ObjectMapper mapper = new ObjectMapper(); // create once, reuse
        
    @Override
	public PingResponse ping(String queueorTopicName) throws Exception {
	   	String responseString = invokeEndpoint("/rest/messaging/" + queueorTopicName + "/ping", "GET");
        PingResponse responseObject;
        responseObject = mapper.readValue(responseString,PingResponse.class);
        return responseObject;
	}
    
 
    
 
	
    private String invokeEndpoint(String endpoint, String method) {
        String route = System.getProperty("rest.app.route");
    
        Response response = sendRequest(route + endpoint, method);
        int responseCode = response.getStatus();
        if ( responseCode != 200 )  {
        	throw new BadRequestException("Incorrect response dode: " + responseCode);
        }
        //Object responseObject = response.getEntity();
        
        
        String responseString = response.readEntity(String.class);
        response.close();
        return responseString;
    }
    
    private Response sendRequest(String url, String method) {
        Client client = ClientBuilder.newClient();
        WebTarget target = client.target(url);

        Response response = null;
        
        if (method.equals("PUT")) {
            Entity<?> empty = Entity.text("");
            response = target.request().put(empty);
        } else if (method.equals("GET")) {
            response = target.request().get();        	
        } else if (method.equals("DELETE")) {
            response = target.request().delete();        	
        } else if (method.equals("POST")) {
            Entity<?> empty = Entity.text("");
            response = target.request().post(empty);     	
        }
        
        return response;
    }

}
