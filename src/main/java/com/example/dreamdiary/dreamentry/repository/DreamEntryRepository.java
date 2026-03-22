package com.example.dreamdiary.dreamentry.repository;

import com.example.dreamdiary.dreamentry.model.DreamEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DreamEntryRepository extends JpaRepository<DreamEntry, Long>, JpaSpecificationExecutor<DreamEntry> {
}
