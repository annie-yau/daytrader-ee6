/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.ibm.websphere.samples.daytrader.rest;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;

import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.jms.ConnectionFactory;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.naming.InitialContext;
import javax.sql.DataSource;
import javax.transaction.UserTransaction;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import com.ibm.websphere.samples.daytrader.QuoteDataBean;
import com.ibm.websphere.samples.daytrader.TradeConfig;
import com.ibm.websphere.samples.daytrader.TradeServices;
import com.ibm.websphere.samples.daytrader.util.FinancialUtils;
import com.ibm.websphere.samples.daytrader.util.Log;

@Path(value = "/")
public class Quotes {


	

    
    private static String dsName = TradeConfig.DATASOURCE;
	private static DataSource datasource = null;
	private boolean inGlobalTxn = false;

	private boolean inSession = false;
    
    
   
    
    public Quotes() {
		if (initialized == false)
			init();
	}
	/**
     * @see TradeServices#createQuote(String, String, BigDecimal)
     */
    @POST
    @Path("quotes/{symbol}/{companyName}/{price}")
    @Produces({"application/json"})
    public QuoteDataBean createQuote(@PathParam(value = "symbol") String symbol, 
    		@PathParam(value = "companyName") String companyName, @PathParam(value = "price") BigDecimal price) throws Exception {
    	System.out.println("Calling create quote");
    	QuoteDataBean quoteData = null;
		Connection conn = null;
		try {
			if (Log.doTrace())
				Log.traceEnter("TradeDirect:createQuote - inSession("
						+ this.inSession + ")");

			price = price.setScale(FinancialUtils.SCALE, FinancialUtils.ROUND);
			double volume = 0.0, change = 0.0;

			conn = getConn();
			PreparedStatement stmt = getStatement(conn, createQuoteSQL);
			stmt.setString(1, symbol); // symbol
			stmt.setString(2, companyName); // companyName
			stmt.setDouble(3, volume); // volume
			stmt.setBigDecimal(4, price); // price
			stmt.setBigDecimal(5, price); // open
			stmt.setBigDecimal(6, price); // low
			stmt.setBigDecimal(7, price); // high
			stmt.setDouble(8, change); // change

			stmt.executeUpdate();
			stmt.close();
			commit(conn);

			quoteData = new QuoteDataBean(symbol, companyName, volume, price,
					price, price, price, change);
			if (Log.doTrace())
				Log.traceExit("TradeDirect:createQuote");
		} catch (Exception e) {
			Log.error("TradeDirect:createQuote -- error creating quote", e);
		} finally {
			releaseConn(conn);
		}
		return quoteData;
	}

    
	/**
     * @see TradeServices#getQuote(String)
     */
    @GET
    @Path("quotes/{symbol}")
    @Produces({"application/json"})
	public QuoteDataBean getQuote(@PathParam(value = "symbol") String symbol) throws Exception {
    		QuoteDataBean quoteData = null;
    		Connection conn = null;
    		UserTransaction txn = null;
    		try {
    			if (Log.doTrace())
    				Log.trace("TradeDirect:getQuote - inSession(" + this.inSession
    						+ ")", symbol);

    			conn = getConn();
    			quoteData = getQuote(conn, symbol);
    			commit(conn);
    		} catch (Exception e) {
    			Log.error("TradeDirect:getQuote -- error getting quote", e);
    			rollBack(conn, e);
    		} finally {
    			releaseConn(conn);
    		}
    		return quoteData;
    	}

    	private QuoteDataBean getQuote(Connection conn, String symbol)
    			throws Exception {
    		QuoteDataBean quoteData = null;
    		PreparedStatement stmt = getStatement(conn, getQuoteSQL);
    		stmt.setString(1, symbol); // symbol

    		ResultSet rs = stmt.executeQuery();

    		if (!rs.next())
    			Log
    					.error("TradeDirect:getQuote -- failure no result.next() for symbol: "
    							+ symbol);

    		else
    			quoteData = getQuoteDataFromResultSet(rs);

    		stmt.close();

    		return quoteData;
    	}
    
    	private QuoteDataBean getQuoteDataFromResultSet(ResultSet rs)
    			throws Exception {
    		
    		QuoteDataBean quoteData = null;
    		quoteData = new QuoteDataBean(rs.getString("symbol"), rs
    				.getString("companyName"), rs.getDouble("volume"), rs
    				.getBigDecimal("price"), rs.getBigDecimal("open1"), rs
    				.getBigDecimal("low"), rs.getBigDecimal("high"), rs
    				.getDouble("change1"));
    		return quoteData;
    	}

    
    
	/**
     * @see TradeServices#getAllQuotes(String)
     */
    @GET
    @Path("quotes")
    @Produces({"application/json"})
	public Collection<?> getAllQuotes() throws Exception {  	
    	Collection<QuoteDataBean> quotes = new ArrayList<QuoteDataBean>();
		QuoteDataBean quoteData = null;

		Connection conn = null;
		try {
			conn = getConn();

			PreparedStatement stmt = getStatement(conn, getAllQuotesSQL);

			ResultSet rs = stmt.executeQuery();

			//DHV - found a fixed a defect
	    	//while (!rs.next()) {
	    	while (rs.next()) {
   				quoteData = getQuoteDataFromResultSet(rs);
    			quotes.add(quoteData);	    			
		    }

			stmt.close();
		} catch (Exception e) {
			Log.error("TradeDirect:getAllQuotes", e);
			rollBack(conn, e);
		}
		finally {
			releaseConn(conn);
		}
		return quotes;
	}

    
    
    
	/**
     * @see TradeServices#updateQuotePriceVolume(String, BigDecimal, double)
     */
    @PUT
    @Path("quotes/{symbol}/{changeFactor}/{sharesTraded}")
    @Produces({"application/json"})
	public QuoteDataBean updateQuotePriceVolume(@PathParam(value = "symbol") String symbol, 
			@PathParam(value = "changeFactor") BigDecimal changeFactor, 
			@PathParam(value = "sharesTraded") double sharesTraded) throws Exception {
    	return updateQuotePriceVolumeInt(symbol, changeFactor, sharesTraded,
				TradeConfig.getPublishQuotePriceChange());
	}
    
    //This is exclusively trade direct
    @PUT
    @Path("quotes/{symbol}/{changeFactor}/{sharesTraded}/{publishQuotePriceChange}")
    @Produces({"application/json"})
	public QuoteDataBean updateQuotePriceVolumeInt(@PathParam(value = "symbol") String symbol, 
			@PathParam(value = "changeFactor") BigDecimal changeFactor, 
			@PathParam(value = "sharesTraded") double sharesTraded, @PathParam(value = "sharesTraded") boolean publishQuotePriceChange) 
					throws Exception {
    	if (TradeConfig.getUpdateQuotePrices() == false)
			return new QuoteDataBean();

		QuoteDataBean quoteData = null;
		Connection conn = null;
		UserTransaction txn = null;
		try {
			if (Log.doTrace())
				Log.trace("TradeDirect:updateQuotePriceVolume - inSession("
						+ this.inSession + ")", symbol, changeFactor,
						new Double(sharesTraded));

			conn = getConn();

			quoteData = getQuoteForUpdate(conn, symbol);
			BigDecimal oldPrice = quoteData.getPrice();
			double newVolume = quoteData.getVolume() + sharesTraded;

			if (oldPrice.equals(TradeConfig.PENNY_STOCK_PRICE)) {
				changeFactor = TradeConfig.PENNY_STOCK_RECOVERY_MIRACLE_MULTIPLIER;
			} else if (oldPrice.compareTo(TradeConfig.MAXIMUM_STOCK_PRICE) > 0) {
				changeFactor = TradeConfig.MAXIMUM_STOCK_SPLIT_MULTIPLIER;
			}

			BigDecimal newPrice = changeFactor.multiply(oldPrice).setScale(2,
					BigDecimal.ROUND_HALF_UP);

			updateQuotePriceVolume(conn, quoteData.getSymbol(), newPrice,
					newVolume);
			quoteData = getQuote(conn, symbol);

			commit(conn);

			if (publishQuotePriceChange) {
				publishQuotePriceChange(quoteData, oldPrice, changeFactor,
						sharesTraded);
			}

		} catch (Exception e) {
			Log
					.error("TradeDirect:updateQuotePriceVolume -- error updating quote price/volume for symbol:"
							+ symbol);
			rollBack(conn, e);
			throw e;
		} finally {
			releaseConn(conn);
		}
		return quoteData;
	}
    
    private QuoteDataBean getQuoteForUpdate(Connection conn, String symbol)
			throws Exception {
		QuoteDataBean quoteData = null;
		PreparedStatement stmt = getStatement(conn, getQuoteForUpdateSQL);
		stmt.setString(1, symbol); // symbol

		ResultSet rs = stmt.executeQuery();

		if (!rs.next())
			Log.error("TradeDirect:getQuote -- failure no result.next()");

		else
			quoteData = getQuoteDataFromResultSet(rs);

		stmt.close();

		return quoteData;
	}
    
	private void updateQuotePriceVolume(Connection conn, String symbol,
			BigDecimal newPrice, double newVolume) throws Exception {

		PreparedStatement stmt = getStatement(conn, updateQuotePriceVolumeSQL);

		stmt.setBigDecimal(1, newPrice);
		stmt.setBigDecimal(2, newPrice);
		stmt.setDouble(3, newVolume);
		stmt.setString(4, symbol);

		int count = stmt.executeUpdate();
		stmt.close();
	}
    /**
     * Moved from TradeSLSLocal
     * @param quote
     * @param oldPrice
     * @param changeFactor
     * @param sharesTraded
     */
    @PUT
    @Path("quotes/{oldPrice}/{changeFactor}/{sharesTraded}")
    @Consumes({"application/json"})
    @Produces({"application/json"})
    public void publishQuotePriceChange(QuoteDataBean quote, BigDecimal oldPrice, BigDecimal changeFactor, double sharesTraded){
    	System.out.println(quote.toString());
    	if (!TradeConfig.getPublishQuotePriceChange())
            return;
        if (Log.doTrace())
            Log.trace("TradeSLSBBean:publishQuotePricePublishing -- quoteData = " + quote);

        javax.jms.Connection conn = null;
        Session sess = null;

        try {
            conn = tConnFactory.createConnection();
            sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageProducer msgProducer = sess.createProducer(streamerTopic);
            TextMessage message = sess.createTextMessage();

            String command = "updateQuote";
            message.setStringProperty("command", command);
            message.setStringProperty("symbol", quote.getSymbol());
            message.setStringProperty("company", quote.getCompanyName());
            message.setStringProperty("price", quote.getPrice().toString());
            message.setStringProperty("oldPrice", oldPrice.toString());
            message.setStringProperty("open", quote.getOpen().toString());
            message.setStringProperty("low", quote.getLow().toString());
            message.setStringProperty("high", quote.getHigh().toString());
            message.setDoubleProperty("volume", quote.getVolume());

            message.setStringProperty("changeFactor", changeFactor.toString());
            message.setDoubleProperty("sharesTraded", sharesTraded);
            message.setLongProperty("publishTime", System.currentTimeMillis());
            message.setText("Update Stock price for " + quote.getSymbol() + " old price = " + oldPrice + " new price = " + quote.getPrice());

            msgProducer.send(message);
        } catch (Exception e) {
            throw new EJBException(e.getMessage(), e); // pass the exception back
        } finally {
            try {
                if (conn != null)
                    conn.close();
                if (sess != null)
                    sess.close();
            } catch (javax.jms.JMSException e) {
                throw new EJBException(e.getMessage(), e); // pass the exception back
            }
        }
    }

    
    /**
     * Moved from TradeSLSLocal
     * @param quote
     * @param oldPrice
     * @param changeFactor
     * @param sharesTraded
     * @throws Exception 
     */
    @GET
    @Path("quotes/investmentReturn/{investment}/{NetValue}")
    @Produces({"application/json"})
    public String investmentReturn(@PathParam(value = "investment") String investment, @PathParam(value = "NetValue") String NetValue) throws Exception {
    	 if (Log.doTrace())
             Log.trace("TradeSLSBBean:investmentReturn");

    	 double NetValueDouble = Double.valueOf(NetValue);
    	 double investmentDouble = Double.valueOf(investment);
         double diff = NetValueDouble - investmentDouble;
         double ir = diff / investmentDouble;
         return String.valueOf(ir); 
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
     * Allocate a new prepared statment for this connection
     * 
     */
	private PreparedStatement getStatement(Connection conn, String sql)
			throws Exception {
		return conn.prepareStatement(sql);
	}

	private static final String createQuoteSQL = "insert into quoteejb "
			+ "( symbol, companyName, volume, price, open1, low, high, change1 ) "
			+ "VALUES (  ?  ,  ?  ,  ?  ,  ?  ,  ?  ,  ?  ,  ?  ,  ?  )";

	private static final String getQuoteSQL = "select * from quoteejb q where q.symbol=?";

	private static final String getAllQuotesSQL = "select * from quoteejb q";

	private static final String getQuoteForUpdateSQL = "select * from quoteejb q where q.symbol=? For Update";

	private static final String updateQuotePriceVolumeSQL = "update quoteejb set "
			+ "price = ?, change1 = ? - open1, volume = ? "
			+ "where symbol = ?";

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
	
}
