from datetime import datetime, timezone
import json
import math
import sqlite3
import time
import pytest
from laptopwatch.tracking import LocationFix, LastSeenStore, GpsdReceiver, TrackingService

NOW = 1_700_000_000_000


def good_fix(offset_ms=0):
    return LocationFix(0.3497, 32.6097, 8.5, NOW + offset_ms)


def test_rejects_impossible_or_fabricated_accuracy(tmp_path):
    store = LastSeenStore(tmp_path/'pos.db')
    for fix in [LocationFix(91, 1, 5, NOW), LocationFix(1, 181, 5, NOW),
                LocationFix(1, 2, 0, NOW), LocationFix(float('nan'), 2, 5, NOW),
                LocationFix(1, 2, 5, NOW-350000), LocationFix(1, 2, 5, NOW, 'fake')]:
        with pytest.raises(ValueError):
            store.add(fix, now_ms=NOW)
    assert store.latest(now_ms=NOW) is None
    store.close()


def test_last_seen_persists_across_restarts_and_expires(tmp_path):
    path = tmp_path/'track.db'
    s = LastSeenStore(path)
    idx = s.add(good_fix(), now_ms=NOW)
    assert idx > 0
    assert s.latest(now_ms=NOW)['is_live_fix']
    s.close()
    s = LastSeenStore(path)
    l = s.latest(now_ms=NOW+3600_000)
    assert l['is_live_fix'] is False
    assert l['age_ms'] == 3600_000
    assert l['latitude'] == 0.3497
    s.close()


def test_offline_queue_ack_only_on_remote_receipt(tmp_path):
    s = LastSeenStore(tmp_path/'track.db')
    s.add(good_fix(), now_ms=NOW)
    svc = TrackingService(s, receiver=None)
    assert svc.on_secure_transport_available(lambda eid, fix: False) == 0
    assert len(s.pending()) == 1
    calls=[]
    assert svc.on_secure_transport_available(lambda eid, fix: calls.append(eid) or True) == 1
    assert len(s.pending()) == 0
    assert calls
    s.close()


def test_reconnect_exception_keeps_fix_queued(tmp_path):
    s = LastSeenStore(tmp_path/'track.db')
    s.add(good_fix(), now_ms=NOW)
    def failure(eid, fix):
        raise ConnectionError('still offline')
    assert TrackingService(s, None).on_secure_transport_available(failure) == 0
    assert len(s.pending()) == 1
    s.close()


def test_valid_gpsd_fix_never_fabricates_precision():
    time_iso=datetime.fromtimestamp(NOW/1000, timezone.utc).isoformat()
    obj={'class':'TPV','mode':3,'lat':0.3497,'lon':32.6097,'epx':15,'epy':20,'time':time_iso}
    result=GpsdReceiver.parse_tpv(obj,now_ms=NOW)
    assert result.accuracy_m == 25.0
    assert result.provider == 'gpsd-gnss'
    assert GpsdReceiver.parse_tpv({**obj, 'epx':None},now_ms=NOW) is None
    assert GpsdReceiver.parse_tpv({**obj, 'time': '2020-01-01T00:00:00Z'},now_ms=NOW) is None
    assert GpsdReceiver.parse_tpv({**obj, 'mode':1},now_ms=NOW) is None


def test_gpsd_rejects_remote_source():
    with pytest.raises(ValueError):
        GpsdReceiver('8.8.8.8')


def test_refresh_uses_receiver_and_queues(tmp_path):
    class Receiver:
        def read_fix(self, *, now_ms=None): return good_fix()
    s=LastSeenStore(tmp_path/'track.db')
    service=TrackingService(s,Receiver())
    assert service.refresh(now_ms=NOW)['is_live_fix']
    assert len(s.pending()) == 1
    s.close()


def test_opt_in_location_poller_samples_and_stops(tmp_path):
    import threading
    from laptopwatch.tracking import LocationPoller
    class Receiver:
        def __init__(self): self.calls=0; self.first=threading.Event()
        def read_fix(self, *, now_ms=None):
            self.calls += 1
            self.first.set()
            return None
    r=Receiver()
    s=LastSeenStore(tmp_path/'pos.db')
    poller=LocationPoller(TrackingService(s,r),interval_seconds=1.0)
    poller.start()
    assert r.first.wait(timeout=1)
    poller.stop()
    assert r.calls>=1
    assert s.latest() is None
    s.close()
