package com.contractor.app.shift.api.dto;

import java.util.List;

public class ShiftStartOptionsResponse {

    private final List<DriverOption> drivers;
    private final List<EquipmentOption> equipment;

    public ShiftStartOptionsResponse(List<DriverOption> drivers, List<EquipmentOption> equipment) {
        this.drivers = drivers;
        this.equipment = equipment;
    }

    public List<DriverOption> getDrivers() {
        return drivers;
    }

    public List<EquipmentOption> getEquipment() {
        return equipment;
    }

    public static final class DriverOption {
        private final Long id;
        private final String name;

        public DriverOption(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        public Long getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }

    public static final class EquipmentOption {
        private final Long id;
        private final String code;
        private final String name;

        public EquipmentOption(Long id, String code, String name) {
            this.id = id;
            this.code = code;
            this.name = name;
        }

        public Long getId() {
            return id;
        }

        public String getCode() {
            return code;
        }

        public String getName() {
            return name;
        }
    }
}
