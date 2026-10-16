package com.bytemarket.support.repository;

import com.bytemarket.support.model.OrderNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IOrderNotificationRepository extends JpaRepository<OrderNotification, Integer> {

    boolean existsByOrderCodeAndEstado(String orderCode, String estado);
}
