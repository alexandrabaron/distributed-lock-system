# Analyse de la Synchronisation : Sujet vs Code Actuel

## Texte du Sujet (pertinent)

```
§When the leader server handling preempt/release requests: 
§ If needed, modify its map and sends a request propose to all follower servers

§ When a follower server receives a request propose
§ -- modify its local map
§ -- check the request is pending or not
§ -- if the request is pending, send an answer to the client
```

## Code Actuel

### Flux Actuel pour une Requête LOCK/UNLOCK depuis un Follower

1. **Client → Follower** : LOCK request
2. **Follower** : Marque comme pending, garde connexion ouverte
3. **Follower → Leader** : Forward request (asynchrone)
4. **Leader** :
   - Valide et met à jour sa map
   - Appelle `notifyFollowers()` (asynchrone, dans thread pool)
   - Retourne `SUCCESS` immédiatement au follower
5. **Leader → Tous les Followers** : Envoie SYNC (asynchrone, en parallèle)
6. **Follower qui a forwardé** :
   - Reçoit `SUCCESS` du leader (mais ne répond pas encore au client)
   - Attend le SYNC
7. **Tous les Followers** :
   - Reçoivent SYNC
   - Met à jour leur map locale
   - Envoient ACK au leader
8. **Follower qui a forwardé** :
   - Reçoit SYNC
   - Met à jour sa map
   - Vérifie si pending → OUI
   - Répond `SUCCESS` au client
   - Ferme connexion

### Points d'Attention

1. **Le leader retourne SUCCESS avant que les SYNC soient envoyés**
   - `notifyFollowers()` est appelé mais s'exécute dans des threads séparés
   - Le leader retourne `SUCCESS` immédiatement après l'appel à `notifyFollowers()`
   - Les SYNC sont envoyés en parallèle, de manière asynchrone

2. **Le leader n'attend PAS les ACK avant de retourner SUCCESS**
   - Chaque thread dans `notifyFollowers()` attend son propre ACK
   - Mais le thread principal du leader ne les attend pas

3. **Le follower qui forwarde attend le SYNC**
   - C'est correct selon le sujet
   - Le follower répond au client seulement après avoir reçu le SYNC

## Question : Le Sujet Requiert-il une Synchronisation Plus Forte ?

### Interprétation 1 : Asynchrone (comme actuellement)
- Le sujet dit juste "sends a request propose" (pas "waits for")
- Le leader envoie et continue
- Le follower attend le SYNC avant de répondre
- ✅ **Implémentation actuelle semble correcte**

### Interprétation 2 : Synchrone (attendre ACK)
- Le leader devrait attendre les ACK de tous les followers avant de retourner SUCCESS
- Cela garantirait que TOUS les followers ont reçu et appliqué le changement
- ⚠️ **Plus strict, mais le sujet ne le dit pas explicitement**

### Interprétation 3 : Au moins l'ACK du follower qui a forwardé
- Le leader devrait attendre l'ACK du follower qui a forwardé la requête
- Cela garantirait que ce follower a bien reçu le SYNC
- ⚠️ **Pas explicitement mentionné dans le sujet**

## Recommandation

Le code actuel semble **CORRECT** selon le sujet car :

1. ✅ Le leader envoie SYNC à tous les followers
2. ✅ Le follower qui a forwardé attend le SYNC
3. ✅ Le follower vérifie pending et répond au client après SYNC

**MAIS** il y a une **petite incohérence théorique** :

- Le leader retourne `SUCCESS` au follower qui forwarde **avant** d'avoir reçu confirmation que les SYNC ont été envoyés
- En pratique, les SYNC sont envoyés très rapidement (thread pool), mais techniquement il y a une petite fenêtre de temps

**Question pour l'utilisateur** : Voulez-vous que le leader attende les ACK de tous les followers avant de retourner SUCCESS ? Cela rendrait la réplication synchrone, ce qui serait plus cohérent avec le mécanisme pending.

