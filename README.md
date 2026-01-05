# 🍽️ Foreigner Table (Global Queueing System)

> **"Wait Less, Experience More."**
> 외국인 관광객을 위한 위치 기반 스마트 식당 대기열 및 AI 대기 시간 예측 서비스

## 1. 📖 프로젝트 개요 (Project Overview)

**Foreigner Table**은 한국을 방문한 외국인 관광객들이 현지 전화번호(KakaoTalk) 없이도 편리하게 맛집 줄서기를 할 수 있는 글로벌 대기열 플랫폼입니다.
단순한 줄서기를 넘어, **PostGIS**를 활용한 정교한 위치 기반 검색과 **AI 모델**을 통한 정확한 대기 시간 예측 정보를 제공합니다.

### 🎯 핵심 목표 (Key Objectives)
* **Global Accessibility:** 한국 전화번호가 없는 외국인을 위해 **Google OAuth** 및 **Email/SSE(Server-Sent Events) 알림** 시스템 구축.
* **High Performance:** **Redis**를 활용한 대용량 트래픽 대기열 처리 및 동시성 제어.
* **Advanced Data Strategy:** **PostgreSQL(PostGIS, JSONB)**를 활용하여 위치 데이터 쿼리 성능을 최적화하고 스키마 유연성 확보.
* **AI Integration:** **Python(FastAPI)** 기반의 AI 모델 서빙을 통해 대기열 상황에 따른 실시간 대기 시간 예측.

---

## 2. 🛠 기술 스택 (Tech Stack)

| Category | Technology | Usage |
| :--- | :--- | :--- |
| **Language** | Java 17+, Python 3.9 | Core Logic, AI Model Serving |
| **Framework** | Spring Boot 3.x, FastAPI | Web Application, AI Serving |
| **Database** | **PostgreSQL 16** | **PostGIS**(위치 검색), **JSONB**(메뉴/운영시간) 활용 |
| **Cache / Queue** | **Redis**, **RabbitMQ** | 대기열(Sorted Set), 이벤트 기반 분산 처리 |
| **Infrastructure** | Docker Compose, GitHub Actions | Container Orchestration, CI/CD |
| **Architecture** | **Hexagonal Architecture** | 도메인 중심의 유연한 아키텍처 (Ports & Adapters) |
