# Lumières, version tablette

L'appli (`com.gathin.lights.tablet`) pour piloter toutes les lumières depuis une tablette Android, sans passer par
la TV. Elle a été faite et testée sur une Xiaomi Pad 6 (Android 14).

## Ce qu'elle fait

- Un accueil avec Tout allumer et Tout éteindre.
- Trois dispositions au choix et plusieurs thèmes, clair ou sombre automatique.
- Une roue de couleurs libre, des nuances rapides, la luminosité et le blanc chaud ou froid.
- Des scènes perso avec un aperçu des couleurs, et des groupes de lumières.
- Un minuteur pour tout éteindre plus tard.

## Configuration

Au premier lancement, choisis ton fichier `lights.json` dans les réglages de l'appli. Elle en garde une copie dans
son dossier privé et se reconnecte à tes lumières. Le format est dans `../exemples/lights.exemple.json`.

Les clés de tes lumières restent sur la tablette, elles ne sont jamais dans le code.

## Compiler

```bash
./gradlew testReleaseUnitTest assembleRelease
```

Sur certaines tablettes Xiaomi, `adb install` est refusé. Dans ce cas, copie l'APK dans le dossier Téléchargements
de la tablette et installe-le à la main depuis l'appli Fichiers.

## Limite connue

Le minuteur repose sur une alarme Android : sur certains appareils très économes en énergie, il peut arriver avec
un peu de retard.
