#!/bin/bash

echo "🛑 Stopping DocSense services..."
echo ""

# Stop all containers
docker-compose down

echo ""
echo "✅ All services stopped"
echo ""
echo "📊 To remove volumes (persistent data):"
echo "  docker-compose down -v"
echo ""
echo "🧹 To remove images:"
echo "  docker image rm docsense-docsense ollama/ollama chromadb/chroma"
