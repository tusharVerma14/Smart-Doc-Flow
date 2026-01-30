#!/bin/bash

echo "=================================="
echo "SmartDocFlow - Complete Test Suite"
echo "=================================="

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Test counter
PASSED=0
FAILED=0

test_service() {
    local service=$1
    local url=$2
    echo -n "Testing $service... "
    
    response=$(curl -s -o /dev/null -w "%{http_code}" $url)
    
    if [ "$response" == "200" ]; then
        echo -e "${GREEN}✓ PASSED${NC}"
        ((PASSED++))
    else
        echo -e "${RED}✗ FAILED (HTTP $response)${NC}"
        ((FAILED++))
    fi
}

echo ""
echo "[1/6] Testing Infrastructure..."
echo "=================================="

# Wait for services to start
echo "Waiting for services to be ready..."
sleep 10

echo ""
echo "[2/6] Testing Service Health Checks..."
echo "=================================="
test_service "Config Service" "http://localhost:8083/api/config/health"
test_service "AI Service" "http://localhost:8082/api/ai/health"
test_service "API Service" "http://localhost:8080/api/documents/health"

echo ""
echo "[3/6] Creating Test Rule..."
echo "=================================="
RULE_RESPONSE=$(curl -s -X POST http://localhost:8083/api/config/rules \
  -H "Content-Type: application/json" \
  -d '{
    "ruleName": "Auto-Approve Small Invoices",
    "documentType": "INVOICE",
    "priority": 1,
    "active": true,
    "conditions": [
      {"field": "totalAmount", "operator": "<", "value": 1000}
    ],
    "conditionLogic": "AND",
    "decision": "AUTO_APPROVED",
    "actions": {}
  }')

if echo "$RULE_RESPONSE" | grep -q "ruleName"; then
    echo -e "${GREEN}✓ Rule created successfully${NC}"
    ((PASSED++))
else
    echo -e "${RED}✗ Failed to create rule${NC}"
    ((FAILED++))
fi

echo ""
echo "[4/6] Uploading Test Document..."
echo "=================================="

# Create test invoice
cat > /tmp/test-invoice.txt << 'EOF'
INVOICE #INV-001

Date: 01/20/2024

Company: Test Corp
Customer: John Doe

Item: Widget A
Quantity: 5
Unit Price: $50.00

Total: $250.00

Thank you!
EOF

UPLOAD_RESPONSE=$(curl -s -X POST http://localhost:8080/api/documents/upload \
  -F "file=@/tmp/test-invoice.txt" \
  -F "userId=testuser" \
  -F "documentType=INVOICE")

JOB_ID=$(echo $UPLOAD_RESPONSE | grep -o '"jobId":"[^"]*' | cut -d'"' -f4)

if [ -n "$JOB_ID" ]; then
    echo -e "${GREEN}✓ Document uploaded successfully${NC}"
    echo "  Job ID: $JOB_ID"
    ((PASSED++))
else
    echo -e "${RED}✗ Failed to upload document${NC}"
    ((FAILED++))
fi

echo ""
echo "[5/6] Waiting for Processing..."
echo "=================================="
sleep 15

echo ""
echo "[6/6] Checking Job Status..."
echo "=================================="

if [ -n "$JOB_ID" ]; then
    STATUS_RESPONSE=$(curl -s "http://localhost:8080/api/documents/jobs/$JOB_ID")
    
    if echo "$STATUS_RESPONSE" | grep -q "COMPLETED"; then
        echo -e "${GREEN}✓ Job completed successfully${NC}"
        ((PASSED++))
        
        if echo "$STATUS_RESPONSE" | grep -q "AUTO_APPROVED"; then
            echo -e "${GREEN}✓ Decision: AUTO_APPROVED (as expected)${NC}"
            ((PASSED++))
        else
            echo -e "${RED}✗ Unexpected decision${NC}"
            ((FAILED++))
        fi
    else
        echo -e "${RED}✗ Job not completed${NC}"
        echo "Status: $STATUS_RESPONSE"
        ((FAILED++))
    fi
fi

echo ""
echo "=================================="
echo "Test Results"
echo "=================================="
echo -e "${GREEN}Passed: $PASSED${NC}"
echo -e "${RED}Failed: $FAILED${NC}"

if [ $FAILED -eq 0 ]; then
    echo -e "\n${GREEN}🎉 ALL TESTS PASSED!${NC}"
    exit 0
else
    echo -e "\n${RED}❌ SOME TESTS FAILED${NC}"
    exit 1
fi
