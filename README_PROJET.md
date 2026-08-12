# Ticket2Cash - Prototype Cashback Co-brande

## 1. Presentation generale

Ticket2Cash est un prototype d'application web developpe pour Afriland First Bank dans le cadre d'une reflexion sur la digitalisation du cashback lie aux cartes co-brandees avec des supermarches.

Le principe est simple : un client effectue un achat dans un supermarche partenaire avec une carte co-brandee Afriland First Bank, puis soumet son ticket de caisse. Le systeme simule l'analyse OCR du ticket, cree une demande de cashback, applique des controles anti-fraude, permet la validation de la claim, puis declenche le paiement cashback.

Ce prototype vise a demontrer un parcours complet, depuis la capture du ticket jusqu'au pilotage metier et securitaire.

---

## 2. Objectif metier

L'objectif du projet est de proposer une solution permettant de :

- Digitaliser le traitement des tickets de caisse.
- Automatiser la creation des demandes de cashback.
- Centraliser les campagnes cashback par commercant.
- Suivre les claims clients.
- Gerer les paiements cashback.
- Detecter les risques de fraude, notamment les doublons de tickets.
- Journaliser les actions sensibles.
- Offrir des dashboards de suivi pour la DEI et les equipes metier.

---

## 3. Technologies utilisees

### Backend

- Java
- Spring Boot
- Spring Web
- Spring Data JPA
- H2 Database
- Maven

### Frontend

- HTML
- CSS
- JavaScript vanilla
- Interface web statique servie par Spring Boot

### Base de donnees

- H2 en mode fichier local

Chemin de la base :

    C:/dev/ticket2cash/data/ticket2cashdb

### Outils de test

- Navigateur web
- H2 Console
- Thunder Client ou Postman
- PowerShell
- VS Code

---

## 4. Architecture fonctionnelle

Le prototype contient les modules suivants :

### Authentification et roles

- Connexion utilisateur.
- Deconnexion.
- Session applicative.
- Roles : ADMIN, OPERATEUR, LECTEUR.
- Restriction des actions selon le role.

### Gestion utilisateurs

- Creation d'utilisateurs.
- Activation et desactivation.
- Changement de role.
- Reset mot de passe par ADMIN.
- Protection contre l'auto-blocage d'un ADMIN.
- Verrouillage automatique apres plusieurs echecs de connexion.
- Deblocage par ADMIN.

### Commercants

- Creation de commercants.
- Suivi du statut KYC.
- Activation et suspension.

### Produits

- Gestion des produits eligibles au cashback.
- Type de cashback : pourcentage, montant fixe ou aucun.
- Association produit-commercant.

### Campagnes cashback

- Creation de campagnes.
- Gestion des dates.
- Budget.
- Plafonds par client.
- Statuts : DRAFT, ACTIVE, PAUSED, EXPIRED, CLOSED.

### OCR ticket simule

- Simulation de lecture OCR.
- Creation automatique d'un ticket.
- Extraction du montant et du numero ticket.
- Generation d'une claim.

### Claims cashback

- Soumission.
- Approbation.
- Rejet.
- Passage au statut PAID apres paiement.

### Paiements cashback

- Creation de paiements.
- Batch cashback.
- Paiement avec statut SUCCESS.

### Anti-fraude

- Detection de doublons par hash ticket.
- Score de risque.
- Creation d'alertes fraude.
- Suivi des alertes.

### Audit logs

- Journalisation des actions sensibles.
- Logs de connexion.
- Logs de securite.
- Logs de parcours demo.
- Logs de paiements, claims, fraudes et exports.

### Dashboards

- Dashboard metier.
- Dashboard securite.
- Rapport de demonstration.
- Parcours demo complet.

### Exports

- Export CSV des claims.
- Export CSV des paiements.
- Export CSV des alertes fraude.
- Export CSV des logs.
- Export HTML imprimable du rapport demo.

---

## 5. Comptes de test

### ADMIN principal

    Username : admin
    Password : admin123

### ADMIN de secours

    Username : secours
    Password : Secours2026

### Utilisateur lecteur de test

    Username : testreset
    Password : Reset2026

Remarque : il est recommande de ne pas changer le mot de passe du compte admin principal pendant les demonstrations. Utiliser plutot le compte secours pour les tests de securite.

---

## 6. Comment lancer le projet

Ouvrir PowerShell puis executer :

    cd C:\dev\ticket2cash
    mvn spring-boot:run

L'application demarre sur :

    http://localhost:8080

Page de connexion :

    http://localhost:8080/login.html

Interface principale :

    http://localhost:8080/index.html

Console H2 :

    http://localhost:8080/h2-console

Parametres H2 :

    JDBC URL  : jdbc:h2:file:C:/dev/ticket2cash/data/ticket2cashdb
    User Name : sa
    Password  :

Le mot de passe H2 est vide.

---

## 7. URLs API importantes

### Authentification

    POST /api/auth/login
    POST /api/auth/logout
    GET  /api/auth/me

### Gestion utilisateurs

    GET  /api/auth/users
    POST /api/auth/users
    PUT  /api/auth/users/{id}/role
    PUT  /api/auth/users/{id}/enabled
    PUT  /api/auth/users/{id}/reset-password
    PUT  /api/auth/users/{id}/unlock

### Dashboard

    GET /api/dashboard/summary
    GET /api/security-dashboard/summary
    GET /api/business-dashboard/summary
    GET /api/demo-report/summary

### Parcours demo

    POST /api/demo-data/init
    POST /api/demo-flow/run

### Rapport demo

    GET /api/demo-report/export-html

### Exports CSV

    GET /api/exports/claims-csv
    GET /api/exports/payments-csv
    GET /api/exports/fraud-alerts-csv
    GET /api/exports/audit-logs-csv

---

## 8. Scenario de demonstration recommande

Pour presenter le prototype devant la DEI, suivre ce parcours :

1. Ouvrir la page de connexion.
2. Se connecter avec le compte secours.
3. Afficher le dashboard metier.
4. Charger les donnees de demonstration.
5. Afficher les statistiques cashback.
6. Afficher le dashboard securite.
7. Montrer la gestion utilisateurs.
8. Aller dans Parcours demo.
9. Cliquer sur Lancer parcours complet.
10. Montrer que le ticket, la claim et le paiement sont crees.
11. Aller dans Rapport demo.
12. Cliquer sur Lancer parcours puis actualiser.
13. Exporter le rapport HTML/PDF.
14. Ouvrir les logs transactions.
15. Montrer la tracabilite des actions.

---

## 9. Regles de securite implementees

Le prototype integre plusieurs mecanismes de securite :

- Controle d'acces par role.
- ADMIN uniquement pour les actions sensibles.
- LECTEUR limite a la consultation.
- OPERATEUR limite aux operations de traitement.
- Verrouillage apres 5 echecs de connexion.
- Deblocage par ADMIN.
- Reset mot de passe par ADMIN.
- Interdiction pour un ADMIN de se desactiver lui-meme.
- Interdiction pour un ADMIN de retirer son propre role ADMIN.
- Conservation d'au moins un compte ADMIN actif.
- Journalisation des actions sensibles.

---

## 10. Regles anti-fraude implementees

Le prototype implemente :

- Generation d'un hash de ticket.
- Detection de doublon ticket.
- Score de risque.
- Creation d'alertes fraude.
- Statuts d'alertes : OPEN, UNDER_REVIEW, RESOLVED, REJECTED.
- Liaison entre ticket, claim, client et commercant.
- Logs d'audit sur les evenements de fraude.

---

## 11. Valeur ajoutee pour Afriland First Bank

Le prototype apporte plusieurs benefices :

- Reduction du traitement manuel des demandes cashback.
- Meilleure experience client.
- Suivi centralise des campagnes cashback.
- Possibilite de piloter les partenariats avec les supermarches.
- Visibilite sur les montants de cashback payes.
- Controle anti-fraude integre.
- Tracabilite des operations sensibles.
- Base technique evolutive pour une future integration avec les systemes bancaires.

---

## 12. Limites du prototype

Cette version reste un prototype local. Les limites actuelles sont :

- OCR reel non encore integre.
- Base H2 locale, non adaptee a la production.
- Paiement bancaire simule.
- Interface frontend simple.
- Pas encore d'integration avec le SI bancaire.
- Pas encore d'application mobile client.
- Pas encore de workflow complet de validation multi-niveaux.
- Pas encore de signature electronique ou validation OTP.

---

## 13. Perspectives d'evolution

Pour aller vers une version industrielle, les evolutions recommandees sont :

- Migrer de H2 vers PostgreSQL.
- Ajouter Spring Security.
- Ajouter JWT ou authentification SSO interne.
- Integrer un vrai moteur OCR.
- Ajouter un stockage des images de tickets.
- Integrer une API bancaire de paiement cashback.
- Ajouter un workflow de validation superviseur.
- Ajouter des notifications email/SMS.
- Ajouter une application mobile client.
- Ajouter une interface commercant.
- Ajouter des rapports PDF officiels.
- Ajouter des indicateurs BI avances.
- Ajouter des tests unitaires et tests d'integration.
- Conteneuriser avec Docker.
- Mettre en place un environnement de recette.

---

## 14. Commandes utiles

Compiler le projet :

    mvn clean compile

Lancer le projet :

    mvn spring-boot:run

Creer une sauvegarde :

    cd C:\dev
    Compress-Archive -Path "C:\dev\ticket2cash" -DestinationPath "C:\dev\ticket2cash-version-finale-dei.zip" -Force

---

## 15. Conclusion

Ticket2Cash demontre la faisabilite d'une plateforme cashback co-brandee pour Afriland First Bank. Le prototype couvre le parcours metier principal, la securite utilisateur, le suivi des paiements, la detection de fraude, les dashboards et la production de rapports.

Il constitue une base solide pour une presentation DEI et pour une eventuelle evolution vers une solution integree au systeme d'information bancaire.