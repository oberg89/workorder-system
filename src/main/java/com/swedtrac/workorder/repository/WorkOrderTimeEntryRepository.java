package com.swedtrac.workorder.repository;

import com.swedtrac.workorder.domain.WorkOrder;
import com.swedtrac.workorder.domain.WorkOrderTimeEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkOrderTimeEntryRepository extends JpaRepository<WorkOrderTimeEntry, Long> {
    List<WorkOrderTimeEntry> findByWorkOrder(WorkOrder workOrder);
}