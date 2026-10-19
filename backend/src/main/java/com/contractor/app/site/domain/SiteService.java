package com.contractor.app.site.domain;

import com.contractor.app.common.api.PageParams;
import com.contractor.app.common.api.PageResponse;
import com.contractor.app.common.error.ApiException;
import com.contractor.app.common.util.Texts;
import com.contractor.app.site.api.dto.SiteRequest;
import com.contractor.app.site.api.dto.SiteResponse;
import com.contractor.app.site.infra.SiteRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SiteService {

    private static final String NOT_FOUND = "الموقع غير موجود.";
    private static final String NAME_TAKEN = "اسم الموقع مستخدم بالفعل.";

    private final SiteRepository sites;

    public SiteService(SiteRepository sites) {
        this.sites = sites;
    }

    @Transactional(readOnly = true)
    public PageResponse<SiteResponse> list(Long tenantId, String q, boolean includeInactive, int page, int size) {
        Page<Site> result = sites.findAll(filter(tenantId, q, includeInactive),
                PageParams.of(page, size, Sort.by("name").ascending()));
        return PageResponse.of(result, SiteResponse::from);
    }

    @Transactional(readOnly = true)
    public SiteResponse get(Long tenantId, Long id) {
        return SiteResponse.from(find(tenantId, id));
    }

    @Transactional
    public SiteResponse create(Long tenantId, SiteRequest request) {
        String name = request.getName().trim();
        if (sites.existsByTenantIdAndNameIgnoreCase(tenantId, name)) {
            throw ApiException.conflict(NAME_TAKEN);
        }
        Site site = new Site(tenantId, name, Texts.blankToNull(request.getAddress()),
                Texts.blankToNull(request.getNotes()));
        if (request.getActive() != null) {
            site.setActive(request.getActive());
        }
        return SiteResponse.from(sites.saveAndFlush(site));
    }

    @Transactional
    public SiteResponse update(Long tenantId, Long id, SiteRequest request) {
        Site site = find(tenantId, id);
        site.requireVersion(request.getVersion());
        String name = request.getName().trim();
        if (sites.existsByTenantIdAndNameIgnoreCaseAndIdNot(tenantId, name, id)) {
            throw ApiException.conflict(NAME_TAKEN);
        }
        site.setName(name);
        site.setAddress(Texts.blankToNull(request.getAddress()));
        site.setNotes(Texts.blankToNull(request.getNotes()));
        if (request.getActive() != null) {
            site.setActive(request.getActive());
        }
        return SiteResponse.from(sites.saveAndFlush(site));
    }

    /** "الحذف" تعطيل: السجل يبقى لأن الورديات القديمة تشير إليه. */
    @Transactional
    public void deactivate(Long tenantId, Long id) {
        Site site = find(tenantId, id);
        site.setActive(false);
        sites.saveAndFlush(site);
    }

    private Site find(Long tenantId, Long id) {
        return sites.findByIdAndTenantId(id, tenantId).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
    }

    private static Specification<Site> filter(Long tenantId, String q, boolean includeInactive) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            if (!includeInactive) {
                predicates.add(cb.isTrue(root.get("active")));
            }
            if (q != null && !q.trim().isEmpty()) {
                String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.<String>get("name")), like),
                        cb.like(cb.lower(root.<String>get("address")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
