# Crédits et licences

Le code de ce dépôt est sous licence MIT (voir `LICENSE`), sauf ce qui est indiqué ci-dessous.

## Lanceur (dossier `lanceur/`)

Ce dossier est basé sur le lanceur Android TV **cleanyourtv** de Gathin, version 3.3.0
(https://github.com/Gathin23/cleanyourtv), publié sous licence MIT, copyright (c) 2026 Gathin.
Son texte de licence est conservé dans `lanceur/LICENSE-cleanyourtv`. La liste de ce qui a été ajouté ou modifié
ici se trouve dans `lanceur/CHANGEMENTS.md`, et le détail ligne par ligne dans `lanceur/changements.patch`.

Le lanceur embarque aussi deux éléments avec leurs propres licences, dont les textes sont dans `lanceur/` :

- la police Outfit, licence SIL OFL 1.1 (`FONT-LICENSE-OFL.txt`)
- les icônes Fluent Emoji de Microsoft, licence MIT (`ICONS-LICENSE-FLUENT-EMOJI.txt`)

## Applis lumières (dossiers `tv/` et `tablette/`)

Elles suivent le modèle d'add-on de cleanyourtv pour la structure du projet Gradle et les fichiers de build.
Le code des applications est écrit pour ce projet. Le protocole utilisé pour parler aux lumières est le protocole
local Tuya, réimplémenté sans bibliothèque externe.
