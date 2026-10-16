package com.ondemandmonitoring.checklist.service.impl;

import com.ondemandmonitoring.checklist.domain.ChecklistDefinition;
import com.ondemandmonitoring.checklist.dto.request.ChecklistRequest;
import com.ondemandmonitoring.checklist.dto.response.ChecklistResponse;
import com.ondemandmonitoring.checklist.mapper.ChecklistMapper;
import com.ondemandmonitoring.checklist.repository.ChecklistDefinitionRepository;
import com.ondemandmonitoring.checklist.service.IChecklistService;
import com.ondemandmonitoring.common.api.PageResponse;
import com.ondemandmonitoring.common.exception.*;
import static com.ondemandmonitoring.checklist.util.ChecklistContentNormalizer.content;
import static com.ondemandmonitoring.checklist.util.ChecklistContentNormalizer.canonicalize;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChecklistServiceImpl implements IChecklistService {
    private static final Set<String> SORT_FIELDS = Set.of("id", "content", "isActive", "createdAt", "updatedAt");
    private final ChecklistDefinitionRepository repository;
    private final ChecklistMapper mapper;

    private com.ondemandmonitoring.pricing.service.ServiceDefaultPriceService defaultPrices;

    /** Optional collaborator: keeps the stored service default price current. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setDefaultPrices(com.ondemandmonitoring.pricing.service.ServiceDefaultPriceService defaultPrices) {
        this.defaultPrices = defaultPrices;
    }

    @Override
    @Transactional
    public ChecklistResponse create(ChecklistRequest request) {
        String content = content(request.getContent());
        String normalized = content.toLowerCase(Locale.ROOT);
        if (repository.existsByNormalizedContent(normalized)) {
            throw new ApiException(ErrorCode.CHECKLIST_ALREADY_EXISTS);
        }
        ChecklistDefinition entity = mapper.toEntity(request);
        entity.setContent(content);
        entity.setNormalizedContent(normalized);
        entity.setDifficulty(request.getDifficulty() == null
                ? com.ondemandmonitoring.pricing.enums.ChecklistDifficulty.BASIC : request.getDifficulty());
        applyPricing(entity, request, true);
        return mapper.toResponse(repository.saveAndFlush(entity));
    }

    @Override
    public ChecklistResponse getById(String id) {
        return mapper.toResponse(repository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.CHECKLIST_NOT_FOUND)));
    }

    @Override
    public PageResponse<ChecklistResponse> getAll(String search, Boolean active, Pageable pageable) {
        if (pageable.getPageSize() > 100) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Page size must not exceed 100");
        }
        pageable.getSort().forEach(order -> {
            if (!SORT_FIELDS.contains(order.getProperty())) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "Unsupported checklist sort property: " + order.getProperty());
            }
        });
        Sort sort = pageable.getSort().isSorted() ? pageable.getSort() : Sort.by(Sort.Direction.DESC, "createdAt");
        if (sort.getOrderFor("id") == null) sort = sort.and(Sort.by("id"));
        String term = search == null ? "" : canonicalize(search).toLowerCase(Locale.ROOT);
        String escaped = term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        Specification<ChecklistDefinition> filter = (root, query, cb) -> {
            var predicate = cb.conjunction();
            if (active != null) predicate = cb.and(predicate, cb.equal(root.get("isActive"), active));
            if (!term.isBlank()) predicate = cb.and(predicate,
                    cb.like(root.get("normalizedContent"), "%" + escaped + "%", '\\'));
            return predicate;
        };
        return PageResponse.from(repository.findAll(filter,
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort)).map(mapper::toResponse));
    }

    @Override
    @Transactional
    public ChecklistResponse update(String id, ChecklistRequest request) {
        ChecklistDefinition entity = locked(id);
        String content = content(request.getContent());
        String normalized = content.toLowerCase(Locale.ROOT);
        if (repository.existsByNormalizedContentAndIdNot(normalized, id)) {
            throw new ApiException(ErrorCode.CHECKLIST_ALREADY_EXISTS);
        }
        entity.setContent(content);
        entity.setNormalizedContent(normalized);
        if (request.getDifficulty() != null) entity.setDifficulty(request.getDifficulty());
        applyPricing(entity, request, false);
        var saved = repository.saveAndFlush(entity);
        if (defaultPrices != null) defaultPrices.refreshForChecklist(id);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public ChecklistResponse updateStatus(String id, boolean active) {
        ChecklistDefinition entity = locked(id);
        entity.setIsActive(active);
        var saved = repository.saveAndFlush(entity);
        if (defaultPrices != null) defaultPrices.refreshForChecklist(id);
        return mapper.toResponse(saved);
    }

    /** Absent fields keep the stored value on update and take the neutral default on create. */
    private void applyPricing(ChecklistDefinition entity, ChecklistRequest request, boolean create) {
        if (request.getBasePrice() != null) {
            if (request.getBasePrice().signum() < 0 || request.getBasePrice().stripTrailingZeros().scale() > 0)
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "basePrice must be a non-negative whole amount");
            entity.setBasePrice(request.getBasePrice().setScale(0));
        } else if (create) entity.setBasePrice(java.math.BigDecimal.ZERO);
        if (request.getIncludedImageCount() != null) entity.setIncludedImageCount(request.getIncludedImageCount());
        if (request.getIncludedVideoCount() != null) entity.setIncludedVideoCount(request.getIncludedVideoCount());
        if (request.getStandardVideoDurationSeconds() != null) entity.setStandardVideoDurationSeconds(request.getStandardVideoDurationSeconds());
        if (request.getPricingProfile() != null) {
            String profile = request.getPricingProfile().trim();
            entity.setPricingProfile(profile.isEmpty() ? null : profile.toUpperCase(Locale.ROOT));
        }
    }

    private ChecklistDefinition locked(String id) {
        return repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ApiException(ErrorCode.CHECKLIST_NOT_FOUND));
    }

}
