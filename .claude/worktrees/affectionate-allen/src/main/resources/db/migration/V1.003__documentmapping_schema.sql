create table document_mapping (
    document_id             uuid primary key not null,
    material_id             uuid not null,
    event_type              varchar(128) not null,
    created_at              timestamptz not null
);

create index idx_document_mapping_event_type on document_mapping(event_type);

