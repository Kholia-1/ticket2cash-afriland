package com.afriland.ticket2cash.points;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PointsRuleRepository extends JpaRepository<PointsRule, Long> {

    List<PointsRule> findAllByOrderByPriorityDescIdAsc();
    List<PointsRule> findByActiveTrueOrderByPriorityDescIdAsc();
}
