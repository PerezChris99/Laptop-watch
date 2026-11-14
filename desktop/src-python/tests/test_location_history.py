"""Additional signed GNSS route regression source.

The LastSeenStore tests exercise actual stored gpsd fixes. The gateway
authentication route test is part of test_gateway.py where the fixture exists.
"""
from laptopwatch.tracking import LastSeenStore, LocationFix


def test_historical_gnss_is_bounded_and_never_live(tmp_path):
    store = LastSeenStore(tmp_path / 'gps-history.db')
    try:
        assert store.recent() == []
        now = 1_700_000_100_000
        for index in range(110):
            fix = LocationFix(latitude=0.2 + index / 10_000,
                              longitude=32.4 + index / 10_000,
                              accuracy_m=8.0, observed_at_ms=now - 10_000 + index,
                              provider='gpsd-gnss')
            store.add(fix, now_ms=now)
        recent = store.recent(limit=1000)
        assert len(recent) == 100
        assert recent[0]['event_id'] > recent[-1]['event_id']
        assert all(item['is_live_fix'] is False for item in recent)
        assert all(item['provider'] == 'gpsd-gnss' for item in recent)
        assert len(store.recent(limit=1)) == 1
    finally:
        store.close()
