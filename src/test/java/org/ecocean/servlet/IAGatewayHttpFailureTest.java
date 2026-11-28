package org.ecocean.servlet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.jdo.JDOObjectNotFoundException;

import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

class IAGatewayHttpFailureTest {
    // Run processQueueMessage's actual catch/requeue decisions. Only isolate
    // the database, the operation that fails, and the delayed queue publisher.
    private static List<Boolean> requeues(String operation, Exception failure) {
        List<Boolean> increments = new ArrayList<>();
        JSONObject job = new JSONObject().put(operation, new JSONObject())
            .put("taskId", "http-failure-test").put("__context", "context0");
        try (MockedConstruction<Shepherd> shepherds = mockConstruction(Shepherd.class);
            MockedStatic<IAGateway> gateway = mockStatic(IAGateway.class, invocation -> {
                String method = invocation.getMethod().getName();
                if (method.equals("_doDetect") || method.equals("_doIdentify")) throw failure;
                if (method.equals("requeueJob")) {
                    JSONObject queued = invocation.getArgument(0);
                    assertEquals("http-failure-test", queued.getString("taskId"));
                    increments.add(invocation.getArgument(1));
                    return true;
                }
                return invocation.callRealMethod();
            })) {
            IAGateway.processQueueMessage(job.toString());
            assertEquals(1, shepherds.constructed().size());
            verify(shepherds.constructed().get(0)).rollbackDBTransaction();
            verify(shepherds.constructed().get(0)).closeDBTransaction();
        }
        return increments;
    }

    @Test void detection608DoesNotRequeueForEitherSpelling() {
        for (String separator : new String[] { " = ", " : " }) {
            assertEquals(Collections.emptyList(), requeues("detect",
                new IOException("HTTP error code" + separator + "608")));
        }
    }

    @Test void detection500And502RetryWithIncrementForEitherSpelling() {
        for (String separator : new String[] { " = ", " : " }) {
            for (int code : new int[] { 500, 502 }) {
                assertEquals(Collections.singletonList(true), requeues("detect",
                    new IOException("HTTP error code" + separator + code)));
            }
        }
    }

    @Test void detectionOtherFailuresRetainRetryWithIncrement() {
        for (String message : new String[] { null, "unknown failure", "HTTP error code = 400" }) {
            assertEquals(Collections.singletonList(true), requeues("detect",
                new IOException(message)));
        }
    }

    @Test void identification500RetriesWithIncrementForEitherSpelling() {
        for (String separator : new String[] { " = ", " : " }) {
            assertEquals(Collections.singletonList(true), requeues("identify",
                new IOException("HTTP error code" + separator + "500")));
        }
    }

    @Test void identificationOtherFailuresRetainRetryWithoutIncrement() {
        for (String message : new String[] { null, "unknown failure", "HTTP error code = 400",
            "HTTP error code = 502", "HTTP error code = 608" }) {
            assertEquals(Collections.singletonList(false), requeues("identify",
                new IOException(message)));
        }
    }

    @Test void identificationEmptyTargetDoesNotRequeue() {
        assertEquals(Collections.emptyList(), requeues("identify",
            new IOException("Empty target annotation list")));
    }

    @Test void missingIdentificationObjectDoesNotRequeue() {
        for (String separator : new String[] { " = ", " : " }) {
            assertEquals(Collections.emptyList(), requeues("identify",
                new JDOObjectNotFoundException("HTTP error code" + separator + "500")));
        }
    }
}
