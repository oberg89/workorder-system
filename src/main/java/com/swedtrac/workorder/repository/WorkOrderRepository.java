package com.swedtrac.workorder.repository;

import com.swedtrac.workorder.domain.WorkOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long> {
}