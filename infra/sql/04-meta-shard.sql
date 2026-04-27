CREATE TABLE object_versions (
  bucket_name      VARCHAR(63)   NOT NULL,
  object_key       VARCHAR(1024) NOT NULL,
  version_id       VARCHAR(26)   NOT NULL,
  object_id        UUID,
  is_delete_marker BOOLEAN       NOT NULL DEFAULT FALSE,
  size_bytes       BIGINT        NOT NULL DEFAULT 0,
  content_type     VARCHAR(255),
  etag             VARCHAR(64),
  user_metadata    TEXT,
  created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
  PRIMARY KEY (bucket_name, object_key, version_id)
);

CREATE INDEX idx_objver_current ON object_versions (bucket_name, object_key, version_id DESC);
CREATE INDEX idx_objver_prefix  ON object_versions (bucket_name, object_key text_pattern_ops);
