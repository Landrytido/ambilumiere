# Ambilumière

Des ampoules et un ruban LED Tuya qui prennent les couleurs de la pochette de la musique qui joue sur la TV.
Tout passe par le réseau local de la maison, sans cloud et sans PC allumé.

Le projet est né sur une Xiaomi MiTV (Android 11) avec un ruban LSC et trois ampoules, et sur une tablette Xiaomi
Pad 6 pour tout piloter. Il y a aussi un thème d'écran d'accueil et un réglage pour réveiller une PS5 depuis la TV.

## Ce qu'il y a dedans

| Dossier | Contenu |
|---|---|
| `lanceur/` | L'écran d'accueil de la TV : carte Spotify compacte avec la pochette, bouton ampoule dans la barre du haut, catalogue d'applis en grille, envoi des couleurs de la pochette aux lumières |
| `tv/` | L'appli « Lumières » de la TV : 4 cartes, scènes, synchro avec la pochette, intensité Doux, Normal ou Vif |
| `tablette/` | L'appli « Lumières » de la tablette : roue de couleurs, scènes perso, groupes, minuteur, thème clair ou sombre |
| `theme-voiture/` | Un exemple de thème d'accueil (PS5 en un clic, dock, barre du haut) |
| `exemples/` | Un fichier de configuration à remplir avec tes propres lumières |
| `docs/` | Comment récupérer les clés de tes ampoules |

## Comment ça marche

1. Le lanceur lit la pochette du morceau en cours (la session média Android) et en tire quatre ou cinq couleurs.
2. Il les envoie à l'appli `tv/` par un message interne (broadcast).
3. L'appli `tv/` applique l'intensité choisie (Doux, Normal, Vif) puis écrit les couleurs dans chaque lumière avec le
   protocole local Tuya (port 6668, versions 3.3 et 3.5). Le ruban reçoit la palette en vague sur ses 10 zones, et
   chaque ampoule prend une couleur de la palette.
4. Quand la musique s'arrête, les lumières reviennent à leur état d'avant.

## Installation, en gros

Il te faut une TV Android avec le débogage ADB activé. Les détails sont dans chaque dossier.

1. Récupère les clés locales de tes lumières, voir `docs/obtenir-les-cles.md`.
2. Copie `exemples/lights.exemple.json` en `lights.json` et remplis-le. Ce fichier ne doit jamais être publié.
3. Compile et installe `lanceur/` puis `tv/` sur la TV. Pousse `lights.json` dans le dossier de l'appli.
4. Pour la tablette, compile `tablette/`, installe l'APK et choisis `lights.json` dans les réglages de l'appli.

Les compilations se font avec Gradle (`./gradlew assembleRelease`), ou dans un conteneur Docker si tu n'as pas le
SDK Android sur ta machine.

## À savoir

- La TV, la tablette et les lumières doivent être sur le même réseau.
- Une ampoule en protocole 3.3 sans réglage de blanc (comme la G125 de mon installation) simule le blanc avec une
  couleur très pâle, le rendu dépend donc de l'ampoule.
- Ce n'est pas un produit fini. Ça marche chez moi, sur mon matériel, et je n'ai pas testé ailleurs.

## Crédits

Le lanceur est basé sur cleanyourtv de Gathin (licence MIT). Le détail est dans `NOTICE.md`.

## Licence

MIT, voir `LICENSE`.
