--
-- The contents of this file are subject to the license and copyright
-- detailed in the LICENSE and NOTICE files at the root of the source
-- tree and available online at
--
-- http://www.dspace.org/license/
--

CREATE SEQUENCE dark_seq;

CREATE TABLE dark
(
  dark_id          INTEGER PRIMARY KEY,
  ark              VARCHAR(256) UNIQUE,
  dspace_object    UUID UNIQUE REFERENCES dspaceobject(uuid),
  resource_type_id INTEGER,
  status           INTEGER,
  client_item_id   VARCHAR(128),
  target           VARCHAR(1024),
  metadata_cid     VARCHAR(128),
  level1_cid       VARCHAR(128),
  level2_cid       VARCHAR(128)
);

CREATE INDEX dark_ark_idx ON dark(ark);
CREATE INDEX dark_dspace_object_idx ON dark(dspace_object);
