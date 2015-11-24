package org.rapla.storage.dbsql.tests;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ConcurrentTests
{
    private Connection con1;
    private Connection con2;
    private String insertT1 = "INSERT INTO T1 (ID, NAME, LAST_CHANGED) VALUES (?, ?, ?)";
    private String insertT2 = "INSERT INTO T2 (ID, T1_ID, LAST_CHANGED) VALUES (?, ?, ?)";
    private String selectT1 = "SELECT * FROM T1 WHERE ID = ?";
    private String selectT2 = "SELECT * FROM T2 WHERE ID = ?";
    private String selectT2ByT1 = "SELECT * FROM T2 WHERE T1_ID = ?";
    private String deleteT1 = "DELETE FROM T1 WHERE ID = ? and LAST_CHANGED = ?";
    private String deleteT2 = "DELETE FROM T2 WHERE ID = ? and LAST_CHANGED = ?";

    private static class T1Obj
    {
        private String id;
        private String name;
        private Date lastChanged;
    }

    private static class T2Obj
    {
        private String id;
        private String t1Id;
        private Date lastChanged;
    }

    private List<T1Obj> t1Objs = new ArrayList<ConcurrentTests.T1Obj>();
    private List<T2Obj> t2Objs = new ArrayList<ConcurrentTests.T2Obj>();

    @Before
    public void createDb() throws Exception
    {
        t1Objs.clear();
        t2Objs.clear();
        con1 = DriverManager.getConnection("jdbc:hsqldb:target/db/test", "sa", "");
        con1.setAutoCommit(false);
        con2 = DriverManager.getConnection("jdbc:hsqldb:target/db/test", "sa", "");
        con2.setAutoCommit(false);
        { // delete old
            final Statement stmt = con1.createStatement();
            stmt.addBatch("DROP TABLE IF EXISTS T1 CASCADE;");
            stmt.addBatch("DROP TABLE IF EXISTS T2 CASCADE;");
            stmt.executeBatch();
            con1.commit();
        }
        final Statement stmt = con1.createStatement();
        stmt.addBatch("CREATE TABLE T1 (ID VARCHAR(20) PRIMARY KEY, NAME VARCHAR(20), LAST_CHANGED TIMESTAMP)");
        stmt.addBatch("CREATE TABLE T2 (ID VARCHAR(20) PRIMARY KEY, T1_ID VARCHAR(20), LAST_CHANGED TIMESTAMP)");
        stmt.executeBatch();
        con1.commit();
        final PreparedStatement ps = con1.prepareStatement(insertT1);
        {
            final T1Obj t11 = new T1Obj();
            t11.id = "1";
            t11.name = "Test1";
            t11.lastChanged = new Date(getNow());
            t1Objs.add(t11);
            insert(ps, t11);
        }
        {
            final T1Obj t12 = new T1Obj();
            t12.id = "2";
            t12.name = "Test2";
            t12.lastChanged = new Date(getNow());
            t1Objs.add(t12);
            insert(ps, t12);
        }
        ps.executeBatch();
        final PreparedStatement ps2 = con2.prepareStatement(insertT2);
        {
            final T2Obj t21 = new T2Obj();
            t21.id = "1";
            t21.t1Id = "2";
            t21.lastChanged = new Date(getNow());
            t2Objs.add(t21);
            insert(ps2, t21);
        }
        ps2.executeBatch();
        con1.commit();
        con2.commit();
    }

    @After
    public void cleanUpDb() throws Exception
    {
        con1.close();
        con2.close();
    }

    private void insert(PreparedStatement ps, T2Obj t21) throws Exception
    {
        ps.setString(1, t21.id);
        ps.setString(2, t21.t1Id);
        ps.setDate(3, t21.lastChanged);
        ps.addBatch();
    }

    private void insert(final PreparedStatement ps, T1Obj t1) throws Exception
    {
        ps.setString(1, t1.id);
        ps.setString(2, t1.name);
        ps.setDate(3, t1.lastChanged);
        ps.addBatch();
    }

    public long getNow()
    {
        return new java.util.Date().getTime();
    }

    private List<T2Obj> getAllT2ByT1Id(PreparedStatement t2SelectByT1, String id) throws Exception
    {
        final List<T2Obj> result = new ArrayList<T2Obj>();
        t2SelectByT1.setString(1, id);
        final ResultSet resultSet = t2SelectByT1.getResultSet();
        while (resultSet.next())
        {
            final T2Obj t2Obj = new T2Obj();
            t2Obj.id = resultSet.getString("ID");
            t2Obj.t1Id = resultSet.getString("T1_ID");
            t2Obj.lastChanged = resultSet.getDate("LAST_CHANGED");
            result.add(t2Obj);
        }
        return result;
    }

    private T1Obj getT1ById(PreparedStatement t1SelectById, String id) throws Exception
    {
        final List<T1Obj> result = new ArrayList<T1Obj>();
        t1SelectById.setString(1, id);
        final ResultSet resultSet = t1SelectById.getResultSet();
        while (resultSet.next())
        {
            final T1Obj t2Obj = new T1Obj();
            t2Obj.id = resultSet.getString("ID");
            t2Obj.name = resultSet.getString("NAME");
            t2Obj.lastChanged = resultSet.getDate("LAST_CHANGED");
            result.add(t2Obj);
        }
        if (result.size() != 1)
        {
            throw new IllegalStateException("Should only find one object!!!");
        }
        return result.get(0);
    }

    @Test
    public void concurrentActionUpdateDelete() throws Exception
    {
        final Semaphore semaphore = new Semaphore(0);
        // first update and then delete
        // So the first thread will call writer for delete, the second will insert with the same ID in the second table
        final Thread t1 = new Thread(new Runnable()
        {
            private Connection con = con1;

            public void run()
            {
                try
                {
                    Thread.sleep(200);
                    final PreparedStatement psDelete = con.prepareStatement(deleteT1);
                    final T1Obj t1Obj = t1Objs.get(0);
                    psDelete.setString(1, t1Obj.id);
                    psDelete.setDate(2, t1Objs.get(0).lastChanged);
                    psDelete.addBatch();
                    psDelete.executeBatch();
                    Thread.sleep(500);
                    t1Obj.lastChanged = new Date(getNow());
                    final PreparedStatement t2Select = con.prepareStatement(selectT2ByT1);
                    final List<T2Obj> allOthers = getAllT2ByT1Id(t2Select, t1Obj.id);
                    if (!allOthers.isEmpty())
                    {
                        throw new IllegalStateException("Dependencies here");
                    }
                    con.commit();
                    semaphore.release();
                }
                catch (Exception e)
                {
                    semaphore.release();
                    Assert.fail("Exception should not happen: " + e.getMessage());
                }
            }
        });
        t1.start();
        final Thread t2 = new Thread(new Runnable()
        {
            private Connection con = con2;

            public void run()
            {
                try
                {
                    Thread.sleep(500);
                    final PreparedStatement selectT1Ps = con.prepareStatement(selectT1);
                    final String t1id = t1Objs.get(0).id;
                    final T1Obj t1ById = getT1ById(selectT1Ps, t1id);
                    if (t1ById == null)
                    {
                        throw new IllegalStateException("should have one object");
                    }
                    final PreparedStatement insertT2Ps = con.prepareStatement(insertT2);
                    T2Obj t21 = new T2Obj();
                    t21.id = "new";
                    t21.lastChanged = new Date(getNow());
                    t21.t1Id = t1ById.id;
                    insert(insertT2Ps, t21);
                    insertT2Ps.executeBatch();
                    con.commit();
                    semaphore.release();
                    Assert.fail("Exception should happen: ");
                }
                catch (Exception e)
                {
                    semaphore.release();
                }
            }
        });
        t2.start();
        semaphore.acquire(2);
    }

    @Test
    public void concurrentActionUpdateUpdate() throws Exception
    {
        final Semaphore semaphore = new Semaphore(0);
        // first update and then delete
        // So the first thread will call writer for delete, the second will insert with the same ID in the second table
        final Thread t1 = new Thread(new Runnable()
        {
            private Connection con = con1;

            public void run()
            {
                try
                {
                    Thread.sleep(200);
                    final PreparedStatement psDelete = con.prepareStatement(deleteT1);
                    final T1Obj t1Obj = t1Objs.get(0);
                    psDelete.setString(1, t1Obj.id);
                    psDelete.setDate(2, t1Objs.get(0).lastChanged);
                    psDelete.addBatch();
                    psDelete.executeBatch();
                    Thread.sleep(500);
                    final T1Obj t1ObjNew = new T1Obj();
                    t1ObjNew.lastChanged = new Date(getNow());
                    t1ObjNew.name = "newName";
                    t1ObjNew.id = t1Obj.id;
                    final PreparedStatement t1Insert = con.prepareStatement(insertT1);
                    insert(t1Insert, t1ObjNew);
                    con.commit();
                    semaphore.release();
                }
                catch (Exception e)
                {
                    semaphore.release();
                    Assert.fail("Exception should not happen: " + e.getMessage());
                }
            }
        });
        t1.start();
        final Thread t2 = new Thread(new Runnable()
        {
            private Connection con = con2;

            public void run()
            {
                try
                {
                    Thread.sleep(500);
                    final PreparedStatement psDelete = con.prepareStatement(deleteT1);
                    final T1Obj t1Obj = t1Objs.get(0);
                    psDelete.setString(1, t1Obj.id);
                    psDelete.setDate(2, t1Objs.get(0).lastChanged);
                    psDelete.addBatch();
                    final int[] result = psDelete.executeBatch();
                    if (result[0] != 1)
                    {
                        throw new IllegalStateException("Entry was deleted by someone else");
                    }
                    Thread.sleep(500);
                    final T1Obj t1ObjNew = new T1Obj();
                    t1ObjNew.lastChanged = new Date(getNow());
                    t1ObjNew.name = "newName";
                    t1ObjNew.id = t1Obj.id;
                    final PreparedStatement t1Insert = con.prepareStatement(insertT1);
                    insert(t1Insert, t1ObjNew);
                    con.commit();
                    semaphore.release();
                    Assert.fail("Exception should happen: ");
                }
                catch (Exception e)
                {
                    semaphore.release();
                }
            }
        });
        t2.start();
        semaphore.acquire(2);

    }

}