package com.kronos.chiron.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing a workout session (Seance).
 * A session groups multiple exercises and can either be an actual user workout or a model/template.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Seance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The title of the workout session.
     */
    private String titre;

    /**
     * The start time of the workout session.
     */
    private LocalDateTime startTime;

    /**
     * The end time of the workout session.
     */
    private LocalDateTime endTime;

    /**
     * The week number in which this session occurs.
     */
    private int weekNumber;

    /**
     * Indicates whether this session is a template (true) or a real executed session (false).
     */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean isModele;

    /**
     * Manual display order for programme templates on the Programme page (lower = higher in the list).
     * Set by the drag-and-drop reorder endpoint. Unused for executed sessions.
     */
    @Column(nullable = false, columnDefinition = "integer default 0")
    private int displayOrder;

    /**
     * The list of exercises performed during this session, kept in user-defined order.
     * The position is stored explicitly on {@link Exercice#getDisplayOrder()} and assigned
     * by the save logic from the input DTO's list order.
     */
    @OneToMany(mappedBy = "seance", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private List<Exercice> exercices = new ArrayList<>();

    /**
     * The user who owns this workout session.
     * Jackson is instructed to ignore Hibernate proxy properties to prevent serialization issues.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Utilisateur utilisateur;

    /**
     * Helper method to add an exercise to this session, maintaining the bidirectional relationship.
     *
     * @param exercice The exercise to add.
     */
    public void addExercice(Exercice exercice) {
        if (this.exercices == null) {
            this.exercices = new ArrayList<>();
        }
        this.exercices.add(exercice);
        exercice.setSeance(this);
    }
}
