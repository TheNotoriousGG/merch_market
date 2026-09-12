.PHONY: up down status backend-check frontend-check e2e api-sync

up:
	docker compose -f infrastructure/compose.yaml up -d --build

down:
	docker compose -f infrastructure/compose.yaml down

status:
	docker compose -f infrastructure/compose.yaml ps --all

backend-check:
	cd backend && ./gradlew qualityGate --no-daemon

frontend-check:
	docker compose -f infrastructure/compose.yaml build frontend
	docker run --rm --user root amra/merch-market-frontend:local npm test
	docker run --rm --user root amra/merch-market-frontend:local npm run typecheck
	docker run --rm --user root amra/merch-market-frontend:local npm run lint

e2e:
	docker compose -f infrastructure/compose.yaml --profile test run --rm e2e

api-sync:
	bash frontend/scripts/sync-api-client.sh
