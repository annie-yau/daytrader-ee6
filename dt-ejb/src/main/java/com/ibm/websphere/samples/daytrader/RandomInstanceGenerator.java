package com.ibm.websphere.samples.daytrader;

import java.util.ArrayList;
import java.util.Collection;

public class RandomInstanceGenerator {

	
	 public static AccountDataBean getRandomInstanceAccountDataBean() {
	        return new AccountDataBean(new Integer(TradeConfig.rndInt(100000)), //accountID
	                TradeConfig.rndInt(10000), //loginCount
	                TradeConfig.rndInt(10000), //logoutCount
	                new java.util.Date(), //lastLogin
	                new java.util.Date(TradeConfig.rndInt(Integer.MAX_VALUE)), //creationDate
	                TradeConfig.rndBigDecimal(1000000.0f), //balance
	                TradeConfig.rndBigDecimal(1000000.0f), //openBalance
	                TradeConfig.rndUserID() //profileID
	        );
	    }
	 
	  public static AccountProfileDataBean getRandomInstanceAccountProfileDataBean() {
	        return new AccountProfileDataBean(
	                TradeConfig.rndUserID(),                        // userID
	                TradeConfig.rndUserID(),                        // passwd
	                TradeConfig.rndFullName(),                      // fullname
	                TradeConfig.rndAddress(),                       // address
	                TradeConfig.rndEmail(TradeConfig.rndUserID()),  //email
	                TradeConfig.rndCreditCard()                     // creditCard
	        );
	    }
	  
	   public static HoldingDataBean getRandomInstanceHoldingDataBean() {
	        return new HoldingDataBean(
	                new Integer(TradeConfig.rndInt(100000)),     //holdingID
	                TradeConfig.rndQuantity(),                     //quantity
	                TradeConfig.rndBigDecimal(1000.0f),             //purchasePrice
	                new java.util.Date(TradeConfig.rndInt(Integer.MAX_VALUE)), //purchaseDate
	                TradeConfig.rndSymbol()                        // symbol
	        );
	    }
	   
	   public static MarketSummaryDataBean getRandomInstanceMarketSummaryDataBean() {
			Collection<QuoteDataBean> gain = new ArrayList<QuoteDataBean>();
			Collection<QuoteDataBean> lose = new ArrayList<QuoteDataBean>();
			
			for (int ii = 0; ii < 5; ii++) {
				QuoteDataBean quote1 = getRandomInstanceQuoteDataBean();
				QuoteDataBean quote2 = getRandomInstanceQuoteDataBean();
				
				gain.add(quote1);
				lose.add(quote2);
			}
			
			return new MarketSummaryDataBean(
				TradeConfig.rndBigDecimal(1000000.0f),
				TradeConfig.rndBigDecimal(1000000.0f),
				TradeConfig.rndQuantity(),
				gain,
				lose
			);
		}
	   
	   public static OrderDataBean getRandomInstanceOrderDataBean() {
	        return new OrderDataBean(
	            new Integer(TradeConfig.rndInt(100000)),
	            TradeConfig.rndBoolean() ? "buy" : "sell",
	            "open",
	            new java.util.Date(TradeConfig.rndInt(Integer.MAX_VALUE)),
	            new java.util.Date(TradeConfig.rndInt(Integer.MAX_VALUE)),
	            TradeConfig.rndQuantity(),
	            TradeConfig.rndBigDecimal(1000.0f),
	            TradeConfig.rndBigDecimal(1000.0f),
	            TradeConfig.rndSymbol()
	        );
	    }
	   
	   public static QuoteDataBean getRandomInstanceQuoteDataBean() {
	        return new QuoteDataBean(
	                        TradeConfig.rndSymbol(), //symbol
	                        TradeConfig.rndSymbol() + " Incorporated", //Company Name
	                        TradeConfig.rndFloat(100000), //volume
	                        TradeConfig.rndBigDecimal(1000.0f), //price
	                        TradeConfig.rndBigDecimal(1000.0f), //open1
	                        TradeConfig.rndBigDecimal(1000.0f), //low
	                        TradeConfig.rndBigDecimal(1000.0f), //high
	                        TradeConfig.rndFloat(100000) //volume
	        );
	    }
}
