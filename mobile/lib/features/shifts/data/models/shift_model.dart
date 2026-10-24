class ShiftModel {
  const ShiftModel({
    required this.id,
    required this.shiftDate,
    required this.shiftType,
    required this.status,
    required this.siteName,
    required this.driverName,
    required this.equipmentCode,
    required this.equipmentName,
    required this.price,
    required this.driverWage,
    required this.priceAfterWage,
    required this.notes,
    this.siteId,
    this.driverId,
    this.equipmentId,
    this.version,
    this.createdAt,
    this.startedAt,
    this.cancelReason = '',
    this.equipmentOperatingCost = 0.0,
  });

  factory ShiftModel.fromJson(Map<String, dynamic> json) {
    return ShiftModel(
      id: _intValue(json['id']),
      shiftDate: _stringValue(json['shiftDate'], '—'),
      shiftType: _stringValue(json['shiftType'], '—'),
      status: _stringValue(json['status'], '—'),
      siteName: _stringValue(json['siteName'], 'غير محدد'),
      driverName: _stringValue(json['driverName'], 'غير محدد'),
      equipmentCode: _stringValue(json['equipmentCode'], ''),
      equipmentName: _stringValue(json['equipmentName'], 'غير محددة'),
      price: _numberValue(json['price']),
      driverWage: _numberValue(json['driverWage']),
      equipmentOperatingCost: _numberValue(json['equipmentOperatingCost']),
      priceAfterWage: _numberValue(json['priceAfterWage']),
      notes: _stringValue(json['notes'], ''),
      siteId: _nullableInt(json['siteId']),
      driverId: _nullableInt(json['driverId']),
      equipmentId: _nullableInt(json['equipmentId']),
      version: _nullableInt(json['version']),
      createdAt: _dateTimeValue(json['createdAt']),
      startedAt: _dateTimeValue(json['startedAt']),
      cancelReason: _stringValue(json['cancelReason'], ''),
    );
  }

  final int id;
  final String shiftDate;
  final String shiftType;
  final String status;
  final String siteName;
  final String driverName;
  final String equipmentCode;
  final String equipmentName;
  final double price;
  final double driverWage;
  final double equipmentOperatingCost;
  final double priceAfterWage;
  final String notes;
  final int? siteId;
  final int? driverId;
  final int? equipmentId;
  final int? version;
  final DateTime? createdAt;
  final DateTime? startedAt;
  final String cancelReason;

  static int? _nullableInt(dynamic value) {
    if (value is int) return value;
    if (value is num) return value.toInt();
    return int.tryParse(value?.toString() ?? '');
  }

  static DateTime? _dateTimeValue(dynamic value) {
    if (value == null) return null;
    final raw = value.toString().trim();
    if (raw.isEmpty) return null;

    // Backend LocalDateTime values are stored in UTC and serialized without a zone suffix.
    // Treat zone-less timestamps as UTC, while preserving explicit offsets.
    final hasZone =
        raw.endsWith('Z') || RegExp(r'[+-]\d{2}:\d{2}').hasMatch(raw);
    final normalized = hasZone ? raw : '${raw}Z';
    return DateTime.tryParse(normalized)?.toLocal();
  }

  static int _intValue(dynamic value) {
    if (value is int) return value;
    if (value is num) return value.toInt();
    return int.tryParse(value?.toString() ?? '') ?? 0;
  }

  static double _numberValue(dynamic value) {
    if (value is num) return value.toDouble();
    return double.tryParse(value?.toString() ?? '') ?? 0;
  }

  static String _stringValue(dynamic value, String fallback) {
    if (value == null) return fallback;
    final result = value.toString().trim();
    return result.isEmpty ? fallback : result;
  }

  String get shiftTypeLabel {
    switch (shiftType) {
      case 'MORNING':
        return 'صباحية';
      case 'EVENING':
        return 'مسائية';
      case 'NIGHT':
        return 'ليلية';
      default:
        return shiftType;
    }
  }

  String get statusLabel {
    switch (status) {
      case 'IN_PROGRESS':
        return 'جارية';
      case 'COMPLETED':
        return 'منتهية';
      case 'CANCELLED':
        return 'ملغاة';
      default:
        return status;
    }
  }
}
