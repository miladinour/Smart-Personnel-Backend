package com.smartwallet.backend.repository;

import com.smartwallet.backend.model.AiRecommendationHistory;
import com.smartwallet.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AiRecommendationHistoryRepository extends JpaRepository<AiRecommendationHistory, Long> {
    Optional<AiRecommendationHistory> findTopByUserAndTitleOrderByDateCreationDesc(User user, String title);
}
