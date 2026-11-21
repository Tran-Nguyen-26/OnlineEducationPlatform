"""RabbitMQ intake for M2 outbox events; acknowledge only after durable job receipt."""

from __future__ import annotations

import json
import logging
import os
import time
from uuid import UUID

import pika
import psycopg

from oep_ai_tutor.errors import AiTutorError
from oep_ai_tutor.index_jobs import IndexEvent, enqueue_index_event


LOG = logging.getLogger(__name__)
EXCHANGE = "oep.events"
ROUTING_KEY = "ai.index.requested"
QUEUE = "oep.ai-tutor.index"
DEAD_LETTER_EXCHANGE = "oep.events.dlx"
DEAD_LETTER_KEY = "ai.index.dead"
DEAD_LETTER_QUEUE = "oep.ai-tutor.index.dlq"
MAX_EVENT_BYTES = 4096
EVENT_FIELDS = frozenset(("event_id", "content_id", "course_id", "lesson_id",
                          "version_number", "action"))


def _unique_object(pairs: list[tuple[str, object]]) -> dict:
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate event field")
        result[key] = value
    return result


def parse_event(body: bytes, message_id: str | None, content_type: str | None) -> IndexEvent:
    if content_type != "application/json" or len(body) > MAX_EVENT_BYTES:
        raise ValueError("Invalid event content type or size")
    payload = json.loads(body.decode("utf-8"), object_pairs_hook=_unique_object)
    if not isinstance(payload, dict) or payload.keys() != EVENT_FIELDS:
        raise ValueError("Invalid index event fields")
    if any(type(payload[key]) is not str for key in (
        "event_id", "content_id", "course_id", "lesson_id", "action"
    )):
        raise ValueError("Invalid index event identifier")
    event = IndexEvent(
        *(UUID(payload[key]) for key in ("event_id", "content_id", "course_id", "lesson_id")),
        payload["version_number"], payload["action"],
    )
    if message_id != str(event.event_id):
        raise ValueError("Outbox message ID differs from event ID")
    return event


def receive(channel, method, properties, body: bytes, database_url: str) -> None:
    """Invalid/conflicting events go to the DLQ; database failures remain unacked."""
    try:
        event = parse_event(body, properties.message_id, properties.content_type)
    except (ValueError, UnicodeError) as exc:
        LOG.warning("Rejecting malformed AI index event: %s", exc)
        channel.basic_reject(delivery_tag=method.delivery_tag, requeue=False)
        return
    try:
        with psycopg.connect(database_url, autocommit=True) as connection:
            enqueue_index_event(connection, event)
    except AiTutorError as exc:
        if exc.code != "INDEX_EVENT_CONFLICT":
            raise
        LOG.warning("Rejecting conflicting AI index event ID")
        channel.basic_reject(delivery_tag=method.delivery_tag, requeue=False)
        return
    channel.basic_ack(delivery_tag=method.delivery_tag)


def declare_topology(channel) -> None:
    channel.exchange_declare(exchange=EXCHANGE, exchange_type="topic", durable=True)
    channel.exchange_declare(exchange=DEAD_LETTER_EXCHANGE, exchange_type="direct", durable=True)
    channel.queue_declare(queue=QUEUE, durable=True, arguments={
        "x-dead-letter-exchange": DEAD_LETTER_EXCHANGE,
        "x-dead-letter-routing-key": DEAD_LETTER_KEY,
    })
    channel.queue_declare(queue=DEAD_LETTER_QUEUE, durable=True)
    channel.queue_bind(queue=QUEUE, exchange=EXCHANGE, routing_key=ROUTING_KEY)
    channel.queue_bind(queue=DEAD_LETTER_QUEUE, exchange=DEAD_LETTER_EXCHANGE,
                       routing_key=DEAD_LETTER_KEY)


def consume_once(rabbit_url: str, database_url: str) -> None:
    parameters = pika.URLParameters(rabbit_url)
    parameters.heartbeat = 30
    parameters.blocked_connection_timeout = 30
    with pika.BlockingConnection(parameters) as connection:
        channel = connection.channel()
        declare_topology(channel)
        channel.basic_qos(prefetch_count=1)
        channel.basic_consume(
            queue=QUEUE, auto_ack=False,
            on_message_callback=lambda ch, method, props, body:
                receive(ch, method, props, body, database_url),
        )
        channel.start_consuming()


def main() -> None:
    rabbit_url = os.environ.get("AI_TUTOR_RABBITMQ_URL", "")
    database_url = os.environ.get("AI_TUTOR_DATABASE_URL", "")
    if not rabbit_url or not database_url:
        raise ValueError("AI_TUTOR_RABBITMQ_URL and AI_TUTOR_DATABASE_URL are required")
    logging.basicConfig(level=logging.INFO)
    delay = 1
    while True:
        try:
            consume_once(rabbit_url, database_url)
            delay = 1
        except KeyboardInterrupt:
            return
        except (pika.exceptions.AMQPError, psycopg.Error, OSError) as exc:
            LOG.warning("AI index intake disconnected; reconnecting in %s seconds: %s", delay, exc)
            time.sleep(delay)
            delay = min(delay * 2, 30)


if __name__ == "__main__":
    main()
