package org.ecocean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.jdo.PersistenceManager;
import javax.jdo.Transaction;
import javax.transaction.Synchronization;

import org.datanucleus.api.jdo.JDODataStoreCache;
import org.datanucleus.cache.Level2Cache;
import org.datanucleus.enhancement.Persistable;
import org.datanucleus.exceptions.NucleusUserException;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.TestPMFUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.invocation.InvocationOnMock;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The post-commit handoff against REAL DataNucleus and REAL Postgres.
 *
 * The unit tests around IndexingManager drive drain/complete by hand. This test does not: it
 * modifies an existing Encounter through a real Shepherd, flushes, commits (or rolls back, or
 * fails to commit) through the real transaction, and asserts on what a mocked IndexingManager
 * was asked to do and WHEN. On main the mock sees a call at flush time -- before commit -- which
 * is the bug; here it must see exactly one call, after commit, for a row that is committed.
 *
 * Reads of committed content happen after commit returns, through a fresh Shepherd, rather than
 * inside the enqueue callback (which runs inside DataNucleus's commit), to keep the test free of
 * any dependency on connection-pool or lock behavior.
 */
@Testcontainers
class IndexingPostCommitHandoffDbTest {
    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("wildbook_test")
            .withUsername("wildbook")
            .withPassword("wildbook");

    static String encId;

    @BeforeAll
    static void setUp() throws Exception {
        CommonConfiguration.initialize("context0", new Properties());

        Properties props = new Properties();
        props.setProperty("datanucleus.ConnectionUserName", postgres.getUsername());
        props.setProperty("datanucleus.ConnectionPassword", postgres.getPassword());
        props.setProperty("datanucleus.ConnectionDriverName", postgres.getDriverClassName());
        props.setProperty("datanucleus.ConnectionURL", postgres.getJdbcUrl());
        props.setProperty("datanucleus.schema.autoCreateTables", "true");
        // explicit, because the level-2 eviction test depends on it (it is also the default)
        props.setProperty("datanucleus.cache.level2.type", "soft");
        TestPMFUtil.closePMF("context0");

        Shepherd sh = new Shepherd("context0", props);
        try {
            sh.beginDBTransaction();
            Encounter enc = new Encounter();
            // keep seeding out of the indexing path entirely
            enc.setSkipAutoIndexing(true);
            enc.setComments("v1");
            sh.storeNewEncounter(enc);
            encId = enc.getId();
            sh.commitDBTransaction();
        } catch (Exception e) {
            sh.rollbackDBTransaction();
            throw e;
        } finally {
            sh.closeDBTransaction();
        }
    }

    @AfterAll
    static void tearDown() {
        TestPMFUtil.closePMF("context0");
    }

    // ---- helpers ------------------------------------------------------------------------------

    /** Records each identity-overload enqueue together with whether the writer's tx was still active. */
    private static final class Recorder {
        final IndexingManager im = mock(IndexingManager.class);
        final List<String> ids = new ArrayList<String>();
        final List<Boolean> writerActiveAtEnqueue = new ArrayList<Boolean>();

        Recorder(final Shepherd writer) {
            doAnswer((InvocationOnMock inv) -> {
                ids.add(inv.getArgument(0));
                writerActiveAtEnqueue.add(writer.getPM().currentTransaction().isActive());
                return null;
            }).when(im).addIndexingQueueEntry(anyString(), any(), anyBoolean());
        }

        void assertExactlyOneEnqueueAfterCommit(String id) {
            verify(im, times(1)).addIndexingQueueEntry(eq(id), eq(Encounter.class), eq(false));
            verify(im, never()).addIndexingQueueEntry(any(Base.class), anyBoolean());
            assertEquals(List.of(id), ids);
            assertEquals(List.of(false), writerActiveAtEnqueue,
                "the enqueue must happen after the writer's transaction has ended");
        }
    }

    // The row as the DATABASE has it -- plain JDBC, deliberately not a Shepherd, so the assertion
    // cannot be satisfied by anything DataNucleus holds in its level-1 or level-2 cache.
    private static String committedComments() {
        String sql = "SELECT \"OCCURRENCEREMARKS\" FROM \"ENCOUNTER\" WHERE \"CATALOGNUMBER\" = ?";

        try (Connection c = DriverManager.getConnection(postgres.getJdbcUrl(),
            postgres.getUsername(), postgres.getPassword());
            PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, encId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "the seeded encounter row exists");
                return rs.getString(1);
            }
        } catch (SQLException e) {
            throw new AssertionError("could not read the encounter row over JDBC", e);
        }
    }

    private static Encounter loadForWrite(Shepherd sh) {
        Encounter enc = sh.getEncounter(encId);

        enc.setSkipAutoIndexing(false);
        return enc;
    }

    /** Throws NucleusUserException from beforeCompletion exactly once, then behaves normally. */
    private static final class VetoOnce implements Synchronization {
        private final Synchronization inner;
        private boolean armed = true;

        VetoOnce(Synchronization inner) {
            this.inner = inner;
        }

        public void beforeCompletion() {
            if (armed) {
                armed = false;
                throw new NucleusUserException("veto this commit once");
            }
            if (inner != null) inner.beforeCompletion();
        }

        public void afterCompletion(int status) {
            if (inner != null) inner.afterCompletion(status);
        }
    }

    // ---- the contract ---------------------------------------------------------------------------

    @Test void flushDoesNotEnqueue_commitEnqueuesOnce_andTheRowIsCommittedByThen() {
        Shepherd sh = new Shepherd("context0");
        Recorder rec = new Recorder(sh);

        sh.setAction("IndexingPostCommitHandoffDbTest.commit");
        try (MockedStatic<IndexingManagerFactory> f = mockStatic(IndexingManagerFactory.class);
            MockedStatic<OpenSearch> os = mockStatic(OpenSearch.class)) {
            f.when(IndexingManagerFactory::getIndexingManager).thenReturn(rec.im);
            os.when(OpenSearch::skipAutoIndexing).thenReturn(false);

            sh.beginDBTransaction();
            loadForWrite(sh).setComments("v2-commit");
            sh.getPM().flush(); // postStore fires here, on main this is where the enqueue happened
            verifyNoInteractions(rec.im);

            sh.commitDBTransaction();
        } finally {
            sh.closeDBTransaction();
        }
        rec.assertExactlyOneEnqueueAfterCommit(encId);
        assertEquals("v2-commit", committedComments());
    }

    // The callback hangs off the transaction, not off Shepherd, so a raw commit is covered too.
    @Test void rawPersistenceManagerCommit_alsoEnqueuesAfterCommit() {
        Shepherd sh = new Shepherd("context0");
        Recorder rec = new Recorder(sh);

        sh.setAction("IndexingPostCommitHandoffDbTest.rawCommit");
        try (MockedStatic<IndexingManagerFactory> f = mockStatic(IndexingManagerFactory.class);
            MockedStatic<OpenSearch> os = mockStatic(OpenSearch.class)) {
            f.when(IndexingManagerFactory::getIndexingManager).thenReturn(rec.im);
            os.when(OpenSearch::skipAutoIndexing).thenReturn(false);

            sh.beginDBTransaction();
            loadForWrite(sh).setComments("v2-raw");
            sh.getPM().flush();
            verifyNoInteractions(rec.im);

            sh.getPM().currentTransaction().commit(); // NOT Shepherd.commitDBTransaction()
        } finally {
            sh.closeDBTransaction();
        }
        rec.assertExactlyOneEnqueueAfterCommit(encId);
        assertEquals("v2-raw", committedComments());
    }

    @Test void rollback_enqueuesNothing() {
        Shepherd sh = new Shepherd("context0");
        Recorder rec = new Recorder(sh);
        String before = committedComments();

        sh.setAction("IndexingPostCommitHandoffDbTest.rollback");
        try (MockedStatic<IndexingManagerFactory> f = mockStatic(IndexingManagerFactory.class);
            MockedStatic<OpenSearch> os = mockStatic(OpenSearch.class)) {
            f.when(IndexingManagerFactory::getIndexingManager).thenReturn(rec.im);
            os.when(OpenSearch::skipAutoIndexing).thenReturn(false);

            sh.beginDBTransaction();
            loadForWrite(sh).setComments("never-committed");
            sh.getPM().flush();
            sh.rollbackDBTransaction();
        } finally {
            sh.closeDBTransaction();
        }
        verifyNoInteractions(rec.im);
        assertEquals(before, committedComments());
    }

    // A second transaction on the same PM, begun RAW (no Shepherd.beginDBTransaction, so nothing
    // re-installs the park): completion of the first must have left a live park behind.
    @Test void secondTransactionBegunRaw_parksAgain() {
        Shepherd sh = new Shepherd("context0");
        Recorder rec = new Recorder(sh);

        sh.setAction("IndexingPostCommitHandoffDbTest.rawSecond");
        try (MockedStatic<IndexingManagerFactory> f = mockStatic(IndexingManagerFactory.class);
            MockedStatic<OpenSearch> os = mockStatic(OpenSearch.class)) {
            f.when(IndexingManagerFactory::getIndexingManager).thenReturn(rec.im);
            os.when(OpenSearch::skipAutoIndexing).thenReturn(false);

            sh.beginDBTransaction();
            loadForWrite(sh).setComments("v2-first");
            sh.commitDBTransaction();
            verify(rec.im, times(1)).addIndexingQueueEntry(eq(encId), eq(Encounter.class), eq(false));

            sh.getPM().currentTransaction().begin(); // raw
            loadForWrite(sh).setComments("v2-second");
            sh.getPM().flush();
            verify(rec.im, times(1)).addIndexingQueueEntry(anyString(), any(), anyBoolean());
            sh.getPM().currentTransaction().commit(); // raw
            verify(rec.im, times(2)).addIndexingQueueEntry(eq(encId), eq(Encounter.class), eq(false));
        } finally {
            sh.closeDBTransaction();
        }
        assertEquals(List.of(false, false), rec.writerActiveAtEnqueue);
        assertEquals("v2-second", committedComments());
    }

    // DataNucleus leaves the transaction ACTIVE when commit() fails with a NucleusUserException,
    // so the caller can fix the problem and commit again. The park must survive the failed commit
    // and drain on the retry. (#1743 drained on "commit threw", which terminalized a park whose
    // transaction was still open -- and then enqueued every later store in that transaction
    // pre-commit.)
    @Test void failedCommitLeavesTheTransactionActive_parkSurvives_retryEnqueuesOnce() {
        Shepherd sh = new Shepherd("context0");
        Recorder rec = new Recorder(sh);

        sh.setAction("IndexingPostCommitHandoffDbTest.failedCommit");
        try (MockedStatic<IndexingManagerFactory> f = mockStatic(IndexingManagerFactory.class);
            MockedStatic<OpenSearch> os = mockStatic(OpenSearch.class)) {
            f.when(IndexingManagerFactory::getIndexingManager).thenReturn(rec.im);
            os.when(OpenSearch::skipAutoIndexing).thenReturn(false);

            sh.beginDBTransaction();
            loadForWrite(sh).setComments("v2-retry");
            sh.getPM().flush(); // the park now holds the request
            verifyNoInteractions(rec.im);

            Transaction tx = sh.getPM().currentTransaction();
            tx.setSynchronization(new VetoOnce(tx.getSynchronization()));

            assertThrows(Exception.class, tx::commit, "first commit is vetoed");
            assertTrue(tx.isActive(), "DataNucleus keeps the transaction open after a user error");
            verifyNoInteractions(rec.im);

            tx.commit(); // the retry
        } finally {
            sh.closeDBTransaction();
        }
        rec.assertExactlyOneEnqueueAfterCommit(encId);
        assertEquals("v2-retry", committedComments());
    }

    // Closing the PM while that vetoed transaction is still active. DataNucleus core defaults
    // closeActiveTxAction to "exception", but the JDO API adapter overrides it to "rollback", so
    // under JDO the close ROLLS BACK: the transaction completes with STATUS_ROLLEDBACK while the
    // PM's user-object map is still there, the park is terminalized and dropped through the normal
    // callback, and only then is the PM torn down. Nothing was committed, so nothing may reach the
    // queue and the row must be unchanged.
    @Test void failedCommitThenClose_rollsBack_andEnqueuesNothing() {
        Shepherd sh = new Shepherd("context0");
        Recorder rec = new Recorder(sh);
        String before = committedComments();
        IndexingManager.PendingBucket park;

        sh.setAction("IndexingPostCommitHandoffDbTest.failedCommitClose");
        try (MockedStatic<IndexingManagerFactory> f = mockStatic(IndexingManagerFactory.class);
            MockedStatic<OpenSearch> os = mockStatic(OpenSearch.class)) {
            f.when(IndexingManagerFactory::getIndexingManager).thenReturn(rec.im);
            os.when(OpenSearch::skipAutoIndexing).thenReturn(false);

            sh.beginDBTransaction();
            loadForWrite(sh).setComments("never-committed-3");
            sh.getPM().flush();

            PersistenceManager pm = sh.getPM();
            Transaction tx = pm.currentTransaction();
            park = (IndexingManager.PendingBucket)pm.getUserObject(
                IndexingManager.PENDING_USER_OBJECT_KEY);
            assertEquals(1, park.size(), "precondition: the request is parked");
            tx.setSynchronization(new VetoOnce(tx.getSynchronization()));
            assertThrows(Exception.class, tx::commit);
            assertTrue(tx.isActive());

            pm.close(); // JDO default closeActiveTxAction=rollback: completes, does not throw
            assertTrue(pm.isClosed());
            assertFalse(tx.isActive());
        } finally {
            sh.rollbackAndClose(); // no-op on a closed PM
        }
        assertTrue(park.isCompleted(), "the rollback callback terminalized the park");
        verifyNoInteractions(rec.im);
        assertEquals(before, committedComments());
    }

    @Test void failedCommitThenRollback_enqueuesNothing() {
        Shepherd sh = new Shepherd("context0");
        Recorder rec = new Recorder(sh);
        String before = committedComments();

        sh.setAction("IndexingPostCommitHandoffDbTest.failedCommitRollback");
        try (MockedStatic<IndexingManagerFactory> f = mockStatic(IndexingManagerFactory.class);
            MockedStatic<OpenSearch> os = mockStatic(OpenSearch.class)) {
            f.when(IndexingManagerFactory::getIndexingManager).thenReturn(rec.im);
            os.when(OpenSearch::skipAutoIndexing).thenReturn(false);

            sh.beginDBTransaction();
            loadForWrite(sh).setComments("never-committed-2");
            sh.getPM().flush();

            Transaction tx = sh.getPM().currentTransaction();
            tx.setSynchronization(new VetoOnce(tx.getSynchronization()));
            assertThrows(Exception.class, tx::commit);
            assertTrue(tx.isActive());

            sh.rollbackDBTransaction();
        } finally {
            sh.closeDBTransaction();
        }
        verifyNoInteractions(rec.im);
        assertEquals(before, committedComments());
    }

    // The level-2 cache is keyed by DataNucleus's internal identity. Completion must evict THAT
    // key -- evicting with JDOHelper.getObjectId()'s javax.jdo.identity object silently misses.
    @Test void committedCompletion_evictsTheRowFromLevel2ByDataNucleusIdentity() {
        // warm the level-2 cache: read + commit puts the object in
        Shepherd warm = new Shepherd("context0");
        Object dnId;
        Level2Cache l2;

        warm.setAction("IndexingPostCommitHandoffDbTest.warm");
        warm.beginDBTransaction();
        try {
            Encounter e = warm.getEncounter(encId);
            dnId = ((Persistable)e).dnGetObjectId();
            JDODataStoreCache dsc =
                (JDODataStoreCache)warm.getPM().getPersistenceManagerFactory().getDataStoreCache();
            // the fixture's L2 is "soft" like production; pin this id so a GC between here and the
            // assertion cannot turn a fixture problem into a false eviction result
            dsc.pin(dnId);
            l2 = dsc.getLevel2Cache();
            warm.commitDBTransaction();
        } finally {
            warm.closeDBTransaction();
        }
        assertTrue(l2.containsOid(dnId), "precondition: the row is in the level-2 cache");

        Shepherd sh = new Shepherd("context0");
        Recorder rec = new Recorder(sh);

        sh.setAction("IndexingPostCommitHandoffDbTest.evict");
        try (MockedStatic<IndexingManagerFactory> f = mockStatic(IndexingManagerFactory.class);
            MockedStatic<OpenSearch> os = mockStatic(OpenSearch.class)) {
            f.when(IndexingManagerFactory::getIndexingManager).thenReturn(rec.im);
            os.when(OpenSearch::skipAutoIndexing).thenReturn(false);

            sh.beginDBTransaction();
            loadForWrite(sh).setComments("v2-evict");
            sh.commitDBTransaction();
        } finally {
            sh.closeDBTransaction();
        }
        rec.assertExactlyOneEnqueueAfterCommit(encId);
        assertFalse(l2.containsOid(dnId), "completion evicted the committed row from level-2");
    }

    // The indexing job's reader must not trust level-2: DataNucleus publishes an object's new
    // state there during preCommit, BEFORE the datastore commit, so another in-flight transaction's
    // copy can be sitting in the cache when the job runs.
    @Test void readerWork_bypassesLevel2ForReads() {
        IndexingManager.ShepherdIndexingWork work = new IndexingManager.ShepherdIndexingWork("probe");

        try {
            Map<String, Object> props = work.shepherd().getPM().getProperties();
            String value = null;
            for (Map.Entry<String, Object> e : props.entrySet()) {
                if (IndexingManager.ShepherdIndexingWork.L2_RETRIEVE_MODE_PROPERTY.equalsIgnoreCase(
                    e.getKey())) value = String.valueOf(e.getValue());
            }
            assertEquals("bypass", value == null ? null : value.toLowerCase(),
                "reader PersistenceManager must read past the level-2 cache");
        } finally {
            work.close();
        }
    }
}
