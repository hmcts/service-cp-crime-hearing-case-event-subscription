-- Supports the daily document purge, which deletes by created_at in batches.
create index idx_document_mapping_created_at on document_mapping(created_at);
