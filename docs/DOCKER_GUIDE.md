# Hướng Dẫn Chạy Toàn Bộ Hệ Thống Bằng Docker (1-Click Run)

Tài liệu này hướng dẫn cách khởi động, kiểm tra và quản lý stack Backend Spring Boot và Database PostgreSQL (PostGIS + pgvector) thông qua Docker một cách đơn giản và thuận tiện nhất.

---

## 1. Yêu Cầu Tiên Quyết

- Máy đã cài đặt **Docker Desktop** (trên macOS đã có sẵn tại `/Applications/Docker.app`).
- Không cần cài đặt Java, Maven hay PostgreSQL trên máy host vì mọi thứ đã được đóng gói và chạy bên trong các Docker container.

---

## 2. Cách Khởi Chạy Nhanh Bằng 1 Lệnh Duy Nhất

Từ thư mục gốc dự án (`on-demand-monitoring-system`), bạn chỉ cần chạy:

### Trên macOS / Linux:
```bash
./run-docker.sh
```
*(Nếu Docker Desktop chưa bật, script sẽ tự động mở Docker Desktop và chờ daemon sẵn sàng trước khi khởi động hệ thống).*

### Trên Windows:
```cmd
run-docker.cmd
```

---

## 3. Các Lệnh Tiện Ích Khác Của Script `run-docker.sh`

| Lệnh | Ý nghĩa |
| :--- | :--- |
| `./run-docker.sh up` | Khởi động cả PostgreSQL và Backend Spring Boot ở chế độ nền (detached). |
| `./run-docker.sh stop` | Dừng toàn bộ các container và giải phóng tài nguyên. |
| `./run-docker.sh logs` | Xem trực tiếp log của Spring Boot Backend theo thời gian thực (nhấn `Ctrl + C` để thoát). |
| `./run-docker.sh status` | Xem trạng thái các container (`ondemand-app`, `ondemand-postgres`). |
| `./run-docker.sh rebuild` | Build lại Docker image khi bạn có thay đổi mã nguồn Java. |

---

## 4. Các Cổng Dịch Vụ Sau Khi Chạy Thành Công

Sau khi chạy lệnh `./run-docker.sh`, các cổng dịch vụ sẽ được expose ra máy host như sau:

- **Backend API Base URL**: [http://localhost:8080](http://localhost:8080)
- **Swagger UI (Interactive API Docs)**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI v3 JSON**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
- **PostgreSQL Database**: Port `5433` (được ánh xạ từ cổng nội bộ `5432`)
  - Database: `ondemand`
  - Username: `ondemand`
  - Password: `ondemand123`
  - Extensions kích hoạt: `postgis`, `vector`, `uuid-ossp`, `hstore`.

---

## 5. Cấu Trúc Đóng Gói Kỹ Thuật

- **Dockerfile (`ondemandmonitoring/Dockerfile`)**:
  - Multi-stage build độc lập:
    - Stage 1: Dùng base `eclipse-temurin:21-jdk` cùng Maven wrapper để build JAR và dọn dẹp file thừa.
    - Stage 2: Dùng base runtime siêu nhẹ `eclipse-temurin:21-jre` để chạy file `app.jar`.
- **Dockerfile.postgres (`ondemandmonitoring/Dockerfile.postgres`)**:
  - Base `postgis/postgis:16-3.4` với `platform: linux/amd64` (chạy tương thích tuyệt đối trên Mac Apple Silicon qua Rosetta).
  - Cài đặt `postgresql-16-pgvector` phục vụ vector search cho AI embeddings.
- **docker-compose.yml (`ondemandmonitoring/docker-compose.yml`)**:
  - Tự động nạp file `.env` vào container `ondemand-app`.
  - Kết nối mạng nội bộ giữa `app` và `postgres` qua `SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/ondemand`.
  - Cơ chế `healthcheck` trên `postgres` đảm bảo container DB sẵn sàng trước khi Spring Boot khởi động.
  - Mount volume dữ liệu database `postgres-data` và volume upload file `./uploads:/app/uploads`.
