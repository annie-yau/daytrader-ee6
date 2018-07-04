
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

public class QuotesIT {
	
	private static TradeAction tAction = new TradeAction();

   @Test
   public void testCreateQuote() throws Exception {
      java.util.UUID uuid = java.util.UUID.randomUUID();
      String symbol = "s:" + uuid.toString();
      String companyName = "S" + uuid.toString() + " Incorporated";
      BigDecimal price = new BigDecimal(138.00);
      System.out.println("testCreateQuote() " + tAction.createQuote(symbol, companyName, price).toString());
   }

   @Test
   public void testGetQuote() throws Exception {
      QuoteDataBean quote = tAction.getQuote("s:1");
      System.out.println("testGetQuote() " + quote.toString());
   }

   @Test
   public void testGetAllQuotes() throws Exception {
      Collection<?> quotesList = tAction.getAllQuotes();
      int index = 0;
      for (Iterator<?> iterator = quotesList.iterator(); iterator.hasNext();) {
    	  QuoteDataBean quotesBean = (QuoteDataBean) iterator.next();   
    	  System.out.println("getAllQuotes( " + index + " ): " + quotesBean.toString());
    	  index++;
    	}
   }

   @Test
   public void testUpdateQuotePriceVolume() throws Exception {
	   System.out.println("testUpdateQuotePriceVolume() " + tAction.updateQuotePriceVolume( "s:0", new BigDecimal(0.0), 100.0).toString());
   }
    
}
