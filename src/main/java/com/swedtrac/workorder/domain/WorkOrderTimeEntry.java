package com.swedtrac.workorder.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "work_order_time_entries")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderTimeEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Koppling till arbetsorder (JsonIgnore för att undvika cirkulär serialisering)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_order_id", nullable = false)
    @JsonIgnore
    private WorkOrder workOrder;

    @Column(nullable = false)
    private String action;        // t.ex. "Bromsbelägg"

    @Column(nullable = false)
    private String work;          // t.ex. "Byte av bromsbelägg"

    @Column(nullable = false)
    private double hours;         // timmar

    @Column(nullable = false)
    private double rate;          // á kr

    @Column(nullable = false)
    private double total;         // hours * rate

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}