# PostGIS + pgvector 공존 이미지
# - store 도메인은 PostGIS(geometry)를 사용하므로 PostGIS 유지 필수
# - RAG(#42)는 pgvector 확장으로 임베딩 벡터를 저장/검색
# 베이스(postgis/postgis)는 공식 postgres(Debian) 이미지 기반이라 PGDG 저장소가 이미 설정되어 있어
# pgvector를 apt 패키지로 간단히 설치할 수 있다.
FROM postgis/postgis:15-3.3

RUN apt-get update \
    && apt-get install -y --no-install-recommends postgresql-15-pgvector \
    && rm -rf /var/lib/apt/lists/*
