#!/bin/bash
set -e

echo "🚀 Starting DocSense with all services..."
echo ""

docker-compose up --build -d

echo ""
echo "⏳ Waiting for services to stabilize (60 seconds)..."
echo "   • Ollama initializing..."
echo "   • ChromaDB initializing..."
echo "   • DocSense starting..."
echo ""

# Give services time to start
sleep 60

echo ""
echo "✅ All services started!"
echo ""
echo "📋 Service status:"
docker-compose ps
echo ""
echo "🔗 Access points:"
echo "  • DocSense Web UI:     http://localhost:8085"
echo "  • Swagger API Docs:    http://localhost:8085/swagger-ui.html"
echo "  • ChromaDB:            http://localhost:8000"
echo "  • Ollama:              http://localhost:11434"
echo ""
echo "📊 View logs:"
echo "  docker-compose logs -f"
echo ""
echo "🛑 Stop services:"
echo "  docker-compose down"
echo ""
echo "⏱️  Pulling Ollama models (this may take several minutes)..."
echo "   • Pulling llama3.2..."
docker-compose exec -T ollama ollama pull llama3.2
echo "   • Pulling nomic-embed-text..."
docker-compose exec -T ollama ollama pull nomic-embed-text
echo ""
echo "✅ Setup complete! DocSense is ready."
echo "   Start asking questions at http://localhost:8085"
