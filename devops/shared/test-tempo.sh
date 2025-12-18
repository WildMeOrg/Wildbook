#!/bin/bash

# Test script to send sample traces to Tempo
# This script sends test traces using curl to verify the Tempo integration

echo "================================================"
echo "Tempo Integration Test Script"
echo "================================================"
echo ""

# Configuration
TEMPO_OTLP_HTTP="${TEMPO_OTLP_HTTP:-http://localhost:4318}"
TEMPO_API="${TEMPO_API:-http://localhost:3200}"
GRAFANA_URL="${GRAFANA_URL:-http://localhost:3000}"

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to check if service is running
check_service() {
    local service_name=$1
    local url=$2
    
    echo -n "Checking $service_name... "
    if curl -s -o /dev/null -w "%{http_code}" "$url" | grep -q "200\|404"; then
        echo -e "${GREEN}✓${NC} Available at $url"
        return 0
    else
        echo -e "${RED}✗${NC} Not available at $url"
        return 1
    fi
}

# Check services
echo "1. Checking Services Status"
echo "----------------------------"
check_service "Tempo API" "$TEMPO_API/ready"
check_service "Tempo OTLP HTTP" "$TEMPO_OTLP_HTTP/v1/traces"
check_service "Grafana" "$GRAFANA_URL/api/health"
echo ""

# Generate a trace ID
TRACE_ID=$(openssl rand -hex 16)
SPAN_ID=$(openssl rand -hex 8)
PARENT_SPAN_ID=$(openssl rand -hex 8)
TIMESTAMP=$(date +%s%N)

echo "2. Sending Test Trace"
echo "----------------------"
echo "Trace ID: $TRACE_ID"
echo "Span ID: $SPAN_ID"
echo ""

# Create OTLP JSON payload
read -r -d '' TRACE_DATA << EOF
{
  "resourceSpans": [
    {
      "resource": {
        "attributes": [
          {
            "key": "service.name",
            "value": { "stringValue": "wildbook-test" }
          },
          {
            "key": "service.version",
            "value": { "stringValue": "1.0.0" }
          },
          {
            "key": "deployment.environment",
            "value": { "stringValue": "development" }
          }
        ]
      },
      "scopeSpans": [
        {
          "scope": {
            "name": "test-tracer",
            "version": "1.0.0"
          },
          "spans": [
            {
              "traceId": "$TRACE_ID",
              "spanId": "$SPAN_ID",
              "parentSpanId": "$PARENT_SPAN_ID",
              "name": "test.operation",
              "kind": 1,
              "startTimeUnixNano": "$TIMESTAMP",
              "endTimeUnixNano": "$(($TIMESTAMP + 1000000000))",
              "attributes": [
                {
                  "key": "http.method",
                  "value": { "stringValue": "GET" }
                },
                {
                  "key": "http.url",
                  "value": { "stringValue": "/api/test" }
                },
                {
                  "key": "http.status_code",
                  "value": { "intValue": "200" }
                },
                {
                  "key": "user.id",
                  "value": { "stringValue": "test-user-123" }
                }
              ],
              "status": {
                "code": 1,
                "message": "OK"
              }
            }
          ]
        }
      ]
    }
  ]
}
EOF

# Send trace to Tempo via OTLP HTTP
echo "Sending trace to Tempo OTLP endpoint..."
RESPONSE=$(curl -X POST "$TEMPO_OTLP_HTTP/v1/traces" \
  -H "Content-Type: application/json" \
  -d "$TRACE_DATA" \
  -s -w "\nHTTP_CODE:%{http_code}")

HTTP_CODE=$(echo "$RESPONSE" | grep "HTTP_CODE:" | cut -d: -f2)

if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "202" ]; then
    echo -e "${GREEN}✓${NC} Trace sent successfully (HTTP $HTTP_CODE)"
else
    echo -e "${RED}✗${NC} Failed to send trace (HTTP $HTTP_CODE)"
    echo "Response: $RESPONSE"
fi
echo ""

# Wait a moment for trace to be processed
echo "Waiting 3 seconds for trace to be processed..."
sleep 3
echo ""

# Query Tempo for the trace
echo "3. Querying Trace from Tempo"
echo "----------------------------"
TRACE_URL="$TEMPO_API/api/traces/$TRACE_ID"
echo "Querying: $TRACE_URL"

TRACE_RESPONSE=$(curl -s -w "\nHTTP_CODE:%{http_code}" "$TRACE_URL")
HTTP_CODE=$(echo "$TRACE_RESPONSE" | grep "HTTP_CODE:" | cut -d: -f2)

if [ "$HTTP_CODE" = "200" ]; then
    echo -e "${GREEN}✓${NC} Trace found in Tempo!"
    echo ""
    echo "You can now view this trace in Grafana:"
    echo "  1. Open Grafana at $GRAFANA_URL"
    echo "  2. Go to Explore"
    echo "  3. Select the Tempo datasource"
    echo "  4. Search for trace ID: $TRACE_ID"
    echo "  5. Or use this direct link:"
    echo "     $GRAFANA_URL/explore?left=%5B%22now-1h%22,%22now%22,%22Tempo%22,%7B%22query%22:%22$TRACE_ID%22%7D%5D"
elif [ "$HTTP_CODE" = "404" ]; then
    echo -e "${YELLOW}⚠${NC} Trace not found yet (may still be processing)"
    echo "Try querying again in a few seconds"
else
    echo -e "${RED}✗${NC} Error querying trace (HTTP $HTTP_CODE)"
fi
echo ""

# Additional test: Send multiple related spans
echo "4. Sending Multi-Span Trace"
echo "---------------------------"
TRACE_ID_2=$(openssl rand -hex 16)
ROOT_SPAN=$(openssl rand -hex 8)
CHILD_SPAN_1=$(openssl rand -hex 8)
CHILD_SPAN_2=$(openssl rand -hex 8)

# Create a more complex trace with multiple spans
read -r -d '' MULTI_SPAN_TRACE << EOF
{
  "resourceSpans": [
    {
      "resource": {
        "attributes": [
          {
            "key": "service.name",
            "value": { "stringValue": "wildbook-api" }
          }
        ]
      },
      "scopeSpans": [
        {
          "spans": [
            {
              "traceId": "$TRACE_ID_2",
              "spanId": "$ROOT_SPAN",
              "name": "encounter.process",
              "startTimeUnixNano": "$TIMESTAMP",
              "endTimeUnixNano": "$(($TIMESTAMP + 3000000000))",
              "attributes": [
                {
                  "key": "encounter.id",
                  "value": { "stringValue": "ENC-2024-001" }
                }
              ]
            },
            {
              "traceId": "$TRACE_ID_2",
              "spanId": "$CHILD_SPAN_1",
              "parentSpanId": "$ROOT_SPAN",
              "name": "image.process",
              "startTimeUnixNano": "$(($TIMESTAMP + 500000000))",
              "endTimeUnixNano": "$(($TIMESTAMP + 1500000000))"
            },
            {
              "traceId": "$TRACE_ID_2",
              "spanId": "$CHILD_SPAN_2",
              "parentSpanId": "$ROOT_SPAN",
              "name": "pattern.match",
              "startTimeUnixNano": "$(($TIMESTAMP + 1600000000))",
              "endTimeUnixNano": "$(($TIMESTAMP + 2800000000))"
            }
          ]
        }
      ]
    }
  ]
}
EOF

curl -X POST "$TEMPO_OTLP_HTTP/v1/traces" \
  -H "Content-Type: application/json" \
  -d "$MULTI_SPAN_TRACE" \
  -s -o /dev/null

echo -e "${GREEN}✓${NC} Multi-span trace sent"
echo "Trace ID: $TRACE_ID_2"
echo ""

echo "================================================"
echo "Test Complete!"
echo "================================================"
echo ""
echo "Next Steps:"
echo "1. Open Grafana at $GRAFANA_URL"
echo "2. Navigate to Explore and select Tempo datasource"
echo "3. Search for one of these trace IDs:"
echo "   - $TRACE_ID (single span)"
echo "   - $TRACE_ID_2 (multi-span)"
echo ""
echo "To send continuous test traces, you can run:"
echo "  watch -n 5 $0"
