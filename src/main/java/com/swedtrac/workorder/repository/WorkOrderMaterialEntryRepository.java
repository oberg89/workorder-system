package com.swedtrac.workorder.repository;

import com.swedtrac.workorder.domain.WorkOrder;
import com.swedtrac.workorder.domain.WorkOrderMaterialEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkOrderMaterialEntryRepository extends JpaRepository<WorkOrderMaterialEntry, Long> {
    List<WorkOrderMaterialEntry> findByWorkOrder(WorkOrder workOrder);
}