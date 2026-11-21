"""M2 outbox delivery must survive redelivery and reject malformed messages."""

from __future__ import annotations

import json
import os
import time
from types import SimpleNamespace
from unittest.mock import Mock
from uuid import uuid4

import pika
import psycopg
from psycopg import sql
from psycopg.conninfo import make_conninfo
import pytest

from oep_ai_tutor import index_consumer
from oep_ai_tutor.index_jobs import get_index_job
from oep_ai_tutor.migrate import migrate


def _event() -> dict:
    return {"event_id": str(uuid4()), "content_id": str(uuid4()),
            "course_id": str(uuid4()), "lesson_id": str(uuid4()),
            "version_number": 1, "action": "UPSERT"}


def _delivery(event: dict, tag: int = 1):
    return (SimpleNamespace(delivery_tag=tag),
            pika.BasicProperties(message_id=event["event_id"], content_type="application/json"),
            json.dumps(event).encode())


@pytest.mark.parametrize("bad_body", [
    b"not-json", b'{"event_id":"first","event_id":"second"}',
    b"{}", b"[1,2]",
])
def test_malformed_delivery_goes_to_dead_letter(monkeypatch, bad_body: bytes) -> None:
    channel = Mock()
    monkeypatch.setattr(index_consumer.psycopg, "connect", Mock())
    method, properties, _ = _delivery(_event())
    index_consumer.receive(channel, method, properties, bad_body, "unused")
    channel.basic_reject.assert_called_once_with(delivery_tag=1, requeue=False)
    channel.basic_ack.assert_not_called()
    index_consumer.psycopg.connect.assert_not_called()


def test_bad_message_id_and_boolean_version_are_rejected(monkeypatch) -> None:
    channel = Mock()
    monkeypatch.setattr(index_consumer.psycopg, "connect", Mock())
    event = _event()
    event["version_number"] = True
    method, properties, body = _delivery(event)
    index_consumer.receive(channel, method, properties, body, "unused")
    event["version_number"] = 1
    method, properties, body = _delivery(event, tag=2)
    properties.message_id = str(uuid4())
    index_consumer.receive(channel, method, properties, body, "unused")
    assert channel.basic_reject.call_count == 2
    channel.basic_ack.assert_not_called()


def test_database_failure_leaves_delivery_unacked(monkeypatch) -> None:
    channel = Mock()
    monkeypatch.setattr(index_consumer.psycopg, "connect", Mock(side_effect=psycopg.OperationalError("down")))
    with pytest.raises(psycopg.OperationalError):
        index_consumer.receive(channel, *_delivery(_event()), "unused")
    channel.basic_ack.assert_not_called()
    channel.basic_reject.assert_not_called()


def test_unexpected_database_value_error_is_not_dead_lettered(monkeypatch) -> None:
    channel = Mock()
    monkeypatch.setattr(index_consumer.psycopg, "connect", Mock(side_effect=ValueError("bad DSN")))
    with pytest.raises(ValueError, match="bad DSN"):
        index_consumer.receive(channel, *_delivery(_event()), "unused")
    channel.basic_ack.assert_not_called()
    channel.basic_reject.assert_not_called()


def test_ack_follows_durable_receipt(monkeypatch) -> None:
    channel = Mock()
    order = []
    connection = Mock()
    connection.__enter__ = Mock(return_value=connection)
    connection.__exit__ = Mock(return_value=None)
    monkeypatch.setattr(index_consumer.psycopg, "connect", Mock(return_value=connection))
    monkeypatch.setattr(index_consumer, "enqueue_index_event", lambda *_: order.append("persist"))
    connection.__exit__.side_effect = lambda *_: order.append("commit")
    channel.basic_ack.side_effect = lambda **_: order.append("ack")
    index_consumer.receive(channel, *_delivery(_event()), "unused")
    assert order == ["persist", "commit", "ack"]


@pytest.mark.integration
def test_rabbit_delivery_persists_once_and_dead_letters_invalid_event(monkeypatch) -> None:
    base_dsn = os.environ.get("AI_TUTOR_TEST_DATABASE_URL")
    rabbit_url = os.environ.get("AI_TUTOR_TEST_RABBITMQ_URL")
    if not base_dsn or not rabbit_url:
        pytest.skip("Set disposable AI_TUTOR_TEST_DATABASE_URL and AI_TUTOR_TEST_RABBITMQ_URL")
    database_name = "oep_ai_broker_" + uuid4().hex
    queue_suffix = uuid4().hex
    monkeypatch.setattr(index_consumer, "QUEUE", "oep.ai-tutor.test." + queue_suffix)
    monkeypatch.setattr(index_consumer, "DEAD_LETTER_QUEUE", "oep.ai-tutor.test.dlq." + queue_suffix)
    with psycopg.connect(base_dsn, autocommit=True) as admin:
        admin.execute(sql.SQL("CREATE DATABASE {}").format(sql.Identifier(database_name)))
    try:
        dsn = make_conninfo(base_dsn, dbname=database_name)
        assert migrate(dsn) == [1, 2, 3]
        with pika.BlockingConnection(pika.URLParameters(rabbit_url)) as broker:
            channel = broker.channel()
            index_consumer.declare_topology(channel)
            event = _event()
            method, properties, body = _delivery(event)
            channel.basic_publish(index_consumer.EXCHANGE, index_consumer.ROUTING_KEY,
                                  body, properties=pika.BasicProperties(
                                      message_id=properties.message_id,
                                      content_type=properties.content_type,
                                      delivery_mode=2))
            for _ in range(2):
                received_method, received_properties, received_body = channel.basic_get(
                    index_consumer.QUEUE, auto_ack=False)
                if received_method is None:
                    # The second delivery models outbox relay redelivery after a lost ack.
                    channel.basic_publish(index_consumer.EXCHANGE, index_consumer.ROUTING_KEY,
                                          body, properties=properties)
                    received_method, received_properties, received_body = channel.basic_get(
                        index_consumer.QUEUE, auto_ack=False)
                assert received_method is not None
                index_consumer.receive(channel, received_method, received_properties,
                                       received_body, dsn)
            with psycopg.connect(dsn) as db:
                row = db.execute("SELECT id FROM ai_tutor.ai_index_jobs WHERE event_id = %s",
                                 (event["event_id"],)).fetchall()
                assert len(row) == 1
                assert get_index_job(db, row[0][0])["status"] == "QUEUED"

            bad = {**event, "version_number": 2}
            channel.basic_publish(index_consumer.EXCHANGE, index_consumer.ROUTING_KEY,
                                  json.dumps(bad).encode(), properties=properties)
            received_method, received_properties, received_body = channel.basic_get(
                index_consumer.QUEUE, auto_ack=False)
            index_consumer.receive(channel, received_method, received_properties,
                                   received_body, dsn)
            dead_method = dead_body = None
            for _ in range(20):
                dead_method, _, dead_body = channel.basic_get(
                    index_consumer.DEAD_LETTER_QUEUE, auto_ack=True)
                if dead_method is not None:
                    break
                time.sleep(0.05)
            assert dead_method is not None and json.loads(dead_body) == bad
            channel.queue_delete(queue=index_consumer.QUEUE)
            channel.queue_delete(queue=index_consumer.DEAD_LETTER_QUEUE)
    finally:
        with psycopg.connect(base_dsn, autocommit=True) as admin:
            admin.execute(sql.SQL("DROP DATABASE {} WITH (FORCE)").format(
                sql.Identifier(database_name)))
