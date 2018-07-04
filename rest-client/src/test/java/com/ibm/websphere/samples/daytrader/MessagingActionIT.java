
package com.ibm.websphere.samples.daytrader;

import org.junit.Test;

public class MessagingActionIT {
	
	private static MessagingAction mAction = new MessagingAction();

	

	@Test
	public void testPingQueue() throws Exception {
	   System.out.println("testCreateQuote() " + mAction.ping("tradeBrokerQueue"));
	}
	@Test
	public void testPingTopic() throws Exception {
		   System.out.println("testCreateQuote() " + mAction.ping("tradeStreamerTopic"));
	}
    
}
