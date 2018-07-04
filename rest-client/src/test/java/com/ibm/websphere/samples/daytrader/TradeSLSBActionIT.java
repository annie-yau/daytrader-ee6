
package com.ibm.websphere.samples.daytrader;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class TradeSLSBActionIT {
	
	private static TradeSLSBAction tradeSLSBAction = new TradeSLSBAction();

	private static MessagingAction mAction = new MessagingAction();


	@Test
	public void testingTwoPhase() throws Exception {
		QuoteDataBean quoteDataBean = tradeSLSBAction.pingTwoPhase("IBM");
		assertNotNull(quoteDataBean);
		   System.out.println("testingTwoPhase() " + tradeSLSBAction.pingTwoPhase("IBM"));
		}
    
}
