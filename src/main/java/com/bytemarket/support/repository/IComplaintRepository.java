package com.bytemarket.support.repository;

import com.bytemarket.support.model.Complaint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IComplaintRepository extends JpaRepository<Complaint, Integer> {
}
