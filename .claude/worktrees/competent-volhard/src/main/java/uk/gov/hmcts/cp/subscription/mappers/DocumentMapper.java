package uk.gov.hmcts.cp.subscription.mappers;

import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import uk.gov.hmcts.cp.subscription.entities.DocumentMappingEntity;
import uk.gov.hmcts.cp.subscription.services.ClockService;

import java.util.UUID;

@Mapper(componentModel = "spring")
public interface DocumentMapper {

    @Mapping(target = "documentId", expression = "java(java.util.UUID.randomUUID())")
    @Mapping(source = "materialId", target = "materialId")
    @Mapping(source = "eventType", target = "eventType")
    @Mapping(target = "createdAt", expression = "java(clockService.nowOffsetUTC())")
    DocumentMappingEntity mapToNewEntity(@Context ClockService clockService, UUID materialId, String eventType);
}

