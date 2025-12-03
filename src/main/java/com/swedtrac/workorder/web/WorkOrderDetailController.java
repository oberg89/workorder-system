package com.swedtrac.workorder.web;

import com.swedtrac.workorder.domain.*;
import com.swedtrac.workorder.repository.WorkOrderMaterialEntryRepository;
import com.swedtrac.workorder.repository.WorkOrderRepository;
import com.swedtrac.workorder.repository.WorkOrderTimeEntryRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/workorders/{workOrderId}")
@CrossOrigin(origins = "*")
public class WorkOrderDetailController {

    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderTimeEntryRepository timeEntryRepository;
    private final WorkOrderMaterialEntryRepository materialEntryRepository;

    public WorkOrderDetailController(
            WorkOrderRepository workOrderRepository,
            WorkOrderTimeEntryRepository timeEntryRepository,
            WorkOrderMaterialEntryRepository materialEntryRepository
    ) {
        this.workOrderRepository = workOrderRepository;
        this.timeEntryRepository = timeEntryRepository;
        this.materialEntryRepository = materialEntryRepository;
    }

    private Optional<WorkOrder> findWorkOrder(Long workOrderId) {
        return workOrderRepository.findById(workOrderId);
    }

    // ================================
    // TID
    // ================================

    @GetMapping("/time-entries")
    public ResponseEntity<List<WorkOrderTimeEntry>> getTimeEntries(@PathVariable Long workOrderId) {
        Optional<WorkOrder> woOpt = findWorkOrder(workOrderId);
        if (woOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        List<WorkOrderTimeEntry> list = timeEntryRepository.findByWorkOrder(woOpt.get());
        return ResponseEntity.ok(list);
    }

    @PostMapping("/time-entries")
    public ResponseEntity<?> saveTimeEntries(
            @PathVariable Long workOrderId,
            @RequestBody List<WorkOrderTimeEntry> entries
    ) {
        Optional<WorkOrder> woOpt = findWorkOrder(workOrderId);
        if (woOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        WorkOrder wo = woOpt.get();

        // Radera gamla och spara nya (enklaste möjliga logik)
        List<WorkOrderTimeEntry> existing = timeEntryRepository.findByWorkOrder(wo);
        timeEntryRepository.deleteAll(existing);

        LocalDateTime now = LocalDateTime.now();
        for (WorkOrderTimeEntry e : entries) {
            e.setId(null);
            e.setWorkOrder(wo);
            e.setTotal(e.getHours() * e.getRate());
            e.setCreatedAt(now);
            e.setUpdatedAt(now);
        }

        List<WorkOrderTimeEntry> saved = timeEntryRepository.saveAll(entries);
        return ResponseEntity.ok(saved);
    }

    // ================================
    // MATERIAL
    // ================================

    @GetMapping("/material-entries")
    public ResponseEntity<List<WorkOrderMaterialEntry>> getMaterialEntries(@PathVariable Long workOrderId) {
        Optional<WorkOrder> woOpt = findWorkOrder(workOrderId);
        if (woOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        List<WorkOrderMaterialEntry> list = materialEntryRepository.findByWorkOrder(woOpt.get());
        return ResponseEntity.ok(list);
    }

    @PostMapping("/material-entries")
    public ResponseEntity<?> saveMaterialEntries(
            @PathVariable Long workOrderId,
            @RequestBody List<WorkOrderMaterialEntry> entries
    ) {
        Optional<WorkOrder> woOpt = findWorkOrder(workOrderId);
        if (woOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        WorkOrder wo = woOpt.get();

        // Radera gamla och spara nya
        List<WorkOrderMaterialEntry> existing = materialEntryRepository.findByWorkOrder(wo);
        materialEntryRepository.deleteAll(existing);

        LocalDateTime now = LocalDateTime.now();
        for (WorkOrderMaterialEntry e : entries) {
            e.setId(null);
            e.setWorkOrder(wo);
            e.setTotal(e.getQuantity() * e.getPrice());
            e.setCreatedAt(now);
            e.setUpdatedAt(now);
        }

        List<WorkOrderMaterialEntry> saved = materialEntryRepository.saveAll(entries);
        return ResponseEntity.ok(saved);
    }
}