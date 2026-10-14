#!/usr/bin/env bash
# ==============================================================================
# Script khởi chạy hệ thống On-Demand Monitoring System bằng Docker Compose
# ==============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_DIR="${SCRIPT_DIR}/ondemandmonitoring"

ACTION="${1:-up}"

# Màu sắc thông báo
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}============================================================${NC}"
echo -e "${BLUE}  ON-DEMAND MONITORING SYSTEM - DOCKER RUNNER               ${NC}"
echo -e "${BLUE}============================================================${NC}"

# 1. Kiểm tra Docker daemon
if ! docker info >/dev/null 2>&1; then
    echo -e "${YELLOW}[!] Docker daemon chưa hoạt động.${NC}"
    if [[ "$OSTYPE" == "darwin"* ]]; then
        echo -e "${YELLOW}[*] Đang mở Docker Desktop trên macOS...${NC}"
        open -a Docker
        echo -e "${YELLOW}[*] Đang chờ Docker daemon khởi động...${NC}"
        while ! docker info >/dev/null 2>&1; do
            sleep 3
        done
        echo -e "${GREEN}[✓] Docker daemon đã sẵn sàng!${NC}"
    else
        echo -e "${RED}[✗] Vui lòng khởi động Docker daemon trước khi tiếp tục.${NC}"
        exit 1
    fi
fi

# 2. Xử lý các action
cd "${COMPOSE_DIR}"

case "$ACTION" in
    up|start)
        echo -e "${GREEN}[*] Đang khởi động toàn bộ stack (PostgreSQL + Spring Boot Backend)...${NC}"
        docker compose up -d
        echo -e "\n${GREEN}[✓] Khởi động thành công!${NC}"
        echo -e "${BLUE}------------------------------------------------------------${NC}"
        echo -e "  - Backend API:   ${GREEN}http://localhost:8080${NC}"
        echo -e "  - Swagger UI:    ${GREEN}http://localhost:8080/swagger-ui.html${NC}"
        echo -e "  - OpenAPI Docs:  ${GREEN}http://localhost:8080/v3/api-docs${NC}"
        echo -e "  - PostgreSQL:    ${GREEN}localhost:5433${NC} (Database: ondemand, User: ondemand)"
        echo -e "${BLUE}------------------------------------------------------------${NC}"
        echo -e "Xem logs backend: ${YELLOW}./run-docker.sh logs${NC}"
        echo -e "Dừng hệ thống:    ${YELLOW}./run-docker.sh stop${NC}"
        ;;
    down|stop)
        echo -e "${YELLOW}[*] Đang dừng hệ thống...${NC}"
        docker compose down
        echo -e "${GREEN}[✓] Đã dừng toàn bộ container.${NC}"
        ;;
    logs)
        echo -e "${BLUE}[*] Đang xem logs của container backend (Ctrl+C để thoát)...${NC}"
        docker compose logs -f app
        ;;
    rebuild)
        echo -e "${YELLOW}[*] Đang build lại các Docker image...${NC}"
        docker compose build
        echo -e "${GREEN}[*] Đang khởi động lại...${NC}"
        docker compose up -d
        echo -e "${GREEN}[✓] Rebuild và khởi động hoàn tất!${NC}"
        ;;
    status|ps)
        docker compose ps
        ;;
    *)
        echo -e "${RED}Lệnh không hợp lệ: $ACTION${NC}"
        echo -e "Cách sử dụng: ./run-docker.sh [up|stop|logs|rebuild|status]"
        exit 1
        ;;
esac
