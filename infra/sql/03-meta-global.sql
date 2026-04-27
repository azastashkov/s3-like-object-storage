CREATE TABLE buckets (
  name               VARCHAR(63)  PRIMARY KEY,
  owner_principal    VARCHAR(128) NOT NULL,
  versioning_enabled BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE multipart_uploads (
  upload_id     UUID         PRIMARY KEY,
  bucket_name   VARCHAR(63)  NOT NULL REFERENCES buckets(name) ON DELETE CASCADE,
  object_key    VARCHAR(1024) NOT NULL,
  initiator     VARCHAR(128) NOT NULL,
  metadata_json TEXT,
  initiated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE multipart_parts (
  upload_id      UUID         NOT NULL REFERENCES multipart_uploads(upload_id) ON DELETE CASCADE,
  part_number    INT          NOT NULL,
  part_object_id UUID         NOT NULL,
  size_bytes     BIGINT       NOT NULL,
  etag           VARCHAR(64)  NOT NULL,
  uploaded_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  PRIMARY KEY (upload_id, part_number)
);
