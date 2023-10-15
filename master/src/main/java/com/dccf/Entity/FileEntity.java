package com.dccf.Entity;

import com.dccf.Model.FileType;
import com.dccf.Model.Status;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "file_table")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "file_id")
    private long fileId;

//    @JoinColumn(name = "task_id", nullable = false)
    @ManyToOne()
    private TaskEntity taskEntity;

    @Column(name = "type", nullable = false)
    FileType fileType;

    @Column(name = "name")
    String name;

    @Column(name = "file_data")
    @Lob
    private byte[] fileData;
}
