package com.ibm.websphere.samples.daytrader.util;

import java.net.URLEncoder;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.client.ClientProperties;

public class RestCallHelper {

	
    public static String invokeEndpoint(String endpoint, String method) {
    	return invokeEndpoint(endpoint, method, -1);
    }
    public static String invokeEndpoint(String endpoint, String method, int connTimeOut) {
       
    	System.out.println(endpoint + " : " + method);
    	Response  response = sendRequest(endpoint, method, connTimeOut);
        int responseCode = response.getStatus();
        //204 mean no content...since nothing is returned this is acceptable
        if ( responseCode != 200 && responseCode != 204 )  {
        	throw new BadRequestException("Incorrect response code: " + responseCode + " from : " + endpoint);
        }
        
        String responseString = response.readEntity(String.class);
        response.close();
        return responseString;
    }

    public static Response sendRequest(String url, String method, int connTimeOut) {
        Client client = ClientBuilder.newClient();
        if (connTimeOut > 0 )
        	client.property(ClientProperties.CONNECT_TIMEOUT, connTimeOut);
        String encodedUrl = url;
        try {
        	encodedUrl = URLEncoder.encode(url);
        } catch (Exception e){
        	//
        }
        System.out.println(encodedUrl + " : " + method);
        
        WebTarget target = client.target(encodedUrl);


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
