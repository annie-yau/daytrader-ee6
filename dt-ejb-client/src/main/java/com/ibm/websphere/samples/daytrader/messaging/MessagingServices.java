/**
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.ibm.websphere.samples.daytrader.messaging;



/**
  * TradeServices interface specifies the business methods provided by the Trade online broker application.
  * These business methods represent the features and operations that can be performed by customers of 
  * the brokerage such as login, logout, get a stock quote, buy or sell a stock, etc.
  * This interface is implemented by {@link Trade} providing an EJB implementation of these
  * business methods and also by {@link TradeDirect} providing a JDBC implementation.
  *
  * @see Trade
  * @see TradeDirect
  *
  */ 
//public interface TradeServices extends Remote {
public interface MessagingServices {
	
   /**
	 * Ping the queue
	 *
	 * @return a PingResponse which is just the message text
	 */
	 PingResponse ping(String queueName) throws Exception;


  
}   

