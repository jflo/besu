# Testing Async RPC Timeout

This guide explains how to test that the async RPC timeout actually interrupts long-running methods and stops consuming resources.

## Prerequisites

1. Running Besu node with the async RPC implementation
2. `curl` and `jq` installed
3. A synced chain with some transaction history

## Configuration

The timeout is set to **90% of `--rpc-http-timeout`**:
- Default `--rpc-http-timeout`: 30 seconds
- Default RPC method timeout: 27 seconds (30 * 0.9)

To test with a shorter timeout, start Besu with:
```bash
besu --rpc-http-timeout=5  # RPC methods timeout after 4.5 seconds
```

## Simple Test: Trace a Complex Block

### 1. Find a block with transactions

```bash
# Get latest block
curl -X POST -H "Content-Type: application/json" \
  --data '{"jsonrpc":"2.0","method":"eth_blockNumber","params":[],"id":1}' \
  http://localhost:8545 | jq

# Get block with transactions (adjust block number as needed)
curl -X POST -H "Content-Type: application/json" \
  --data '{"jsonrpc":"2.0","method":"eth_getBlockByNumber","params":["0x1000000",true],"id":1}' \
  http://localhost:8545 | jq '.result.transactions | length'
```

### 2. Trigger a slow trace operation

Open **two terminal windows**:

**Terminal 1 - Monitor Besu CPU usage:**
```bash
# This will show CPU usage every second
watch -n 1 "ps aux | grep '[j]ava.*besu' | awk '{print \$3 \"% CPU\"}'"
```

**Terminal 2 - Make the RPC call:**
```bash
# Record start time
echo "Start: $(date '+%H:%M:%S')"

# Make a complex trace call
time curl -X POST -H "Content-Type: application/json" \
  --data '{
    "jsonrpc":"2.0",
    "method":"debug_traceBlockByNumber",
    "params":["0x1000000", {"tracer":"callTracer"}],
    "id":1
  }' \
  http://localhost:8545 | jq

# Record end time
echo "End: $(date '+%H:%M:%S')"
```

### 3. Observe the behavior

**What to look for:**

1. **In Terminal 1 (CPU monitor):**
   - CPU usage spikes when tracing starts
   - CPU usage **drops** after ~27 seconds (or your configured timeout * 0.9)
   - This proves the worker thread stopped processing

2. **In Terminal 2 (curl response):**
   - Request returns in ~27-30 seconds with timeout error:
   ```json
   {
     "jsonrpc": "2.0",
     "id": 1,
     "error": {
       "code": -32002,
       "message": "Timeout error"
     }
   }
   ```

3. **In Besu logs:**
   - Look for a log message like:
   ```
   WARN  o.h.b.e.a.j.e.AsyncJsonRpcProcessor - JSON-RPC method debug_traceBlockByNumber timed out after 27000 ms for request 1
   ```

## Advanced Test: Verify Thread Cleanup

To prove the thread is actually interrupted and not just ignored:

### 1. Get thread count before request
```bash
# Count worker threads
ps -M $(pgrep -f "java.*besu") | grep -c "vert.x-worker"
```

### 2. Make slow request in background
```bash
curl -X POST -H "Content-Type: application/json" \
  --data '{"jsonrpc":"2.0","method":"debug_traceBlockByNumber","params":["0x1000000",{}],"id":1}' \
  http://localhost:8545 > /tmp/response.json &
CURL_PID=$!
```

### 3. Monitor while request is running
```bash
# Watch for ~30 seconds
for i in {1..35}; do
  echo "Second $i:"
  ps -M $(pgrep -f "java.*besu") | grep "vert.x-worker" | head -3
  sleep 1
done
```

### 4. Check result
```bash
wait $CURL_PID
cat /tmp/response.json | jq
```

**Expected behavior:**
- Worker thread is busy for ~27 seconds
- Thread becomes idle after timeout
- Response contains timeout error
- Thread is returned to pool (not leaked)

## Automated Test Script

Run the provided test script:
```bash
chmod +x test-async-timeout.sh

# Test with default 30s timeout
./test-async-timeout.sh

# Test with custom 5s timeout (Besu must be started with --rpc-http-timeout=5)
TIMEOUT_SEC=4.5 ./test-async-timeout.sh
```

## What This Proves

1. **Timeout works**: Methods are interrupted after the configured time
2. **Resources released**: CPU usage drops when timeout occurs
3. **Thread stops**: Worker thread stops processing the request
4. **Proper error**: Client receives a proper JSON-RPC error response
5. **No resource leak**: Thread is returned to pool, not stuck forever

## Troubleshooting

**If timeout doesn't occur:**
- Check Besu is running with the async implementation
- Verify `--rpc-http-timeout` configuration
- Try a more complex block or transaction to trace
- Use a shorter timeout for testing: `--rpc-http-timeout=5`

**If you can't find a slow operation:**
- Use a block from a busy period (check etherscan for your network)
- Try `debug_traceTransaction` on a complex contract call
- Use full opcode tracing: `{"disableStorage":false,"disableMemory":false,"disableStack":false}`
