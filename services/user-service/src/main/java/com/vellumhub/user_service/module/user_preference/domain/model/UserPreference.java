package com.vellumhub.user_service.module.user_preference.domain.model;

import com.vellumhub.user_service.module.user.domain.UserEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "user_preferences")
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private UserEntity user;

    @ElementCollection
    @CollectionTable(
            name = "user_preference_genres",
            joinColumns = @JoinColumn(name = "preference_id")
    )
    @Column(name = "genre_name")
    private List<String> genres;

    private String about;

}