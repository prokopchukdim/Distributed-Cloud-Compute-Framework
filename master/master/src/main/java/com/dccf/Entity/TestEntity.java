package com.dccf.Entity;

import com.dccf.Model.FileType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "test_table")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private long fileId;

    @Column(name = "misc")
    String misc;
}
