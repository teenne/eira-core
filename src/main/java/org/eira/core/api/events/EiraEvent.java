package org.eira.core.api.events;

/**
 * Marker interface for all Eira events.
 *
 * All events that can be published or subscribed to via the Eira event bus
 * must implement this interface.
 *
 * <p>This interface is in the 'events' package (plural) for compatibility with
 * Eira Relay. The 'event' package (singular) contains the full-featured API.
 *
 * <p>This interface extends the rich EiraEvent interface to ensure events defined
 * here can be published through the main event bus.
 */
public interface EiraEvent extends org.eira.core.api.event.EiraEvent {
    // Marker interface - no methods required
}
