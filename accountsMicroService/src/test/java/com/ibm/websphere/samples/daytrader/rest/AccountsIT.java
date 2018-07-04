
package com.ibm.websphere.samples.daytrader.rest;

import org.junit.Test;

import com.ibm.websphere.samples.daytrader.TradeAction;

import com.ibm.websphere.samples.daytrader.AccountDataBean;
import com.ibm.websphere.samples.daytrader.AccountProfileDataBean;
import com.ibm.websphere.samples.daytrader.HoldingDataBean;
import com.ibm.websphere.samples.daytrader.OrderDataBean;
import com.ibm.websphere.samples.daytrader.QuoteDataBean;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Iterator;

public class AccountsIT {
	
	private static TradeAction tAction = new TradeAction();
	

   @Test
   public void testGetHoldings() throws Exception {
      Collection<?> holdingsList = tAction.getHoldings("uid:0");
	  int index = 0;
      for (Iterator<?> iterator = holdingsList.iterator(); iterator.hasNext();) {
    	  HoldingDataBean holdingBean = (HoldingDataBean) iterator.next();   
    	  System.out.println("getHoldings( " + index + " ): " + holdingBean.toString());
    	  index++;
    	}
   }

   @Test
   public void testGetHolding() throws Exception {
      HoldingDataBean holding = tAction.getHolding(1);
      System.out.println("testGetHolding() " + holding.toString());
   }

   @Test
   public void testGetAccountData() throws Exception {
      AccountDataBean account = tAction.getAccountData("uid:0");
      System.out.println("testGetAccountData() " + account.toString());
   }

   @Test
   public void testGetAccountProfileData() throws Exception {
      AccountProfileDataBean accountProfile = tAction.getAccountProfileData("uid:0");
      System.out.println("testGetAccountProfileData() " + accountProfile.toString());
   }
   
   @Test
   public void testUpdateAccountProfile() throws Exception {
      AccountProfileDataBean accountProfileData = new AccountProfileDataBean();
      accountProfileData.setAddress("nyc");
      accountProfileData.setCreditCard("999999999");
      accountProfileData.setEmail("uid:0" + "@ibm.com");
      accountProfileData.setFullName("foo");
      accountProfileData.setPassword("xxx");
      accountProfileData.setUserID("uid:0");
      
      System.out.println("testUpdateAccountProfile() " + tAction.updateAccountProfile(accountProfileData).toString());
   }

   @Test
   public void testLogin() throws Exception {
      AccountDataBean accountData = tAction.login("uid:0","xxx");
      System.out.println("testLogin() " + accountData.toString());
   }

   @Test
   public void testLogout() throws Exception {
	   tAction.logout("uid:0");
   }

   @Test
   public void testRegister() throws Exception {
	  String userID = java.util.UUID.randomUUID().toString();
	  System.out.println("testRegister() " + tAction.register(userID, "xxx", "foo", "nyc",  "foo@ibm.com", "999999999",  new BigDecimal(10000.00)).toString());
   }

   @Test
   public void testBuy() throws Exception {
	   System.out.println("testBuy() " + tAction.buy("uid:0","s:1",100,0).toString());
   }

   @Test
   public void testSell() throws Exception {
	   System.out.println("testSell() " + tAction.sell("uid:0", 3, 0).toString());
   }

   @Test
   public void testGetOrders() throws Exception {
   	  Collection<?> orderList = tAction.getOrders("uid:0");
	  int index = 0;
      for (Iterator<?> iterator = orderList.iterator(); iterator.hasNext();) {
    	  OrderDataBean orderBean = (OrderDataBean) iterator.next();   
    	  System.out.println("getOrders( " + index + " ): " + orderBean.toString());
    	  index++;
    	}
   }

   @Test
   public void testGetCosedOrders() throws Exception {
	   Collection<?> orderList = tAction.getClosedOrders("uid:0");
	  int index = 0;
      for (Iterator<?> iterator = orderList.iterator(); iterator.hasNext();) {
    	  OrderDataBean orderBean = (OrderDataBean) iterator.next();   
    	  System.out.println("getOrders( " + index + " ): " + orderBean.toString());
    	  index++;
    	}
   }
    
}
