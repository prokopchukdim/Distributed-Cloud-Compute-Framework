package com.dccf.Entity;

import com.dccf.Model.Status;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long taskId;

    @Column(name = "worker_identifier")
    private String workerIdentifier;

    @Column(name = "status")
    private Status status;
}
