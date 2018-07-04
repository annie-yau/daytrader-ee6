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
package com.ibm.websphere.samples.daytrader.rest;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;

import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.naming.InitialContext;
import javax.sql.DataSource;
import javax.transaction.UserTransaction;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.websphere.samples.daytrader.AccountDataBean;
import com.ibm.websphere.samples.daytrader.AccountProfileDataBean;
import com.ibm.websphere.samples.daytrader.HoldingDataBean;
import com.ibm.websphere.samples.daytrader.OrderDataBean;
import com.ibm.websphere.samples.daytrader.QuoteDataBean;
import com.ibm.websphere.samples.daytrader.TradeConfig;
import com.ibm.websphere.samples.daytrader.TradeServices;
import com.ibm.websphere.samples.daytrader.rest.KeySequenceDirect;
import com.ibm.websphere.samples.daytrader.util.FinancialUtils;
import com.ibm.websphere.samples.daytrader.util.Log;
import com.ibm.websphere.samples.daytrader.util.RestCallHelper;


@Path(value = "/")
public class Accounts 

{

	String quotesRoute = System.getenv("quotes.app.route");
	
	
	private static String dsName = TradeConfig.DATASOURCE;

	private static DataSource datasource = null;

	private boolean inGlobalTxn = false;

	private boolean inSession = false;

	protected static ObjectMapper mapper = new ObjectMapper(); 
	
	public Accounts() {
		if (initialized == false)
			init();
	}
	
	/**
     * @see TradeServices#buy(String, String, double)
     */
    @POST
    @Path("accounts/{userID}/buyorders/{symbol}/{quantity}/{orderProcessingMode}")
    @Produces({"application/json"})
	public OrderDataBean buy(@PathParam(value = "userID") String userID, @PathParam(value = "symbol") String symbol, 
			@PathParam(value = "quantity") double quantity, 
			@PathParam(value = "orderProcessingMode") int orderProcessingMode) throws Exception {

		Connection conn = null;
		OrderDataBean orderData = null;
		UserTransaction txn = null;

		/*
         * total = (quantity * purchasePrice) + orderFee
         */
		BigDecimal total;

		try {
			if (Log.doTrace())
				Log.trace(
						"TradeDirect:buy - inSession(" + this.inSession + ")",
						userID, symbol, new Double(quantity));

			if (!inSession && orderProcessingMode == TradeConfig.ASYNCH_2PHASE) {
				if (Log.doTrace())
					Log
							.trace("TradeDirect:buy create/begin global transaction");
				// FUTURE the UserTransaction be looked up once
				txn = (javax.transaction.UserTransaction) context
						.lookup("java:comp/UserTransaction");
				txn.begin();
				setInGlobalTxn(true);
			}

			conn = getConn();

			AccountDataBean accountData = getAccountData(conn, userID);
			
			//IBM Replace call to microservice call
			//QuoteDataBean quoteData = getQuoteData(conn, symbol);
			
			//Call external microservice
			String responseString = RestCallHelper.invokeEndpoint(quotesRoute + "/quotes/" + symbol, "GET");
	        QuoteDataBean quoteData;
	        quoteData = mapper.readValue(responseString,QuoteDataBean.class);
	        
			
			HoldingDataBean holdingData = null; // the buy operation will create
                                                // the holding

			orderData = createOrder(conn, accountData, quoteData, holdingData,
					"buy", quantity);

			// Update -- account should be credited during completeOrder
			BigDecimal price = quoteData.getPrice();
			BigDecimal orderFee = orderData.getOrderFee();
			total = (new BigDecimal(quantity).multiply(price)).add(orderFee);
			// subtract total from account balance
			creditAccountBalance(conn, accountData, total.negate());

			try {
				if (orderProcessingMode == TradeConfig.SYNCH)
					completeOrder(conn, orderData.getOrderID());
				else if (orderProcessingMode == TradeConfig.ASYNCH_2PHASE)
					queueOrder(orderData.getOrderID(), true); // 2-phase
                                                                // commit
			} catch (JMSException je) {
				Log.error("TradeBean:buy(" + userID + "," + symbol + ","
						+ quantity + ") --> failed to queueOrder", je);
				/* On exception - cancel the order */

				cancelOrder(conn, orderData.getOrderID());
			}

			orderData = getOrderData(conn, orderData.getOrderID().intValue());

			if (txn != null) {
				if (Log.doTrace())
					Log.trace("TradeDirect:buy committing global transaction");
				txn.commit();
				setInGlobalTxn(false);
			} else
				commit(conn);
		} catch (Exception e) {
			Log.error("TradeDirect:buy error - rolling back", e);
			if (getInGlobalTxn())
				txn.rollback();
			else
				rollBack(conn, e);
		} finally {
			releaseConn(conn);
		}

		return orderData;
	}

	/**
     * @see TradeServices#sell(String, Integer)
     */
    @POST
    @Path("accounts/{userID}/sellorders/{holdingID}/{orderProcessingMode}")
    @Produces({"application/json"})
	public OrderDataBean sell(@PathParam(value = "userID") String userID, @PathParam(value = "holdingID") Integer holdingID, 
			@PathParam(value = "orderProcessingMode") int orderProcessingMode) throws Exception {
		Connection conn = null;
		OrderDataBean orderData = null;
		UserTransaction txn = null;

		/*
         * total = (quantity * purchasePrice) + orderFee
         */
		BigDecimal total;

		try {
			if (Log.doTrace())
				Log.trace("TradeDirect:sell - inSession(" + this.inSession
						+ ")", userID, holdingID);

			if (!inSession && orderProcessingMode == TradeConfig.ASYNCH_2PHASE) {
				if (Log.doTrace())
					Log
							.trace("TradeDirect:sell create/begin global transaction");
				// FUTURE the UserTransaction be looked up once

				txn = (javax.transaction.UserTransaction) context
						.lookup("java:comp/UserTransaction");
				txn.begin();
				setInGlobalTxn(true);
			}

			conn = getConn();

			AccountDataBean accountData = getAccountData(conn, userID);
			HoldingDataBean holdingData = getHoldingData(conn, holdingID
					.intValue());
			QuoteDataBean quoteData = null;
			if (holdingData != null) {
				//IBM Replace call to microservice call
				//QuoteDataBean quoteData = getQuoteData(conn, symbol);
				
				//Call external microservice
				
				String responseString = RestCallHelper.invokeEndpoint(quotesRoute + "/quotes/" + holdingData.getQuoteID(), "GET");
		        quoteData = mapper.readValue(responseString,QuoteDataBean.class);
		        				
				//BEFORE: quoteData = getQuoteData(conn, holdingData.getQuoteID());
				
			}

			if ((accountData == null) || (holdingData == null)
					|| (quoteData == null)) {
				String error = "TradeDirect:sell -- error selling stock -- unable to find:  \n\taccount="
						+ accountData
						+ "\n\tholding="
						+ holdingData
						+ "\n\tquote="
						+ quoteData
						+ "\nfor user: "
						+ userID
						+ " and holdingID: " + holdingID;
				Log.error(error);
				if (getInGlobalTxn())
					txn.rollback();
				else
					rollBack(conn, new Exception(error));
				return orderData;
			}

			double quantity = holdingData.getQuantity();

			orderData = createOrder(conn, accountData, quoteData, holdingData,
					"sell", quantity);

			// Set the holdingSymbol purchaseDate to selling to signify the sell
            // is "inflight"
			updateHoldingStatus(conn, holdingData.getHoldingID(), holdingData
					.getQuoteID());

			// UPDATE -- account should be credited during completeOrder
			BigDecimal price = quoteData.getPrice();
			BigDecimal orderFee = orderData.getOrderFee();
			total = (new BigDecimal(quantity).multiply(price))
					.subtract(orderFee);
			creditAccountBalance(conn, accountData, total);

			try {
				if (orderProcessingMode == TradeConfig.SYNCH)
					completeOrder(conn, orderData.getOrderID());
				else if (orderProcessingMode == TradeConfig.ASYNCH_2PHASE)
					queueOrder(orderData.getOrderID(), true); // 2-phase
                                                                // commit
			} catch (JMSException je) {
				Log.error("TradeBean:sell(" + userID + "," + holdingID
						+ ") --> failed to queueOrder", je);
				/* On exception - cancel the order */

				cancelOrder(conn, orderData.getOrderID());
			}

			orderData = getOrderData(conn, orderData.getOrderID().intValue());

			if (txn != null) {
				if (Log.doTrace())
					Log.trace("TradeDirect:sell committing global transaction");
				txn.commit();
				setInGlobalTxn(false);
			} else
				commit(conn);
		} catch (Exception e) {
			Log.error("TradeDirect:sell error", e);
			if (getInGlobalTxn())
				txn.rollback();
			else
				rollBack(conn, e);
		} finally {
			releaseConn(conn);
		}

		return orderData;
	}

	
    /**
     * @see TradeServices#queueOrder(Integer)
     */
	public void queueOrder(Integer orderID, boolean twoPhase) throws Exception {
		if (Log.doTrace())
			Log.trace("TradeDirect:queueOrder - inSession(" + this.inSession
					+ ")", orderID);

		javax.jms.Connection conn = null;
		Session sess = null;

		try {
			conn = qConnFactory.createConnection();
			sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
			MessageProducer producer = sess.createProducer(queue);

			TextMessage message = sess.createTextMessage();

			String command = "neworder";
			message.setStringProperty("command", command);
			message.setIntProperty("orderID", orderID.intValue());
			message.setBooleanProperty("twoPhase", twoPhase);
			message.setBooleanProperty("direct", true);
			message.setLongProperty("publishTime", System.currentTimeMillis());
			message.setText("neworder: orderID=" + orderID
					+ " runtimeMode=Direct twoPhase=" + twoPhase);

			if (Log.doTrace())
				Log.trace("TradeDirectBean:queueOrder Sending message: "
						+ message.getText());
			producer.send(message);
			sess.close();
		}

		catch (Exception e) {
			throw e; // pass the exception back
		}

		finally {
			if (sess != null)
				sess.close();
			if (conn != null)
				conn.close();
		}
	}

	private OrderDataBean completeOrder(Connection conn, Integer orderID) 
			throws Exception {

		OrderDataBean orderData = null;
		if (Log.doTrace())
			Log.trace("TradeDirect:completeOrderInternal - inSession("
					+ this.inSession + ")", orderID);

		PreparedStatement stmt = getStatement(conn, getOrderSQL);
		stmt.setInt(1, orderID.intValue());

		ResultSet rs = stmt.executeQuery();

		if (!rs.next()) {
			Log.error("TradeDirect:completeOrder -- unable to find order: "
					+ orderID);
			stmt.close();
			return orderData;
		}
		orderData = getOrderDataFromResultSet(rs);

		String orderType = orderData.getOrderType();
		String orderStatus = orderData.getOrderStatus();

		// if (order.isCompleted())
		if ((orderStatus.compareToIgnoreCase("completed") == 0)
				|| (orderStatus.compareToIgnoreCase("alertcompleted") == 0)
				|| (orderStatus.compareToIgnoreCase("cancelled") == 0))
			throw new Exception(
					"TradeDirect:completeOrder -- attempt to complete Order that is already completed");

		int accountID = rs.getInt("account_accountID");
		String quoteID = rs.getString("quote_symbol");
		int holdingID = rs.getInt("holding_holdingID");

		BigDecimal price = orderData.getPrice();
		double quantity = orderData.getQuantity();
		//BigDecimal orderFee = orderData.getOrderFee();

		// get the data for the account and quote
		// the holding will be created for a buy or extracted for a sell

		/*
         * Use the AccountID and Quote Symbol from the Order AccountDataBean
         * accountData = getAccountData(accountID, conn); QuoteDataBean
         * quoteData = getQuoteData(conn, quoteID);
         */
		String userID = getAccountProfileData(conn, new Integer(accountID))
				.getUserID();

		HoldingDataBean holdingData = null;

		if (Log.doTrace())
			Log.trace("TradeDirect:completeOrder--> Completing Order "
					+ orderData.getOrderID() + "\n\t Order info: " + orderData
					+ "\n\t Account info: " + accountID + "\n\t Quote info: "
					+ quoteID);

		// if (order.isBuy())
		if (orderType.compareToIgnoreCase("buy") == 0) {
			/*
             * Complete a Buy operation - create a new Holding for the Account -
             * deduct the Order cost from the Account balance
             */

			holdingData = createHolding(conn, accountID, quoteID, quantity,
					price);
			updateOrderHolding(conn, orderID.intValue(), holdingData
					.getHoldingID().intValue());
		}

		// if (order.isSell()) {
		if (orderType.compareToIgnoreCase("sell") == 0) {
			/*
             * Complete a Sell operation - remove the Holding from the Account -
             * deposit the Order proceeds to the Account balance
             */
			holdingData = getHoldingData(conn, holdingID);
			if (holdingData == null)
				Log.debug("TradeDirect:completeOrder:sell -- user: " + userID
						+ " already sold holding: " + holdingID);
			else
				removeHolding(conn, holdingID, orderID.intValue());

		}

		updateOrderStatus(conn, orderData.getOrderID(), "closed");

		if (Log.doTrace())
			Log.trace("TradeDirect:completeOrder--> Completed Order "
					+ orderData.getOrderID() + "\n\t Order info: " + orderData
					+ "\n\t Account info: " + accountID + "\n\t Quote info: "
					+ quoteID + "\n\t Holding info: " + holdingData);

		stmt.close();

		commit(conn);

		// commented out following call
        // - orderCompleted doesn't really do anything (think it was a hook for old Trade caching code)
		
		// signify this order for user userID is complete
		// This call does not work here for SESSION Mode / Sync
		/*if (TradeConfig.runTimeMode != TradeConfig.SESSION3 || TradeConfig.orderProcessingMode != TradeConfig.SYNCH )
		{
		    TradeAction tradeAction = new TradeAction(this);
		    tradeAction.orderCompleted(userID, orderID);
		}
		 */
		return orderData;
	}



	private void cancelOrder(Connection conn, Integer orderID) throws Exception {
		updateOrderStatus(conn, orderID, "cancelled");
	}

	public void orderCompleted(String userID, Integer orderID) throws Exception {
		throw new UnsupportedOperationException(
				"TradeDirect:orderCompleted method not supported");
	}

	private HoldingDataBean createHolding(Connection conn, int accountID,
			String symbol, double quantity, BigDecimal purchasePrice)
			throws Exception {
		//HoldingDataBean holdingData = null;

		Timestamp purchaseDate = new Timestamp(System.currentTimeMillis());
		PreparedStatement stmt = getStatement(conn, createHoldingSQL);

		Integer holdingID = KeySequenceDirect.getNextID(conn, "holding",
				inSession, getInGlobalTxn());
		stmt.setInt(1, holdingID.intValue());
		stmt.setTimestamp(2, purchaseDate);
		stmt.setBigDecimal(3, purchasePrice);
		stmt.setDouble(4, quantity);
		stmt.setString(5, symbol);
		stmt.setInt(6, accountID);
		stmt.executeUpdate();

		stmt.close();

		return getHoldingData(conn, holdingID.intValue());
	}

	private void removeHolding(Connection conn, int holdingID, int orderID)
			throws Exception {
		PreparedStatement stmt = getStatement(conn, removeHoldingSQL);

		stmt.setInt(1, holdingID);
		stmt.executeUpdate();
		stmt.close();

		// set the HoldingID to NULL for the purchase and sell order now that
		// the holding as been removed
		stmt = getStatement(conn, removeHoldingFromOrderSQL);

		stmt.setInt(1, holdingID);
		stmt.executeUpdate();
		stmt.close();

	}

	private OrderDataBean createOrder(Connection conn,
			AccountDataBean accountData, QuoteDataBean quoteData,
			HoldingDataBean holdingData, String orderType, double quantity)
			throws Exception {
		//OrderDataBean orderData = null;

		Timestamp currentDate = new Timestamp(System.currentTimeMillis());

		PreparedStatement stmt = getStatement(conn, createOrderSQL);

		Integer orderID = KeySequenceDirect.getNextID(conn, "order", inSession,
				getInGlobalTxn());
		stmt.setInt(1, orderID.intValue());
		stmt.setString(2, orderType);
		stmt.setString(3, "open");
		stmt.setTimestamp(4, currentDate);
		stmt.setDouble(5, quantity);
		stmt.setBigDecimal(6, quoteData.getPrice().setScale(
				FinancialUtils.SCALE, FinancialUtils.ROUND));
		stmt.setBigDecimal(7, TradeConfig.getOrderFee(orderType));
		stmt.setInt(8, accountData.getAccountID().intValue());
		if (holdingData == null)
			stmt.setNull(9, java.sql.Types.INTEGER);
		else
			stmt.setInt(9, holdingData.getHoldingID().intValue());
		stmt.setString(10, quoteData.getSymbol());
		stmt.executeUpdate();

		stmt.close();

		return getOrderData(conn, orderID.intValue());
	}

	/**
     * @see TradeServices#getOrders(String)
     */
    @GET
    @Path("accounts/{userID}/orders")
    @Produces({"application/json"})
	public Collection<?> getOrders(@PathParam(value = "userID") String userID) throws Exception {
		Collection<OrderDataBean> orderDataBeans = new ArrayList<OrderDataBean>();
		Connection conn = null;
		try {
			if (Log.doTrace())
				Log.trace("TradeDirect:getOrders - inSession(" + this.inSession
						+ ")", userID);

			conn = getConn();
			PreparedStatement stmt = getStatement(conn, getOrdersByUserSQL);
			stmt.setString(1, userID);

			ResultSet rs = stmt.executeQuery();

			// TODO: return top 5 orders for now -- next version will add a
            // getAllOrders method
			// also need to get orders sorted by order id descending
			int i = 0;
			while ((rs.next()) && (i++ < 5)) {
				OrderDataBean orderData = getOrderDataFromResultSet(rs);
				orderDataBeans.add(orderData);
			}

			stmt.close();
			commit(conn);

		} catch (Exception e) {
			Log.error("TradeDirect:getOrders -- error getting user orders", e);
			rollBack(conn, e);
		} finally {
			releaseConn(conn);
		}
		return orderDataBeans;
	}

	/**
     * @see TradeServices#getClosedOrders(String)
     */
    @PUT
    @Path("accounts/{userID}/closedorders")
    @Produces({"application/json"})
    public Collection<?> getClosedOrders(@PathParam(value = "userID") String userID) throws Exception {
 
		Collection<OrderDataBean> orderDataBeans = new ArrayList<OrderDataBean>();
		Connection conn = null;
		try {
			if (Log.doTrace())
				Log.trace("TradeDirect:getClosedOrders - inSession("
						+ this.inSession + ")", userID);

			conn = getConn();
			PreparedStatement stmt = getStatement(conn, getClosedOrdersSQL);
			stmt.setString(1, userID);

			ResultSet rs = stmt.executeQuery();

			while (rs.next()) {
				OrderDataBean orderData = getOrderDataFromResultSet(rs);
				orderData.setOrderStatus("completed");
				updateOrderStatus(conn, orderData.getOrderID(), orderData
						.getOrderStatus());
				orderDataBeans.add(orderData);

			}

			stmt.close();
			commit(conn);
		} catch (Exception e) {
			Log.error("TradeDirect:getClosedOrders -- error getting user orders", e);
			rollBack(conn, e);
		} finally {
			releaseConn(conn);
		}
		return orderDataBeans;
	}



	
	/**
     * @see TradeServices#getHoldings(String)
     */
    @GET
    @Path("accounts/{userID}/holdings")
    @Produces({"application/json"})
    public Collection<?> getHoldings(@PathParam(value = "userID") String userID) throws Exception {
		Collection<HoldingDataBean> holdingDataBeans = new ArrayList<HoldingDataBean>();
		Connection conn = null;
		try {
			if (Log.doTrace())
				Log.trace("TradeDirect:getHoldings - inSession("
						+ this.inSession + ")", userID);

			conn = getConn();
			PreparedStatement stmt = getStatement(conn, getHoldingsForUserSQL);
			stmt.setString(1, userID);

			ResultSet rs = stmt.executeQuery();

			while (rs.next()) {
				HoldingDataBean holdingData = getHoldingDataFromResultSet(rs);
				holdingDataBeans.add(holdingData);
			}

			stmt.close();
			commit(conn);

		} catch (Exception e) {
			Log.error("TradeDirect:getHoldings -- error getting user holings",
					e);
			rollBack(conn, e);
		} finally {
			releaseConn(conn);
		}
		return holdingDataBeans;
	}

	/**
     * @see TradeServices#getHolding(Integer)
     */
    @GET
    @Path("accounts/{userID}/holdings/{holdingID}")
    @Produces({"application/json"})
    public HoldingDataBean getHolding(@PathParam(value = "holdingID") Integer holdingID) throws Exception {
		HoldingDataBean holdingData = null;
		Connection conn = null;
		try {
			if (Log.doTrace())
				Log.trace("TradeDirect:getHolding - inSession("
						+ this.inSession + ")", holdingID);

			conn = getConn();
			holdingData = getHoldingData(holdingID.intValue());

			commit(conn);

		} catch (Exception e) {
			Log.error("TradeDirect:getHolding -- error getting holding "
					+ holdingID + "", e);
			rollBack(conn, e);
		} finally {
			releaseConn(conn);
		}
		return holdingData;
	}

	/**
     * @see TradeServices#getAccountData(String)
     */
    @GET
    @Path("accounts/{userID}")
    @Produces({"application/json"})
	public AccountDataBean getAccountData(@PathParam(value = "userID") String userID) throws Exception {
 		try {
			AccountDataBean accountData = null;
			Connection conn = null;
			try {
				if (Log.doTrace())
					Log.trace("TradeDirect:getAccountData - inSession("
							+ this.inSession + ")", userID);

				conn = getConn();
				accountData = getAccountData(conn, userID);
				commit(conn);

			} catch (Exception e) {
				Log
						.error(
								"TradeDirect:getAccountData -- error getting account data",
								e);
				rollBack(conn, e);
			} finally {
				releaseConn(conn);
			}
			return accountData;
		} catch (Exception e) {
			throw new Exception(e.getMessage(), e);
		}
	}

	private AccountDataBean getAccountData(Connection conn, String userID)
			throws Exception {
		PreparedStatement stmt = getStatement(conn, getAccountForUserSQL);
		stmt.setString(1, userID);
		ResultSet rs = stmt.executeQuery();
		AccountDataBean accountData = getAccountDataFromResultSet(rs);
		stmt.close();
		return accountData;
	}


	private HoldingDataBean getHoldingData(int holdingID) throws Exception {
		HoldingDataBean holdingData = null;
		Connection conn = null;
		try {
			conn = getConn();
			holdingData = getHoldingData(conn, holdingID);
			commit(conn);
		} catch (Exception e) {
			Log.error("TradeDirect:getHoldingData -- error getting data", e);
			rollBack(conn, e);
		} finally {
			releaseConn(conn);
		}
		return holdingData;
	}

	private HoldingDataBean getHoldingData(Connection conn, int holdingID)
			throws Exception {
		HoldingDataBean holdingData = null;
		PreparedStatement stmt = getStatement(conn, getHoldingSQL);
		stmt.setInt(1, holdingID);
		ResultSet rs = stmt.executeQuery();
		if (!rs.next())
			Log.error("TradeDirect:getHoldingData -- no results -- holdingID="
					+ holdingID);
		else
			holdingData = getHoldingDataFromResultSet(rs);

		stmt.close();
		return holdingData;
	}

	private OrderDataBean getOrderData(Connection conn, int orderID)
			throws Exception {
		OrderDataBean orderData = null;
		if (Log.doTrace())
			Log.trace("TradeDirect:getOrderData(conn, " + orderID + ")");
		PreparedStatement stmt = getStatement(conn, getOrderSQL);
		stmt.setInt(1, orderID);
		ResultSet rs = stmt.executeQuery();
		if (!rs.next())
			Log.error("TradeDirect:getOrderData -- no results for orderID:"
					+ orderID);
		else
			orderData = getOrderDataFromResultSet(rs);
		stmt.close();
		return orderData;
	}

	/**
     * @see TradeServices#getAccountProfileData(String)
     */
	   @GET
	    @Path("accounts/{userID}/profile")
	    @Produces({"application/json"})
	    public AccountProfileDataBean getAccountProfileData(
	    		@PathParam(value = "userID") String userID) throws Exception {

		AccountProfileDataBean accountProfileData = null;
		Connection conn = null;

		try {
			if (Log.doTrace())
				Log.trace("TradeDirect:getAccountProfileData - inSession("
						+ this.inSession + ")", userID);

			conn = getConn();
			accountProfileData = getAccountProfileData(conn, userID);
			commit(conn);
		} catch (Exception e) {
			Log
					.error(
							"TradeDirect:getAccountProfileData -- error getting profile data",
							e);
			rollBack(conn, e);
		} finally {
			releaseConn(conn);
		}
		return accountProfileData;
	}

	private AccountProfileDataBean getAccountProfileData(Connection conn,
			String userID) throws Exception {
		PreparedStatement stmt = getStatement(conn, getAccountProfileSQL);
		stmt.setString(1, userID);

		ResultSet rs = stmt.executeQuery();

		AccountProfileDataBean accountProfileData = getAccountProfileDataFromResultSet(rs);
		stmt.close();
		return accountProfileData;
	}

	private AccountProfileDataBean getAccountProfileData(Connection conn,
			Integer accountID) throws Exception {
		PreparedStatement stmt = getStatement(conn,
				getAccountProfileForAccountSQL);
		stmt.setInt(1, accountID.intValue());

		ResultSet rs = stmt.executeQuery();

		AccountProfileDataBean accountProfileData = getAccountProfileDataFromResultSet(rs);
		stmt.close();
		return accountProfileData;
	}

	/**
     * @see TradeServices#updateAccountProfile(AccountProfileDataBean)
     */
	   @PUT
	    @Path("accounts/{userID}/profile/{password}/{fullName}/{address}/{email}/{creditCard}")
	    @Produces({"application/json"})
		public AccountProfileDataBean updateAccountProfile(
					@PathParam(value = "userID") String userID,
					@PathParam(value = "password") String password,
					@PathParam(value = "fullName") String fullName,
					@PathParam(value = "address") String address,
					@PathParam(value = "email") String email,
					@PathParam(value = "creditCard") String creditCard) throws Exception {
		   //IBM added new profile data declaration to allow for paramters to be passed in through URL
	  	    AccountProfileDataBean profileData = new AccountProfileDataBean(
				  userID,password,fullName,address,email,creditCard);  
		AccountProfileDataBean accountProfileData = null;
		Connection conn = null;

		try {
			if (Log.doTrace())
				Log.trace("TradeDirect:updateAccountProfileData - inSession("
						+ this.inSession + ")", profileData.getUserID());

			conn = getConn();
			updateAccountProfile(conn, profileData);

			accountProfileData = getAccountProfileData(conn, profileData
					.getUserID());
			commit(conn);
		} catch (Exception e) {
			Log
					.error(
							"TradeDirect:getAccountProfileData -- error getting profile data",
							e);
			rollBack(conn, e);
		} finally {
			releaseConn(conn);
		}
		return accountProfileData;
	}

	private void creditAccountBalance(Connection conn,
			AccountDataBean accountData, BigDecimal credit) throws Exception {
		PreparedStatement stmt = getStatement(conn, creditAccountBalanceSQL);

		stmt.setBigDecimal(1, credit);
		stmt.setInt(2, accountData.getAccountID().intValue());

		stmt.executeUpdate();
		stmt.close();

	}

	// Set Timestamp to zero to denote sell is inflight
	// UPDATE -- could add a "status" attribute to holding
	private void updateHoldingStatus(Connection conn, Integer holdingID,
			String symbol) throws Exception {
		Timestamp ts = new Timestamp(0);
		PreparedStatement stmt = getStatement(conn,
				"update holdingejb set purchasedate= ? where holdingid = ?");

		stmt.setTimestamp(1, ts);
		stmt.setInt(2, holdingID.intValue());
		stmt.executeUpdate();
		stmt.close();
	}

	private void updateOrderStatus(Connection conn, Integer orderID,
			String status) throws Exception {
		PreparedStatement stmt = getStatement(conn, updateOrderStatusSQL);

		stmt.setString(1, status);
		stmt.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
		stmt.setInt(3, orderID.intValue());
		stmt.executeUpdate();
		stmt.close();
	}

	private void updateOrderHolding(Connection conn, int orderID, int holdingID)
			throws Exception {
		PreparedStatement stmt = getStatement(conn, updateOrderHoldingSQL);

		stmt.setInt(1, holdingID);
		stmt.setInt(2, orderID);
		stmt.executeUpdate();
		stmt.close();
	}

	private void updateAccountProfile(Connection conn,
			AccountProfileDataBean profileData) throws Exception {
		PreparedStatement stmt = getStatement(conn, updateAccountProfileSQL);

		stmt.setString(1, profileData.getPassword());
		stmt.setString(2, profileData.getFullName());
		stmt.setString(3, profileData.getAddress());
		stmt.setString(4, profileData.getEmail());
		stmt.setString(5, profileData.getCreditCard());
		stmt.setString(6, profileData.getUserID());

		stmt.executeUpdate();
		stmt.close();
	}

	

	/**
     * @see TradeServices#login(String, String)
     */
    @PUT
    @Path("accounts/{userID}/{password}")
    @Produces({"application/json"})
	public AccountDataBean login(@PathParam(value = "userID") String userID, 
			@PathParam(value = "password") String password) throws Exception {

		AccountDataBean accountData = null;
		Connection conn = null;
		try {
			if (Log.doTrace())
				Log.trace("TradeDirect:login - inSession(" + this.inSession
						+ ")", userID, password);

			conn = getConn();
			PreparedStatement stmt = getStatement(conn, getAccountProfileSQL);
			stmt.setString(1, userID);

			ResultSet rs = stmt.executeQuery();
			if (!rs.next()) {
				Log.error("TradeDirect:login -- failure to find account for"
						+ userID);
				throw new javax.ejb.FinderException("Cannot find account for"
						+ userID);
			}

			String pw = rs.getString("passwd");
			stmt.close();
			if ((pw == null) || (pw.equals(password) == false)) {
				String error = "TradeDirect:Login failure for user: " + userID
						+ "\n\tIncorrect password-->" + userID + ":" + password;
				Log.error(error);
				throw new Exception(error);
			}

			stmt = getStatement(conn, loginSQL);
			stmt.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
			stmt.setString(2, userID);

			stmt.executeUpdate();
			// ?assert rows==1?
			stmt.close();

			stmt = getStatement(conn, getAccountForUserSQL);
			stmt.setString(1, userID);
			rs = stmt.executeQuery();

			accountData = getAccountDataFromResultSet(rs);

			stmt.close();

			commit(conn);
		} catch (Exception e) {
			Log.error("TradeDirect:login -- error logging in user", e);
			rollBack(conn, e);
		} finally {
			releaseConn(conn);
		}
		return accountData;

		/*
         * setLastLogin( new Timestamp(System.currentTimeMillis()) );
         * setLoginCount( getLoginCount() + 1 );
         */
	}

    @PUT
    @Path("accounts/{userID}")
	public void logout(@PathParam(value = "userID") String userID) throws Exception {
		if (Log.doTrace())
			Log.trace("TradeDirect:logout - inSession(" + this.inSession + ")",
					userID);
		Connection conn = null;
		try {
			conn = getConn();
			PreparedStatement stmt = getStatement(conn, logoutSQL);
			stmt.setString(1, userID);
			stmt.executeUpdate();
			stmt.close();

			commit(conn);
		} catch (Exception e) {
			Log.error("TradeDirect:logout -- error logging out user", e);
			rollBack(conn, e);
		} finally {
			releaseConn(conn);
		}
	}

	/**
     * @see TradeServices#register(String, String, String, String, String,
     *      String, BigDecimal, boolean)
     */
    @POST
    @Path("accounts/{userID}/{password}/{fullname}/{address}/{email}/{creditcard}/{openBalance}")
    @Produces({"application/json"})
	public AccountDataBean register(
			@PathParam(value = "userID") String userID, 
			@PathParam(value = "password") String password, @PathParam(value = "fullname") String fullname, 
			@PathParam(value = "address") String address, @PathParam(value = "email") String email, 
			@PathParam(value = "creditcard") String creditcard, 
			@PathParam(value = "openBalance") BigDecimal openBalance) throws Exception {

		AccountDataBean accountData = null;
		Connection conn = null;
		try {
			if (Log.doTrace())
				Log.traceEnter("TradeDirect:register - inSession("
						+ this.inSession + ")");

			conn = getConn();
			PreparedStatement stmt = getStatement(conn, createAccountSQL);

			Integer accountID = KeySequenceDirect.getNextID(conn, "account",
					inSession, getInGlobalTxn());
			BigDecimal balance = openBalance;
			Timestamp creationDate = new Timestamp(System.currentTimeMillis());
			Timestamp lastLogin = creationDate;
			int loginCount = 0;
			int logoutCount = 0;

			stmt.setInt(1, accountID.intValue());
			stmt.setTimestamp(2, creationDate);
			stmt.setBigDecimal(3, openBalance);
			stmt.setBigDecimal(4, balance);
			stmt.setTimestamp(5, lastLogin);
			stmt.setInt(6, loginCount);
			stmt.setInt(7, logoutCount);
			stmt.setString(8, userID);
			stmt.executeUpdate();
			stmt.close();

			stmt = getStatement(conn, createAccountProfileSQL);
			stmt.setString(1, userID);
			stmt.setString(2, password);
			stmt.setString(3, fullname);
			stmt.setString(4, address);
			stmt.setString(5, email);
			stmt.setString(6, creditcard);
			stmt.executeUpdate();
			stmt.close();

			commit(conn);

			accountData = new AccountDataBean(accountID, loginCount,
					logoutCount, lastLogin, creationDate, balance, openBalance,
					userID);
			if (Log.doTrace())
				Log.traceExit("TradeDirect:register");
		} catch (Exception e) {
			Log.error("TradeDirect:register -- error registering new user", e);
		} finally {
			releaseConn(conn);
		}
		return accountData;
	}

	private AccountDataBean getAccountDataFromResultSet(ResultSet rs)
			throws Exception {
		AccountDataBean accountData = null;

		if (!rs.next())
			Log
					.error("TradeDirect:getAccountDataFromResultSet -- cannot find account data");

		else
			accountData = new AccountDataBean(new Integer(rs
					.getInt("accountID")), rs.getInt("loginCount"), rs
					.getInt("logoutCount"), rs.getTimestamp("lastLogin"), rs
					.getTimestamp("creationDate"), rs.getBigDecimal("balance"),
					rs.getBigDecimal("openBalance"), rs
							.getString("profile_userID"));
		return accountData;
	}

	private AccountProfileDataBean getAccountProfileDataFromResultSet(
			ResultSet rs) throws Exception {
		AccountProfileDataBean accountProfileData = null;

		if (!rs.next())
			Log
					.error("TradeDirect:getAccountProfileDataFromResultSet -- cannot find accountprofile data");
		else
			accountProfileData = new AccountProfileDataBean(rs
					.getString("userID"), rs.getString("passwd"), rs
					.getString("fullName"), rs.getString("address"), rs
					.getString("email"), rs.getString("creditCard"));

		return accountProfileData;
	}

	private HoldingDataBean getHoldingDataFromResultSet(ResultSet rs)
			throws Exception {
		HoldingDataBean holdingData = null;

		holdingData = new HoldingDataBean(new Integer(rs.getInt("holdingID")),
				rs.getDouble("quantity"), rs.getBigDecimal("purchasePrice"), rs
						.getTimestamp("purchaseDate"), rs
						.getString("quote_symbol"));
		return holdingData;
	}

	private OrderDataBean getOrderDataFromResultSet(ResultSet rs)
			throws Exception {
		OrderDataBean orderData = null;

		orderData = new OrderDataBean(new Integer(rs.getInt("orderID")), rs
				.getString("orderType"), rs.getString("orderStatus"), rs
				.getTimestamp("openDate"), rs.getTimestamp("completionDate"),
				rs.getDouble("quantity"), rs.getBigDecimal("price"), rs
						.getBigDecimal("orderFee"), rs
						.getString("quote_symbol"));
		return orderData;
	}


	
	
	private void releaseConn(Connection conn) throws Exception {
		try {
			if (conn != null) {
				conn.close();
				if (Log.doTrace()) {
					synchronized (lock) {
						connCount--;
					}
					Log
							.trace("TradeDirect:releaseConn -- connection closed, connCount="
									+ connCount);
				} 
			}
		} catch (Exception e) {
			Log
					.error(
							"TradeDirect:releaseConnection -- failed to close connection",
							e);
		}
	}

	/*
     * Lookup the TradeData datasource
     * 
     */
	private void getDataSource() throws Exception {
		datasource = (DataSource) context.lookup(dsName);
	}

	/*
     * Allocate a new connection to the datasource
     * 
     */
	private static int connCount = 0;

	private static Integer lock = new Integer(0);

	private Connection getConn() throws Exception {

		Connection conn = null;
		if (datasource == null)
			getDataSource();
		conn = datasource.getConnection();
		conn.setAutoCommit(false);
		if (Log.doTrace()) {
			synchronized (lock) {
				connCount++;
			}
			Log
					.trace("TradeDirect:getConn -- new connection allocated, IsolationLevel="
							+ conn.getTransactionIsolation()
							+ " connectionCount = " + connCount);
		}

		return conn;
	}

	public Connection getConnPublic() throws Exception {
		return getConn();
	}

	/*
     * Commit the provided connection if not under Global Transaction scope -
     * conn.commit() is not allowed in a global transaction. the txn manager
     * will perform the commit
     */
	private void commit(Connection conn) throws Exception {
		if (!inSession) {
			if ((getInGlobalTxn() == false) && (conn != null))
				conn.commit();
		}
	}

	/*
     * Rollback the statement for the given connection
     * 
     */
	private void rollBack(Connection conn, Exception e) throws Exception {
		if (!inSession) {
			Log
					.log("TradeDirect:rollBack -- rolling back conn due to previously caught exception -- inGlobalTxn="
							+ getInGlobalTxn());
			if ((getInGlobalTxn() == false) && (conn != null))
				conn.rollback();
			else
				throw e; // Throw the exception
			// so the Global txn manager will rollBack
		}
	}

	/*
     * Allocate a new prepared statment for this connection
     * 
     */
	private PreparedStatement getStatement(Connection conn, String sql)
			throws Exception {
		return conn.prepareStatement(sql);
	}




	private static final String createAccountSQL = "insert into accountejb "
			+ "( accountid, creationDate, openBalance, balance, lastLogin, loginCount, logoutCount, profile_userid) "
			+ "VALUES (  ?  ,  ?  ,  ?  ,  ?  ,  ?  ,  ?  ,  ?  ,  ?  )";

	private static final String createAccountProfileSQL = "insert into accountprofileejb "
			+ "( userid, passwd, fullname, address, email, creditcard ) "
			+ "VALUES (  ?  ,  ?  ,  ?  ,  ?  ,  ?  ,  ?  )";

	private static final String createHoldingSQL = "insert into holdingejb "
			+ "( holdingid, purchaseDate, purchasePrice, quantity, quote_symbol, account_accountid ) "
			+ "VALUES (  ?  ,  ?  ,  ?  ,  ?  ,  ?  ,  ? )";

	private static final String createOrderSQL = "insert into orderejb "
			+ "( orderid, ordertype, orderstatus, opendate, quantity, price, orderfee, account_accountid,  holding_holdingid, quote_symbol) "
			+ "VALUES (  ?  ,  ?  ,  ?  ,  ?  ,  ?  ,  ?  ,  ?  , ? , ? , ?)";

	private static final String removeHoldingSQL = "delete from holdingejb where holdingid = ?";

	private static final String removeHoldingFromOrderSQL = "update orderejb set holding_holdingid=null where holding_holdingid = ?";

	private final static String updateAccountProfileSQL = "update accountprofileejb set "
			+ "passwd = ?, fullname = ?, address = ?, email = ?, creditcard = ? "
			+ "where userid = (select profile_userid from accountejb a "
			+ "where a.profile_userid=?)";

	private final static String loginSQL = "update accountejb set lastLogin=?, logincount=logincount+1 "
			+ "where profile_userid=?";

	private static final String logoutSQL = "update accountejb set logoutcount=logoutcount+1 "
			+ "where profile_userid=?";

	private final static String getAccountProfileSQL = "select * from accountprofileejb ap where ap.userid = "
			+ "(select profile_userid from accountejb a where a.profile_userid=?)";

	private final static String getAccountProfileForAccountSQL = "select * from accountprofileejb ap where ap.userid = "
			+ "(select profile_userid from accountejb a where a.accountid=?)";

	private static final String getAccountForUserSQL = "select * from accountejb a where a.profile_userid = "
			+ "( select userid from accountprofileejb ap where ap.userid = ?)";

	private static final String getHoldingSQL = "select * from holdingejb h where h.holdingid = ?";

	private static final String getHoldingsForUserSQL = "select * from holdingejb h where h.account_accountid = "
			+ "(select a.accountid from accountejb a where a.profile_userid = ?)";

	private static final String getOrderSQL = "select * from orderejb o where o.orderid = ?";

	private static final String getOrdersByUserSQL = "select * from orderejb o where o.account_accountid = "
			+ "(select a.accountid from accountejb a where a.profile_userid = ?)";

	private static final String getClosedOrdersSQL = "select * from orderejb o "
			+ "where o.orderstatus = 'closed' AND o.account_accountid = "
			+ "(select a.accountid from accountejb a where a.profile_userid = ?)";

	private static final String creditAccountBalanceSQL = "update accountejb set "
			+ "balance = balance + ? " + "where accountid = ?";

	private static final String updateOrderStatusSQL = "update orderejb set "
			+ "orderstatus = ?, completiondate = ? " + "where orderid = ?";

	private static final String updateOrderHoldingSQL = "update orderejb set "
			+ "holding_holdingID = ? " + "where orderid = ?";

	private static boolean initialized = false;

	public static synchronized void init() {
	    if (initialized)
	        return;
	    if (Log.doTrace())
	        Log.trace("TradeDirect:init -- *** initializing");
	    try {
	        if (Log.doTrace())
	            Log.trace("TradeDirect: init");
	        context = new InitialContext();
	        datasource = (DataSource) context.lookup(dsName);
	    } catch (Exception e) {
	        Log.error("TradeDirect:init -- error on JNDI lookups of DataSource -- TradeDirect will not work",e);
	        return;
	    }

	    try {
	        qConnFactory = (ConnectionFactory) context.lookup("java:comp/env/jms/QueueConnectionFactory");
	    } catch (Exception e) {
	        try {
	            qConnFactory = (ConnectionFactory) context.lookup("java:/jms/QueueConnectionFactory");
	        } catch (Exception e2) {
	            Log.error("TradeDirect:init  Unable to locate QueueConnectionFactory.\n\t -- Asynchronous mode will not work correctly and Quote Price change publishing will be disabled");
	            TradeConfig.setPublishQuotePriceChange(false);
	        }
	    }

		try {
			queue = (Queue) context.lookup("java:comp/env/jms/TradeBrokerQueue");
		} catch (Exception e) {
		    try {
		        queue = (Queue) context.lookup("openejb:Resource/jms/TradeBrokerQueue");
		    } catch (Exception e2) {
		        try {
		            queue = (Queue) context.lookup("java:/jms/TradeBrokerQueue");
		        } catch (Exception e3) {
		            try {
		                queue = (Queue) context.lookup("jms/TradeBrokerQueue");
		            } catch (Exception e4) {
		                Log.error("TradeDirect:init  Unable to locate TradeBrokerQueue.\n\t -- Asynchronous mode will not work correctly and Quote Price change publishing will be disabled");
		                TradeConfig.setPublishQuotePriceChange(false);
		            }		    
		        }
		    }
		}
		
		try {
		    tConnFactory = (ConnectionFactory) context.lookup("java:comp/env/jms/TopicConnectionFactory");
		} catch (Exception e) {
		    try {
		        tConnFactory = (ConnectionFactory) context.lookup("java:/jms/TopicConnectionFactory");
		    } catch (Exception e2) {
		        Log.error("TradeDirect:init  Unable to locate TopicConnectionFactory.\n\t -- Asynchronous mode will not work correctly and Quote Price change publishing will be disabled");
		        TradeConfig.setPublishQuotePriceChange(false);
		    }
		}

		try {
		    streamerTopic = (Topic) context.lookup("java:comp/env/jms/TradeStreamerTopic");
		} catch (Exception e) {
		    try {
		        streamerTopic = (Topic) context.lookup("openejb:Resource/jms/TradeStreamerTopic");
		    } catch (Exception e2) {
		        try {
		            streamerTopic = (Topic) context.lookup("java:/jms/TradeStreamerTopic");
		        } catch (Exception e3) {
		            try {
		                streamerTopic = (Topic) context.lookup("jms/TradeStreamerTopic");
		            } catch (Exception e4) {
		                Log.error("TradeDirect:init  Unable to locate TradeStreamerTopic.\n\t -- Asynchronous mode will not work correctly and Quote Price change publishing will be disabled");
		                TradeConfig.setPublishQuotePriceChange(false);
		            }
		        }	
		    }
		}
		
		if (Log.doTrace())
			Log.trace("TradeDirect:init -- +++ initialized");

		initialized = true;
	}

	public static void destroy() {
		try {
			if (!initialized)
				return;
			Log.trace("TradeDirect:destroy");
		} catch (Exception e) {
			Log.error("TradeDirect:destroy", e);
		}
	}

	private static InitialContext context;

	private static ConnectionFactory qConnFactory;

	private static Queue queue;

	private static ConnectionFactory tConnFactory;

	private static Topic streamerTopic;

	/**
     * Gets the inGlobalTxn
     * 
     * @return Returns a boolean
     */
	private boolean getInGlobalTxn() {
		return inGlobalTxn;
	}

	/**
     * Sets the inGlobalTxn
     * 
     * @param inGlobalTxn
     *            The inGlobalTxn to set
     */
	private void setInGlobalTxn(boolean inGlobalTxn) {
		this.inGlobalTxn = inGlobalTxn;
	}



}
