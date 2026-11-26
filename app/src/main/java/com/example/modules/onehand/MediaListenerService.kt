package com.example.modules.onehand

import android.service.notification.NotificationListenerService

/**
 * Accès aux notifications : sert uniquement de « clé » à Android pour lister les sessions
 * média actives (source audio et titre en cours de la zone audio). Aucune notification
 * n’est lue ni enregistrée.
 */
class MediaListenerService : NotificationListenerService()
