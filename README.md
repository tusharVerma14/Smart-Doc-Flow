# 🚀 SmartDocFlow

**SmartDocFlow** is a production-ready, queue-driven, microservices-based document processing system.
It supports **Invoices, Loan Applications, and KYC documents** with OCR, rule-based decisions, and full async processing.

---

## ✨ Key Highlights

* ✅ True microservices architecture (4 independent services)
* ✅ Queue-driven & asynchronous (Redis)
* ✅ OCR-powered extraction (Tesseract)
* ✅ Dynamic workflow rules (no code changes)
* ✅ Docker-first, one-command startup
* ✅ Production-ready (logging, persistence, scalability)

---

## 🧩 Microservices Overview

| Service            | Port | Responsibility                               |
| ------------------ | ---- | -------------------------------------------- |
| **API Service**    | 8080 | File upload, job tracking, Redis producer    |
| **Worker Service** | 8081 | Queue consumer, rule evaluation              |
| **AI Service**     | 8082 | OCR + data extraction (Invoice / Loan / KYC) |
| **Config Service** | 8083 | Workflow rules management                    |

**Infrastructure:** MySQL 8.0, Redis 7

---

## 📂 Project Structure

```text
smartdocflow/
├── docker-compose.yml
├── README.md
├── test.sh
│
├── api-service/
├── worker-service/
├── ai-service/
└── config-service/
```

---

## ▶️ Run Locally (3 Steps)

### 1️⃣ Prerequisites

* Docker Desktop

```bash
docker --version
docker compose version
```

---

### 2️⃣ Start the System

```bash
cd smartdocflow
docker compose up --build
```

⏱ First run may take ~10 minutes.

---

### 3️⃣ Run Tests

```bash
./test.sh
```

---

## ✅ Verify Services

```bash
docker compose ps
```

Health checks:

```bash
curl http://localhost:8080/api/documents/health
curl http://localhost:8082/api/ai/health
curl http://localhost:8083/api/config/health
```

---

## 🧪 End-to-End Flow

1. Upload document via **API Service**
2. Job queued in **Redis**
3. **Worker Service** processes job
4. OCR + extraction via **AI Service**
5. Rules fetched from **Config Service**
6. Final decision stored in **MySQL**

---

## 📤 Sample Upload

```bash
curl -X POST http://localhost:8080/api/documents/upload \
  -F "file=@invoice.txt" \
  -F "userId=testuser" \
  -F "documentType=INVOICE"
```

---

## 🗄️ Database Access

```bash
docker exec -it smartdocflow-mysql mysql -uroot -proot smartdocflow
```

---

## 📜 Logs

```bash
docker compose logs -f
```

---

## 🌐 Deployment

* Push to GitHub
* Deploy services independently (Railway / Render / ECS)
* Replace MySQL & Redis with managed services

---

## 🎯 Supported Use Cases

* Invoice processing & auto-approval
* Loan application screening
* KYC verification workflows
* Any document-driven workflow (configurable)

---

## 🧠 Architecture

```text
Client → API Service → Redis Queue → Worker Service
                          ↓
                    AI Service + Config Service
                          ↓
                       MySQL Storage
```

---

## 🏁 Status

✅ Fully functional
✅ End-to-end tested
✅ Ready for production deployment

---

**Built with Spring Boot, Redis, MySQL, Docker & OCR**
