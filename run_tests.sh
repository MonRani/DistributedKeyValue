#!/bin/bash

# Paxos Consensus Algorithm Test Runner
# This script compiles and runs the comprehensive test suite

echo "=========================================="
echo "Paxos Consensus Algorithm Test Runner"
echo "=========================================="

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "Error: Java is not installed or not in PATH"
    exit 1
fi

# Check if javac is installed
if ! command -v javac &> /dev/null; then
    echo "Error: Java compiler (javac) is not installed or not in PATH"
    exit 1
fi

echo "Java version: $(java -version 2>&1 | head -n 1)"
echo "Java compiler version: $(javac -version 2>&1)"
echo ""

# Create src directory if it doesn't exist
if [ ! -d "src" ]; then
    echo "Error: src directory not found"
    exit 1
fi

# Compile the project
echo "Compiling project..."
if javac -cp . src/*.java; then
    echo "✓ Compilation successful"
else
    echo "✗ Compilation failed"
    exit 1
fi

echo ""

# Run the test suite
echo "Running Paxos Consensus Algorithm Test Suite..."
echo "=============================================="
echo ""

if java -cp src PaxosConsensusTest; then
    echo ""
    echo "✓ Test suite completed successfully"
else
    echo ""
    echo "✗ Test suite failed"
    exit 1
fi

echo ""
echo "Test execution completed!" 