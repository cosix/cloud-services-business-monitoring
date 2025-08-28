package com.cimparato.csbm.service;

import com.cimparato.csbm.dto.notification.NotificationDTO;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;

/**
 * Servizio per la deduplicazione dei messaggi in un sistema di messaggistica distribuito.
 *
 * Questo servizio risolve il problema della duplicazione dei messaggi in Kafka, garantendo
 * che ogni messaggio venga elaborato una sola volta anche in presenza di ritrasmissioni,
 * retry o elaborazioni parallele.
 *
 * Utilizza una cache in-memory con TTL (Time-To-Live) per tenere traccia dei messaggi
 * già elaborati, identificati tramite un hash MD5 del loro contenuto. Questo approccio
 * è efficiente e non richiede infrastrutture esterne come database relazionali.
 *
 * La cache è configurata per mantenere gli ID dei messaggi per un periodo limitato
 * (tipicamente 24 ore), dopo il quale vengono automaticamente rimossi per evitare
 * una crescita illimitata della memoria utilizzata.
 */
@Service
@RequiredArgsConstructor
public class MessageDeduplicationService {

    private final Cache<String, Boolean> processedMessageCache;

    public boolean isProcessed(String messageId) {
        return processedMessageCache.getIfPresent(messageId) != null;
    }

    public void markAsProcessed(String messageId) {
        processedMessageCache.put(messageId, Boolean.TRUE);
    }

    /**
     * Genera un identificatore univoco (hash MD5) per una notifica basato sul suo contenuto.
     *
     * L'hash MD5 generato dalle proprietà della notifica (customerId, type, content, recipient)
     * è deterministico e univoco, permettendo l'implementazione dell'idempotenza senza dipendere
     * da database esterni.
     *
     * @param notification l'oggetto NotificationDTO da cui generare l'ID
     * @return hash MD5 che rappresenta univocamente il contenuto della notifica
     */
    public String generateMessageId(NotificationDTO notification) {
        return DigestUtils.md5Hex(
                notification.getCustomerId() + ":" +
                        notification.getType() + ":" +
                        notification.getContent() + ":" +
                        (notification.getRecipient() != null ? notification.getRecipient() + ":" : "") +
                        notification.getCreatedAt()
        );
    }

    /**
     * Restituisce le statistiche della cache di deduplicazione.
     *
     * @return le statistiche della cache
     */
    public CacheStats getCacheStats() {
        return processedMessageCache.stats();
    }
}
