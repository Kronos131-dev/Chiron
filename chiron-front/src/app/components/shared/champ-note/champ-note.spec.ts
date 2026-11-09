import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';

import { ChampNote } from './champ-note';

describe('ChampNote', () => {
  let fixture: ComponentFixture<ChampNote>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [ChampNote] }).compileComponents();
    fixture = TestBed.createComponent(ChampNote);
    fixture.componentRef.setInput('libelle', 'Note');
    fixture.componentRef.setInput('indication', 'Ressenti');
  });

  function boutonEffacer(): HTMLButtonElement | null {
    return fixture.nativeElement.querySelector('button');
  }

  it('offers the clear button only when there is something to clear', async () => {
    fixture.componentRef.setInput('valeur', '');
    await fixture.whenStable();
    expect(boutonEffacer()).toBeNull();

    fixture.componentRef.setInput('valeur', 'Épaule sensible');
    await fixture.whenStable();
    expect(boutonEffacer()).not.toBeNull();
  });

  it('empties the note in one tap and tells the parent', async () => {
    fixture.componentRef.setInput('valeur', 'Épaule sensible');
    await fixture.whenStable();
    const recues: (string | null | undefined)[] = [];
    fixture.componentInstance.valeur.subscribe((valeur) => recues.push(valeur));

    boutonEffacer()!.click();
    await fixture.whenStable();

    expect(recues).toEqual(['']);
    expect(fixture.nativeElement.querySelector('textarea').value).toBe('');
    expect(boutonEffacer()).toBeNull();
  });

  it('reports what the athlete types', async () => {
    await fixture.whenStable();
    const zone: HTMLTextAreaElement = fixture.nativeElement.querySelector('textarea');

    zone.value = 'Nouvelle note';
    zone.dispatchEvent(new Event('input'));

    expect(fixture.componentInstance.valeur()).toBe('Nouvelle note');
  });
});
