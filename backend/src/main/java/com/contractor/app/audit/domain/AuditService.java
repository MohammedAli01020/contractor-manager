package com.contractor.app.audit.domain;

import com.contractor.app.audit.api.dto.AuditResponse;
import com.contractor.app.audit.infra.AuditLogRepository;
import com.contractor.app.common.api.PageParams;
import com.contractor.app.common.api.PageResponse;
import com.contractor.app.common.security.CurrentUser;
import com.contractor.app.user.domain.User;
import com.contractor.app.user.infra.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * سجل التدقيق العام. المستخدم الفاعل يؤخذ من الجلسة الحالية، فلا يمكن لأي كود
 * أن ينسب عملية لغير صاحبها. الكتابة تتم داخل نفس معاملة العملية المسجَّلة:
 * إن فشلت العملية لا يبقى لها أثر في السجل.
 */
@Service
public class AuditService {

    private final AuditLogRepository logs;
    private final UserRepository users;
    private final ZoneId businessZone;

    public AuditService(AuditLogRepository logs, UserRepository users,
                        @Value("${app.business-timezone:Africa/Cairo}") String businessTimezone) {
        this.logs = logs;
        this.users = users;
        this.businessZone = ZoneId.of(businessTimezone);
    }

    public void record(Long tenantId, AuditEntityType type, Long entityId, AuditAction action, String field,
                       String oldValue, String newValue) {
        logs.save(new AuditLog(tenantId, CurrentUser.get().getUserId(), type, entityId, action, field,
                clip(oldValue), clip(newValue)));
    }

    /** يسجّل صفًا لكل حقل تغيّر فعلًا (UPDATED). التغييرات الفارغة لا تُسجَّل. */
    public void recordChanges(Long tenantId, AuditEntityType type, Long entityId, ChangeSet changes) {
        for (Change change : changes.items) {
            record(tenantId, type, entityId, AuditAction.UPDATED, change.field, change.oldValue, change.newValue);
        }
    }

    @Transactional(readOnly = true)
    public List<AuditResponse> history(Long tenantId, AuditEntityType type, Long entityId) {
        List<AuditLog> rows = logs.findByTenantIdAndEntityTypeAndEntityIdOrderByCreatedAtDescIdDesc(
                tenantId, type, entityId);
        Map<Long, String> names = userNames(tenantId, rows);
        return rows.stream().map(r -> new AuditResponse(r, names.get(r.getUserId()))).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditResponse> search(Long tenantId, AuditEntityType type, Long entityId, Long userId,
                                              LocalDate from, LocalDate to, int page, int size) {
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            if (type != null) {
                predicates.add(cb.equal(root.get("entityType"), type));
            }
            if (entityId != null) {
                predicates.add(cb.equal(root.get("entityId"), entityId));
            }
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<LocalDateTime>get("createdAt"), startOfDayUtc(from)));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.<LocalDateTime>get("createdAt"), startOfDayUtc(to.plusDays(1))));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<AuditLog> result = logs.findAll(spec,
                PageParams.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        Map<Long, String> names = userNames(tenantId, result.getContent());
        return PageResponse.of(result, r -> new AuditResponse(r, names.get(r.getUserId())));
    }

    /** بداية اليوم بتوقيت العمل (القاهرة) محوّلة إلى UTC لأن التخزين بتوقيت UTC. */
    private LocalDateTime startOfDayUtc(LocalDate date) {
        return date.atStartOfDay(businessZone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private Map<Long, String> userNames(Long tenantId, List<AuditLog> rows) {
        Set<Long> ids = new HashSet<>();
        rows.forEach(r -> ids.add(r.getUserId()));
        Map<Long, String> names = new HashMap<>();
        if (!ids.isEmpty()) {
            users.findAllById(ids).stream().filter(u -> tenantId.equals(u.getTenantId()))
                    .forEach(u -> names.put(u.getId(), u.getFullName()));
        }
        return names;
    }

    private static String clip(String value) {
        return value != null && value.length() > 480 ? value.substring(0, 480) : value;
    }

    // ------------------------------------------------------------------ change tracking

    private static final class Change {
        private final String field;
        private final String oldValue;
        private final String newValue;

        private Change(String field, String oldValue, String newValue) {
            this.field = field;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }
    }

    /** يجمع الفروق بين القيم القديمة والجديدة. المبالغ تُقارَن بقيمتها لا بعدد خاناتها العشرية. */
    public static final class ChangeSet {
        private final List<Change> items = new ArrayList<>();

        public ChangeSet track(String field, Object oldValue, Object newValue) {
            boolean same;
            if (oldValue instanceof BigDecimal && newValue instanceof BigDecimal) {
                same = ((BigDecimal) oldValue).compareTo((BigDecimal) newValue) == 0;
            } else {
                same = Objects.equals(oldValue, newValue);
            }
            if (!same) {
                items.add(new Change(field, text(oldValue), text(newValue)));
            }
            return this;
        }

        private static String text(Object value) {
            if (value == null) {
                return null;
            }
            return value instanceof BigDecimal ? ((BigDecimal) value).toPlainString() : value.toString();
        }
    }

    /** لاستخدام الـ Controller: اسم المستخدم حسب المعرّف. */
    @Transactional(readOnly = true)
    public String userName(Long tenantId, Long userId) {
        return users.findByIdAndTenantId(userId, tenantId).map(User::getFullName).orElse("-");
    }
}
