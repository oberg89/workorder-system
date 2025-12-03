package com.swedtrac.workorder.repository;

import com.swedtrac.workorder.domain.WorkOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long> {
    // Lägg till egna finder-metoder här vid behov, t.ex.
    // List<WorkOrder> findByStatus(WorkOrderStatus status);
}