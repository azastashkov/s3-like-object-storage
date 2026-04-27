CREATE TABLE placements (
  object_id     UUID PRIMARY KEY,
  primary_node  VARCHAR(64) NOT NULL,
  replica_nodes TEXT[]      NOT NULL,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  state         VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
                  CHECK (state IN ('ACTIVE','TOMBSTONED','RECLAIMED'))
);

CREATE INDEX idx_placement_state_created ON placements (state, created_at);
