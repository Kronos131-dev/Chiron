# 🏛️ Chiron - Le Sanctuaire de l'Entraînement

![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)
![Angular](https://img.shields.io/badge/Angular-17%2B-dd0031.svg)
![AI](https://img.shields.io/badge/AI-Mistral%20%7C%20LangChain4j-ffb779.svg)

**Chiron** est une application web full-stack innovante dédiée à la gestion, la planification et le suivi des entraînements sportifs. Propulsée par une Intelligence Artificielle intégrée agissant comme un coach martial et sans concession, Chiron permet aux athlètes d'enregistrer leurs performances par la voix ou le texte en temps réel.

---

## ✨ Fonctionnalités Principales

* 🤖 **Coach IA Intégré (Chiron)** : Une interface conversationnelle stricte (alimentée par Mistral AI via LangChain4j) pour enregistrer vos séances de sport à la volée.
* 🎙️ **Reconnaissance Vocale** : Enregistrez vos séries, répétitions et poids directement par la voix pendant l'effort (support de la Web Speech API avec dictionnaire de musculation personnalisé).
* 📖 **Annales (Journal)** : Historique complet de vos séances passées, groupées par semaine, avec un suivi précis du volume d'entraînement.
* 📋 **Programmes & Modèles** : Créez, éditez et lancez des modèles de routines d'entraînement réutilisables.
* 🛡️ **Profils & Rangs** : Un système de progression dynamique (Citoyen, Athlète, Spartiate, Héros, Olympien) basé sur votre volume mensuel de séries.
* 🤝 **L'Agora & Système de Coaching** : Découvrez d'autres athlètes. Gardez votre profil privé ou rendez-le public. Attribuez des droits de modification exclusifs à d'autres utilisateurs en les nommant "Coach".

---

## 🏗️ Architecture Technique

Le projet est divisé en deux parties distinctes :

### Backend (`chiron-back`)
* **Framework** : Java / Spring Boot
* **Sécurité** : Spring Security & JWT (JSON Web Tokens) statiques sans état.
* **Persistance** : Spring Data JPA / Hibernate (Base de données relationnelle).
* **Intelligence Artificielle** : LangChain4j intégré avec le modèle `mistral-small-latest` (Mistral AI). L'IA est dotée d'outils (Function Calling) pour lire et écrire en base de données.
* **Documentation API** : Swagger UI / OpenAPI 3.0.

### Frontend (`chiron-front`)
* **Framework** : Angular (Standalone Components)
* **Style** : Tailwind CSS pour un design moderne, sombre et "héroïque".
* **État** : Utilisation intensive des `Signals` Angular pour une réactivité optimale.
* **Routage** : Protégé par des Guards (`authGuard`) et intercepteurs HTTP pour l'injection du JWT.

---

## 🚀 Installation & Lancement (Environnement de Développement)

### Prérequis
* Java 17+
* Node.js (v18+) et npm/yarn
* Angular CLI
* Une base de données relationnelle configurée (selon `application.yml`)
* Une clé API Mistral AI valide

### 1. Configuration du Backend

```bash
cd chiron-back
```
* Éditez le fichier de configuration (ex: `src/main/resources/application.yml`) pour y insérer vos identifiants de base de données.
* Assurez-vous que la variable d'environnement ou la propriété `langchain4j.mistral-ai.chat-model.api-key` contient votre clé API Mistral.
* Lancez l'application Spring Boot :
```bash
./mvnw spring-boot:run
```
Le backend sera disponible sur `http://localhost:9090`.
La documentation Swagger est accessible sur `http://localhost:9090/swagger-ui.html`.

### 2. Configuration du Frontend

```bash
cd chiron-front
npm install
```
* Vérifiez que le fichier `src/environments/environment.ts` pointe bien vers votre backend (par défaut `http://localhost:9090/api`).
* Lancez le serveur de développement Angular :
```bash
ng serve
```
Le frontend sera disponible sur `http://localhost:4200`.

---

## 🔒 Gestion des Accès & Rôles

* **Utilisateur Standard (`ROLE_USER`)** : Peut gérer ses propres séances, modifier son profil, chercher d'autres utilisateurs et nommer des coachs.
* **Coach** : Un utilisateur standard désigné comme "Coach" par un autre utilisateur. Il peut contourner les restrictions de visibilité (profil privé) et modifier directement les programmes de son élève.
* **Administrateur (`ROLE_ADMIN`)** : Dispose de droits de lecture globaux sur l'ensemble des historiques et programmes.

---



