package com.dccf.Repository;

import com.dccf.Entity.TaskEntity;
import com.dccf.Entity.TestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TestRepository extends JpaRepository<TestEntity, Long> {

    @Query(value = "INSERT INTO test_table (misc) VALUES (:misc)", nativeQuery = true)
    void insertNewMisc(@Param("misc") String misc);
}
