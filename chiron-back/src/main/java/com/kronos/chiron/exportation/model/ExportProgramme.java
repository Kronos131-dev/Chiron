package com.kronos.chiron.exportation.model;

import com.kronos.chiron.utilisateur.model.Utilisateur;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExportProgramme {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    @Column(nullable = false, length = 120)
    private String nom;

    @Column(name = "cree_a", nullable = false)
    private LocalDateTime creeA;

    @Column(name = "modifie_a", nullable = false)
    private LocalDateTime modifieA;

    @OneToMany(mappedBy = "export", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordre ASC")
    @Builder.Default
    private List<ExportProgrammeModele> modeles = new ArrayList<>();

    @OneToMany(mappedBy = "export", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ExportProgrammeSeance> seances = new ArrayList<>();

    @OneToMany(mappedBy = "export", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ExportProgrammeExclusion> exclusions = new ArrayList<>();

    public void ajouterModele(ExportProgrammeModele modele) {
        modele.setExport(this);
        modeles.add(modele);
    }

    public void ajouterSeance(ExportProgrammeSeance seance) {
        seance.setExport(this);
        seances.add(seance);
    }

    public void ajouterExclusion(ExportProgrammeExclusion exclusion) {
        exclusion.setExport(this);
        exclusions.add(exclusion);
    }
}
