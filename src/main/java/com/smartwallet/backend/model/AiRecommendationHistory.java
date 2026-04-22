package com.smartwallet.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_recommendation_history")
@Data
@NoArgsConstructor
public class AiRecommendationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String title;

    private String category;

    @Column(nullable = false)
    private LocalDateTime dateCreation = LocalDateTime.now();

    public AiRecommendationHistory(User user, String title, String category, LocalDateTime dateCreation) {
        this.user = user;
        this.title = title;
        this.category = category;
        this.dateCreation = dateCreation;
    }
}
