# Vérification des raccourcis d’exécutables

Vérification locale du 13 octobre 2026, sur Windows, avec Java 21 et le SDK Android
36 installé dans `build/android-sdk`. `local.properties`, les outils téléchargés,
les journaux et les APK restent dans les emplacements locaux ignorés par Git.

| Contrôle | Résultat |
| --- | --- |
| Compilation Kotlin/Java Legacy Debug | Réussie |
| Compilation Kotlin/Java Modern Debug | Réussie |
| Assemblage APK Legacy Debug | Réussi |
| Assemblage APK Modern Debug | Réussi |
| Tests ciblés Legacy | 39 réussis, aucun échec |
| Tests ciblés Modern | 13 réussis, aucun échec |
| Signature des deux APK (`apksigner verify`) | Valide, signature v2, certificat Android Debug |
| Identité des APK (`aapt2 dump badging`) | `app.gamenative`, version 1.3.0 / code 23 |
| Appareil connecté (`adb devices -l`) | Aucun |

Les 13 nouveaux tests couvrent les identités indépendantes, les cartes de
bibliothèque, la persistance et la modification/suppression d’un frère, les
chemins autorisés, les anciens et nouveaux intents, les cibles disparues, la
protection des pochettes hors stockage dédié et l’absence d’écriture de
l’exécutable de session dans la configuration parent. Ils ont été exécutés sur
les deux variantes. Les 26 autres tests Legacy couvrent les favoris, les filtres
de jeux masqués, le tri et la configuration ColdClientLoader existante.

Les tests Robolectric des nouvelles classes utilisent une application Android
simple pour éviter de démarrer les services réels. Ils tournent sur l’API 29,
compatible avec le minimum de la variante Modern.

## Limites des vérifications globales

La suite complète de tests existants a été tentée. Elle a rencontré des erreurs
de chemins sous Windows dans `SteamAutoCloudTest` : son montage Wine de test
crée un dossier nommé `c:`, que Windows ne peut pas créer comme un dossier
ordinaire. Un test de liens symboliques a également échoué. La suite s’est
ensuite bloquée dans l’initialisation de PostHog depuis `PluviaApp.onCreate`
sous Robolectric. Le diagnostic des threads est conservé dans
`build/test-threads.txt` ; cette exécution a été interrompue. La suite globale
n’est donc pas annoncée comme réussie.

Lint a été exécuté sur Legacy et Modern. Le premier passage a signalé les
traductions manquantes des nouveaux textes hors français/anglais. Leur repli
anglais est désormais explicite et limité au nouveau fichier de ressources.
Les rapports globaux comportent aussi des erreurs dans le code existant, notamment
des appels à des API Android plus récentes que le `minSdk` déclaré. Les rapports
se trouvent dans `app/build/reports/lint-results-legacyDebug.html` et
`app/build/reports/lint-results-modernDebug.html`. La commande globale incluant
lint ne doit pas être confondue avec le succès de l’assemblage et des tests ciblés.
Le dernier passage conserve **116 erreurs Legacy** et **110 erreurs Modern**.
Il ne signale aucun diagnostic dans les nouveaux fichiers de la fonctionnalité.

Les journaux des dernières vérifications sont dans
`build/verification/verified-final.log`. Les rapports de tests sont dans
`app/build/reports/tests/testLegacyDebugUnitTest/` et
`app/build/reports/tests/testModernDebugUnitTest/`.

## APK produits et signature

- Modern : `app/build/outputs/apk/modern/debug/app-modern-debug.apk`
  (minimum Android API 29, cible API 36).
- Legacy : `app/build/outputs/apk/legacy/debug/app-legacy-debug.apk`
  (minimum Android API 26, cible API 28).

Ces APK utilisent le certificat **Android Debug** local et le package officiel
`app.gamenative`. Ils ne peuvent remplacer une installation officielle que si
Android considère sa signature compatible. Ne pas désinstaller l’application
officielle pour forcer leur installation : ses données privées pourraient être
supprimées. Une variante coinstallée nécessite un autre `applicationId` et
l’adaptation des chemins/intents liés au package ; elle ne peut pas reprendre
directement le stockage privé officiel. Le [guide de la fonctionnalité](executable-shortcuts.md)
détaille cette limite.

## Essai à effectuer sur l’Ayn Thor

À effectuer uniquement avec une installation de test disposant légalement des
fichiers du jeu et du préfixe Wine nécessaires, ou une mise à jour signée de façon
compatible avec l’installation existante.

1. Ouvrir les options de Kingdom Hearts puis **Créer un raccourci d’exécutable**.
   Créer deux entrées avec des noms et pochettes différents, par exemple
   `KINGDOM HEARTS FINAL MIX.exe` et `KINGDOM HEARTS II FINAL MIX.exe`.
2. Vérifier leurs cartes distinctes dans la bibliothèque, leur recherche, les
   favoris et la navigation à la manette. Redémarrer GameNative et vérifier leur
   conservation.
3. Lancer chaque entrée depuis GameNative, en quittant la session précédente.
   Vérifier le jeu obtenu, puis relancer le parent et contrôler que son exécutable
   d’origine est conservé.
4. Définir Cocoon comme lanceur par défaut, épingler les deux entrées depuis
   GameNative, puis les lancer depuis Cocoon à froid et à chaud. Vérifier le retour
   au lanceur, le nom et la pochette de chaque icône.
5. Modifier une entrée, puis en supprimer une. Vérifier que l’autre et le parent
   restent utilisables. Tester ensuite les autres exécutables de la capture.

Wine, les performances sur Thor et la conservation des intents par Cocoon n’ont
pas été testés matériellement. Le profil iiSU documenté utilise uniquement le
Steam App ID et n’est pas compatible avec la distinction des exécutables sans
adaptation de son intégration.

## Changement extérieur dans le dépôt

Windows a refusé la lecture de
`app/src/main/cpp/lsfg-vk-android/thirdparty/pe-parse/tests/assets/corkami-poc-dataset/PE/bin/96emptysections.exe`
avec un message antivirus. Ce fichier de corpus de tests est ensuite apparu
manquant dans Git, laissant le sous-module marqué modifié. Il n’a pas été
restauré ni inclus dans les changements de la fonctionnalité. Ce fichier n’est
pas un jeu installé.
