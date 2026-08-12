# CHECKLIST DE DEMONSTRATION DEI - Ticket2Cash

## 1. Avant la presentation

Verifier que le projet compile :

    cd C:\dev\ticket2cash
    mvn clean compile

Resultat attendu :

    BUILD SUCCESS

Lancer le projet :

    mvn spring-boot:run

Verifier que le serveur demarre sur :

    http://localhost:8080

---

## 2. Comptes a utiliser

### Compte ADMIN principal

    Username : admin
    Password : admin123

### Compte ADMIN de demonstration

    Username : secours
    Password : Secours2026

### Compte LECTEUR

    Username : testreset
    Password : Reset2026

Important :

- Ne pas modifier le mot de passe du compte admin pendant la presentation.
- Utiliser le compte secours pour la demonstration.
- Garder admin comme compte de recuperation.

---

## 3. Pages importantes

Page de connexion :

    http://localhost:8080/login.html

Interface principale :

    http://localhost:8080/index.html

H2 Console :

    http://localhost:8080/h2-console

Rapport HTML exportable :

    http://localhost:8080/api/demo-report/export-html

---

## 4. Scenario de demonstration recommande

### Etape 1 - Connexion

Se connecter avec :

    secours
    Secours2026

Montrer que l'utilisateur est ADMIN.

---

### Etape 2 - Dashboard metier

Aller dans :

    Dashboard metier

Montrer :

- Commercants
- Produits
- Campagnes actives
- Tickets
- Claims
- Cashback paye
- Alertes fraude
- Top commercants
- Derniers paiements

Message a dire :

    Ce dashboard donne une vision metier globale de l'activite cashback.

---

### Etape 3 - Charger les donnees demo

Dans Dashboard metier, cliquer sur :

    Charger donnees demo

Puis cliquer sur :

    Actualiser dashboard metier

Verifier que les compteurs sont alimentes.

Message a dire :

    Ces donnees permettent de simuler un environnement reel avec des commercants, campagnes, tickets, claims et paiements.

---

### Etape 4 - Parcours demo complet

Aller dans :

    Parcours demo

Cliquer sur :

    Lancer parcours complet

Resultat attendu :

- Ticket cree
- Claim creee
- Claim approuvee
- Paiement cashback cree
- Claim passee en PAID
- Logs audit generes

Message a dire :

    En un seul clic, le systeme execute le cycle complet du cashback, depuis le ticket OCR jusqu'au paiement.

---

### Etape 5 - Verification Claims

Aller dans :

    Claims

Verifier qu'une nouvelle claim existe avec le statut :

    PAID

Message a dire :

    La claim suit un cycle de traitement avec des statuts permettant de tracer son evolution.

---

### Etape 6 - Verification Paiements cashback

Aller dans :

    Paiements cashback

Verifier le paiement cree avec :

    SUCCESS

Message a dire :

    Le module de paiement permet de simuler le credit cashback vers le client.

---

### Etape 7 - Verification Logs transactions

Aller dans :

    Logs transactions

Chercher les actions :

    DEMO_FLOW_TICKET_CREATED
    DEMO_FLOW_CLAIM_SUBMITTED
    DEMO_FLOW_CLAIM_APPROVED
    DEMO_FLOW_PAYMENT_SUCCESS

Message a dire :

    Chaque action importante est journalisee pour garantir la tracabilite.

---

### Etape 8 - Dashboard securite

Aller dans :

    Dashboard securite

Montrer :

- Utilisateurs actifs
- Comptes verrouilles
- Connexions reussies
- Echecs connexion
- Deblocages ADMIN
- Reset password ADMIN

Message a dire :

    Ce dashboard permet de suivre les evenements de securite et les comptes utilisateurs.

---

### Etape 9 - Gestion utilisateurs

Aller dans :

    Utilisateurs

Montrer :

- Liste des utilisateurs
- Creation utilisateur
- Changement de role
- Activation / desactivation
- Reset mot de passe
- Deblocage compte

Message a dire :

    Le prototype integre une administration complete des utilisateurs avec controle des roles.

---

### Etape 10 - Rapport demo

Aller dans :

    Rapport demo

Cliquer sur :

    Lancer parcours puis actualiser

Montrer :

- Identification projet
- Indicateurs metier
- Indicateurs securite
- Modules disponibles
- Points anti-fraude
- Derniers logs importants

Message a dire :

    Le rapport consolide automatiquement les elements importants pour une presentation ou un suivi interne.

---

### Etape 11 - Export rapport HTML / PDF

Dans Rapport demo, cliquer sur :

    Exporter rapport HTML / PDF

Une nouvelle page s'ouvre.

Faire :

    Ctrl + P

Puis choisir :

    Enregistrer en PDF

Message a dire :

    Le rapport peut etre exporte en PDF pour archivage ou transmission.

---

## 5. Points forts a mettre en avant

- Parcours cashback complet.
- Simulation OCR ticket.
- Creation automatique de claim.
- Paiement cashback simule.
- Controle anti-fraude par hash ticket.
- Gestion utilisateurs et roles.
- Verrouillage compte apres echecs.
- Protection anti-blocage ADMIN.
- Dashboards metier et securite.
- Rapport de demonstration exportable.
- Logs d'audit complets.

---

## 6. Limites a reconnaitre

- OCR reel non encore integre.
- Paiement bancaire reel non encore connecte.
- Base H2 locale adaptee au prototype, pas a la production.
- Interface frontend simple.
- Pas encore d'application mobile client.
- Pas encore de workflow superviseur multi-niveaux.

---

## 7. Perspectives a annoncer

- Migration vers PostgreSQL.
- Integration d'un vrai OCR.
- Stockage des images de tickets.
- Connexion aux API bancaires.
- Application mobile client.
- Interface commercant.
- Notifications SMS ou email.
- Dashboard BI avance.
- Spring Security / SSO.
- Deploiement Docker.
- Environnement de recette.

---

## 8. Probleme possible et solution rapide

### Probleme : impossible de se connecter

Essayer :

    admin / admin123

Si le compte secours ne marche plus, utiliser admin.

---

### Probleme : page non actualisee

Faire :

    Ctrl + F5

---

### Probleme : serveur deja lance ou bloque

Dans PowerShell :

    Ctrl + C

Puis relancer :

    mvn spring-boot:run

---

### Probleme : erreur de compilation

Executer :

    mvn clean compile

Lire uniquement les lignes qui commencent par :

    [ERROR]

---

### Probleme : base H2 inaccessible

Verifier :

    http://localhost:8080/h2-console

Parametres :

    JDBC URL  : jdbc:h2:file:C:/dev/ticket2cash/data/ticket2cashdb
    User Name : sa
    Password  :

---

## 9. Phrase de conclusion

Ticket2Cash demontre la faisabilite d'une plateforme cashback co-brandee pour Afriland First Bank. Le prototype couvre le parcours metier principal, la securite, la tracabilite, l'anti-fraude, les dashboards et le reporting. Il constitue une base solide pour une future evolution vers une solution integree au systeme d'information bancaire.