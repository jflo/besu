#!/bin/bash
# Test script to demonstrate async RPC timeout behavior
# This script tests that long-running RPC methods are interrupted after the configured timeout

set -e

BESU_RPC_URL="${BESU_RPC_URL:-http://localhost:8545}"
TIMEOUT_SEC="${TIMEOUT_SEC:-30}"

echo "=========================================="
echo "Testing Async RPC Timeout Behavior"
echo "=========================================="
echo "RPC URL: $BESU_RPC_URL"
echo "Expected timeout: ~$TIMEOUT_SEC seconds (90% of HTTP timeout)"
echo ""

# Test 1: Find a transaction to trace
echo "Step 1: Getting latest block number..."
LATEST_BLOCK=$(curl -s -X POST -H "Content-Type: application/json" \
  --data '{"jsonrpc":"2.0","method":"eth_blockNumber","params":[],"id":1}' \
  $BESU_RPC_URL | jq -r '.result')

echo "Latest block: $LATEST_BLOCK"
BLOCK_NUM=$((16#${LATEST_BLOCK#0x}))
echo "Block number (decimal): $BLOCK_NUM"
echo ""

# Test 2: Find a block with transactions
echo "Step 2: Looking for a block with transactions to trace..."
for i in $(seq 1 100); do
  TEST_BLOCK=$((BLOCK_NUM - i))
  BLOCK_HEX=$(printf "0x%x" $TEST_BLOCK)
  
  TX_COUNT=$(curl -s -X POST -H "Content-Type: application/json" \
    --data "{\"jsonrpc\":\"2.0\",\"method\":\"eth_getBlockTransactionCountByNumber\",\"params\":[\"$BLOCK_HEX\"],\"id\":1}" \
    $BESU_RPC_URL | jq -r '.result')
  
  if [ "$TX_COUNT" != "0x0" ] && [ "$TX_COUNT" != "null" ]; then
    echo "Found block $BLOCK_HEX with $TX_COUNT transactions"
    TARGET_BLOCK=$BLOCK_HEX
    break
  fi
done

if [ -z "$TARGET_BLOCK" ]; then
  echo "No blocks with transactions found in last 100 blocks. Using latest block anyway."
  TARGET_BLOCK=$LATEST_BLOCK
fi

echo ""
echo "Step 3: Testing debug_traceBlockByNumber with full tracing (this should take a while)..."
echo "Target block: $TARGET_BLOCK"
echo "Start time: $(date '+%Y-%m-%d %H:%M:%S')"
START_TIME=$(date +%s)

# Monitor CPU usage in background
echo ""
echo "Monitoring CPU usage (press Ctrl+C after test completes)..."
echo "Time(s) | CPU% | Working Threads"
echo "--------------------------------"

(
  while true; do
    ELAPSED=$(($(date +%s) - START_TIME))
    CPU=$(ps aux | grep "[j]ava.*besu" | awk '{print $3}' | head -1)
    THREADS=$(ps -M $(pgrep -f "java.*besu" | head -1) 2>/dev/null | grep -c "vert.x-worker" || echo "0")
    echo "$ELAPSED | ${CPU:-0.0} | $THREADS"
    sleep 1
  done
) &
MONITOR_PID=$!

# Make the RPC call with full trace options (most expensive)
RESPONSE=$(curl -s -w "\nHTTP_STATUS:%{http_code}\nTIME_TOTAL:%{time_total}" \
  -X POST -H "Content-Type: application/json" \
  --data "{
    \"jsonrpc\":\"2.0\",
    \"method\":\"debug_traceBlockByNumber\",
    \"params\":[\"$TARGET_BLOCK\", {
      \"tracer\":\"callTracer\",
      \"tracerConfig\":{\"withLog\":true}
    }],
    \"id\":1
  }" \
  $BESU_RPC_URL)

# Stop monitoring
kill $MONITOR_PID 2>/dev/null || true

END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

echo ""
echo "=================================="
echo "Results"
echo "=================================="
echo "End time: $(date '+%Y-%m-%d %H:%M:%S')"
echo "Total duration: ${DURATION}s"
echo ""

# Parse response
HTTP_STATUS=$(echo "$RESPONSE" | grep "HTTP_STATUS:" | cut -d: -f2)
TIME_TOTAL=$(echo "$RESPONSE" | grep "TIME_TOTAL:" | cut -d: -f2)
JSON_RESPONSE=$(echo "$RESPONSE" | grep -v "HTTP_STATUS:" | grep -v "TIME_TOTAL:")

echo "HTTP Status: $HTTP_STATUS"
echo "Curl time: ${TIME_TOTAL}s"
echo ""

# Check if we got a timeout error
if echo "$JSON_RESPONSE" | jq -e '.error.code == -32002' > /dev/null 2>&1; then
  echo "✓ TIMEOUT ERROR RECEIVED (as expected)"
  echo "Error message: $(echo "$JSON_RESPONSE" | jq -r '.error.message')"
  echo ""
  echo "This demonstrates that:"
  echo "1. The RPC method was interrupted after ~${TIMEOUT_SEC}s"
  echo "2. The HTTP connection returned a proper error response"
  echo "3. CPU usage should have dropped after timeout (check logs above)"
elif echo "$JSON_RESPONSE" | jq -e '.result' > /dev/null 2>&1; then
  echo "✓ SUCCESS - Request completed before timeout"
  echo "Result size: $(echo "$JSON_RESPONSE" | jq -r '.result' | wc -c) bytes"
else
  echo "✗ UNEXPECTED RESPONSE"
  echo "$JSON_RESPONSE" | jq '.' || echo "$JSON_RESPONSE"
fi

echo ""
echo "To verify thread was actually interrupted, check Besu logs for:"
echo "  'JSON-RPC method debug_traceBlockByNumber timed out after <time> ms'"
