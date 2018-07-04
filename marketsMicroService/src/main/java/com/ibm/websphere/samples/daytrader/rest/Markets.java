// DHV ADDED FOR STEP 2 

package com.ibm.websphere.samples.daytrader.rest;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;

import javax.jms.ConnectionFactory;
import javax.jms.Queue;
import javax.jms.Topic;
import javax.naming.InitialContext;
import javax.sql.DataSource;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import com.ibm.websphere.samples.daytrader.MarketSummaryDataBean;
import com.ibm.websphere.samples.daytrader.QuoteDataBean;
import com.ibm.websphere.samples.daytrader.TradeConfig;
import com.ibm.websphere.samples.daytrader.TradeServices;
import com.ibm.websphere.samples.daytrader.util.Log;

@Path(value = "/")
public class Markets {

    
   
	private static String dsName = TradeConfig.DATASOURCE;

	private static DataSource datasource = null;

	private static BigDecimal ZERO = new BigDecimal(0.0);

	private boolean inGlobalTxn = false;

	private boolean inSession = false;
    
	
	public Markets() {
		if (initialized == false)
			init();
	}
	/**
     * @see TradeServices#getMarketSummary()
     */
    @GET
    @Path("/markets")
    @Produces({"application/json"})
	public MarketSummaryDataBean getMarketSummary() throws Exception {
    	

    		MarketSummaryDataBean marketSummaryData = null;
    		Connection conn = null;
    		try {
    			if (Log.doTrace())
    				Log.trace("TradeDirect:getMarketSummary - inSession("
    						+ this.inSession + ")");

    			conn = getConn();
    			PreparedStatement stmt = getStatement(conn,
    					getTSIAQuotesOrderByChangeSQL,
    					ResultSet.TYPE_SCROLL_INSENSITIVE,
    					ResultSet.CONCUR_READ_ONLY);

    			ArrayList<QuoteDataBean> topGainersData = new ArrayList<QuoteDataBean>(5);
    			ArrayList<QuoteDataBean> topLosersData = new ArrayList<QuoteDataBean>(5);

    			ResultSet rs = stmt.executeQuery();

    			int count = 0;
    			while (rs.next() && (count++ < 5)) {
    				QuoteDataBean quoteData = getQuoteDataFromResultSet(rs);
    				topLosersData.add(quoteData);
    			}

    			stmt.close();
    			stmt = getStatement(
    					conn,
    					"select * from quoteejb q where q.symbol like 's:1__' order by q.change1 DESC",
    					ResultSet.TYPE_SCROLL_INSENSITIVE,
    					ResultSet.CONCUR_READ_ONLY);
    			rs = stmt.executeQuery();

    			count = 0;
    			while (rs.next() && (count++ < 5)) {
    				QuoteDataBean quoteData = getQuoteDataFromResultSet(rs);
    				topGainersData.add(quoteData);
    			}

    			/*
                 * rs.last(); count = 0; while (rs.previous() && (count++ < 5) ) {
                 * QuoteDataBean quoteData = getQuoteDataFromResultSet(rs);
                 * topGainersData.add(quoteData); }
                 */

    			stmt.close();

    			BigDecimal TSIA = ZERO;
    			BigDecimal openTSIA = ZERO;
    			double volume = 0.0;

    			if ((topGainersData.size() > 0) || (topLosersData.size() > 0)) {

    				stmt = getStatement(conn, getTSIASQL);
    				rs = stmt.executeQuery();

    				if (!rs.next())
    					Log
    							.error("TradeDirect:getMarketSummary -- error w/ getTSIASQL -- no results");
    				else
    					TSIA = rs.getBigDecimal("TSIA");
    				stmt.close();

    				stmt = getStatement(conn, getOpenTSIASQL);
    				rs = stmt.executeQuery();

    				if (!rs.next())
    					Log
    							.error("TradeDirect:getMarketSummary -- error w/ getOpenTSIASQL -- no results");
    				else
    					openTSIA = rs.getBigDecimal("openTSIA");
    				stmt.close();

    				stmt = getStatement(conn, getTSIATotalVolumeSQL);
    				rs = stmt.executeQuery();

    				if (!rs.next())
    					Log
    							.error("TradeDirect:getMarketSummary -- error w/ getTSIATotalVolumeSQL -- no results");
    				else
    					volume = rs.getDouble("totalVolume");
    				stmt.close();
    			}
    			commit(conn);

    			marketSummaryData = new MarketSummaryDataBean(TSIA, openTSIA,
    					volume, topGainersData, topLosersData);

    		}

    		catch (Exception e) {
    			Log.error("TradeDirect:login -- error logging in user", e);
    			rollBack(conn, e);
    		} finally {
    			releaseConn(conn);
    		}
    		return marketSummaryData;

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

    	private PreparedStatement getStatement(Connection conn, String sql,
    			int type, int concurrency) throws Exception {
    		return conn.prepareStatement(sql, type, concurrency);
    	}

 
    	private static final String getTSIAQuotesOrderByChangeSQL = "select * from quoteejb q "
    			+ "where q.symbol like 's:1__' order by q.change1";

    	private static final String getTSIASQL = "select SUM(price)/count(*) as TSIA from quoteejb q "
    			+ "where q.symbol like 's:1__'";

    	private static final String getOpenTSIASQL = "select SUM(open1)/count(*) as openTSIA from quoteejb q "
    			+ "where q.symbol like 's:1__'";

    	private static final String getTSIATotalVolumeSQL = "select SUM(volume) as totalVolume from quoteejb q "
    			+ "where q.symbol like 's:1__'";

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


}
