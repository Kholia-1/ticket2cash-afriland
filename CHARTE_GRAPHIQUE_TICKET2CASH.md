# Charte graphique Ticket2Cash

## 1. Identité visuelle générale

Ticket2Cash adopte une identité professionnelle, bancaire et moderne. L’interface est volontairement sobre afin de faciliter la supervision des transactions, du cashback, de la fidélité et des actions d’audit.

La hiérarchie visuelle repose sur quatre principes :

- une base claire et respirante en mode clair ;
- un mode sombre disponible pour le confort d’utilisation ;
- le rouge Afriland pour les actions et repères importants ;
- des couleurs sémantiques limitées pour indiquer un état : succès, attention, information ou erreur.

Les styles sont actuellement définis dans le bloc CSS intégré à `src/main/resources/static/index.html`. Aucun fichier CSS externe principal n’a été identifié pour le shell de l’application.

## 2. Palette de couleurs

### Couleurs de marque et d’accent

| Couleur | Code HEX | Rôle | Usage dans l’application |
|---|---|---|---|
| Rouge Afriland | `#C1121F` | Couleur de marque et action principale | Boutons primaires, élément actif du menu, liens importants, focus et accents |
| Rouge foncé | `#A50F1A` | Variante d’interaction | État `hover` des boutons primaires |
| Rouge très foncé | `#8A0C15` | Contraste de marque | Texte associé à certains accents rouges |
| Rouge très clair | `#FDECEE` | Teinte de marque | Sélection, rôle utilisateur, fonds d’alerte légère et zones d’accent |
| Rouge secondaire | `#F2545B` | Accent visuel | Dégradés des avatars et éléments décoratifs |

### Couleurs sémantiques

| Couleur | Code HEX | Rôle | Usage dans l’application |
|---|---|---|---|
| Vert succès | `#127A45` | État positif | Paiement réussi, campagne active, validation et badges approuvés |
| Fond succès | `#E6F4EC` | Fond d’état positif | Badges et messages de confirmation |
| Orange attention | `#B25E09` | État à surveiller | En attente, traitement manuel, révision et avertissements |
| Fond attention | `#FBEFDD` | Fond d’avertissement | Badges et informations nécessitant une action |
| Bleu information | `#1553B8` | Information et contexte | Badges informatifs, synthèses, intégration et audit |
| Fond information | `#E7F0FE` | Fond informatif | Panneaux d’aide et messages contextuels |
| Rouge erreur | `#B42318` | Erreur ou risque | Fraude, rejet, échec et alertes sensibles |
| Fond erreur | `#FCE9E7` | Fond d’erreur | Badges d’erreur, boutons de déconnexion et alertes |
| Gris badge | `#5A6472` | État neutre | Valeurs non initialisées, éléments legacy et informations secondaires |
| Fond gris badge | `#EEF1F5` | Fond neutre | Badges neutres et éléments désactivés |

### Neutres et surfaces — mode clair

| Couleur | Code HEX | Rôle | Usage dans l’application |
|---|---|---|---|
| Fond général | `#F4F6FA` | Arrière-plan principal | Zone globale de contenu |
| Surface blanche | `#FFFFFF` | Surface élevée | Sidebar, topbar, cartes KPI, panneaux et tableaux |
| Surface secondaire | `#F7F9FC` | Contraste doux | En-têtes de tableau, champs et zones secondaires |
| Bordure | `#E6EAF1` | Séparation légère | Cartes, champs, panneaux et tableaux |
| Bordure forte | `#D6DCE7` | Séparation renforcée | Hover, scrollbars et contours plus visibles |
| Texte principal | `#101828` | Contraste principal | Titres, valeurs KPI et contenu important |
| Texte secondaire | `#48566B` | Hiérarchie secondaire | Sous-titres, labels et données secondaires |
| Texte atténué | `#8A94A6` | Information discrète | Aides, placeholders, descriptions et dates |

### Neutres et surfaces — mode sombre

Le mode sombre réutilise les mêmes accents sémantiques sur des surfaces plus profondes :

| Couleur | Code HEX | Rôle |
|---|---|---|
| Fond sombre | `#090C12` | Arrière-plan global |
| Surface sombre | `#12161F` | Cartes et panneaux |
| Surface secondaire sombre | `#161C27` | Champs, zones élevées et tableaux |
| Bordure sombre | `#232B39` | Séparations principales |
| Bordure forte sombre | `#2C3648` | Séparations renforcées |
| Texte clair | `#E9EDF5` | Titres et contenu principal |
| Texte secondaire clair | `#AEB7C6` | Sous-titres et informations secondaires |
| Texte atténué sombre | `#727D90` | Aides et éléments discrets |
| Fond sidebar sombre | `#0D121A` | Menu latéral |

Les valeurs `rgba(...)` utilisées dans les ombres, overlays et accents sombres sont des variantes de transparence des couleurs précédentes. Elles ne constituent pas de nouvelles couleurs fonctionnelles.

## 3. Typographie et hiérarchie visuelle

- **Texte courant** : `Inter`, avec repli vers les polices système (`system-ui`, `Segoe UI`, `Roboto`).
- **Titres et valeurs importantes** : `Plus Jakarta Sans`, avec un poids généralement élevé pour les pages, KPI et en-têtes.
- **Références techniques et identifiants** : `JetBrains Mono`, utilisée pour les références de transaction, cartes masquées, codes et valeurs techniques.

Hiérarchie recommandée :

1. Les titres de page sont courts, visibles et orientés action.
2. Les sous-titres expliquent le rôle de la page en une phrase.
3. Les labels de tableaux et formulaires utilisent une taille plus petite et une couleur secondaire.
4. Les badges indiquent un état ou une catégorie, sans remplacer le texte métier.
5. Les boutons importants restent visibles dans l’en-tête ou près du bloc concerné.

## 4. Mise en page

### Menu latéral

Le menu est placé dans une sidebar fixe et structurée par groupes accordéon : Tableaux de bord, Cashback Cartes, Partenaires & Offres, Fidélité Afriland, Contrôle & Audit, Administration et Intégration.

Le groupe actif reste ouvert et l’élément actif utilise un dégradé rouge avec texte blanc. La recherche du menu est placée en haut, tandis que les informations utilisateur et la déconnexion sont regroupées dans le pied de sidebar.

### Topbar

La topbar contient la marque Ticket2Cash, le rôle connecté, la recherche globale, la date, la langue, les notifications, le thème et l’avatar. Elle reste compacte afin de laisser la priorité au contenu métier.

### Cartes KPI

Les cartes KPI utilisent une surface blanche ou sombre, une bordure fine, des angles arrondis et une ombre discrète. La valeur principale est plus grande que son label. L’icône est placée dans une petite zone teintée rouge Afriland.

### Blocs fonctionnels

Les panneaux regroupent les informations et actions liées à un même sujet. Ils utilisent une bordure légère, des angles arrondis et un en-tête séparé lorsque cela améliore la lecture.

### Tableaux

Les tableaux privilégient la lisibilité : en-têtes en capitales discrètes, bordures horizontales, survol léger et références en police monospace. Les tableaux de transactions peuvent défiler horizontalement lorsque les données secondaires sont nombreuses ; l’action principale reste prioritaire.

### Espaces et alignements

Les espacements sont réguliers, les boutons sont alignés dans les zones d’action et les contenus sont organisés en grille. Les vues mobiles réduisent le nombre de colonnes et réorganisent les blocs sans changer les fonctions métier.

## 5. Composants UI principaux

- **Bouton primaire** : rouge Afriland `#C1121F`, texte blanc, utilisé pour une action principale comme importer, enregistrer ou traiter.
- **Bouton secondaire** : surface neutre avec bordure, utilisé pour actualiser, annuler, consulter ou ouvrir un détail.
- **Bouton de risque** : variante rouge sémantique pour une suppression, un rejet ou une action sensible.
- **Badge** : pastille arrondie avec un point coloré ; les classes `b-ok`, `b-warn`, `b-info`, `b-danger` et `b-muted` expriment les états.
- **Carte** : surface, bordure, rayon et ombre cohérents pour les KPI et les blocs de contenu.
- **Tableau** : en-tête secondaire, contenu contrasté, références monospace et états sous forme de badges.
- **Formulaire** : champs sur fond de surface, bordure fine, rayon arrondi et focus rouge avec halo léger.
- **Alerte** : panneau ou toast avec couleur sémantique, message court et action claire.
- **Section de synthèse** : panneau d’aide ou grille KPI, utilisé notamment pour les dashboards et la page Avantages clients.

## 6. Application par module

### Tableaux de bord

Les dashboards utilisent les cartes KPI, les graphiques simples, les panneaux de synthèse et les liens vers les listes détaillées. Les couleurs sémantiques servent à distinguer performance, attente, risque et succès.

### Cashback Cartes

Les transactions, paiements, contrôles manuels et réclamations utilisent des tableaux, des badges d’état et des boutons d’action visibles. Les tickets/OCR conservent un traitement visuel discret de type **Legacy**.

### Fidélité Afriland

Les pages de fidélité reprennent les cartes, panneaux et tableaux communs. Les badges identifient les points, niveaux, vouchers, cartes prépayées et offres CLO sans créer une palette indépendante.

### Contrôle & Audit

Les alertes fraude utilisent principalement le rouge de risque, tandis que les journaux d’audit utilisent le bleu informationnel. Les détails techniques restent lisibles mais ne doivent jamais exposer de données sensibles.

### Administration

Les utilisateurs, paramètres, profil, finance et modules techniques utilisent les mêmes panneaux et formulaires. Les opérations sensibles doivent être accompagnées d’un état clair et, si nécessaire, d’une confirmation.

### Intégration

Les pages Webhooks et API partenaires utilisent les badges informationnels et les blocs de documentation courts. Les exemples de payload doivent employer `maskedCard` et `cardHash` fictif, jamais un numéro complet.

## 7. Principes de cohérence

- Garder les couleurs Afriland et leurs usages sémantiques.
- Ne pas multiplier les couleurs sans nécessité fonctionnelle.
- Garder des pages sobres, avec une information priorisée.
- Garder les modules Cashback Cartes et Fidélité Afriland bien séparés.
- Éviter les textes longs et les répétitions ; privilégier une aide courte.
- Ne jamais afficher ni stocker un PAN complet dans l’interface ou les exemples.
- Utiliser uniquement `maskedCard` pour une carte affichée.
- Garder les tableaux lisibles, avec détails secondaires dans un panneau ou une vue dédiée.
- Garder les actions importantes visibles et associées à un libellé explicite.
- Conserver le contraste, le focus clavier et une lecture correcte en mode sombre.

## 8. Recommandations d’évolution

- Documenter les variables CSS directement dans le bloc de styles ou dans une référence dédiée.
- Centraliser les couleurs et éviter les valeurs hexadécimales isolées dans les fonctions JavaScript.
- Maintenir une cohérence visuelle entre Cashback Cartes, Fidélité Afriland et Avantages clients.
- Réutiliser les mêmes styles pour les badges, boutons, tableaux, panneaux et cartes KPI.
- Prévoir une version responsive propre pour les tableaux larges et les actions nombreuses.
- Ajouter des tests visuels ou des contrôles automatisés pour prévenir l’exposition de PAN et de secrets.
- Vérifier les contrastes lors de toute nouvelle couleur ou variante de thème.

