# NAKIVO Dashboard (Android)

Application Android pour consulter l'état d'un serveur **NAKIVO Backup & Replication**
via son API HTTP (JSON-RPC, `https://<director>:4443/c/router`).

## Fonctionnalités

- **Tableau de bord** : santé globale, jobs réussis / en échec / en cours, VM protégées,
  dernières exécutions, occupation des repositories, état et charge des transporters.
- **Jobs** : liste filtrable (tous, échecs, en cours, désactivés), progression en direct,
  détail de la dernière exécution et JSON brut renvoyé par l'API.
- **Stockage** : chaque Backup Repository avec espace utilisé / libre, nombre de sauvegardes, état.
- Actualisation par glissement et automatique (30 s, 1 min, 5 min ou désactivée).
- **Certificat auto-signé** : à la première connexion l'app affiche l'empreinte SHA-256 du
  certificat du Director ; une fois acceptée, seul ce certificat est accepté (épinglage).
- Identifiants chiffrés sur le téléphone (Android Keystore).
- Écran **diagnostic** : réponse JSON brute de chaque appel, copiable.

## Installation

1. Télécharger l'APK depuis la release **latest** :
   <https://github.com/conichonhaa/nakivo-apk/releases/download/latest/nakivo-dashboard.apk>
2. Autoriser l'installation depuis le navigateur, installer.
3. Saisir l'adresse du Director (par ex. `https://192.168.1.29:4443`), l'utilisateur et le mot de passe.

Depuis l'extérieur, passer par le VPN : ne pas exposer le port 4443 sur Internet.

> Conseil : créer dans NAKIVO un utilisateur dédié avec un rôle en lecture seule.

## Compilation

Chaque push sur `main` déclenche GitHub Actions (`.github/workflows/build-apk.yml`) qui :
- compile l'APK signé,
- le publie comme artefact du build,
- met à jour la release `latest`.

Un tag `vX.Y` crée en plus une release versionnée.

La clé de signature (`keystore/app.keystore`) est versionnée volontairement pour que
chaque build ait la même signature et s'installe par-dessus la version précédente.
Elle ne sert qu'à cette app personnelle.

Compilation locale : `./gradlew assembleRelease` (JDK 17 + Android SDK 35).

## Appels API utilisés

| Écran | action / method |
|---|---|
| Connexion | `AuthenticationManagement` / `login` |
| Tableau de bord | `JobSummaryManagement` / `getGroupInfo` |
| Jobs | `JobSummaryManagement` / `getJobInfo` |
| Stockage | `BackupManagement` / `getBackupRepositories` |
