package com.contractor.app.live.domain;

import com.contractor.app.driver.domain.Driver;
import com.contractor.app.driver.domain.DriverAvailability;
import com.contractor.app.driver.infra.DriverRepository;
import com.contractor.app.equipment.domain.Equipment;
import com.contractor.app.equipment.infra.EquipmentRepository;
import com.contractor.app.equipment.infra.EquipmentTypeRepository;
import com.contractor.app.live.api.dto.LiveDriverResponse;
import com.contractor.app.live.api.dto.LiveEquipmentResponse;
import com.contractor.app.shift.domain.Shift;
import com.contractor.app.shift.domain.ShiftStatus;
import com.contractor.app.shift.infra.ShiftRepository;
import com.contractor.app.site.infra.SiteRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "شغال الآن" لا تُخزَّن في أي مكان: تُحسب من الورديات الشغالة. بهذا لا يمكن أن تتناقض الحالة
 * مع الورديات. حالة "متاح" للتسجيل = فعّال + (متاح/حالة AVAILABLE) + بلا وردية شغالة.
 */
@Service
public class LiveStatusService {

    public static final String WORKING = "WORKING";

    private final ShiftRepository shifts;
    private final DriverRepository drivers;
    private final EquipmentRepository equipment;
    private final EquipmentTypeRepository types;
    private final SiteRepository sites;

    public LiveStatusService(ShiftRepository shifts, DriverRepository drivers, EquipmentRepository equipment,
                             EquipmentTypeRepository types, SiteRepository sites) {
        this.shifts = shifts;
        this.drivers = drivers;
        this.equipment = equipment;
        this.types = types;
        this.sites = sites;
    }

    @Transactional(readOnly = true)
    public List<LiveEquipmentResponse> equipment(Long tenantId, boolean onlyFree) {
        List<Shift> running = shifts.findByTenantIdAndStatusOrderByShiftDateDescIdDesc(tenantId,
                ShiftStatus.IN_PROGRESS);
        Map<Long, Shift> latest = new HashMap<>();
        Map<Long, Integer> counts = new HashMap<>();
        for (Shift s : running) {
            latest.putIfAbsent(s.getEquipmentId(), s);
            counts.merge(s.getEquipmentId(), 1, Integer::sum);
        }
        Map<Long, String> siteNames = siteNames(tenantId, running);
        Map<Long, String> driverNames = driverNames(tenantId, running);

        List<Equipment> active = equipment.findByTenantIdAndActiveTrueOrderByCodeAsc(tenantId);
        Set<Long> typeIds = new HashSet<>();
        active.forEach(e -> typeIds.add(e.getEquipmentTypeId()));
        Map<Long, String> typeNames = new HashMap<>();
        if (!typeIds.isEmpty()) {
            types.findByTenantIdAndIdIn(tenantId, typeIds).forEach(t -> typeNames.put(t.getId(), t.getName()));
        }

        List<LiveEquipmentResponse> result = new ArrayList<>();
        for (Equipment e : active) {
            Shift current = latest.get(e.getId());
            String live = current != null ? WORKING : e.getStatus().name();
            if (onlyFree && !"AVAILABLE".equals(live)) {
                continue;
            }
            result.add(new LiveEquipmentResponse(e.getId(), e.getCode(), e.getName(),
                    typeNames.get(e.getEquipmentTypeId()), e.getStatus().name(), live,
                    current == null ? null : current.getId(),
                    current == null ? null : current.getShiftDate(),
                    current == null ? null : siteNames.get(current.getSiteId()),
                    current == null ? null : driverNames.get(current.getDriverId()),
                    counts.getOrDefault(e.getId(), 0)));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<LiveDriverResponse> drivers(Long tenantId, boolean onlyFree) {
        List<Shift> running = shifts.findByTenantIdAndStatusOrderByShiftDateDescIdDesc(tenantId,
                ShiftStatus.IN_PROGRESS);
        Map<Long, Shift> latest = new HashMap<>();
        Map<Long, Integer> counts = new HashMap<>();
        for (Shift s : running) {
            latest.putIfAbsent(s.getDriverId(), s);
            counts.merge(s.getDriverId(), 1, Integer::sum);
        }
        Map<Long, String> siteNames = siteNames(tenantId, running);
        Map<Long, String> equipmentCodes = new HashMap<>();
        Set<Long> equipmentIds = new HashSet<>();
        running.forEach(s -> equipmentIds.add(s.getEquipmentId()));
        equipment.findAllById(equipmentIds).stream().filter(e -> tenantId.equals(e.getTenantId()))
                .forEach(e -> equipmentCodes.put(e.getId(), e.getCode()));

        List<LiveDriverResponse> result = new ArrayList<>();
        for (Driver d : drivers.findByTenantIdAndActiveTrueOrderByNameAsc(tenantId)) {
            Shift current = latest.get(d.getId());
            String live;
            if (current != null) {
                live = WORKING;
            } else if (d.getAvailability() == DriverAvailability.UNAVAILABLE) {
                live = "UNAVAILABLE";
            } else {
                live = "AVAILABLE";
            }
            if (onlyFree && !"AVAILABLE".equals(live)) {
                continue;
            }
            result.add(new LiveDriverResponse(d.getId(), d.getName(), d.getWorkerType().name(),
                    d.getAvailability().name(), d.getUnavailableReason(), live,
                    current == null ? null : current.getId(),
                    current == null ? null : current.getShiftDate(),
                    current == null ? null : siteNames.get(current.getSiteId()),
                    current == null ? null : equipmentCodes.get(current.getEquipmentId()),
                    counts.getOrDefault(d.getId(), 0)));
        }
        return result;
    }

    private Map<Long, String> siteNames(Long tenantId, List<Shift> running) {
        Set<Long> ids = new HashSet<>();
        running.forEach(s -> ids.add(s.getSiteId()));
        Map<Long, String> names = new HashMap<>();
        sites.findAllById(ids).stream().filter(s -> tenantId.equals(s.getTenantId()))
                .forEach(s -> names.put(s.getId(), s.getName()));
        return names;
    }

    private Map<Long, String> driverNames(Long tenantId, List<Shift> running) {
        Set<Long> ids = new HashSet<>();
        running.forEach(s -> ids.add(s.getDriverId()));
        Map<Long, String> names = new HashMap<>();
        drivers.findAllById(ids).stream().filter(d -> tenantId.equals(d.getTenantId()))
                .forEach(d -> names.put(d.getId(), d.getName()));
        return names;
    }
}
