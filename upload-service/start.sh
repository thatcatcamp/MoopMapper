#!/bin/bash

echo "🗺️ Starting MoopMapper Upload Service..."
echo "Server will be available at: http://192.168.68.104:8000"
echo "API Documentation: http://192.168.68.104:8000/docs"
echo ""

# Create virtual environment if it doesn't exist
if [ ! -d "venv" ]; then
    echo "Creating virtual environment..."
    python3 -m venv venv
fi

# Activate virtual environment
source venv/bin/activate

# Install dependencies
echo "Installing dependencies..."
pip install -r requirements.txt

# Create necessary directories
mkdir -p uploads processed metadata static

echo ""
echo "Starting FastAPI server..."
python main.py