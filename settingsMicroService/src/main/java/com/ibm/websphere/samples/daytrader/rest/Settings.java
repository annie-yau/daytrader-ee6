
// DHV
// ADDED FOR STEP 2

package com.ibm.websphere.samples.daytrader.rest;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.jms.ConnectionFactory;
import javax.jms.Queue;
import javax.jms.Topic;
import javax.naming.InitialContext;
import javax.sql.DataSource;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import com.ibm.websphere.samples.daytrader.RunStatsDataBean;
import com.ibm.websphere.samples.daytrader.TradeConfig;
import com.ibm.websphere.samples.daytrader.TradeServices;
import com.ibm.websphere.samples.daytrader.util.Log;
import com.ibm.websphere.samples.daytrader.util.MDBStats;


@Path(value = "/")
public class Settings {

    public Settings() {}
   
   
    
	/*
     * Allocate a new connection to the datasource
     * 
     */
	private static int connCount = 0;

	private static Integer lock = new Integer(0);
	
	private static String dsName = TradeConfig.DATASOURCE;

	private static DataSource datasource = null;

	private static BigDecimal ZERO = new BigDecimal(0.0);

	private boolean inGlobalTxn = false;

	private boolean inSession = false;
	/**
     * @see TradeServices#resetTrade(boolean)
     */
    @DELETE
    @Path("/settings/{deleteAll}")
    @Produces({"application/json"})
	public RunStatsDataBean resetTrade(@PathParam(value = "deleteAll") boolean deleteAll) throws Exception {
    	// Clear MDB Statistics
    			MDBStats.getInstance().reset();
    			// Reset Trade

    			RunStatsDataBean runStatsData = new RunStatsDataBean();
    			Connection conn = null;
    			try {
    				if (Log.doTrace())
    					Log.traceEnter("TradeDirect:resetTrade deleteAll rows="
    							+ deleteAll);

    				conn = getConn();
    				PreparedStatement stmt = null;
    				ResultSet rs = null;

    				if (deleteAll) {
    					try {
    						stmt = getStatement(conn, "delete from quoteejb");
    						stmt.executeUpdate();
    						stmt.close();
    						stmt = getStatement(conn, "delete from accountejb");
    						stmt.executeUpdate();
    						stmt.close();
    						stmt = getStatement(conn, "delete from accountprofileejb");
    						stmt.executeUpdate();
    						stmt.close();
    						stmt = getStatement(conn, "delete from holdingejb");
    						stmt.executeUpdate();
    						stmt.close();
    						stmt = getStatement(conn, "delete from orderejb");
    						stmt.executeUpdate();
    						stmt.close();
    						// FUTURE: - DuplicateKeyException - For now, don't start at
    						// zero as KeySequenceDirect and KeySequenceBean will still
    	                    // give out
    						// the cached Block and then notice this change. Better
    	                    // solution is
    						// to signal both classes to drop their cached blocks
    						// stmt = getStatement(conn, "delete from keygenejb");
    						// stmt.executeUpdate();
    						// stmt.close();
    						commit(conn);
    					} catch (Exception e) {
    						Log
    								.error(
    										e,
    										"TradeDirect:resetTrade(deleteAll) -- Error deleting Trade users and stock from the Trade database");
    					}
    					return runStatsData;
    				}

    				stmt = getStatement(conn,
    						"delete from holdingejb where holdingejb.account_accountid is null");
    				int x = stmt.executeUpdate();
    				stmt.close();

    				// Count and Delete newly registered users (users w/ id that start
    	            // "ru:%":
    				stmt = getStatement(conn,
    						"delete from accountprofileejb where userid like 'ru:%'");
    				int rowCount = stmt.executeUpdate();
    				stmt.close();

    				stmt = getStatement(
    						conn,
    						"delete from orderejb where account_accountid in (select accountid from accountejb a where a.profile_userid like 'ru:%')");
    				rowCount = stmt.executeUpdate();
    				stmt.close();

    				stmt = getStatement(
    						conn,
    						"delete from holdingejb where account_accountid in (select accountid from accountejb a where a.profile_userid like 'ru:%')");
    				rowCount = stmt.executeUpdate();
    				stmt.close();

    				stmt = getStatement(conn,
    						"delete from accountejb where profile_userid like 'ru:%'");
    				int newUserCount = stmt.executeUpdate();
    				runStatsData.setNewUserCount(newUserCount);
    				stmt.close();

    				// Count of trade users
    				stmt = getStatement(
    						conn,
    						"select count(accountid) as \"tradeUserCount\" from accountejb a where a.profile_userid like 'uid:%'");
    				rs = stmt.executeQuery();
    				rs.next();
    				int tradeUserCount = rs.getInt("tradeUserCount");
    				runStatsData.setTradeUserCount(tradeUserCount);
    				stmt.close();

    				rs.close();
    				// Count of trade stocks
    				stmt = getStatement(
    						conn,
    						"select count(symbol) as \"tradeStockCount\" from quoteejb a where a.symbol like 's:%'");
    				rs = stmt.executeQuery();
    				rs.next();
    				int tradeStockCount = rs.getInt("tradeStockCount");
    				runStatsData.setTradeStockCount(tradeStockCount);
    				stmt.close();

    				// Count of trade users login, logout
    				stmt = getStatement(
    						conn,
    						"select sum(loginCount) as \"sumLoginCount\", sum(logoutCount) as \"sumLogoutCount\" from accountejb a where  a.profile_userID like 'uid:%'");
    				rs = stmt.executeQuery();
    				rs.next();
    				int sumLoginCount = rs.getInt("sumLoginCount");
    				int sumLogoutCount = rs.getInt("sumLogoutCount");
    				runStatsData.setSumLoginCount(sumLoginCount);
    				runStatsData.setSumLogoutCount(sumLogoutCount);
    				stmt.close();

    				rs.close();
    				// Update logoutcount and loginCount back to zero

    				stmt = getStatement(
    						conn,
    						"update accountejb set logoutCount=0,loginCount=0 where profile_userID like 'uid:%'");
    				rowCount = stmt.executeUpdate();
    				stmt.close();

    				// count holdings for trade users
    				stmt = getStatement(
    						conn,
    						"select count(holdingid) as \"holdingCount\" from holdingejb h where h.account_accountid in "
    								+ "(select accountid from accountejb a where a.profile_userid like 'uid:%')");

    				rs = stmt.executeQuery();
    				rs.next();
    				int holdingCount = rs.getInt("holdingCount");
    				runStatsData.setHoldingCount(holdingCount);
    				stmt.close();
    				rs.close();

    				// count orders for trade users
    				stmt = getStatement(
    						conn,
    						"select count(orderid) as \"orderCount\" from orderejb o where o.account_accountid in "
    								+ "(select accountid from accountejb a where a.profile_userid like 'uid:%')");

    				rs = stmt.executeQuery();
    				rs.next();
    				int orderCount = rs.getInt("orderCount");
    				runStatsData.setOrderCount(orderCount);
    				stmt.close();
    				rs.close();

    				// count orders by type for trade users
    				stmt = getStatement(
    						conn,
    						"select count(orderid) \"buyOrderCount\"from orderejb o where (o.account_accountid in "
    								+ "(select accountid from accountejb a where a.profile_userid like 'uid:%')) AND "
    								+ " (o.orderType='buy')");

    				rs = stmt.executeQuery();
    				rs.next();
    				int buyOrderCount = rs.getInt("buyOrderCount");
    				runStatsData.setBuyOrderCount(buyOrderCount);
    				stmt.close();
    				rs.close();

    				// count orders by type for trade users
    				stmt = getStatement(
    						conn,
    						"select count(orderid) \"sellOrderCount\"from orderejb o where (o.account_accountid in "
    								+ "(select accountid from accountejb a where a.profile_userid like 'uid:%')) AND "
    								+ " (o.orderType='sell')");

    				rs = stmt.executeQuery();
    				rs.next();
    				int sellOrderCount = rs.getInt("sellOrderCount");
    				runStatsData.setSellOrderCount(sellOrderCount);
    				stmt.close();
    				rs.close();

    				// Delete cancelled orders
    				stmt = getStatement(conn,
    						"delete from orderejb where orderStatus='cancelled'");
    				int cancelledOrderCount = stmt.executeUpdate();
    				runStatsData.setCancelledOrderCount(cancelledOrderCount);
    				stmt.close();
    				rs.close();

    				// count open orders by type for trade users
    				stmt = getStatement(
    						conn,
    						"select count(orderid) \"openOrderCount\"from orderejb o where (o.account_accountid in "
    								+ "(select accountid from accountejb a where a.profile_userid like 'uid:%')) AND "
    								+ " (o.orderStatus='open')");

    				rs = stmt.executeQuery();
    				rs.next();
    				int openOrderCount = rs.getInt("openOrderCount");
    				runStatsData.setOpenOrderCount(openOrderCount);

    				stmt.close();
    				rs.close();
    				// Delete orders for holding which have been purchased and sold
    				stmt = getStatement(conn,
    						"delete from orderejb where holding_holdingid is null");
    				int deletedOrderCount = stmt.executeUpdate();
    				runStatsData.setDeletedOrderCount(deletedOrderCount);
    				stmt.close();
    				rs.close();

    				commit(conn);

    				System.out.println("TradeDirect:reset Run stats data\n\n"
    						+ runStatsData);
    			} catch (Exception e) {
    				Log.error(e, "Failed to reset Trade");
    				rollBack(conn, e);
    				throw e;
    			} finally {
    				releaseConn(conn);
    			}
    			return runStatsData;

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



    
	/**
     * @see TradeServices#resetTrade(boolean)
     */
    @PUT
    @Path("/settings/tables/")
    @Produces({"application/json"})
	public void recreateDBTables() throws Exception {
    	
    	String dbProductName = checkDBProductName();
		
		String ddlFile; 
		 if (dbProductName.startsWith("DB2/")) // if db is DB2
         {
             ddlFile = "dbscripts/db2/Table.ddl";
         }
         else if (dbProductName.startsWith("Apache Derby")) //if db is Derby
         {
             ddlFile = "dbscripts/derby/Table.ddl";
         }
         else if (dbProductName.startsWith("Oracle")) // if the Db is Oracle
         {
             ddlFile = "dbscripts/oracle/Table.ddl";
         }
         else if (dbProductName.startsWith("PostgreSQL")) // if the DB is postgreSQL
         {
         	ddlFile = "dbscripts/postgresql/Table.ddl";
         }
         else // Unsupported "Other" Database
         {
             ddlFile = "/dbscripts/other/Table.ddl";
         }	
		 File f = new File(ddlFile);
		 System.out.println(f.getAbsolutePath());
		 FileInputStream fis = new FileInputStream(ddlFile);
		 new TradeBuildDB(new java.io.PrintWriter(System.out), fis);
		 System.out.println("Done creating tables");
    	}
    
    /**
     * @see TradeServices#resetTrade(boolean)
     */
    @PUT
    @Path("/settings/database/")
    @Produces({"application/json"})
	public void createDB() throws Exception {
    	System.out.println("START Calling createDB");
    	new TradeBuildDB(new java.io.PrintWriter(System.out), null);
    	System.out.println("END Calling createDB");
    }
    
	/**
     * @see TradeServices#resetTrade(boolean)
     */
    @GET
    @Path("/settings/dbname")
    @Produces({"application/json"})
	public String checkDBProductName() throws Exception {
    	Connection conn = null;
		String dbProductName = null;

		try {
			if (Log.doTrace())
				Log.traceEnter("TradeDirect:checkDBProductName");

			conn = getConn();
			DatabaseMetaData dbmd = conn.getMetaData();
			dbProductName = dbmd.getDatabaseProductName();
		} catch (SQLException e) {
			Log
					.error(
							e,
							"TradeDirect:checkDBProductName() -- Error checking the Daytrader Database Product Name");
		} finally {
			releaseConn(conn);
		}
		return dbProductName;
	}
    
    /*
     * Lookup the TradeData datasource
     * 
     */
	private void getDataSource() throws Exception {
		datasource = (DataSource) context.lookup(dsName);
	}
    
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
