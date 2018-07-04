
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

public class SettingsIT {
	
	private static TradeAction tAction = new TradeAction();
	
   // DHV
   // This side effects the database so I only tested it a few times. 
   // If you want to run it then uncomment it, but not that you will 
   // have to do a maven clean install with the -P option to get the 
   // database back, and that may take a few minutes to complete.
   //
   //   @Test
   //   public void testResetTrade() throws Exception{
   //      tAction.resetTrade(false);
   //   }

}
