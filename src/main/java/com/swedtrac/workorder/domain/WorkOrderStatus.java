package com.swedtrac.workorder.domain;

public enum WorkOrderStatus {
    OPEN,
    IN_PROGRESS,
    COMPLETED,
    READY_FOR_INVOICING,
    INVOICED,
    CANCELLED
}