package com.dccf.Entity;

import com.dccf.Model.FileType;
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
public class FileEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int fileId;

    @ManyToOne()
    @JoinColumn(name = "task_id", nullable = false)
    private int taskId;

    @Column(name = "type", nullable = false)
    FileType fileType;

    @Column(name = "file_data")
    @Lob
    private byte[] fileData;
}
