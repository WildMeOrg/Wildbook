# Wildbook Logging and Observability Infrastructure

## Overview

This document describes the centralized logging and observability infrastructure developed for the Wildbook platform. The solution provides unified log aggregation, search, and visualization capabilities across multiple Wildbook instances running in Docker containers.

## Architecture

### Technology Stack

- **Grafana Alloy**: OpenTelemetry-compatible collector for log collection and forwarding (replacing the deprecated Promtail)
- **Grafana Loki**: Log aggregation and storage system
- **Grafana**: Visualization and querying interface
- **Log4j2**: Java application logging framework with JSON output
- **Docker**: Container platform with label-based log routing

### Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                     Production VMs                           │
├─────────────────────────────────────────────────────────────┤
│  ┌──────────────────────────────────────────────────────┐  │
│  │ VM1: Flukebook Domain                                │  │
│  │  ┌─────────┐ ┌─────────┐ ┌──────────┐ ┌─────────┐  │  │
│  │  │Wildbook │ │  Nginx  │ │PostgreSQL│ │OpenSearch│  │  │
│  │  │(Tomcat) │ │         │ │          │ │         │  │  │
│  │  └────┬────┘ └────┬────┘ └────┬─────┘ └────┬────┘  │  │
│  │       │           │           │             │        │  │
│  │       └───────────┴───────────┴─────────────┘        │  │
│  │                       │                              │  │
│  │              ┌────────▼────────┐                     │  │
│  │              │  Grafana Alloy  │                     │  │
│  │              │   (Collector)   │                     │  │
│  │              └────────┬────────┘                     │  │
│  └───────────────────────┼──────────────────────────────┘  │
│                          │                                  │
│  [Additional VMs with same structure]                       │
│                          │                                  │
└──────────────────────────┼──────────────────────────────────┘
                           │
                    ┌──────▼──────┐
                    │   Network   │
                    └──────┬──────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│                Central Monitoring Server                     │
│  ┌────────────┐  ┌────────────┐  ┌────────────────────┐   │
│  │   Loki     │◄─┤  Grafana   │  │  Prometheus        │   │
│  │ (Storage)  │  │   (UI)     │  │  (Metrics)         │   │
│  └────────────┘  └────────────┘  └────────────────────┘   │
└──────────────────────────────────────────────────────────────┘
```

## Configuration Files

The complete configuration files for this infrastructure are located in the repository:

```
Wildbook/
├── src/main/resources/
│   └── log4j2.xml                                          # Log4j2 configuration
├── devops/
│   ├── deploy/
│   │   ├── docker-compose.yml                             # Main application stack
│   │   ├── _env.template                                  # Environment variables template
│   │   └── .dockerfiles/
│   │       └── alloy/
│   │           └── config.alloy                           # Grafana Alloy configuration
│   └── shared/
│       ├── docker-compose.observability.yml               # Observability stack
│       └── .dockerfiles/
│           ├── loki/
│           │   └── loki-config.yaml                       # Loki configuration
│           └── grafana/
│               └── provisioning/
│                   ├── datasources/
│                   │   └── datasources.yml                # Grafana data sources
│                   └── dashboards/
│                       ├── dashboards.yml                 # Dashboard provisioning
│                       └── json/
│                           ├── 1-wildbook-overview.json  # Log overview dashboard
│                           └── 2-user-activity.json      # User activity dashboard
│                           └── ...                       # Other dashboards
```

## Component Configuration

### 1. Log4j2 Configuration

**Configuration File**: `src/main/resources/log4j2.xml`

#### Key Considerations

- **JSON Output Format**: Uses `JsonTemplateLayout` with ECS (Elastic Common Schema) for structured logging
- **Environment Variables**: Injects service metadata (SERVICE_NAME, ENVIRONMENT, DOMAIN_NAME, HOSTNAME) into every log entry
- **Dual Output**: Logs to both console (for Docker) and rolling file (for backup/debugging)
- **Performance**: Asynchronous logging recommended for high-throughput scenarios

#### Required Maven Dependencies

Add these to your `pom.xml`:

- `log4j-api` and `log4j-core` (version 2.23.1)
- `log4j-layout-template-json` for JSON output
- `log4j-slf4j2-impl` to bridge SLF4J calls
- `log4j-jcl` and `log4j-jul` for legacy logging bridges

**Important**: Exclude competing logging implementations (`slf4j-log4j12`, `slf4j-simple`, `logback-classic`) from dependencies to avoid conflicts.

### 2. Grafana Alloy Configuration

**Configuration File**: `devops/deploy/.dockerfiles/alloy/config.alloy`

#### Key Considerations

- **Docker Integration**: Uses the Docker socket to discover and collect logs from containers
- **Label-Based Collection**: Only collects from containers with `logging=alloy` label
- **JSON Parsing**: Automatically parses JSON-structured logs from Wildbook
- **Batch Processing**: Configures batching for efficient network usage (1MB batches, 5s wait)
- **Multi-Service Support**: Different parsing pipelines for Wildbook, nginx, PostgreSQL, etc.

#### Important Configuration Points

1. **Container Discovery**: Alloy automatically discovers containers via Docker labels
2. **Log Processing Pipeline**: Separate processing stages for different log formats
3. **Timestamp Extraction**: Ensures accurate time ordering of logs
4. **Label Extraction**: Converts Docker labels and log fields to Loki labels for querying

### 3. Docker Compose Configuration

**Configuration Files**:
- Application stack: `devops/deploy/docker-compose.yml`
- Observability stack: `devops/shared/docker-compose.observability.yml`
- Environment template: `devops/deploy/_env.template`

#### Key Considerations

- **Network Segregation**: Separate networks for application (`app-network`) and observability (`observability-network`)
- **Container Labels**: Essential for log routing and metadata
- **Environment Variables**: Pass service metadata to applications for inclusion in logs
- **Volume Mounts**: Alloy needs access to Docker socket for container discovery
- **Cross-Platform Compatibility**: Configuration works on Linux, macOS, and Windows

#### Required Environment Variables

See `devops/deploy/_env.template` for a complete template. Key variables:

- `DOMAIN_NAME`: Identifies the Wildbook instance (e.g., flukebook, mantamatcher)
- `HOSTNAME`: VM or container host identifier
- `ENVIRONMENT`: Deployment environment (development, staging, production)
- `LOKI_URL`: Central Loki endpoint for log aggregation

### 4. Loki Configuration

**Configuration File**: `devops/shared/.dockerfiles/loki/loki-config.yaml`

#### Key Considerations

- **Storage**: Uses TSDB index and filesystem storage (suitable for single-node deployments)
- **Retention**: 31-day retention period by default (configurable)
- **Performance Limits**: Configured for moderate load (10MB/s ingestion rate)
- **Caching**: Embedded cache for improved query performance
- **Compaction**: Automatic compaction to optimize storage

#### Scaling Considerations

For production deployments:
- Small (< 50 GB/day): 2 CPU, 4 GB RAM, 100 GB SSD
- Medium (50-200 GB/day): 4 CPU, 8 GB RAM, 500 GB SSD
- Large (> 200 GB/day): Consider distributed deployment with object storage

### 5. Grafana Configuration

**Configuration Files**:
- Data sources: `devops/shared/.dockerfiles/grafana/provisioning/datasources/datasources.yml`
- Dashboard provisioning: `devops/shared/.dockerfiles/grafana/provisioning/dashboards/dashboards.yml`
- Dashboard JSON files: `devops/shared/.dockerfiles/grafana/provisioning/dashboards/json/*.json`

#### Pre-configured Dashboards

1. **Wildbook Logs Dashboard** (`wildbook-logs.json`)
    - Service health overview
    - Error rate trends
    - Log volume by service

2. **User Activity Dashboard** (`user-activity.json`)
    - Login success/failure rates
    - Active users over time
    - User action tracking

## Application Integration

### Java Code Examples

#### Using ThreadContext for Structured Logging

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

public class UserAuthenticationService {
    private static final Logger logger = LogManager.getLogger(UserAuthenticationService.class);
    
    public void authenticateUser(String username, String password, String clientIp) {
        long startTime = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();
        
        try {
            // Add context that will be included in all logs within this thread
            ThreadContext.put("request_id", requestId);
            ThreadContext.put("endpoint", "/api/login");
            ThreadContext.put("http_method", "POST");
            ThreadContext.put("client_ip", clientIp);
            ThreadContext.put("username", username);
            
            logger.info("Authentication attempt started");
            
            // Perform authentication
            boolean success = performAuthentication(username, password);
            
            if (success) {
                String sessionId = createSession(username);
                ThreadContext.put("action", "login_success");
                ThreadContext.put("user_id", username);
                ThreadContext.put("session_id", sessionId);
                ThreadContext.put("duration_ms", String.valueOf(System.currentTimeMillis() - startTime));
                logger.info("User authenticated successfully");
            } else {
                ThreadContext.put("action", "login_failed");
                ThreadContext.put("error_type", "invalid_credentials");
                ThreadContext.put("duration_ms", String.valueOf(System.currentTimeMillis() - startTime));
                logger.warn("Authentication failed for user");
            }
            
        } catch (Exception ex) {
            ThreadContext.put("action", "login_error");
            ThreadContext.put("error_type", ex.getClass().getSimpleName());
            ThreadContext.put("error_message", ex.getMessage());
            logger.error("Authentication error", ex);
        } finally {
            // Always clear ThreadContext to prevent memory leaks
            ThreadContext.clearAll();
        }
    }
}
```

#### Encounter Processing with Logging

```java
public class EncounterProcessor {
    private static final Logger logger = LogManager.getLogger(EncounterProcessor.class);
    
    public void processEncounter(Encounter encounter) {
        ThreadContext.put("encounter_id", encounter.getId());
        ThreadContext.put("species", encounter.getSpecies());
        ThreadContext.put("location", encounter.getLocation());
        
        try {
            logger.info("Starting encounter processing");
            
            // Image processing
            long imageStartTime = System.currentTimeMillis();
            processImages(encounter);
            ThreadContext.put("image_processing_ms", 
                String.valueOf(System.currentTimeMillis() - imageStartTime));
            logger.info("Image processing completed");
            
            // Pattern matching
            long matchStartTime = System.currentTimeMillis();
            List<Match> matches = findMatches(encounter);
            ThreadContext.put("matches_found", String.valueOf(matches.size()));
            ThreadContext.put("matching_ms", 
                String.valueOf(System.currentTimeMillis() - matchStartTime));
            logger.info("Pattern matching completed");
            
            // Save to database
            saveEncounter(encounter);
            logger.info("Encounter saved successfully");
            
        } catch (Exception ex) {
            logger.error("Encounter processing failed", ex);
            throw ex;
        } finally {
            ThreadContext.clearAll();
        }
    }
}
```

## Deployment

### Production Deployment Steps

1. **Deploy Central Monitoring Server**
   ```bash
   # On central monitoring server
   cd /opt/wildbook/devops/shared
   docker-compose -f docker-compose.observability.yml up -d
   ```

2. **Deploy Alloy on Each VM**
   ```bash
   # On each domain VM
   cd /opt/wildbook/devops/deploy
   docker-compose up -d alloy
   ```

3. **Configure Environment Variables**
   ```bash
   # Copy template and edit
   cp _env.template .env
   # Edit .env with domain-specific values
   ```

4. **Update Application Containers**
   ```bash
   # Add labels and environment variables to existing containers
   docker-compose up -d
   ```

### Development Setup

For local development, run both stacks on the same machine:

```bash
# Start observability stack
cd devops/shared
docker-compose -f docker-compose.observability.yml up -d

# Start application with Alloy
cd ../deploy
docker-compose up -d
```

## Querying and Visualization

### LogQL Query Examples

```logql
# All Wildbook errors
{service="wildbook", level="ERROR"}

# Login failures in the last hour
{service="wildbook"} 
  | json 
  | action="login_failed" 
  | __error__=""

# Slow API requests (>1000ms)
{service="wildbook"} 
  | json 
  | duration_ms > 1000

# Encounter processing by species
{service="wildbook"} 
  | json 
  | logger="EncounterProcessor" 
  | species="Megaptera novaeangliae"

# Error rate by service
sum by (service) (
  rate({level="ERROR"}[5m])
)

# Top users by activity
topk(10,
  sum by (username) (
    count_over_time({service="wildbook"} | json | username != "" [1h])
  )
)
```

### Grafana Dashboard Configuration

Create dashboards with the following panels:

1. **Service Health Overview**
    - Log volume by service
    - Error rate trends
    - Response time percentiles

2. **User Activity Dashboard**
    - Login success/failure rates
    - Active users over time
    - Geographic distribution of users

3. **Encounter Processing Dashboard**
    - Processing times by stage
    - Species distribution
    - Match success rates

4. **Infrastructure Dashboard**
    - Container resource usage
    - Network traffic
    - Database query performance

## Monitoring and Alerting

### Alert Rules

Configure alerts in Loki for critical issues:

```yaml
groups:
  - name: wildbook_alerts
    interval: 1m
    rules:
      - alert: HighErrorRate
        expr: |
          sum by (domain, service) (
            rate({level="ERROR"}[5m])
          ) > 0.1
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "High error rate in {{ $labels.domain }}/{{ $labels.service }}"
          
      - alert: LoginFailureSpike
        expr: |
          sum(
            rate({service="wildbook"} | json | action="login_failed" [5m])
          ) > 10
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Spike in login failures detected"
          
      - alert: SlowProcessing
        expr: |
          histogram_quantile(0.95,
            sum by (le) (
              rate({service="wildbook"} | json | duration_ms != "" [5m])
            )
          ) > 5000
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "95th percentile response time exceeds 5 seconds"
```

## Troubleshooting

### Common Issues and Solutions

1. **Logs not appearing in Loki**
    - Verify Alloy is running: `docker logs alloy`
    - Check network connectivity: `docker exec alloy wget -O- http://loki:3100/ready`
    - Ensure containers have `logging=alloy` label

2. **JSON parsing errors**
    - Verify Log4j2 configuration is producing valid JSON
    - Check with: `docker logs wildbook | jq`
    - Review Alloy logs for parsing errors

3. **High memory usage**
    - Adjust Loki retention period
    - Implement log sampling for high-volume services
    - Configure appropriate cache sizes

4. **Missing labels in queries**
    - Verify Docker labels are properly set
    - Check Alloy label extraction configuration
    - Ensure JSON fields are being extracted correctly

### Performance Optimization

1. **Reduce Log Volume**
   ```yaml
   # Sample 10% of successful requests
   stage.match {
     selector = "{service=\"nginx\", status=\"200\"}"
     stage.drop {
       expression = "__meta_random > 0.1"
     }
   }
   ```

2. **Optimize Queries**
    - Use label selectors before line filters
    - Limit time ranges appropriately
    - Use recording rules for frequently-used queries

3. **Resource Allocation**
    - Loki: 4GB RAM minimum, 8GB recommended
    - Alloy: 256MB RAM per instance
    - Grafana: 2GB RAM minimum

## Future Enhancements

### Distributed Tracing Integration

To add application tracing capabilities:

1. **Add Tempo** for trace storage
2. **Instrument Java code** with OpenTelemetry
3. **Configure Alloy** to collect and forward traces
4. **Correlate logs and traces** in Grafana

### Metrics Collection

Extend the observability stack with:

1. **Prometheus** for metrics storage
2. **JMX exporter** for JVM metrics
3. **Node exporter** for system metrics
4. **Custom application metrics** via Micrometer

### Advanced Analytics

1. **Machine learning** for anomaly detection
2. **Automated root cause analysis**
3. **Predictive alerting** based on trends
4. **Cost optimization** recommendations

## Maintenance and Operations

### Backup Strategy

1. **Loki Data**: Daily snapshots of `/loki` directory
2. **Grafana Dashboards**: Export as JSON and version control
3. **Configuration Files**: Store in Git repository

### Upgrade Process

1. Test upgrades in development environment
2. Backup current data and configurations
3. Perform rolling updates starting with monitoring infrastructure
4. Update application containers last
5. Verify all logs are flowing correctly

### Capacity Planning

Monitor these metrics for capacity planning:

- Log ingestion rate (MB/second)
- Storage growth rate (GB/day)
- Query performance (p99 latency)
- Label cardinality growth

## Security Considerations

1. **Network Segmentation**: Use separate networks for application and monitoring traffic
2. **Authentication**: Enable basic auth or OAuth for Grafana and Loki endpoints
3. **Encryption**: Use TLS for all external communications
4. **Access Control**: Implement RBAC for Grafana users
5. **Log Sanitization**: Mask sensitive data (passwords, tokens) before logging

## Conclusion

This logging and observability infrastructure provides comprehensive visibility into the Wildbook platform's operation across multiple domains and services. The combination of structured logging, efficient collection with Alloy, scalable storage with Loki, and powerful visualization with Grafana creates a robust solution for monitoring, debugging, and optimizing the platform.

The architecture is designed to scale horizontally as the number of Wildbook instances grows, while maintaining low operational overhead and cost-effectiveness through the use of open-source technologies.