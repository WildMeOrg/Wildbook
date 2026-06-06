OpenAI Codex v0.130.0
--------
workdir: /mnt/c/Wildbook-clean2
model: gpt-5.5
provider: openai
approval: never
sandbox: workspace-write [workdir, /tmp, /home/jason/.codex/memories]
reasoning effort: xhigh
reasoning summaries: none
session id: 019e3e5c-3e8c-7d81-82f1-dfc33bb6e5a4
--------
user
# Wildbook v2 ml-service migration — Codex review context bundle

You are reviewing code on the `migrate-ml-service-v2` branch of the
Wildbook repo (`/mnt/c/Wildbook-clean2`). This bundle gives you the
project conventions, repo gotchas, and current architecture that the
code under review assumes.

## Repo facts

- **Stack:** Java 17, Tomcat 9, DataNucleus 5.2.7 (JDO), PostgreSQL 13,
  OpenSearch 2.15 (3.1 on the live amphibian-reptile deployment),
  React 18.
- **Persistence:** JDO with manual transactions via the `Shepherd`
  class. Not Hibernate, not JPA.
- **Indexing:** OpenSearch is **async** from JDO writes. An
  `IndexingManager` background thread picks up dirty entities and
  pushes them to OS; OS additionally has its own refresh interval
  (~1s default).
- **Branch context:** v2 of the ml-service migration. v1 was abandoned
  on `migrate-ml-service`. Current branch (`migrate-ml-service-v2`)
  has the 20 v2 commits plus the Track 1 empty-match-prospects work
  in progress. See
  `docs/plans/2026-05-09-ml-service-migration-v2.md` and
  `docs/plans/2026-05-18-empty-match-prospects-design.md`.

## Shepherd pattern

```java
Shepherd shep = new Shepherd(context);
shep.setAction(ACTION_PREFIX + "methodName");
try {
    shep.beginDBTransaction();
    // ... JDO operations ...
    shep.commitDBTransaction();
} catch (Exception ex) {
    // log
} finally {
    shep.rollbackAndClose();
}
```

`rollbackAndClose` is idempotent — safe after commit and safe after
early return.

**Critical gotcha:** never hold a Shepherd open across a network call.
The v2 polling-thread design (commit `c6ffe5d20`) uses a Phase A / B /
C pattern: load a detached DTO under Shepherd in Phase A, do the HTTP
work without Shepherd in Phase B, persist outcome in a fresh Shepherd
in Phase C.

## JDO naming

`@PrimaryKey` field → PostgreSQL column `ID` (or domain-specific
`CATALOGNUMBER` for `ENCOUNTER`, `INDIVIDUALID` for `MARKEDINDIVIDUAL`).
Join tables use `_OID` (owner) and `_EID` (element) suffixes. The
`EMBEDDING` table uses `ANNOTATION_ID` (no `_OID` suffix — it's a
direct FK, not a JDO-generated join).

## OpenSearch async indexing — visibility gotcha

`OpenSearch.indexRefresh(indexName)` forces a Lucene refresh
boundary; **does not** drain the Wildbook IndexingManager queue. If
you need "after this write the doc must be searchable" semantics,
use `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` (added
in c7, commit `f429c5bf8`).

## IA.json structure (ml-service v2)

```jsonc
{
  "default": {
    "_id_conf": {
      "default": {
        "pipeline_root": "vector",   // "vector" = ml-service v2
        "method": "miewid-msv4.1",   // embedding model id
        "version": "4.1",
        "embedding_dimension": 2152,
        // legacy entries have api_endpoint instead of method/version
      }
    },
    "_mlservice_conf": {
      "default": {
        "base_url": "https://ml-service.example.com:8008",
        "detection_endpoint": "/pipeline/",
        "extraction_endpoint": "/extract/",
        "model_id": "...",
        "match_against_species": [...]
      }
    }
  }
}
```

`Embedding.findMatchProspects` gates entry on
`isVectorConfig = method != null || api_endpoint != null` — both
nullable independently.

## v1 antipatterns to avoid

1. **Don't hold Shepherd across HTTP.** Phase A/B/C pattern instead.
2. **Don't accept null returns ambiguously.** `null` means "we
   couldn't tell" — distinct enums for "no work" vs "failed" vs
   "rejected".
3. **Don't park silently.** Every parked annotation logs why with
   the original error string available for ops.
4. **Don't write large commits.** v1 wrote 800 lines and asked for
   review; v2 keeps commits to ~80 lines avg with design + code
   review per commit.

## CRLF/LF gotcha on this Windows-mounted repo

The Edit tool sometimes flips LF files to CRLF when editing on
`/mnt/c/Wildbook-clean2`. Reviewers should call this out if they
see `git ls-files --eol` reporting `i/lf w/crlf`.

## What we want from this review

Code-review the diff below. Focus on:
- Correctness given the Wildbook conventions above.
- Whether the implementation matches the locked design.
- Test coverage and gaps.
- Anything else.

**Do not write to any file.** Review-only.

---

# Codex code-review: Track 2 C7 — Task.getStoredStatus()

Empty-match-prospects design Track 2 C7 (locked design Track 2
items "Add Task.getStoredStatus" + Codex round-3/4 Major): add
the raw, side-effect-free status accessor needed by the batch
gate's Phase 1.

`Task.getStatus(Shepherd)` at line 597 mutates timed-out tasks
as a side-effect (sets status to "error" + writes a TIMEOUT
status detail) when the timed-out predicate fires. The Track 2
gate reads sibling task statuses in a read-only way; using the
existing getter would corrupt sibling tasks as a side-effect of
the read. New `getStoredStatus()` returns `this.status` directly.

## Diff

diff --git a/src/main/java/org/ecocean/ia/Task.java b/src/main/java/org/ecocean/ia/Task.java
index 969388a89..cc543ce2a 100644
--- a/src/main/java/org/ecocean/ia/Task.java
+++ b/src/main/java/org/ecocean/ia/Task.java
@@ -594,6 +594,20 @@ public class Task implements java.io.Serializable {
         return complete;
     }
 
+    /**
+     * Read the raw persisted status field without the timed-out-task
+     * mutation side-effect that {@link #getStatus(Shepherd)} performs.
+     * Callers gating read-only decisions on status (e.g., the
+     * empty-match-prospects Track 2 batch gate) need this so a read of
+     * a sibling task's status doesn't mutate that task as a side
+     * effect.
+     *
+     * <p>(Empty-match-prospects design Track 2 C7.)</p>
+     */
+    public String getStoredStatus() {
+        return this.status;
+    }
+
     public String getStatus(Shepherd myShepherd) {
         // see if we might be dead in the water
         // TODO skipping status==null cuz i cant figure out what this means and there are so many of them


## New test file:

```java
package org.ecocean.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * Coverage of {@link Task#getStoredStatus()} — the raw, side-effect-free
 * accessor needed by the empty-match-prospects Track 2 batch gate so a
 * read of a sibling task's status doesn't trigger the timed-out-task
 * mutation that {@link Task#getStatus(org.ecocean.shepherd.core.Shepherd)}
 * performs. (Empty-match-prospects design Track 2 C7.)
 */
class TaskGetStoredStatusTest {

    @Test void returnsNull_whenStatusUnset() {
        assertNull(new Task().getStoredStatus());
    }

    @Test void returnsValue_whenStatusSet() {
        Task t = new Task();
        t.setStatus("completed");
        assertEquals("completed", t.getStoredStatus());
    }

    @Test void returnsValue_forNonTerminalStatus() {
        Task t = new Task();
        t.setStatus("processing-mlservice");
        assertEquals("processing-mlservice", t.getStoredStatus());
    }

    @Test void doesNotMutate_evenIfTaskWouldTimeOut() {
        // Build a task with no creation/activity timestamps; getStatus(Shepherd)
        // could in principle flip its status to "error". getStoredStatus
        // must return whatever was persisted without mutating.
        Task t = new Task();
        t.setStatus(null);
        assertNull(t.getStoredStatus());
        // After the read, the underlying field is still null — i.e., the
        // accessor did not lazily initialize anything.
        assertNull(t.getStoredStatus());
    }
}
```


## Validation

- TaskGetStoredStatusTest: 4/4 (new)
- LF-normalized after edit
- No existing callers of `getStatus(Shepherd)` are touched.

## Review scope

- Trivial change, but: is `public` the right visibility for the
  accessor? It mirrors getStatus's visibility.
- Method placement above getStatus(Shepherd) — design preference
  or move it elsewhere?
- Naming: `getStoredStatus` vs `getRawStatus` vs `getPersistedStatus`.
  Codex round-4 review used both; the design doc landed on
  `getStoredStatus`. Stick with that?
- Anything else.

**Do not write to any file.** Review-only.

warning: Codex could not find bubblewrap on PATH. Install bubblewrap with your OS package manager. See the sandbox prerequisites: https://developers.openai.com/codex/concepts/sandboxing#prerequisites. Codex will use the bundled bubblewrap in the meantime.
codex
I’ll keep this review read-only and verify the surrounding `Task` behavior plus the actual test placement/naming before calling findings.
exec
/bin/bash -lc "git status --short && git ls-files --eol src/main/java/org/ecocean/ia/Task.java 'src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "git diff -- src/main/java/org/ecocean/ia/Task.java 'src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/Task.java | sed -n '560,650p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"getStoredStatus|getStatus\\(Shepherd|class TaskGetStoredStatusTest|setStatus\\(\" src/main/java/org/ecocean/ia/Task.java src/test/java/org/ecocean/ia src/test/java -g '*Task*'" in /mnt/c/Wildbook-clean2
 succeeded in 491ms:
   560	        query.setOrdering("created");
   561	        Collection c = (Collection)query.execute();
   562	        List<Task> listy = new ArrayList<Task>(c);
   563	        query.closeAll();
   564	        return listy;
   565	    }
   566	
   567	    public static List<Task> getRootTasksFor(MediaAsset ma, Shepherd myShepherd) {
   568	        return onlyRoots(getTasksFor(ma, myShepherd));
   569	    }
   570	
   571	    // takes a bunch of tasks and returns only roots (without duplication)
   572	    public static List<Task> onlyRoots(List<Task> all) {
   573	        List<Task> roots = new ArrayList<Task>();
   574	
   575	        for (Task t : all) {
   576	            Task r = t.getRootTask();
   577	            if (!roots.contains(r)) roots.add(r);
   578	        }
   579	        return roots;
   580	    }
   581	
   582	    public boolean areSelfAndOrAllChildrenComplete() {
   583	        boolean complete = false;
   584	
   585	        if (!hasChildren() && completionDateInMilliseconds != null) {
   586	            complete = true;
   587	        } else if (hasChildren()) {
   588	            List<Task> children = getChildren();
   589	            complete = true;
   590	            for (Task t : children) {
   591	                if (!t.areSelfAndOrAllChildrenComplete()) complete = false;
   592	            }
   593	        }
   594	        return complete;
   595	    }
   596	
   597	    /**
   598	     * Read the raw persisted status field without the timed-out-task
   599	     * mutation side-effect that {@link #getStatus(Shepherd)} performs.
   600	     * Callers gating read-only decisions on status (e.g., the
   601	     * empty-match-prospects Track 2 batch gate) need this so a read of
   602	     * a sibling task's status doesn't mutate that task as a side
   603	     * effect.
   604	     *
   605	     * <p>(Empty-match-prospects design Track 2 C7.)</p>
   606	     */
   607	    public String getStoredStatus() {
   608	        return this.status;
   609	    }
   610	
   611	    public String getStatus(Shepherd myShepherd) {
   612	        // see if we might be dead in the water
   613	        // TODO skipping status==null cuz i cant figure out what this means and there are so many of them
   614	        if (!statusInEndState() && timedOutDueToInactivity() && !(this.status == null)) {
   615	            this.status = "error";
   616	            long ti = timeInactive();
   617	            setStatusDetailsAddError("TIMEOUT",
   618	                "this task is likely timed out; no activity for " + Util.millisToHumanApprox(ti));
   619	            return this.status;
   620	        }
   621	        // if status is not null, just send it
   622	        if (status != null) return status;
   623	        // otherwise
   624	        // note: this is LOCAL status :(  so it is not changing this.status, only returning the value
   625	        String status = "waiting to queue";
   626	        ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadByTaskID(getId(), "IBEISIA",
   627	            myShepherd);
   628	        if (logs != null && logs.size() > 0) {
   629	            Collections.reverse(logs); // so it has newest first like mostRecent above
   630	            IdentityServiceLog l = logs.get(0);
   631	            JSONObject islObj = l.toJSONObject();
   632	            if (islObj.optString("status") != null &&
   633	                islObj.optString("status").equals("completed")) {
   634	                status = islObj.optString("status");
   635	            } else if (islObj.optJSONObject("status") != null &&
   636	                (islObj.optJSONObject("status").optJSONObject("needReview") != null)) {
   637	                status = "completed";
   638	            } else if (logs.toString().indexOf("score") > -1) {
   639	                status = "completed";
   640	            } else if (islObj.toString().indexOf("HTTP error code") > -1) {
   641	                status = "error";
   642	            } else if (!islObj.optString("queueStatus").equals("")) {
   643	                status = islObj.optString("queueStatus");
   644	            } else if (islObj.opt("status") != null &&
   645	                islObj.opt("status").toString().indexOf("initIdentify") > -1) {
   646	                status = "queuing";
   647	            }
   648	            // if(islObj.optString("queueStatus").equals("queued")){sendIdentify=false;}
   649	            // if(status.equals("waiting to queue"))System.out.println("islObj: "+islObj.toString());
   650	        }

 succeeded in 830ms:
src/main/java/org/ecocean/ia/Task.java:599:     * mutation side-effect that {@link #getStatus(Shepherd)} performs.
src/main/java/org/ecocean/ia/Task.java:607:    public String getStoredStatus() {
src/main/java/org/ecocean/ia/Task.java:611:    public String getStatus(Shepherd myShepherd) {
src/main/java/org/ecocean/ia/Task.java:713:    public void setStatus(String newStatus) {
src/test/java/org/ecocean/ia/TaskStatusInEndStateTest.java:17:        t.setStatus("completed");
src/test/java/org/ecocean/ia/TaskStatusInEndStateTest.java:23:        t.setStatus("error");
src/test/java/org/ecocean/ia/TaskStatusInEndStateTest.java:29:        t.setStatus("dropped-stale");
src/test/java/org/ecocean/ia/TaskStatusInEndStateTest.java:35:        t.setStatus("initiated");
src/test/java/org/ecocean/ia/TaskStatusInEndStateTest.java:42:        t.setStatus("completed-foo");
src/test/java/org/ecocean/ia/TaskStatusInEndStateTest.java:17:        t.setStatus("completed");
src/test/java/org/ecocean/ia/TaskStatusInEndStateTest.java:23:        t.setStatus("error");
src/test/java/org/ecocean/ia/TaskStatusInEndStateTest.java:29:        t.setStatus("dropped-stale");
src/test/java/org/ecocean/ia/TaskStatusInEndStateTest.java:35:        t.setStatus("initiated");
src/test/java/org/ecocean/ia/TaskStatusInEndStateTest.java:42:        t.setStatus("completed-foo");
src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java:9: * Coverage of {@link Task#getStoredStatus()} — the raw, side-effect-free
src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java:15:class TaskGetStoredStatusTest {
src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java:18:        assertNull(new Task().getStoredStatus());
src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java:23:        t.setStatus("completed");
src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java:24:        assertEquals("completed", t.getStoredStatus());
src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java:29:        t.setStatus("processing-mlservice");
src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java:30:        assertEquals("processing-mlservice", t.getStoredStatus());
src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java:34:        // Build a task with no creation/activity timestamps; getStatus(Shepherd)
src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java:35:        // could in principle flip its status to "error". getStoredStatus
src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java:38:        t.setStatus(null);
src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java:39:        assertNull(t.getStoredStatus());
src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java:42:        assertNull(t.getStoredStatus());
src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java:9: * Coverage of {@link Task#getStoredStatus()} — the raw, side-effect-free
src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java:15:class TaskGetStoredStatusTest {
src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java:18:        assertNull(new Task().getStoredStatus());
src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java:23:        t.setStatus("completed");
src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java:24:        assertEquals("completed", t.getStoredStatus());
src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java:29:        t.setStatus("processing-mlservice");
src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java:30:        assertEquals("processing-mlservice", t.getStoredStatus());
src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java:34:        // Build a task with no creation/activity timestamps; getStatus(Shepherd)
src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java:35:        // could in principle flip its status to "error". getStoredStatus
src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java:38:        t.setStatus(null);
src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java:39:        assertNull(t.getStoredStatus());
src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java:42:        assertNull(t.getStoredStatus());
src/test/java/org/ecocean/api/TaskMatchResults.java:90:                verify(mockResponse).setStatus(401);
src/test/java/org/ecocean/api/TaskMatchResults.java:115:                verify(mockResponse).setStatus(400);
src/test/java/org/ecocean/api/TaskMatchResults.java:140:                verify(mockResponse).setStatus(404);
src/test/java/org/ecocean/api/TaskMatchResults.java:168:                verify(mockResponse).setStatus(200);

 succeeded in 1279ms:
diff --git a/src/main/java/org/ecocean/ia/Task.java b/src/main/java/org/ecocean/ia/Task.java
index 969388a89..cc543ce2a 100644
--- a/src/main/java/org/ecocean/ia/Task.java
+++ b/src/main/java/org/ecocean/ia/Task.java
@@ -594,6 +594,20 @@ public class Task implements java.io.Serializable {
         return complete;
     }
 
+    /**
+     * Read the raw persisted status field without the timed-out-task
+     * mutation side-effect that {@link #getStatus(Shepherd)} performs.
+     * Callers gating read-only decisions on status (e.g., the
+     * empty-match-prospects Track 2 batch gate) need this so a read of
+     * a sibling task's status doesn't mutate that task as a side
+     * effect.
+     *
+     * <p>(Empty-match-prospects design Track 2 C7.)</p>
+     */
+    public String getStoredStatus() {
+        return this.status;
+    }
+
     public String getStatus(Shepherd myShepherd) {
         // see if we might be dead in the water
         // TODO skipping status==null cuz i cant figure out what this means and there are so many of them

codex
The added accessor is exactly where the side-effecting getter lives, so I’m checking whether the test actually exercises the timeout edge and whether line endings stayed clean.
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/Task.java | sed -n '650,760p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java | sed -n '1,120p'" in /mnt/c/Wildbook-clean2
 succeeded in 356ms:
     1	package org.ecocean.ia;
     2	
     3	import static org.junit.jupiter.api.Assertions.assertEquals;
     4	import static org.junit.jupiter.api.Assertions.assertNull;
     5	
     6	import org.junit.jupiter.api.Test;
     7	
     8	/**
     9	 * Coverage of {@link Task#getStoredStatus()} — the raw, side-effect-free
    10	 * accessor needed by the empty-match-prospects Track 2 batch gate so a
    11	 * read of a sibling task's status doesn't trigger the timed-out-task
    12	 * mutation that {@link Task#getStatus(org.ecocean.shepherd.core.Shepherd)}
    13	 * performs. (Empty-match-prospects design Track 2 C7.)
    14	 */
    15	class TaskGetStoredStatusTest {
    16	
    17	    @Test void returnsNull_whenStatusUnset() {
    18	        assertNull(new Task().getStoredStatus());
    19	    }
    20	
    21	    @Test void returnsValue_whenStatusSet() {
    22	        Task t = new Task();
    23	        t.setStatus("completed");
    24	        assertEquals("completed", t.getStoredStatus());
    25	    }
    26	
    27	    @Test void returnsValue_forNonTerminalStatus() {
    28	        Task t = new Task();
    29	        t.setStatus("processing-mlservice");
    30	        assertEquals("processing-mlservice", t.getStoredStatus());
    31	    }
    32	
    33	    @Test void doesNotMutate_evenIfTaskWouldTimeOut() {
    34	        // Build a task with no creation/activity timestamps; getStatus(Shepherd)
    35	        // could in principle flip its status to "error". getStoredStatus
    36	        // must return whatever was persisted without mutating.
    37	        Task t = new Task();
    38	        t.setStatus(null);
    39	        assertNull(t.getStoredStatus());
    40	        // After the read, the underlying field is still null — i.e., the
    41	        // accessor did not lazily initialize anything.
    42	        assertNull(t.getStoredStatus());
    43	    }
    44	}

 succeeded in 364ms:
   650	        }
   651	        System.out.println("[DEBUG] getStatus() fell through to status='" + status + "' on Task " +
   652	            this.getId());
   653	        return status;
   654	    }
   655	
   656	    // this is stitched together from import.jsp. godspeed.
   657	    // "resumeStalledTasks" functionality was stripped from this. if needed, revisit original method in import.jsp
   658	    // also the original building/modification of (passed-in) idStatusMap is dropped
   659	    public String getOverallStatus(Shepherd myShepherd) {
   660	        String status = "unknown";
   661	
   662	        if (this.hasChildren()) {
   663	            // accumulate status across children
   664	            HashMap<String, String> map = new HashMap<String, String>();
   665	            // this should only ever be two layers deep
   666	            for (Task childTask : this.getChildren()) {
   667	                if (childTask.hasChildren()) {
   668	                    for (Task childTask2 : childTask.getChildren()) {
   669	                        if ((childTask2.getObjectAnnotations() != null) &&
   670	                            (childTask2.getObjectAnnotations().size() > 0) &&
   671	                            childTask2.getObjectAnnotations().get(0).getMatchAgainst() &&
   672	                            (childTask2.getObjectAnnotations().get(0).getIAClass() != null)) {
   673	                            map.put(childTask2.getId(), childTask2.getStatus(myShepherd));
   674	                        }
   675	                    }
   676	                } else {
   677	                    if ((childTask.getObjectAnnotations() != null) &&
   678	                        (childTask.getObjectAnnotations().size() > 0) &&
   679	                        childTask.getObjectAnnotations().get(0).getMatchAgainst() &&
   680	                        (childTask.getObjectAnnotations().get(0).getIAClass() != null)) {
   681	                        map.put(childTask.getId(), childTask.getStatus(myShepherd));
   682	                    }
   683	                }
   684	            }
   685	            // now, how do we report these?
   686	            HashMap<String, Integer> resultsMap = new HashMap<String, Integer>();
   687	            for (String key : map.values()) {
   688	                // task results
   689	                if (!resultsMap.containsKey(key)) {
   690	                    resultsMap.put(key, new Integer(1));
   691	                } else {
   692	                    resultsMap.put(key, new Integer(resultsMap.get(key) + 1));
   693	                }
   694	            }
   695	            status = resultsMap.toString();
   696	        } else { // childless
   697	            status = this.getStatus(myShepherd);
   698	        }
   699	        return status;
   700	    }
   701	
   702	    public boolean isFastlane(Shepherd myShepherd) {
   703	        String status = "waiting to queue";
   704	        ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadByTaskID(getId(), "IBEISIA",
   705	            myShepherd);
   706	
   707	        if (getParameters() != null && getParameters().optBoolean("fastlane", false)) {
   708	            return true;
   709	        }
   710	        return false;
   711	    }
   712	
   713	    public void setStatus(String newStatus) {
   714	        setModified();
   715	        if (newStatus == null) status = null;
   716	        else { status = newStatus; }
   717	    }
   718	
   719	    public Long getCompletionDateInMilliseconds() { return completionDateInMilliseconds; }
   720	
   721	    // this will set all date stuff based on ms since epoch
   722	    public void setCompletionDateInMilliseconds(Long ms) {
   723	        this.completionDateInMilliseconds = ms;
   724	    }
   725	
   726	    // no arg = set to now
   727	    public void setCompletionDateInMilliseconds() {
   728	        this.completionDateInMilliseconds = Long.valueOf(System.currentTimeMillis());
   729	    }
   730	
   731	    // capture original queue message to make this Task more easily resumeable
   732	    public String getQueueResumeMessage() { return queueResumeMessage; }
   733	    public void setQueueResumeMessage(String message) {
   734	        if (message == null) { queueResumeMessage = null; } else {
   735	            queueResumeMessage = message;
   736	        }
   737	    }
   738	
   739	    public JSONObject getMatchingSetFilter() {
   740	        if (getParameters() == null) return null;
   741	        return getParameters().optJSONObject("matchingSetFilter");
   742	    }
   743	
   744	    public JSONObject getIdentificationMethodInfo() {
   745	        if (getParameters() == null) return null;
   746	        if (getParameters().optJSONObject("ibeis.identification") == null) return null;
   747	        JSONObject rtn = new JSONObject();
   748	        // vector/embed flavor
   749	        if (getParameters().getJSONObject("ibeis.identification").optString("api_endpoint",
   750	            null) != null) {
   751	            String modelId = getParameters().getJSONObject("ibeis.identification").optString(
   752	                "model_id", null);
   753	            if (modelId == null) {
   754	                rtn.put("description", "Vector embedding match");
   755	            } else {
   756	                rtn.put("description", "Vector embedding match (model: " + modelId + ")");
   757	                rtn.put("modelId", modelId);
   758	            }
   759	            return rtn;
   760	        }

 succeeded in 17866ms:
 M .github/workflows/claude-code-review.yml
 M .github/workflows/claude.yml
 M .gitignore
 M archive/copyBuild.sh
 M archive/imageTransformWrapper.sh
 M archive/sql/ml_service_embedding_audit.sql
 M archive/sql/ml_service_idempotency.sql
 M config/git-json-info.ps1
 M config/git-json-info.sh
 M devops/deploy/.dockerfiles/alloy/config.alloy
 M devops/deploy/.dockerfiles/alloy/config.tempo.alloy
 M devops/deploy/.dockerfiles/db/postgresql.conf
 M devops/deploy/.dockerfiles/nginx/nginx-https.conf
 M devops/deploy/.dockerfiles/nginx/nginx.conf
 M devops/deploy/.dockerfiles/opensearch/log4j2.properties
 M devops/deploy/.dockerfiles/tomcat/server.xml
 M devops/deploy/_env.template
 M devops/deploy/docker-compose.yml
 M devops/development/.dockerfiles/tomcat/server.xml
 M devops/development/docker-compose.yml
 M docs/plans/2026-05-09-ml-service-migration-v2.md
 M docs/plans/2026-05-18-wbia-image-registration-design.md
 M frontend/maven-build.sh
 M frontend/package-lock.json
 M frontend/package.json
 M frontend/src/App.jsx
 M frontend/src/AuthenticatedSwitch.jsx
 M frontend/src/FrontDesk.jsx
 M frontend/src/SiteSettingsContext.jsx
 M frontend/src/UnAuthenticatedSwitch.jsx
 M frontend/src/__tests__/FrontDesk.test.js
 M frontend/src/__tests__/components/AddAdditionalModal.test.js
 M frontend/src/__tests__/components/AuthenticatedSwitch.test.js
 M frontend/src/__tests__/components/Map.test.js
 M frontend/src/__tests__/components/SearchAndSelectInput.test.js
 M frontend/src/__tests__/pages/BulkImport/BulkImportEditableDataTable.test.js
 M frontend/src/__tests__/pages/BulkImport/BulkImportImageUpload.test.js
 M frontend/src/__tests__/pages/BulkImport/BulkImportInstuctionsModal.test.js
 M frontend/src/__tests__/pages/BulkImport/BulkImportStore.test.js
 M frontend/src/__tests__/pages/BulkImport/BulkImportTask.test.js
 M frontend/src/__tests__/pages/Encounter/ContactInfoCard.test.js
 M frontend/src/__tests__/pages/Encounter/ContactInfoModal.test.js
 M frontend/src/__tests__/pages/Encounter/DateSectionEdit.test.js
 M frontend/src/__tests__/pages/Encounter/DateSectionReview.test.js
 M frontend/src/__tests__/pages/Encounter/EditAnnotation.test.js
 M frontend/src/__tests__/pages/Encounter/Encounter.test.js
 M frontend/src/__tests__/pages/Encounter/EncounterPageViewOnly.test.js
 M frontend/src/__tests__/pages/Encounter/EncounterStore.test.js
 M frontend/src/__tests__/pages/Encounter/HelperFunctions.test.js
 M frontend/src/__tests__/pages/Encounter/IdentifySectionEdit.test.js
 M frontend/src/__tests__/pages/Encounter/IdentifySectionReview.test.js
 M frontend/src/__tests__/pages/Encounter/ImageCard.test.js
 M frontend/src/__tests__/pages/Encounter/ImageModal.test.js
 M frontend/src/__tests__/pages/Encounter/ImageModalStore.test.js
 M frontend/src/__tests__/pages/Encounter/LocationSectionEdit.test.js
 M frontend/src/__tests__/pages/Encounter/MapDisplay.test.js
 M frontend/src/__tests__/pages/Encounter/MatchCriteria.test.js
 M frontend/src/__tests__/pages/Encounter/MeasurementsEdit.test.js
 M frontend/src/__tests__/pages/Encounter/MeasurementsReview.test.js
 M frontend/src/__tests__/pages/Encounter/MoreDetails.test.js
 M frontend/src/__tests__/pages/Encounter/NewMatchStore.test.js
 M frontend/src/__tests__/pages/Encounter/ProjectsCard.test.js
 M frontend/src/__tests__/pages/EncounterSearchPageAndFilters/BiologicalSamplesAndAnalysesFilter.test.js
 M frontend/src/__tests__/pages/EncounterSearchPageAndFilters/CalenderView.test.js
 M frontend/src/__tests__/pages/EncounterSearchPageAndFilters/DateFilter.test.js
 M frontend/src/__tests__/pages/EncounterSearchPageAndFilters/EncounterFormStore.test.js
 M frontend/src/__tests__/pages/EncounterSearchPageAndFilters/EncounterSearch.test.js
 M frontend/src/__tests__/pages/EncounterSearchPageAndFilters/IndividualDateFilter.test.js
 M frontend/src/__tests__/pages/LandingPage/LandingPage.test.js
 M frontend/src/__tests__/pages/LandingPage/PickUpWhereYouLeft.test.js
 M frontend/src/__tests__/pages/ManualAnnotationPage/ManualAnnotation.test.js
 M frontend/src/__tests__/pages/MatchResults/CreateNewIndividualModal.test.jsx
 M frontend/src/__tests__/pages/MatchResults/InstructionsModal.test.jsx
 M frontend/src/__tests__/pages/MatchResults/MatchConfirmedModal.test.jsx
 M frontend/src/__tests__/pages/MatchResults/MatchCriteriaDrawer.test.jsx
 M frontend/src/__tests__/pages/MatchResults/MatchResults.test.jsx
 M frontend/src/__tests__/pages/MatchResults/MatchResultsBottomBar.test.jsx
 M frontend/src/__tests__/pages/MatchResults/NewIndividualCreatedModal.test.jsx
 M frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js
 M frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js
 M frontend/src/__tests__/pages/PoliciesAndData.test.js
 M frontend/src/__tests__/pages/ReportAnEncounterPage/ImageSection.test.js
 M frontend/src/__tests__/pages/ReportAnEncounterPage/PlaceSection.test.js
 M frontend/src/__tests__/pages/ReportAnEncounterPage/ReportAnEncounter.test.js
 M frontend/src/__tests__/pages/ReportAnEncounterPage/ReportEncounterStore.test.js
 M frontend/src/__tests__/pages/ReportAnEncounterPage/SpeciesSection.test.js
 M frontend/src/__tests__/pages/login/LoginPageAuthenticate.test.js
 M frontend/src/__tests__/pages/login/LoginPageButtonState.test.js
 M frontend/src/__tests__/pages/login/LoginPageError.test.js
 M frontend/src/__tests__/pages/login/LoginPageInput.test.js
 M frontend/src/__tests__/pages/login/LoginPageLinks.test.js
 M frontend/src/__tests__/pages/login/LoginPagePasswordToggle.test.js
 M frontend/src/__tests__/pages/login/LoginPageRender.test.js
 M frontend/src/__tests__/pages/login/LoginPageSubmit.test.js
 M frontend/src/components/AnnotationOverlay.jsx
 M frontend/src/components/AuthenticatedAppHeader.jsx
 M frontend/src/components/Chip.jsx
 M frontend/src/components/ContainerWithSpinner.jsx
 M frontend/src/components/DataTable.jsx
 M frontend/src/components/FilterPanel.jsx
 M frontend/src/components/Footer.jsx
 M frontend/src/components/Form/FormGroupMultiSelect.jsx
 M frontend/src/components/ImageModal.jsx
 M frontend/src/components/LoadingScreen.jsx
 M frontend/src/components/Map.jsx
 M frontend/src/components/MultiSelectWithCheckbox.jsx
 M frontend/src/components/SimpleDataTable.jsx
 M frontend/src/components/SmallSpinner.jsx
 M frontend/src/components/UnAuthenticatedAppHeader.jsx
 M frontend/src/components/filterFields/BiologicalSamplesAndAnalysesFilter.jsx
 M frontend/src/components/filterFields/DateFilter.jsx
 M frontend/src/components/filterFields/ImageLabelFilter.jsx
 M frontend/src/components/filterFields/IndividualsObservationAttributeFilter.jsx
 M frontend/src/components/filterFields/LocationFilterMap.jsx
 M frontend/src/components/filterFields/LocationFilterText.jsx
 M frontend/src/components/filterFields/MetadataFilter.jsx
 M frontend/src/components/filterFields/ObservationAttributeFilter.jsx
 M frontend/src/components/filterFields/SocialFilter.jsx
 M frontend/src/components/generalInputs/CoordinatesInput.jsx
 M frontend/src/components/header/HeaderDropdownItems.jsx
 M frontend/src/components/header/Menu.jsx
 M frontend/src/components/home/PickUpWhereYouLeft.jsx
 M frontend/src/components/icons/EditIcon.jsx
 M frontend/src/components/icons/EncounterIcon.jsx
 M frontend/src/components/icons/ExitIcon.jsx
 M frontend/src/components/icons/FullscreenIcon.jsx
 M frontend/src/components/icons/SpotMappingIcon.jsx
 M frontend/src/components/icons/SpotMappingIcon2.jsx
 M frontend/src/constants/navMenu.js
 M frontend/src/hooks/useDocumentTitle.js
 M frontend/src/locale/de.json
 M frontend/src/locale/en.json
 M frontend/src/locale/es.json
 M frontend/src/locale/fr.json
 M frontend/src/locale/it.json
 M frontend/src/models/encounters/useFilterEncountersWithMediaAssets.js
 M frontend/src/pages/AboutUs.jsx
 M frontend/src/pages/BulkImport/BulkImportErrorSummaryBar.jsx
 M frontend/src/pages/BulkImport/BulkImportImageUpload.jsx
 M frontend/src/pages/BulkImport/BulkImportInstructionsModal.jsx
 M frontend/src/pages/BulkImport/BulkImportStore.js
 M frontend/src/pages/BulkImport/BulkImportTask.jsx
 M frontend/src/pages/BulkImport/EditableDataTable.jsx
 M frontend/src/pages/Citation.jsx
 M frontend/src/pages/EditAnnotation.jsx
 M frontend/src/pages/Encounter/AddPeople.jsx
 M frontend/src/pages/Encounter/AttributesSectionEdit.jsx
 M frontend/src/pages/Encounter/ContactInfoCard.jsx
 M frontend/src/pages/Encounter/ContactInfoModal.jsx
 M frontend/src/pages/Encounter/Encounter.jsx
 M frontend/src/pages/Encounter/IdentifySectionEdit.jsx
 M frontend/src/pages/Encounter/IdentifySectionReview.jsx
 M frontend/src/pages/Encounter/ImageCard.jsx
 M frontend/src/pages/Encounter/LocationSectionEdit.jsx
 M frontend/src/pages/Encounter/MapDisplay.jsx
 M frontend/src/pages/Encounter/MatchCriteria.jsx
 M frontend/src/pages/Encounter/MetadataSectionEdit.jsx
 M frontend/src/pages/Encounter/MoreDetails.jsx
 M frontend/src/pages/Encounter/ProjectsCard.jsx
 M frontend/src/pages/Encounter/SpotMappingCard.jsx
 M frontend/src/pages/Encounter/constants.js
 M frontend/src/pages/Encounter/pollingHelpers.js
 M frontend/src/pages/Encounter/stores/EncounterStore.js
 M frontend/src/pages/Encounter/stores/NewMatchStore.js
 M frontend/src/pages/Encounter/stores/helperFunctions.js
 M frontend/src/pages/HowToPhotograph.jsx
 M frontend/src/pages/Login.jsx
 M frontend/src/pages/ManualAnnotation.jsx
 M frontend/src/pages/MatchResultsPage/MatchResults.jsx
 M frontend/src/pages/MatchResultsPage/components/CreateNewIndividualModal.jsx
 M frontend/src/pages/MatchResultsPage/components/EmptyMatchPlaceholder.jsx
 M frontend/src/pages/MatchResultsPage/components/InspectorModal.jsx
 M frontend/src/pages/MatchResultsPage/components/InstructionsModal.jsx
 M frontend/src/pages/MatchResultsPage/components/MatchConfirmedModal.jsx
 M frontend/src/pages/MatchResultsPage/components/MatchCriteriaDrawer.jsx
 M frontend/src/pages/MatchResultsPage/components/MatchProspectTable.jsx
 M frontend/src/pages/MatchResultsPage/components/MatchResultsBottomBar.jsx
 M frontend/src/pages/MatchResultsPage/components/NewIndividualCreatedModal.jsx
 M frontend/src/pages/MatchResultsPage/constants.js
 M frontend/src/pages/MatchResultsPage/helperFunctions.js
 M frontend/src/pages/MatchResultsPage/icons/ExitFullScreenIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/FilterIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/FullScreenIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/HatchMarkIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/InfoIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/ToggleAnnotationIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/ZoomInIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/ZoomOutIcon.jsx
 M frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js
 M frontend/src/pages/PoliciesAndData/PoliciesAndData.jsx
 M frontend/src/pages/ReportsAndManagamentPages/ImageSection.jsx
 M frontend/src/pages/ReportsAndManagamentPages/PlaceSection.jsx
 M frontend/src/pages/ReportsAndManagamentPages/ReportEncounter.jsx
 M frontend/src/pages/ReportsAndManagamentPages/ReportEncounterStore.js
 M frontend/src/pages/ReportsAndManagamentPages/SpeciesSection.jsx
 M frontend/src/pages/SearchPages/EncounterSearch.jsx
 M frontend/src/pages/SearchPages/components/ExportModal.jsx
 M frontend/src/pages/SearchPages/searchResultTabs/GalleryView.jsx
 M frontend/src/pages/SearchPages/stores/EncounterFormStore.js
 M frontend/src/pages/SearchPages/stores/ImageModalStore.js
 M frontend/src/service-worker.js
 M pom.xml
 M src/main/java/org/ecocean/Annotation.java
 M src/main/java/org/ecocean/AnnotationLite.java
 M src/main/java/org/ecocean/Base.java
 M src/main/java/org/ecocean/CommonConfiguration.java
 M src/main/java/org/ecocean/EmailTemplate.java
 M src/main/java/org/ecocean/Embedding.java
 M src/main/java/org/ecocean/Encounter.java
 M src/main/java/org/ecocean/IAJsonProperties.java
 M src/main/java/org/ecocean/ImageProcessor.java
 M src/main/java/org/ecocean/LocationID.java
 M src/main/java/org/ecocean/MarkedIndividual.java
 M src/main/java/org/ecocean/NotificationMailer.java
 M src/main/java/org/ecocean/Occurrence.java
 M src/main/java/org/ecocean/OpenSearch.java
 M src/main/java/org/ecocean/RestClient.java
 M src/main/java/org/ecocean/SpotterConserveIO.java
 M src/main/java/org/ecocean/Survey.java
 M src/main/java/org/ecocean/User.java
 M src/main/java/org/ecocean/Util.java
 M src/main/java/org/ecocean/acm/AcmUtil.java
 M src/main/java/org/ecocean/api/GenericObject.java
 M src/main/java/org/ecocean/api/Login.java
 M src/main/java/org/ecocean/api/Logout.java
 M src/main/java/org/ecocean/api/MarkedIndividualInfo.java
 M src/main/java/org/ecocean/api/SiteSettings.java
 M src/main/java/org/ecocean/api/bulk/BulkImportUtil.java
 M src/main/java/org/ecocean/api/patch/EncounterPatchValidator.java
 M src/main/java/org/ecocean/export/EncounterCOCOExportFile.java
 M src/main/java/org/ecocean/grid/AppletHeartbeatThread.java
 M src/main/java/org/ecocean/grid/EncounterLite.java
 M src/main/java/org/ecocean/grid/GridManager.java
 M src/main/java/org/ecocean/grid/MatchGraphCreationThread.java
 M src/main/java/org/ecocean/grid/MatchedPoints.java
 M src/main/java/org/ecocean/grid/SpotTriangle.java
 M src/main/java/org/ecocean/grid/WorkAppletHeadlessEpic.java
 M src/main/java/org/ecocean/ia/IA.java
 M src/main/java/org/ecocean/ia/IAException.java
 M src/main/java/org/ecocean/ia/MLService.java
 M src/main/java/org/ecocean/ia/MatchResult.java
 M src/main/java/org/ecocean/ia/MatchResultProspect.java
 M src/main/java/org/ecocean/ia/MlServiceClient.java
 M src/main/java/org/ecocean/ia/MlServiceJobOutcome.java
 M src/main/java/org/ecocean/ia/MlServiceProcessor.java
 M src/main/java/org/ecocean/ia/Task.java
 M src/main/java/org/ecocean/identity/IBEISIA.java
 M src/main/java/org/ecocean/identity/IdentityServiceLog.java
 M src/main/java/org/ecocean/media/AssetStore.java
 M src/main/java/org/ecocean/media/AssetStoreConfig.java
 M src/main/java/org/ecocean/media/AssetStoreFactory.java
 M src/main/java/org/ecocean/media/Feature.java
 M src/main/java/org/ecocean/media/LocalAssetStore.java
 M src/main/java/org/ecocean/media/MediaAsset.java
 M src/main/java/org/ecocean/mmutil/FileUtilities.java
 M src/main/java/org/ecocean/mmutil/MediaUtilities.java
 M src/main/java/org/ecocean/movement/Path.java
 M src/main/java/org/ecocean/opendata/OBISSeamap.java
 M src/main/java/org/ecocean/opendata/Share.java
 M src/main/java/org/ecocean/resumableupload/UploadServlet.java
 M src/main/java/org/ecocean/security/ShepherdRealm.java
 M src/main/java/org/ecocean/servlet/AnnotationEdit.java
 M src/main/java/org/ecocean/servlet/EncounterDelete.java
 M src/main/java/org/ecocean/servlet/EncounterForm.java
 M src/main/java/org/ecocean/servlet/EncounterRemoveAnnotation.java
 M src/main/java/org/ecocean/servlet/EncounterRemoveSpots.java
 M src/main/java/org/ecocean/servlet/GrothMatchServlet.java
 M src/main/java/org/ecocean/servlet/IAGateway.java
 M src/main/java/org/ecocean/servlet/IAGlobals.java
 M src/main/java/org/ecocean/servlet/MediaAssetModify.java
 M src/main/java/org/ecocean/servlet/SiteSearch.java
 M src/main/java/org/ecocean/servlet/SubmitSpotsAndImage.java
 M src/main/java/org/ecocean/servlet/UserCreate.java
 M src/main/java/org/ecocean/servlet/UserResetPassword.java
 M src/main/java/org/ecocean/servlet/WorkspaceDelete.java
 M src/main/java/org/ecocean/servlet/export/EncounterSearchExportCOCO.java
 M src/main/java/org/ecocean/servlet/export/IndividualSearchExportCapture.java
 M src/main/java/org/ecocean/servlet/export/MarkRecaptureEncounters.java
 M src/main/java/org/ecocean/servlet/export/SimpleCMROutput.java
 M src/main/java/org/ecocean/servlet/importer/DeleteImportTask.java
 M src/main/java/org/ecocean/servlet/importer/ImportTask.java
 M src/main/java/org/ecocean/servlet/importer/StandardImport.java
 M src/main/java/org/ecocean/shepherd/core/Shepherd.java
 M src/main/java/org/ecocean/shepherd/core/ShepherdProperties.java
 M src/main/resources/bundles/IA.json
 M src/main/resources/bundles/IA.properties
 M src/main/resources/bundles/commonConfiguration.properties
 M src/main/resources/bundles/de/commonConfigurationLabels.properties
 M src/main/resources/bundles/de/header.properties
 M src/main/resources/bundles/en/commonConfigurationLabels.properties
 M src/main/resources/bundles/en/header.properties
 M src/main/resources/bundles/en/index.properties
 M src/main/resources/bundles/en/occurrence.properties
 M src/main/resources/bundles/en/survey.properties
 M src/main/resources/bundles/es/commonConfigurationLabels.properties
 M src/main/resources/bundles/es/header.properties
 M src/main/resources/bundles/es/index.properties
 M src/main/resources/bundles/fr/commonConfigurationLabels.properties
 M src/main/resources/bundles/fr/encounter.properties
 M src/main/resources/bundles/fr/encounterSearch.properties
 M src/main/resources/bundles/fr/header.properties
 M src/main/resources/bundles/fr/index.properties
 M src/main/resources/bundles/haplotypeColorCodes.properties
 M src/main/resources/bundles/it/commonConfigurationLabels.properties
 M src/main/resources/bundles/it/header.properties
 M src/main/resources/bundles/it/index.properties
 M src/main/resources/bundles/locationID.json
 M src/main/resources/bundles/locationIDGPS.properties
 M src/main/resources/bundles/locationID_indocet.json
 M src/main/resources/bundles/newIndividualNumbers.properties
 M src/main/resources/bundles/opendata.properties
 M src/main/resources/log4j2.xml
 M src/main/resources/org/ecocean/ia/package.jdo
 M src/main/resources/org/ecocean/media/package.jdo
 M src/main/resources/org/ecocean/package.jdo
 M src/main/resources/servletResponseTemplate.htm
 M src/main/webapp/WEB-INF/web.xml
 M src/main/webapp/appadmin/catchUpEmbeddings.jsp
 M src/main/webapp/appadmin/importEmbeddings.jsp
 M src/main/webapp/appadmin/testEmbeddingSearch.jsp
 M src/main/webapp/contactus.jsp
 M src/main/webapp/css/imageEnhancer.css
 M src/main/webapp/cust/mantamatcher/styles/_wildbook.less
 M src/main/webapp/datacleaning/individuals.jsp
 M src/main/webapp/encounters/biologicalSamples.jsp
 M src/main/webapp/encounters/encounterCropTool.jsp
 M src/main/webapp/encounters/encounterSpotTool.jsp
 M src/main/webapp/encounters/manualAnnotation.jsp
 M src/main/webapp/encounters/scanEndApplet.jsp
 M src/main/webapp/encounters/spotMatchingAlgorithm.jsp
 M src/main/webapp/header.jsp
 M src/main/webapp/iaResults.jsp
 M src/main/webapp/import.jsp
 M src/main/webapp/import/photos.jsp
 M src/main/webapp/import/reviewDirectory.jsp
 M src/main/webapp/import/spreadsheet.jsp
 M src/main/webapp/index.jsp
 M src/main/webapp/javascript/ia.IBEIS.js
 M src/main/webapp/javascript/relationshipDiagrams/graphAbstract.js
 M src/main/webapp/listImages.jsp
 M src/main/webapp/logout.jsp
 M src/main/webapp/obrowse.jsp
 M src/main/webapp/occurrence.jsp
 M src/main/webapp/pictureBook.jsp
 M src/main/webapp/projects/project.jsp
 M src/main/webapp/submit.jsp
 M src/main/webapp/surveys/survey.jsp
 M src/main/webapp/surveys/surveyMapEmbed.jsp
 M src/main/webapp/welcome.jsp
 M src/test/java/org/ecocean/AnnotationTest.java
 M src/test/java/org/ecocean/EmbeddingTest.java
 M src/test/java/org/ecocean/IAJsonPropertiesTest.java
 M src/test/java/org/ecocean/MatchResultTest.java
 M src/test/java/org/ecocean/OpenSearchVisibilityTest.java
 M src/test/java/org/ecocean/UtilTest.java
 M src/test/java/org/ecocean/api/EncounterApiTest.java
 M src/test/java/org/ecocean/api/EncounterExportImagesTest.java
 M src/test/java/org/ecocean/api/SettingApiTest.java
 M src/test/java/org/ecocean/api/TaskMatchResults.java
 M src/test/java/org/ecocean/export/EncounterCOCOExportFileTest.java
 M src/test/java/org/ecocean/grid/GrothOptimizationTest.java
 M src/test/java/org/ecocean/grid/GrothParameterSweepTest.java
 M src/test/java/org/ecocean/grid/MatchGraphCacheTest.java
 M src/test/java/org/ecocean/ia/IdentificationTest.java
 M src/test/java/org/ecocean/ia/MlServiceClientTest.java
 M src/test/java/org/ecocean/ia/MlServiceJobOutcomeTest.java
 M src/test/java/org/ecocean/ia/MlServiceProcessorTest.java
 M src/test/java/org/ecocean/ia/TaskStatusInEndStateTest.java
 M src/test/java/org/ecocean/media/MediaAssetDetectionStatusTest.java
 M src/test/resources/log4j2-test.xml
?? IA.json
?? logs/
?? src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java
i/lf    w/lf    attr/                 	src/main/java/org/ecocean/ia/Task.java

exec
/bin/bash -lc 'rg -n "created|last|activity|timedOutDueToInactivity|timeInactive|statusInEndState" src/main/java/org/ecocean/ia/Task.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'git ls-files --eol --others --exclude-standard src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'git diff -- src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/Task.java | sed -n '1,140p;500,560p;900,960p'" in /mnt/c/Wildbook-clean2
 succeeded in 469ms:
 succeeded in 518ms:
31:    private long created = -1;
50:        created = System.currentTimeMillis();
65:        return created;
72:    public long timeInactive() {
76:        if (created > 0) return (now - created);
81:    public boolean timedOutDueToInactivity() {
82:        return (timeInactive() > TIMEOUT_INACTIVE_MILLIS);
85:    public boolean statusInEndState() {
90:        // error; the inactivity-timeout watchdog must not flip it to error.
462:        j.put("created", created);
464:        j.put("createdDate", new DateTime(created));
507:                   .append("(" + new DateTime(created) + "|" + new DateTime(modified) + ")")
539:            query.setOrdering("created");
560:        query.setOrdering("created");
614:        if (!statusInEndState() && timedOutDueToInactivity() && !(this.status == null)) {
616:            long ti = timeInactive();
618:                "this task is likely timed out; no activity for " + Util.millisToHumanApprox(ti));
827:        rtn.put("timeInactiveMillis", timeInactive());

 succeeded in 493ms:
     1	/*
     2	    an instance of an ia.Task can be persisted and represents the state of that task
     3	    ... replacement (and improvement upon, hopefully) messy identity/IdentityServiceLog.java
     4	 */
     5	package org.ecocean.ia;
     6	
     7	import java.util.ArrayList;
     8	import java.util.Collection;
     9	import java.util.Collections;
    10	import java.util.HashMap;
    11	import java.util.List;
    12	import java.util.Map;
    13	import java.util.Set;
    14	import javax.jdo.Query;
    15	import org.apache.commons.lang3.builder.ToStringBuilder;
    16	import org.ecocean.Annotation;
    17	import org.ecocean.Encounter;
    18	import org.ecocean.media.MediaAsset;
    19	import org.ecocean.shepherd.core.Shepherd;
    20	import org.ecocean.User;
    21	import org.ecocean.Util;
    22	import org.joda.time.DateTime;
    23	import org.json.JSONArray;
    24	import org.json.JSONObject;
    25	
    26	import org.ecocean.identity.IdentityServiceLog;
    27	
    28	public class Task implements java.io.Serializable {
    29	    public static long TIMEOUT_INACTIVE_MILLIS = 7l * 24l * 60l * 60l * 1000l;
    30	    private String id = null;
    31	    private long created = -1;
    32	    private long modified = -1;
    33	    // private List<Object> objects = null;  //in some perfect world i could figure out how to persist this.  :/  oh, for a wb base class.
    34	    private List<MediaAsset> objectMediaAssets = null;
    35	    private List<Annotation> objectAnnotations = null;
    36	    private Task parent = null;
    37	    private List<Task> children = null;
    38	    private String parameters = null;
    39	    private String status;
    40	    // general use, but notably will contain error details when status=error
    41	    private String statusDetails = null;
    42	    private Long completionDateInMilliseconds;
    43	    private String queueResumeMessage;
    44	
    45	    public Task() {
    46	        this(Util.generateUUID());
    47	    }
    48	    public Task(String id) {
    49	        this.id = id;
    50	        created = System.currentTimeMillis();
    51	        modified = System.currentTimeMillis();
    52	    }
    53	    // makes a child of the passed Task (and inherits the parameters!!)
    54	    public Task(Task p) {
    55	        this();
    56	        this.setParameters(p.getParameters());
    57	        this.setParent(p);
    58	    }
    59	
    60	    public String getId() {
    61	        return id;
    62	    }
    63	
    64	    public long getCreatedLong() {
    65	        return created;
    66	    }
    67	
    68	    public long getModifiedLong() {
    69	        return modified;
    70	    }
    71	
    72	    public long timeInactive() {
    73	        long now = System.currentTimeMillis();
    74	
    75	        if (modified > 0) return (now - modified);
    76	        if (created > 0) return (now - created);
    77	        // weird or inconclusive:
    78	        return -1l;
    79	    }
    80	
    81	    public boolean timedOutDueToInactivity() {
    82	        return (timeInactive() > TIMEOUT_INACTIVE_MILLIS);
    83	    }
    84	
    85	    public boolean statusInEndState() {
    86	        if ("completed".equals(status)) return true;
    87	        if ("error".equals(status)) return true;
    88	        // ml-service migration v2: "dropped-stale" is terminal — the task's
    89	        // target was deleted before the queued job ran. Neither success nor
    90	        // error; the inactivity-timeout watchdog must not flip it to error.
    91	        if ("dropped-stale".equals(status)) return true;
    92	        return false;
    93	    }
    94	
    95	    public void setModified() {
    96	        modified = System.currentTimeMillis();
    97	    }
    98	
    99	    public boolean canUserAccess(User user, Shepherd myShepherd) {
   100	        if (user == null) return false;
   101	        if (user.isAdmin(myShepherd)) return true;
   102	        Encounter enc = null;
   103	        // if we have annotations, use first to determine encounter
   104	        if (this.countObjectAnnotations() > 0) {
   105	            enc = this.getObjectAnnotations().get(0).findEncounter(myShepherd);
   106	        } else if (this.countObjectMediaAssets() > 0) { // no annots, use asset instead
   107	            MediaAsset ma = this.getObjectMediaAssets().get(0);
   108	            // we iterate over all annots on this asset til we find an encounter.
   109	            // it might be better to find *all* encounters and return access based on each;
   110	            // however the main use for userHasAccess() revolves around *annotation-based* tasks (matching)
   111	            // so i think this means asset-based access of tasks will be rare or unused anyway
   112	            for (Annotation ann : ma.getAnnotations()) {
   113	                if (ann != null) enc = ann.findEncounter(myShepherd);
   114	                if (enc != null) break;
   115	            }
   116	        }
   117	        if (enc == null) return false;
   118	        if (enc.isPubliclyReadable()) return true;
   119	        // note: we also have enc.canUserView() and enc.canUserEdit() !!! :(
   120	        return enc.canUserAccess(user, myShepherd.getContext());
   121	    }
   122	
   123	/*
   124	    // not really convinced these are accurate enough to use
   125	    //   actual computation of these things is complicated
   126	    //   leaving these for future potential exploration, if needed.
   127	
   128	    public boolean isTypeDetection() {
   129	        if (this.hasObjectMediaAssets()) return true;
   130	        if (this.hasObjectAnnotations()) return false;
   131	        if (this.parameters == null) return false;
   132	        if (this.getParameters().optJSONObject("ibeis.identification") != null) return false;
   133	        if (this.getParameters().optBoolean("ibeis.detection", false)) return true;
   134	        return false;
   135	    }
   136	    public boolean isTypeIdentification() {
   137	        if (this.isTypeDetection()) return false;  // we trust this a little more if (this.hasObjectAnnotations()) return true;
   138	        if (this.parameters == null) return false;
   139	        if (this.getParameters().optJSONObject("ibeis.identification") != null) return true;
   140	        return false;
   500	        if (id == null) return Util.generateUUID().hashCode(); // random(ish) so we dont get two users with no uuid equals! :/
   501	        return id.hashCode();
   502	    }
   503	
   504	    public String toString() {
   505	        return new ToStringBuilder(this)
   506	                   .append(id)
   507	                   .append("(" + new DateTime(created) + "|" + new DateTime(modified) + ")")
   508	                   .append(numChildren() + "Kids")
   509	                   .append(countObjectMediaAssets() + "MA")
   510	                   .append(countObjectAnnotations() + "Ann")
   511	                   .append("params=" + ((this.getParameters() ==
   512	                null) ? "(none)" : this.getParameters().toString()))
   513	                   .toString();
   514	    }
   515	
   516	    public static Task load(String taskId, Shepherd myShepherd) {
   517	        Task t = null;
   518	
   519	        try {
   520	            t = ((Task)(myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(
   521	                Task.class, taskId), true)));
   522	        } catch (Exception ex) {}; // swallow jdo not found noise
   523	        return t;
   524	    }
   525	
   526	    public static List<Task> getTasksFor(Annotation ann, Shepherd myShepherd) {
   527	        return getTasksFor(ann, myShepherd, null);
   528	    }
   529	
   530	    // TODO: evaluate if we should support versions for multiple objects (when needed)
   531	    public static List<Task> getTasksFor(Annotation ann, Shepherd myShepherd, String ordering) {
   532	        String qstr =
   533	            "SELECT FROM org.ecocean.ia.Task WHERE objectAnnotations.contains(obj) && obj.id == \""
   534	            + ann.getId() + "\" VARIABLES org.ecocean.Annotation obj";
   535	        Query query = myShepherd.getPM().newQuery(qstr);
   536	
   537	        query.setIgnoreCache(true);
   538	        if (ordering == null) {
   539	            query.setOrdering("created");
   540	        } else {
   541	            query.setOrdering(ordering);
   542	        }
   543	        Collection c = (Collection)query.execute();
   544	        List<Task> listy = new ArrayList<Task>(c);
   545	        query.closeAll();
   546	        return listy;
   547	    }
   548	
   549	    public static List<Task> getRootTasksFor(Annotation ann, Shepherd myShepherd) {
   550	        return onlyRoots(getTasksFor(ann, myShepherd));
   551	    }
   552	
   553	    public static List<Task> getTasksFor(MediaAsset ma, Shepherd myShepherd) {
   554	        String qstr =
   555	            "SELECT FROM org.ecocean.ia.Task WHERE objectMediaAssets.contains(obj) && obj.id == " +
   556	            ma.getId() + " VARIABLES org.ecocean.media.MediaAsset obj";
   557	        Query query = myShepherd.getPM().newQuery(qstr);
   558	
   559	        query.setIgnoreCache(true);
   560	        query.setOrdering("created");

 succeeded in 665ms:
i/      w/lf    attr/                 	src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java

codex
The implementation itself is intentionally narrow. The main review risk I see is in the new test’s last case: it describes a timeout scenario, but the constructed task cannot currently take the timeout mutation path.
exec
/bin/bash -lc 'rg -n "getStoredStatus|StoredStatus|raw.*status|side-effect|sibling task|Track 2 C7|batch gate" docs/plans/2026-05-18-empty-match-prospects-design.md docs/plans/2026-05-09-ml-service-migration-v2.md src/main/java src/test/java/org/ecocean/ia' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"getStatus\\(\" src/main/java src/test/java | head -n 80" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'git diff --check -- src/main/java/org/ecocean/ia/Task.java src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java' in /mnt/c/Wildbook-clean2
 succeeded in 2111ms:
src/test/java/org/ecocean/EmbeddingTest.java:85:        // Task.getStatus(Shepherd) is the public accessor and runs an
src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java:12: * mutation that {@link Task#getStatus(org.ecocean.shepherd.core.Shepherd)}
src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java:34:        // Build a task with no creation/activity timestamps; getStatus(Shepherd)
src/main/java/org/ecocean/identity/IdentityServiceLog.java:95:    public String getStatus() {
src/main/java/org/ecocean/api/BulkImport.java:879:        String persistedStatus = task.getStatus();
src/main/java/org/ecocean/ia/Task.java:599:     * mutation side-effect that {@link #getStatus(Shepherd)} performs.
src/main/java/org/ecocean/ia/Task.java:611:    public String getStatus(Shepherd myShepherd) {
src/main/java/org/ecocean/ia/Task.java:651:        System.out.println("[DEBUG] getStatus() fell through to status='" + status + "' on Task " +
src/main/java/org/ecocean/ia/Task.java:673:                            map.put(childTask2.getId(), childTask2.getStatus(myShepherd));
src/main/java/org/ecocean/ia/Task.java:681:                        map.put(childTask.getId(), childTask.getStatus(myShepherd));
src/main/java/org/ecocean/ia/Task.java:697:            status = this.getStatus(myShepherd);
src/main/java/org/ecocean/ia/Task.java:881:            rtn.put("status", getStatus(myShepherd));
src/main/java/org/ecocean/Resolver.java:57:    public String getStatus() {
src/main/java/org/ecocean/media/MediaAssetSet.java:46:    public String getStatus() {
src/main/java/org/ecocean/servlet/IAGateway.java:507:                    if (l.getStatus() != null) t.put("status", new JSONObject(l.getStatus()));
src/main/java/org/ecocean/servlet/importer/ImportTask.java:317:    public String getStatus() { return status; }
src/main/java/org/ecocean/servlet/importer/ImportTask.java:403:                String status = atask.getStatus(myShepherd);
src/main/java/org/ecocean/servlet/importer/ImportTask.java:412:                    String latestStatus = "numLatestTask_" + atask.getStatus(myShepherd);

 succeeded in 2121ms:
docs/plans/2026-05-18-empty-match-prospects-design.md:55:  topTask-null WARN, the two-wait split, the raw status accessor,
docs/plans/2026-05-18-empty-match-prospects-design.md:81:- **`Task.getStoredStatus()` added** to `Task.java` (Codex round-4
docs/plans/2026-05-18-empty-match-prospects-design.md:82:  Major). Returns the raw persisted status field without the
docs/plans/2026-05-18-empty-match-prospects-design.md:83:  timed-out-task mutation side-effect of `getStatus(Shepherd)`.
docs/plans/2026-05-18-empty-match-prospects-design.md:115:  for MA detectionStatus). Use a raw persisted-status accessor,
docs/plans/2026-05-18-empty-match-prospects-design.md:318:**raw persisted status**.
docs/plans/2026-05-18-empty-match-prospects-design.md:343:**Use raw persisted status** (Codex round-3 Major + round-4
docs/plans/2026-05-18-empty-match-prospects-design.md:353: * mutation side-effect that getStatus(Shepherd) performs. Use
docs/plans/2026-05-18-empty-match-prospects-design.md:355: * where mutating timed-out tasks as a side-effect would corrupt
docs/plans/2026-05-18-empty-match-prospects-design.md:358:public String getStoredStatus() {
docs/plans/2026-05-18-empty-match-prospects-design.md:363:The gate reads `siblingChildTask.getStoredStatus()` and tests
docs/plans/2026-05-18-empty-match-prospects-design.md:964:  side-effect would corrupt the gate's read).
src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java:9: * Coverage of {@link Task#getStoredStatus()} — the raw, side-effect-free
src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java:10: * accessor needed by the empty-match-prospects Track 2 batch gate so a
src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java:11: * read of a sibling task's status doesn't trigger the timed-out-task
src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java:13: * performs. (Empty-match-prospects design Track 2 C7.)
src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java:15:class TaskGetStoredStatusTest {
src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java:18:        assertNull(new Task().getStoredStatus());
src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java:24:        assertEquals("completed", t.getStoredStatus());
src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java:30:        assertEquals("processing-mlservice", t.getStoredStatus());
src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java:35:        // could in principle flip its status to "error". getStoredStatus
src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java:39:        assertNull(t.getStoredStatus());
src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java:42:        assertNull(t.getStoredStatus());
src/main/java/org/ecocean/Encounter.java:2193:    // we need these methods because our side-effected setGenus will silently break an import (!!!!!) in an edge case I cannot identify
src/main/java/org/ecocean/ia/Task.java:598:     * Read the raw persisted status field without the timed-out-task
src/main/java/org/ecocean/ia/Task.java:599:     * mutation side-effect that {@link #getStatus(Shepherd)} performs.
src/main/java/org/ecocean/ia/Task.java:601:     * empty-match-prospects Track 2 batch gate) need this so a read of
src/main/java/org/ecocean/ia/Task.java:602:     * a sibling task's status doesn't mutate that task as a side
src/main/java/org/ecocean/ia/Task.java:605:     * <p>(Empty-match-prospects design Track 2 C7.)</p>
src/main/java/org/ecocean/ia/Task.java:607:    public String getStoredStatus() {

 succeeded in 2235ms:
exec
/bin/bash -lc "nl -ba docs/plans/2026-05-18-empty-match-prospects-design.md | sed -n '340,370p'" in /mnt/c/Wildbook-clean2
 succeeded in 500ms:
   340	}
   341	```
   342	
   343	**Use raw persisted status** (Codex round-3 Major + round-4
   344	Major). The current public getter `Task.getStatus(Shepherd)` is
   345	derived and can mutate timed-out tasks (`Task.java:597`), which we
   346	don't want inside the gate. A raw accessor does NOT exist yet, so
   347	this design adds one:
   348	
   349	```java
   350	// In Task.java, alongside getStatus():
   351	/**
   352	 * Returns the persisted status field WITHOUT the timed-out-task
   353	 * mutation side-effect that getStatus(Shepherd) performs. Use
   354	 * this when reading status for read-only decisions (e.g., gating)
   355	 * where mutating timed-out tasks as a side-effect would corrupt
   356	 * the caller's logic.
   357	 */
   358	public String getStoredStatus() {
   359	    return this.status;
   360	}
   361	```
   362	
   363	The gate reads `siblingChildTask.getStoredStatus()` and tests
   364	against the terminal set listed below. Either-being-terminal is
   365	sufficient.
   366	
   367	Terminal vs non-terminal status sets:
   368	
   369	**MediaAsset.detectionStatus** (constants from `IBEISIA.java:73-82`).
   370	Terminal:

exec
/bin/bash -lc "nl -ba src/test/java/org/ecocean/EmbeddingTest.java | sed -n '70,100p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/api/BulkImport.java | sed -n '860,890p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/Task.java | sed -n '850,895p'" in /mnt/c/Wildbook-clean2
 succeeded in 477ms:
    70	            .put("pipeline_root", "vector");
    71	        assertTrue(Embedding.findMatchProspects(conf, null, null));
    72	    }
    73	
    74	    @Test void findMatchProspects_acceptsLegacyApiEndpointConfig() {
    75	        // Legacy: api_endpoint present, no method. Must keep working.
    76	        JSONObject conf = new JSONObject().put("api_endpoint", "http://legacy");
    77	        assertTrue(Embedding.findMatchProspects(conf, null, null));
    78	    }
    79	
    80	    @Test void findMatchProspects_zeroAnnotationsCompletesParent() {
    81	        JSONObject conf = new JSONObject().put("method", "miewid");
    82	        Task task = new Task();   // no annotations
    83	        boolean rtn = Embedding.findMatchProspects(conf, task, null);
    84	        assertTrue(rtn);
    85	        // Task.getStatus(Shepherd) is the public accessor and runs an
    86	        // inactivity-timeout check that needs the persistent state. For a
    87	        // unit test on a brand-new in-memory Task, we go through the
    88	        // statusInEndState predicate, which only inspects this.status.
    89	        assertTrue(task.statusInEndState());
    90	        assertNotNull(task.getCompletionDateInMilliseconds());
    91	    }
    92	}

 succeeded in 493ms:
   860	
   861	    private static JSONObject taskJson(ImportTask task, boolean detailed, Shepherd myShepherd) {
   862	        JSONObject jt = new JSONObject();
   863	
   864	        jt.put("id", task.getId());
   865	        jt.put("creator",
   866	            task.getCreator() ==
   867	            null ? JSONObject.NULL : task.getCreator().infoJSONObject(myShepherd));
   868	        jt.put("dateCreated", task.getCreated());
   869	        jt.put("sourceName", task.getSourceName());
   870	        jt.put("matchingLocations", task.getMatchingLocations());
   871	        jt.put("legacy", task.isLegacy());
   872	        jt.put("errors", task.getErrors());
   873	        // "importPercent" was deemed more consistent and useful
   874	        jt.put("importPercent", task.getProcessingProgress());
   875	        jt.put("numberEncounters", task.numberEncounters());
   876	
   877	        // FIXME - this status may get tweaked below. hacky. we should figure out a way to
   878	        // set it properly when IA pipeline is finished
   879	        String persistedStatus = task.getStatus();
   880	        jt.put("status", persistedStatus);
   881	        jt.put("_statusPersisted", persistedStatus);
   882	        // FIXME see note on iaSummaryJson() about how this is slow, therefore
   883	        // we move it inside detailed==true, but this makes status fubar! :(
   884	        if (detailed) {
   885	            JSONObject iaSummary = task.iaSummaryJson(myShepherd);
   886	            jt.put("iaSummary", iaSummary);
   887	            if (iaSummary.optBoolean("pipelineStarted", false)) {
   888	                if (iaSummary.optBoolean("pipelineComplete", false)) {
   889	                    jt.put("status", "complete");
   890	                } else if ("complete".equals(persistedStatus)) {

 succeeded in 611ms:
   850	            MatchResult mr = getLatestMatchResult(myShepherd);
   851	            if ((mr == null) && !hasChildren()) {
   852	                System.out.println(
   853	                    "[DEBUG] matchResultsJson() found no MatchResults; generating on (leaf) Task " +
   854	                    this.getId());
   855	                List<MatchResult> mrs = generateMatchResults(myShepherd);
   856	                rtn.put("_generatedMatchResultsSize", mrs.size()); // leave a clue that we did the work!
   857	                if (mrs.size() > 0) {
   858	                    mr = mrs.get(mrs.size() - 1);
   859	                    // this hack is important cuz it forces a db commit even though we are a GET api call sorrynotsorry
   860	                    rtn.put("_commitShepherd", true);
   861	                }
   862	            }
   863	            if (mr != null)
   864	                rtn.put("matchResults", mr.jsonForApiGet(cutoff, projectIds, myShepherd));
   865	        }
   866	        // now we recurse thru children if applicable
   867	        if (hasChildren()) {
   868	            JSONArray charr = new JSONArray();
   869	            for (Task child : children) {
   870	                // TODO decide if we need to process child????
   871	                JSONObject childJson = child.matchResultsJson(cutoff, projectIds, myShepherd);
   872	                // we have to bubble this up all the way to the toplevel  :/
   873	                if (childJson.optBoolean("_commitShepherd", false))
   874	                    rtn.put("_commitShepherd", true);
   875	                charr.put(childJson);
   876	            }
   877	            rtn.put("children", charr);
   878	            // if we dont have children (leaf nodes) we get the status
   879	        } else {
   880	            // unsure which of these two things is more accurate or useful; thus including both
   881	            rtn.put("status", getStatus(myShepherd));
   882	            rtn.put("statusOverall", getOverallStatus(myShepherd));
   883	            rtn.put("statusDetails", getStatusDetails());
   884	        }
   885	        return rtn;
   886	    }
   887	}

exec
/bin/bash -lc "sed -n '1,60p' src/test/java/org/ecocean/ia/TaskStatusInEndStateTest.java" in /mnt/c/Wildbook-clean2
 succeeded in 511ms:
package org.ecocean.ia;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * v2 commit #5: Task.statusInEndState recognizes "dropped-stale" as terminal.
 * The inactivity-timeout watchdog must not flip a deliberately-dropped task
 * (target deleted before the queued ml-service job ran) to "error".
 */
class TaskStatusInEndStateTest {

    @Test void completedIsTerminal() {
        Task t = new Task();
        t.setStatus("completed");
        assertTrue(t.statusInEndState());
    }

    @Test void errorIsTerminal() {
        Task t = new Task();
        t.setStatus("error");
        assertTrue(t.statusInEndState());
    }

    @Test void droppedStaleIsTerminal() {
        Task t = new Task();
        t.setStatus("dropped-stale");
        assertTrue(t.statusInEndState());
    }

    @Test void initiatedIsNotTerminal() {
        Task t = new Task();
        t.setStatus("initiated");
        assertFalse(t.statusInEndState());
    }

    @Test void typoIsNotTerminal() {
        // "completed-foo" must not accidentally pass the terminal check.
        Task t = new Task();
        t.setStatus("completed-foo");
        assertFalse(t.statusInEndState());
    }
}

codex
**Findings**

Low: [TaskGetStoredStatusTest.java:33](/mnt/c/Wildbook-clean2/src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java:33) says it covers a task that “would time out,” but it does not. `new Task()` initializes fresh timestamps, and `setStatus(null)` bypasses the timeout mutation guard in [Task.java:614](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/Task.java:614). So this test proves null remains null, but not the core regression: non-terminal stored status on a timed-out task is read without flipping to `"error"`. I’d make this test set a non-terminal status and force `timedOutDueToInactivity()` true, with the timeout static restored in `finally`.

No production correctness issues found in [Task.java:607](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/Task.java:607). `public` visibility, placement immediately above `getStatus(Shepherd)`, and the `getStoredStatus` name all match the locked design. The implementation is side-effect-free and avoids Shepherd/network concerns.

I also checked the touched files for the CRLF gotcha: `Task.java` is `i/lf w/lf`, and the new untracked test file is `w/lf`. I did not run Maven tests because this was review-only and test execution would write build artifacts.
tokens used
67,964
**Findings**

Low: [TaskGetStoredStatusTest.java:33](/mnt/c/Wildbook-clean2/src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java:33) says it covers a task that “would time out,” but it does not. `new Task()` initializes fresh timestamps, and `setStatus(null)` bypasses the timeout mutation guard in [Task.java:614](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/Task.java:614). So this test proves null remains null, but not the core regression: non-terminal stored status on a timed-out task is read without flipping to `"error"`. I’d make this test set a non-terminal status and force `timedOutDueToInactivity()` true, with the timeout static restored in `finally`.

No production correctness issues found in [Task.java:607](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/Task.java:607). `public` visibility, placement immediately above `getStatus(Shepherd)`, and the `getStoredStatus` name all match the locked design. The implementation is side-effect-free and avoids Shepherd/network concerns.

I also checked the touched files for the CRLF gotcha: `Task.java` is `i/lf w/lf`, and the new untracked test file is `w/lf`. I did not run Maven tests because this was review-only and test execution would write build artifacts.
