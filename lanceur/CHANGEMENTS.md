# Ce qui a été ajouté au lanceur

Base : cleanyourtv 3.3.0 de Gathin (MIT). Tout le reste du dossier est resté tel quel.
`changements.patch` contient la différence exacte avec cette version.

## Fichiers ajoutés

| Fichier | Rôle |
|---|---|
| `NowPlaying.java` | Le titre en cours : titre, artiste, pochette, état de lecture |
| `NowPlayingCard.java` | La carte Spotify compacte de l'écran d'accueil avec la pochette |
| `MediaListener.java` | Lit la session média Android (Spotify et les autres lecteurs) |
| `LightsPalette.java` | Tire 4 ou 5 couleurs de la pochette et les envoie à l'appli lumières |
| `ArtBackdrop.java` | Fond flouté à partir de la pochette |

## Fichiers modifiés

| Fichier | Changement |
|---|---|
| `AndroidManifest.xml` | Déclaration du service d'écoute des médias |
| `MainActivity.java` | Branchement de la carte, de l'écoute et de l'envoi des couleurs |
| `AppLibraryDialog.java`, `LibraryApps.java` | Catalogue d'applis en grille de 6 colonnes |
| `ClockScene.java`, `WallpaperPlayer.java` | Petits ajustements de l'écran de veille |
| `res/values*/strings.xml` | Textes de la carte, en français aussi |

## Réglage des couleurs

Dans `LightsPalette.java` : les pixels trop ternes ou trop sombres sont ignorés, les teintes sont regroupées par
tranches de 15 degrés, et une couleur gardée a au moins 50 % de saturation et 60 % de luminosité. La force finale se
règle ensuite dans l'appli lumières (Doux, Normal, Vif).
