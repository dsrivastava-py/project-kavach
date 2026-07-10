.PHONY: up down seed migrate test report loadtest logs shell

# Start all services (builds if needed)
up:
	docker-compose up -d --build

# Stop and remove containers
down:
	docker-compose down

# Run database migrations
migrate:
	docker-compose exec api alembic upgrade head

# Seed scam corpus + demo data (run after migrate)
seed:
	docker-compose exec api python scripts/seed_corpus.py
	docker-compose exec api python scripts/seed_demo_data.py

# Run test suite
test:
	docker-compose exec api pytest app/tests/ -v

# Generate precision/recall/latency report
report:
	docker-compose exec api python scripts/report_metrics.py

# Load test (requires server running via `make up`)
loadtest:
	locust -f scripts/loadtest.py --headless -u 50 -r 5 --run-time 60s --host http://localhost:8000

# Tail logs for all services
logs:
	docker-compose logs -f

# Open shell inside API container
shell:
	docker-compose exec api bash
