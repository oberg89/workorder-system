package com.swedtrac.workorder.controller;

import com.swedtrac.workorder.domain.WorkOrder;
import com.swedtrac.workorder.domain.WorkOrderStatus;
import com.swedtrac.workorder.repository.WorkOrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workorders")
public class WorkOrderController {

    private final WorkOrderRepository repository;

    public WorkOrderController(WorkOrderRepository repository) {
        this.repository = repository;
    }

    /**
     * GET /api/workorders
     * Hämtar alla arbetsorder.
     */
    @GetMapping
    public List<WorkOrder> getAll() {
        return repository.findAll();
    }

    /**
     * GET /api/workorders/{id}
     * Hämtar en specifik arbetsorder.
     */
    @GetMapping("/{id}")
    public WorkOrder getById(@PathVariable Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Arbetsorder med id " + id + " hittades inte"
                ));
    }

    /**
     * POST /api/workorders
     * Skapar en ny arbetsorder.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkOrder create(@RequestBody WorkOrder workOrder) {
        // Validering
        if (workOrder.getOrderNumber() == null || workOrder.getOrderNumber().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ordernummer får inte vara tomt");
        }
        if (workOrder.getTitle() == null || workOrder.getTitle().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Titel får inte vara tom");
        }
        if (workOrder.getCustomer() == null || workOrder.getCustomer().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kund får inte vara tom");
        }

        // Sätt created_at om den är null
        if (workOrder.getCreatedAt() == null) {
            workOrder.setCreatedAt(LocalDateTime.now());
        }

        // Sätt default status om den är null
        if (workOrder.getStatus() == null) {
            workOrder.setStatus(WorkOrderStatus.OPEN);
        }

        // Sätt updated_at om du har ett sådant fält
        try {
            workOrder.setUpdatedAt(LocalDateTime.now());
        } catch (NoSuchMethodError | RuntimeException ignored) {
            // om du inte har fältet/lösningen än, ignorerar vi bara
        }

        return repository.save(workOrder);
    }

    /**
     * PUT /api/workorders/{id}
     * Uppdaterar en befintlig arbetsorder (alla huvudfält).
     * Används av GUI:t när du redigerar en befintlig order.
     */
    @PutMapping("/{id}")
    public WorkOrder update(@PathVariable Long id, @RequestBody WorkOrder updated) {
        WorkOrder existing = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Arbetsorder med id " + id + " hittades inte"
                ));

        // Uppdatera fält – samma som i formuläret
        existing.setOrderNumber(updated.getOrderNumber());
        existing.setTitle(updated.getTitle());
        existing.setCustomer(updated.getCustomer());
        existing.setCategory(updated.getCategory());
        existing.setDescription(updated.getDescription());
        existing.setTrainNumber(updated.getTrainNumber());
        existing.setVehicle(updated.getVehicle());
        existing.setLocation(updated.getLocation());

        // Rör inte status här – den hanteras i PATCH /{id}/status
        // Uppdatera updatedAt om du har det fältet
        try {
            existing.setUpdatedAt(LocalDateTime.now());
        } catch (NoSuchMethodError | RuntimeException ignored) {
        }

        return repository.save(existing);
    }

    /**
     * PATCH /api/workorders/{id}/status
     * Uppdaterar endast status på en arbetsorder.
     * Används av status-knapparna i GUI:t.
     */
    @PatchMapping("/{id}/status")
    public WorkOrder updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        WorkOrder workOrder = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Arbetsorder med id " + id + " hittades inte"
                ));

        String statusStr = body.get("status");
        if (statusStr == null || statusStr.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status får inte vara tom");
        }

        WorkOrderStatus newStatus;
        try {
            newStatus = WorkOrderStatus.valueOf(statusStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Ogiltig status: " + statusStr + ". Giltiga värden: OPEN, IN_PROGRESS, COMPLETED, INVOICED, CANCELLED"
            );
        }

        workOrder.setStatus(newStatus);

        // Uppdatera updatedAt om fältet finns
        try {
            workOrder.setUpdatedAt(LocalDateTime.now());
        } catch (NoSuchMethodError | RuntimeException ignored) {
        }

        return repository.save(workOrder);
    }
}