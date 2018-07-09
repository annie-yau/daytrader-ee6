
package com.ibm.websphere.samples.daytrader;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Collection;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;




import org.glassfish.jersey.client.ClientProperties;


//Jackson
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.websphere.samples.daytrader.util.Log;

public class TradeAction implements TradeServices {

	protected static ObjectMapper mapper = new ObjectMapper(); // create once, reuse
	String accountsRoute = System.getenv("accounts_app_route");
	String marketsRoute = System.getenv("markets_app_route");
	String quotesRoute = System.getenv("quotes_app_route");
	String settingsRoute = System.getenv("settings_app_route");
	
	
    @Override
	public RunStatsDataBean resetTrade(boolean deleteAll) throws Exception {
	   	String responseString = invokeEndpoint(settingsRoute + "/settings/" + deleteAll, "DELETE");
        RunStatsDataBean responseObject;
        responseObject = mapper.readValue(responseString,RunStatsDataBean.class);
        return responseObject;
	}
    
    @Override
	public MarketSummaryDataBean getMarketSummary() throws Exception {
	   	String responseString = invokeEndpoint(marketsRoute + "/markets/", "GET");
        MarketSummaryDataBean responseObject;
        responseObject = mapper.readValue(responseString,MarketSummaryDataBean.class);
        return responseObject;
	}
    
    @Override
	public QuoteDataBean createQuote(String symbol, String companyName, BigDecimal price) throws Exception {
	   	String responseString = invokeEndpoint(quotesRoute + "/quotes/" + symbol + "/" + companyName + "/" + price , "POST");
        QuoteDataBean responseObject;
        responseObject = mapper.readValue(responseString,QuoteDataBean.class);
        return responseObject;
	}

    @Override
	public QuoteDataBean getQuote(String symbol) throws Exception {
	   	String responseString = invokeEndpoint(quotesRoute + "/quotes/" + symbol, "GET");
        QuoteDataBean responseObject;
        responseObject = mapper.readValue(responseString,QuoteDataBean.class);
        return responseObject;
	}
	
    @Override
	public Collection<?> getAllQuotes() throws Exception {
	   	String responseString = invokeEndpoint(quotesRoute + "/quotes/", "GET");
        Collection<?> responseObject = Arrays.asList(mapper.readValue(responseString, QuoteDataBean[].class));
        return responseObject;
	}

    @Override
    public QuoteDataBean updateQuotePriceVolume(String symbol, BigDecimal changeFactor, double sharesTraded) throws Exception {
	   	String responseString = invokeEndpoint(quotesRoute + "/quotes/" + symbol + "/" + changeFactor + "/" + sharesTraded, "PUT");
        QuoteDataBean responseObject;
        responseObject = mapper.readValue(responseString,QuoteDataBean.class);
        return responseObject;
	}

    @Override
    public Collection<?> getHoldings(String userID) throws Exception {
	   	String responseString = invokeEndpoint(accountsRoute + "/accounts/" + userID + "/holdings", "GET");
        Collection<HoldingDataBean> responseObject;
        responseObject = Arrays.asList(mapper.readValue(responseString, HoldingDataBean[].class));
        return responseObject;
    }

    @Override
    public HoldingDataBean getHolding(Integer holdingID) throws Exception {
	   	String responseString = invokeEndpoint(accountsRoute + "/accounts/" + "UNUSED" + "/holdings/" + holdingID, "GET");
        HoldingDataBean responseObject;
        responseObject = mapper.readValue(responseString,HoldingDataBean.class);
        return responseObject;
    }

    @Override
    public AccountDataBean getAccountData(String userID) throws Exception {
	   	String responseString = invokeEndpoint(accountsRoute + "/accounts/" + userID, "GET");
        AccountDataBean responseObject;
        responseObject = mapper.readValue(responseString,AccountDataBean.class);
        return responseObject;
    }
 
    @Override
    public AccountProfileDataBean getAccountProfileData(String userID) throws Exception {
	   	String responseString = invokeEndpoint(accountsRoute + "/accounts/" + userID + "/profile", "GET");
        AccountProfileDataBean responseObject;
        responseObject = mapper.readValue(responseString,AccountProfileDataBean.class);
        return responseObject;
    }

    @Override
    public AccountProfileDataBean updateAccountProfile(AccountProfileDataBean accountProfileData) throws Exception {
    	String userID = accountProfileData.getUserID();
    	String password = accountProfileData.getPassword();
    	String fullName = accountProfileData.getFullName();
    	String address = accountProfileData.getAddress();
    	String email = accountProfileData.getEmail();
    	String creditCard = accountProfileData.getCreditCard();
        
	   	String responseString = invokeEndpoint(accountsRoute + "/accounts/" + userID + "/profile/" + password
	   			+ "/" + fullName + "/" + address + "/" + email + "/" + creditCard, "PUT");
        AccountProfileDataBean responseObject;
        responseObject = mapper.readValue(responseString,AccountProfileDataBean.class);
        return responseObject;
    }
    
    @Override
    public AccountDataBean login(String userID, String password) throws Exception {
	   	String responseString = invokeEndpoint(accountsRoute + "/accounts/" + userID + "/" + password, "PUT");
        AccountDataBean responseObject;
        responseObject = mapper.readValue(responseString,AccountDataBean.class);
        return responseObject;
    }

    @Override
    public void logout(String userID) throws Exception {
	   	invokeEndpoint(accountsRoute + "/accounts/" + userID, "PUT");
    }

    @Override
    public AccountDataBean register(String userID, String password, String fullname, String address, String email, String creditCard, BigDecimal openBalance) throws Exception {
	   	String responseString = invokeEndpoint(accountsRoute + "/accounts/" + 
	   			userID + "/" + password + "/" +
	   			fullname + "/" + address + "/" +
	   			email + "/" + creditCard + "/" +
	   			openBalance, "POST");
        AccountDataBean responseObject;
        responseObject = mapper.readValue(responseString,AccountDataBean.class);
        return responseObject;
    }

    @Override
	public OrderDataBean buy(String userID, String symbol, double quantity, int orderProcessingMode) throws Exception {
	   	String responseString;
	   	responseString = invokeEndpoint(
	   			accountsRoute + "/accounts/" + userID + "/buyorders/" + symbol + "/" + quantity + "/" + orderProcessingMode, 
	   			"POST");
        OrderDataBean responseObject = mapper.readValue(responseString,OrderDataBean.class);
        return responseObject;
        
	}
	
    @Override
	public OrderDataBean sell(String userID, Integer holdingID, int orderProcessingMode) throws Exception {
	   	String responseString;
	   	responseString = invokeEndpoint(
	   			accountsRoute + "/accounts/" + userID + "/sellorders/" + holdingID + "/" + orderProcessingMode, 
	   			"POST");
	   	OrderDataBean responseObject = mapper.readValue(responseString,OrderDataBean.class);
	   	return responseObject;
	}

    @Override
	public Collection<?> getOrders(String userID) throws Exception {
    	  if (Log.doActionTrace())
              Log.trace("TradeAction:getOrders", userID);
	   	String responseString = invokeEndpoint( accountsRoute + "/accounts/" + userID + "/orders", "GET");
        Collection<?> responseObject = Arrays.asList(mapper.readValue(responseString, OrderDataBean[].class));
	   	return responseObject;
	}

    @Override
	public Collection<?> getClosedOrders(String userID) throws Exception {
	   	String responseString = invokeEndpoint( accountsRoute + "/accounts/" + userID + "/closedorders", "PUT");
        Collection<?> responseObject = Arrays.asList(mapper.readValue(responseString, OrderDataBean[].class));
	   	return responseObject;
	}

    @Override
	public void queueOrder(Integer orderID, boolean twoPhase) throws Exception {
    	throw new UnsupportedOperationException("TradeAction:queueOrder method not supported");
	}

    @Override
	public OrderDataBean completeOrder(Integer orderID, boolean twoPhase) throws Exception {
    	throw new UnsupportedOperationException("TradeAction:completeOrder method not supported");
	}

    @Override
	public void cancelOrder(Integer orderID, boolean twoPhase) throws Exception {
    	throw new UnsupportedOperationException("TradeAction:cancelOrder method not supported");
	}

    @Override
	public void orderCompleted(String userID, Integer orderID) throws Exception {
    	throw new UnsupportedOperationException("TradeAction:orderCompleted method not supported");
	}
    
    
    public void recreateDBTables(){
    	invokeEndpoint( settingsRoute + "/settings/tables", "PUT");
        
    }
    
    public void buildDB(){
    	//inc
    	invokeEndpoint( settingsRoute + "/settings/database", "PUT", 100000);
        
    }
	
    protected String invokeEndpoint(String endpoint, String method) {
    	return invokeEndpoint(endpoint, method, -1);
    }
    protected String invokeEndpoint(String endpoint, String method, int connTimeOut) {
       
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

    protected Response sendRequest(String url, String method, int connTimeOut) {
        Client client = ClientBuilder.newClient();
        if (connTimeOut > 0 )
        	client.property(ClientProperties.CONNECT_TIMEOUT, connTimeOut);
        String encodedUrl = url;
        try {
		System.out.println("Input url = " + url);
        	encodedUrl = URLEncoder.encode(url);
        } catch (Exception e){
        	//
		System.out.println("exception!");
        }
        System.out.println("Encode url = " + encodedUrl + " : " + method);
        
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
