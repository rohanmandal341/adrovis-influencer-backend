# Influencer Core API (Spring Boot 3 + Java 21)

Production-ready backend for influencer campaign curation, creator discovery, and marketing automation.

## 🚀 Quick Run (Instant Local Dev - Zero Docker Build Wait)
With Java 21 installed on your Windows machine:
```powershell
# 1. Set your database password
$env:SUPABASE_DB_PASSWORD="your-db-password"

# 2. Run immediately with Spring Boot DevTools live reload
./mvnw spring-boot:run
```
That's it! Any changes you make to Java files or templates automatically hot-reload in under 1 second.

## 🐳 Docker Deployment
To run containerized:
```bash
docker compose up --build
```

## 🌐 Deploy to Render
1. Push this folder to a GitHub repository.
2. In Render Dashboard, click **New +** -> **Blueprint** or **Web Service**.
3. Select your repo. Render automatically detects `Dockerfile` and `render.yaml`.
4. Fill in the environment variables:
   - `DATABASE_URL`: `jdbc:postgresql://db.dhfstnonquoyfgnpswsw.supabase.co:5432/postgres?sslmode=require`
   - `DATABASE_USERNAME`: `postgres`
   - `DATABASE_PASSWORD`: `your-supabase-password`
   - `BACKEND_BASE_URL`: `https://your-service-name.onrender.com`
