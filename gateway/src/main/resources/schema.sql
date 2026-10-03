CREATE TABLE IF NOT EXISTS inference_requests (
  id BIGSERIAL PRIMARY KEY,
  request_id VARCHAR(36) NOT NULL UNIQUE,
  model VARCHAR(255) NOT NULL,
  worker_id VARCHAR(255),
  prompt_hash VARCHAR(64) NOT NULL,
  latency_ms BIGINT NOT NULL,
  token_count INTEGER NOT NULL,
  cached BOOLEAN NOT NULL,
  success BOOLEAN NOT NULL,
  error_type VARCHAR(255),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_inference_requests_created_at ON inference_requests(created_at);
