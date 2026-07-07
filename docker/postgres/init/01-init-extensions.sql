-- 컨테이너 최초 기동(빈 데이터 디렉터리) 시 1회 자동 실행된다.
-- 확장을 "설치"(Dockerfile)한 것과 별개로, DB에서 "활성화"(CREATE EXTENSION)해야 실제로 사용 가능하다.

-- store 도메인: 좌표(geometry) 저장/검색
CREATE EXTENSION IF NOT EXISTS postgis;

-- RAG(#42): 임베딩 벡터 저장/유사도 검색
CREATE EXTENSION IF NOT EXISTS vector;

-- PgVectorStore가 metadata/식별자 처리에 사용
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
