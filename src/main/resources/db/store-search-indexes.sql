-- 식당 검색 인덱스
-- Hibernate(ddl-auto)는 GIST 표현식 인덱스나 GIN trigram 인덱스를 만들 수 없으므로,
-- 스키마 생성 후 spring.sql.init(defer-datasource-initialization)으로 실행한다. 여러 번 실행돼도 안전하도록 IF NOT EXISTS 사용.

-- LIKE '%키워드%' 부분 일치 검색용 trigram 확장
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 반경 검색: 쿼리의 s.location::geography 식과 정확히 일치해야 인덱스를 사용한다
CREATE INDEX IF NOT EXISTS idx_store_location_geog ON store USING GIST ((location::geography));

-- 이름 부분 일치 검색 (앞에 %가 붙어 B-tree로는 불가)
CREATE INDEX IF NOT EXISTS idx_store_name_trgm ON store USING GIN (name gin_trgm_ops);

-- 카테고리 필터
CREATE INDEX IF NOT EXISTS idx_store_category ON store (category);
