# Tempo Tracing Integration for Wildbook

## Overview

This document explains how to integrate distributed tracing with Grafana Tempo into your Wildbook application. Tracing provides detailed insights into request flow across services, helping identify performance bottlenecks and debug issues.

## What's Been Added

### 1. Tempo Service
- **Container**: `tempo-dev`
- **Ports**:
  - `3200`: Tempo API and UI
  - `4317`: OpenTelemetry Protocol (OTLP) gRPC
  - `4318`: OpenTelemetry Protocol (OTLP) HTTP
  - `14268`: Jaeger Thrift HTTP
  - `9411`: Zipkin

### 2. Updated Grafana Configuration
- Added Tempo as a datasource
- Configured trace-to-logs correlation
- Enabled service graph visualization
- Set up exemplar support for metrics correlation

### 3. Alloy Configuration Updates
- Added OTLP receiver for traces
- Configured trace processing pipeline
- Set up trace forwarding to Tempo
- Added trace ID extraction from logs

## Java Application Integration

### Maven Dependencies

Add these dependencies to your `pom.xml`:

```xml
<!-- OpenTelemetry Core -->
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-api</artifactId>
    <version>1.32.0</version>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-sdk</artifactId>
    <version>1.32.0</version>
</dependency>

<!-- OpenTelemetry Exporters -->
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
    <version>1.32.0</version>
</dependency>

<!-- OpenTelemetry Instrumentation -->
<dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-servlet-5.0</artifactId>
    <version>1.32.0-alpha</version>
</dependency>
<dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-jdbc</artifactId>
    <version>1.32.0-alpha</version>
</dependency>

<!-- Bridge for SLF4J logs -->
<dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-log4j-context-data-2.17-autoconfigure</artifactId>
    <version>1.32.0-alpha</version>
</dependency>
```

### OpenTelemetry Configuration

Create a configuration class:

```java
package org.ecocean.telemetry;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;

public class TracingConfiguration {
    private static OpenTelemetry openTelemetry;
    private static Tracer tracer;
    
    static {
        initializeOpenTelemetry();
    }
    
    private static void initializeOpenTelemetry() {
        // Configure the service resource
        Resource resource = Resource.getDefault()
            .merge(Resource.create(Attributes.of(
                ResourceAttributes.SERVICE_NAME, System.getenv().getOrDefault("SERVICE_NAME", "wildbook"),
                ResourceAttributes.SERVICE_VERSION, "1.0.0",
                ResourceAttributes.SERVICE_NAMESPACE, System.getenv().getOrDefault("DOMAIN_NAME", "unknown"),
                ResourceAttributes.DEPLOYMENT_ENVIRONMENT, System.getenv().getOrDefault("ENVIRONMENT", "development")
            )));
        
        // Configure the OTLP exporter
        String endpoint = System.getenv().getOrDefault("OTEL_EXPORTER_OTLP_ENDPOINT", "http://alloy:4317");
        OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
            .setEndpoint(endpoint)
            .build();
        
        // Build the tracer provider
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
            .setResource(resource)
            .build();
        
        // Build OpenTelemetry
        openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .buildAndRegisterGlobal();
        
        // Get a tracer
        tracer = openTelemetry.getTracer("wildbook-tracer", "1.0.0");
    }
    
    public static OpenTelemetry getOpenTelemetry() {
        return openTelemetry;
    }
    
    public static Tracer getTracer() {
        return tracer;
    }
}
```

### Adding Tracing to Your Code

#### Example: Encounter Processing

```java
package org.ecocean.services;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.ecocean.telemetry.TracingConfiguration;

public class EncounterProcessor {
    private static final Logger logger = LogManager.getLogger(EncounterProcessor.class);
    private static final Tracer tracer = TracingConfiguration.getTracer();
    
    public void processEncounter(Encounter encounter) {
        // Start a span for the entire operation
        Span span = tracer.spanBuilder("encounter.process")
            .setAttribute("encounter.id", encounter.getId())
            .setAttribute("encounter.species", encounter.getSpecies())
            .setAttribute("encounter.location", encounter.getLocation())
            .startSpan();
        
        // Add trace context to logs
        ThreadContext.put("trace_id", span.getSpanContext().getTraceId());
        ThreadContext.put("span_id", span.getSpanContext().getSpanId());
        
        try (Scope scope = span.makeCurrent()) {
            logger.info("Starting encounter processing");
            
            // Process images with a child span
            processImages(encounter);
            
            // Match patterns with a child span
            findMatches(encounter);
            
            // Save to database with a child span
            saveEncounter(encounter);
            
            span.setStatus(StatusCode.OK);
            logger.info("Encounter processed successfully");
            
        } catch (Exception ex) {
            span.setStatus(StatusCode.ERROR, ex.getMessage());
            span.recordException(ex);
            logger.error("Encounter processing failed", ex);
            throw ex;
        } finally {
            span.end();
            ThreadContext.remove("trace_id");
            ThreadContext.remove("span_id");
        }
    }
    
    private void processImages(Encounter encounter) {
        Span span = tracer.spanBuilder("image.process")
            .setAttribute("image.count", encounter.getImages().size())
            .startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            // Image processing logic
            Thread.sleep(100); // Simulate processing
            span.addEvent("Images processed");
        } catch (Exception ex) {
            span.setStatus(StatusCode.ERROR);
            span.recordException(ex);
        } finally {
            span.end();
        }
    }
    
    private void findMatches(Encounter encounter) {
        Span span = tracer.spanBuilder("pattern.match")
            .startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            // Pattern matching logic
            Thread.sleep(200); // Simulate matching
            span.setAttribute("matches.found", 3);
        } catch (Exception ex) {
            span.setStatus(StatusCode.ERROR);
            span.recordException(ex);
        } finally {
            span.end();
        }
    }
    
    private void saveEncounter(Encounter encounter) {
        Span span = tracer.spanBuilder("database.save")
            .setAttribute("db.system", "postgresql")
            .setAttribute("db.operation", "INSERT")
            .startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            // Database save logic
            Thread.sleep(50); // Simulate DB operation
        } catch (Exception ex) {
            span.setStatus(StatusCode.ERROR);
            span.recordException(ex);
        } finally {
            span.end();
        }
    }
}
```

#### Example: REST API Endpoint

```java
package org.ecocean.api;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

@Path("/api/encounters")
public class EncounterAPI {
    private static final Tracer tracer = TracingConfiguration.getTracer();
    
    @GET
    @Path("/{id}")
    @Produces("application/json")
    public Response getEncounter(@PathParam("id") String id, 
                                 @Context HttpServletRequest request) {
        
        Span span = tracer.spanBuilder("GET /api/encounters/{id}")
            .setSpanKind(SpanKind.SERVER)
            .setAttribute("http.method", "GET")
            .setAttribute("http.url", request.getRequestURL().toString())
            .setAttribute("http.target", "/api/encounters/" + id)
            .setAttribute("encounter.id", id)
            .startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            // Your business logic here
            Encounter encounter = encounterService.findById(id);
            
            if (encounter == null) {
                span.setAttribute("http.status_code", 404);
                return Response.status(404).build();
            }
            
            span.setAttribute("http.status_code", 200);
            return Response.ok(encounter).build();
            
        } catch (Exception ex) {
            span.setStatus(StatusCode.ERROR);
            span.recordException(ex);
            span.setAttribute("http.status_code", 500);
            throw ex;
        } finally {
            span.end();
        }
    }
}
```

### Servlet Filter for Automatic Tracing

```java
package org.ecocean.telemetry;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebFilter("/*")
public class TracingFilter implements Filter {
    private static final Tracer tracer = TracingConfiguration.getTracer();
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, 
                        FilterChain chain) throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        String spanName = httpRequest.getMethod() + " " + httpRequest.getServletPath();
        
        Span span = tracer.spanBuilder(spanName)
            .setSpanKind(SpanKind.SERVER)
            .setAttribute("http.method", httpRequest.getMethod())
            .setAttribute("http.scheme", httpRequest.getScheme())
            .setAttribute("http.host", httpRequest.getServerName())
            .setAttribute("http.target", httpRequest.getRequestURI())
            .setAttribute("http.user_agent", httpRequest.getHeader("User-Agent"))
            .startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            chain.doFilter(request, response);
            span.setAttribute("http.status_code", httpResponse.getStatus());
        } catch (Exception ex) {
            span.setStatus(StatusCode.ERROR);
            span.recordException(ex);
            throw ex;
        } finally {
            span.end();
        }
    }
}
```

## Environment Variables

Add these to your Docker Compose or deployment configuration:

```yaml
environment:
  # OpenTelemetry configuration
  - OTEL_SERVICE_NAME=wildbook
  - OTEL_EXPORTER_OTLP_ENDPOINT=http://alloy:4317
  - OTEL_EXPORTER_OTLP_PROTOCOL=grpc
  - OTEL_TRACES_EXPORTER=otlp
  - OTEL_METRICS_EXPORTER=none  # Disable metrics for now
  - OTEL_LOGS_EXPORTER=none     # Disable OTLP logs (using Log4j2)
  
  # Existing environment variables
  - SERVICE_NAME=wildbook
  - DOMAIN_NAME=${DOMAIN_NAME}
  - ENVIRONMENT=${ENVIRONMENT}
```

## Testing the Integration

1. **Start the observability stack**:
   ```bash
   cd devops/development
   docker-compose -f docker-compose.observability.yml up -d
   ```

2. **Run the test script**:
   ```bash
   chmod +x test-tempo.sh
   ./test-tempo.sh
   ```

3. **View traces in Grafana**:
   - Open http://localhost:3000
   - Go to Explore
   - Select Tempo datasource
   - Search for traces

## Viewing Traces

### In Grafana Explore

1. **Search by Trace ID**: Enter a specific trace ID
2. **Search by Tags**: 
   - `service.name = wildbook`
   - `encounter.id = ENC-2024-001`
   - `http.status_code = 500`
3. **Search by Time Range**: Select time range and run query

### Trace to Logs Correlation

When viewing a trace in Grafana:
1. Click on a span
2. Click "Logs for this span"
3. Grafana will automatically query Loki for logs with matching trace ID

### Service Graph

After traces are collected:
1. Go to Explore
2. Select Tempo datasource
3. Click on "Service Graph" tab
4. View service dependencies and latencies

## Best Practices

1. **Use Semantic Conventions**: Follow OpenTelemetry semantic conventions for attribute names
2. **Add Context**: Include relevant business context (user ID, encounter ID, etc.)
3. **Handle Errors**: Always set error status and record exceptions
4. **Use Spans Wisely**: Create spans for significant operations (>10ms)
5. **Correlate with Logs**: Always add trace_id and span_id to ThreadContext
6. **Sample in Production**: Use probabilistic sampling for high-volume production traffic

## Troubleshooting

### Traces Not Appearing

1. Check Alloy is receiving traces:
   ```bash
   docker logs alloy | grep -i trace
   ```

2. Check Tempo is receiving traces:
   ```bash
   curl http://localhost:3200/metrics | grep traces_received
   ```

3. Verify network connectivity:
   ```bash
   docker exec wildbook curl -v http://alloy:4317
   ```

### Performance Impact

- Typical overhead: 1-3% CPU
- Memory: ~50MB for instrumentation
- Network: ~1KB per trace

To reduce overhead:
- Use sampling (10% is often sufficient)
- Limit attribute size
- Batch exports

## Next Steps

1. **Add Custom Instrumentation**: Instrument critical business operations
2. **Set Up Alerts**: Create alerts based on trace metrics
3. **Implement Sampling**: Configure intelligent sampling for production
4. **Add Exemplars**: Link metrics to traces for better correlation
5. **Dashboard Creation**: Build dashboards showing trace-derived metrics
