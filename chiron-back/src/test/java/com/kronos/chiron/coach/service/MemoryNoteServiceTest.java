package com.kronos.chiron.coach.service;

import com.kronos.chiron.coach.model.ChironMemoryNote;
import com.kronos.chiron.coach.model.MemoryNoteType;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import com.kronos.chiron.coach.persistence.ChironMemoryNoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemoryNoteServiceTest {

    @Mock
    private ChironMemoryNoteRepository repository;

    @InjectMocks
    private MemoryNoteService memoryNoteService;

    private Utilisateur user;

    @BeforeEach
    void setUp() {
        user = new Utilisateur();
        user.setId(1L);
    }

    @Test
    void save_buildsNoteFromUserTypeAndContent() {
        when(repository.save(any(ChironMemoryNote.class))).thenAnswer(i -> i.getArgument(0));

        memoryNoteService.save(user, MemoryNoteType.PREFERENCE, "préfère le matin");

        ArgumentCaptor<ChironMemoryNote> captor = ArgumentCaptor.forClass(ChironMemoryNote.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getUtilisateur()).isSameAs(user);
        assertThat(captor.getValue().getType()).isEqualTo(MemoryNoteType.PREFERENCE);
        assertThat(captor.getValue().getContent()).isEqualTo("préfère le matin");
    }

    @Test
    void save_returnsThePersistedNote() {
        ChironMemoryNote persisted = ChironMemoryNote.builder().id(42L).build();
        when(repository.save(any(ChironMemoryNote.class))).thenReturn(persisted);

        assertThat(memoryNoteService.save(user, MemoryNoteType.PREFERENCE, "x")).isSameAs(persisted);
    }

    @Test
    void getRecent_queriesWithTheRequestedLimitOnTheFirstPage() {
        when(repository.findByUtilisateurOrderByCreatedAtDesc(user, PageRequest.of(0, 10)))
                .thenReturn(List.of());

        assertThat(memoryNoteService.getRecent(user, 10)).isEmpty();
        verify(repository).findByUtilisateurOrderByCreatedAtDesc(user, PageRequest.of(0, 10));
    }

    @Test
    void getRecent_returnsWhatTheRepositoryGives() {
        ChironMemoryNote note = ChironMemoryNote.builder().id(1L).content("a").build();
        when(repository.findByUtilisateurOrderByCreatedAtDesc(user, PageRequest.of(0, 5)))
                .thenReturn(List.of(note));

        assertThat(memoryNoteService.getRecent(user, 5)).containsExactly(note);
    }

    @Test
    void getByType_filtersOnTheGivenType() {
        ChironMemoryNote note = ChironMemoryNote.builder().type(MemoryNoteType.OBJECTIF).build();
        when(repository.findByUtilisateurAndTypeOrderByCreatedAtDesc(user, MemoryNoteType.OBJECTIF))
                .thenReturn(List.of(note));

        assertThat(memoryNoteService.getByType(user, MemoryNoteType.OBJECTIF)).containsExactly(note);
    }

    @Test
    void delete_noteBelongsToTheUser_deletesItAndReturnsTrue() {
        ChironMemoryNote note = ChironMemoryNote.builder().id(7L).build();
        when(repository.findByIdAndUtilisateur(7L, user)).thenReturn(Optional.of(note));

        assertThat(memoryNoteService.delete(user, 7L)).isTrue();
        verify(repository).delete(note);
    }

    @Test
    void delete_noteMissingOrNotOwned_returnsFalseWithoutDeleting() {
        when(repository.findByIdAndUtilisateur(7L, user)).thenReturn(Optional.empty());

        assertThat(memoryNoteService.delete(user, 7L)).isFalse();
        verify(repository, never()).delete(any());
    }
}
