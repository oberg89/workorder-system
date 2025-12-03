package com.swedtrac.workorder.web;

import com.swedtrac.workorder.domain.WorkOrder;
import com.swedtrac.workorder.domain.WorkOrderStatus;
import com.swedtrac.workorder.repository.WorkOrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/workorders")
@CrossOrigin(origins = "*") // tillåter frontend (kan låsas ned senare)
public class WorkOrderController {

    private final WorkOrderRepository workOrderRepository;

    public WorkOrderController(WorkOrderRepository workOrderRepository) {
        this.workOrderRepository = workOrderRepository;
    }

    // ================================
    // GET /api/workorders
    // ================================
    @GetMapping
    public List<WorkOrder> getAllWorkOrders() {
        return workOrderRepository.findAll();
    }

    // ================================
    // GET /api/workorders/{id}
    // ================================
    @GetMapping("/{id}")
    public ResponseEntity<WorkOrder> getWorkOrderById(@PathVariable Long id) {
        Optional<WorkOrder> optional = workOrderRepository.findById(id);
        return optional
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ================================
    // POST /api/workorders
    // Skapa ny arbetsorder
    // ================================
    @PostMapping
    public ResponseEntity<?> createWorkOrder(@RequestBody WorkOrder workOrder) {
        // Enkel validering
        if (workOrder.getOrderNumber() == null || workOrder.getOrderNumber().isBlank()) {
            return ResponseEntity.badRequest().body("orderNumber är obligatoriskt");
        }
        if (workOrder.getTitle() == null || workOrder.getTitle().isBlank()) {
            return ResponseEntity.badRequest().body("title är obligatoriskt");
        }
        if (workOrder.getCustomer() == null || workOrder.getCustomer().isBlank()) {
            return ResponseEntity.badRequest().body("customer är obligatoriskt");
        }

        // Nya ordrar ska alltid starta som OPEN om inget annat anges
        if (workOrder.getStatus() == null) {
            workOrder.setStatus(WorkOrderStatus.OPEN);
        }

        // Skapa tidsstämplar om de saknas
        LocalDateTime now = LocalDateTime.now();
        if (workOrder.getCreatedAt() == null) {
            workOrder.setCreatedAt(now);
        }
        workOrder.setUpdatedAt(now);

        // archivedAt ska vara null på nya ordrar
        workOrder.setArchivedAt(null);

        WorkOrder saved = workOrderRepository.save(workOrder);
        return new ResponseEntity<>(saved, HttpStatus.CREATED);
    }

    // ================================
    // PUT /api/workorders/{id}
    // Uppdatera befintlig arbetsorder (ej status/arkiv)
    // ================================
    @PutMapping("/{id}")
    public ResponseEntity<?> updateWorkOrder(
            @PathVariable Long id,
            @RequestBody WorkOrder updated
    ) {
        Optional<WorkOrder> optional = workOrderRepository.findById(id);
        if (optional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        WorkOrder existing = optional.get();

        // Uppdatera fält som ska gå att ändra från formuläret
        existing.setOrderNumber(updated.getOrderNumber());
        existing.setTitle(updated.getTitle());
        existing.setDescription(updated.getDescription());
        existing.setCustomer(updated.getCustomer());
        existing.setCategory(updated.getCategory());
        existing.setTrainNumber(updated.getTrainNumber());
        existing.setVehicle(updated.getVehicle());
        existing.setLocation(updated.getLocation());
        existing.setTrack(updated.getTrack());

        // Vi ändrar inte createdAt
        existing.setUpdatedAt(LocalDateTime.now());

        // Vi låter status / archivedAt INTE påverkas av detta PUT
        // (de styrs via egen endpoint nedan)

        WorkOrder saved = workOrderRepository.save(existing);
        return ResponseEntity.ok(saved);
    }

    // ================================
    // PATCH /api/workorders/{id}/status?status=IN_PROGRESS
    // Uppdatera status + hantera arkivering
    // ================================
    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(
            @PathVariable Long id,
            @RequestParam("status") WorkOrderStatus newStatus
    ) {
        Optional<WorkOrder> optional = workOrderRepository.findById(id);
        if (optional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        WorkOrder workOrder = optional.get();

        // Sätt ny status
        workOrder.setStatus(newStatus);
        workOrder.setUpdatedAt(LocalDateTime.now());

        // Hantera arkivering
        if (newStatus == WorkOrderStatus.CANCELLED) {
            // Avbrutna ordrar markeras som arkiverade direkt
            workOrder.setArchivedAt(LocalDateTime.now());
        } else if (newStatus == WorkOrderStatus.COMPLETED ||
                newStatus == WorkOrderStatus.READY_FOR_INVOICING ||
                newStatus == WorkOrderStatus.INVOICED) {
            // Här kan vi välja att INTE arkivera automatiskt
            // utan bara lämna archivedAt som den är
            // (t.ex. arkiveras man manuellt senare).
        } else {
            // OPEN eller IN_PROGRESS -> inte arkiverad
            workOrder.setArchivedAt(null);
        }

        WorkOrder saved = workOrderRepository.save(workOrder);
        return ResponseEntity.ok(saved);
    }

    // ================================
    // PATCH /api/workorders/{id}/archive
    // Manuell arkivering (t.ex. från frontend senare)
    // ================================
    @PatchMapping("/{id}/archive")
    public ResponseEntity<?> archiveWorkOrder(@PathVariable Long id) {
        Optional<WorkOrder> optional = workOrderRepository.findById(id);
        if (optional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        WorkOrder workOrder = optional.get();
        workOrder.setArchivedAt(LocalDateTime.now());
        workOrder.setUpdatedAt(LocalDateTime.now());

        WorkOrder saved = workOrderRepository.save(workOrder);
        return ResponseEntity.ok(saved);
    }
}