package com.oep.coremodel.event;

import java.time.Instant;

/** Marker for every message sent over the event bus. Serialised as JSON. */
public interface DomainEvent {
    Instant occurredAt();
}
