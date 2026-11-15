import uuid
from datetime import datetime, timezone
import pytest
from laptopwatch_billing.app import Store
from laptopwatch_billing.reconcile import reconcile, ReconciliationError

NOW = 1784210000
SUB = 'sub_'+'s'*26
CUSTOMER = 'ctm_'+'c'*26
PRICE = 'pri_'+'p'*26

def stamp(sec):
    return datetime.fromtimestamp(sec, timezone.utc).isoformat().replace('+00:00','Z')

class Provider:
    def __init__(self, subscription, transactions):
        self.sub, self.tx = subscription, transactions
    def subscription(self, sub):
        return self.sub
    def transactions(self, sub):
        return self.tx

@pytest.fixture
def setup(tmp_path):
    store = Store(str(tmp_path/'billing.db'))
    account = str(uuid.uuid4())
    store.create_account(account)
    with store.tx() as db:
        db.execute('INSERT INTO subscriptions VALUES (?,?,?,?,?,?,?)',
                   (SUB,account,CUSTOMER,PRICE,'past_due',NOW+100,(NOW-200)*1_000_000))
    subscription = {'id':SUB,'customer_id':CUSTOMER,'status':'active',
        'items':[{'price':{'id':PRICE}}],
        'updated_at':stamp(NOW-20),
        'current_billing_period':{'ends_at':stamp(NOW+3600)}}
    transaction = {'id':'txn_'+'t'*26,'status':'completed','subscription_id':SUB,
        'customer_id':CUSTOMER,
        'billing_period':{'starts_at':stamp(NOW-30),'ends_at':stamp(NOW+3600)}}
    yield store, subscription, transaction
    store.db.close()

def test_dry_run_and_verified_payment_are_separate(setup):
    store, sub, tx = setup
    provider = Provider(sub,[tx]); prices = {PRICE:('pro',3)}
    dry = reconcile(store,provider,SUB,prices,now=NOW)
    assert dry['effective_status']=='active' and dry['applied'] is False
    assert store.db.execute('SELECT status FROM subscriptions').fetchone()[0]=='past_due'
    done = reconcile(store,provider,SUB,prices,now=NOW,apply=True)
    assert done['applied'] and done['confirmed_payments']==1
    assert store.db.execute('SELECT status FROM subscriptions').fetchone()[0]=='active'
    assert store.db.execute('SELECT COUNT(*) FROM paid_transactions').fetchone()[0]==1
    assert reconcile(store,provider,SUB,prices,now=NOW,apply=True)['applied']
    assert store.db.execute('SELECT COUNT(*) FROM paid_transactions').fetchone()[0]==1

def test_unpaid_renewal_is_denied(setup):
    store, sub, tx = setup
    tx['billing_period']['ends_at'] = stamp(NOW+1800)
    result = reconcile(store,Provider(sub,[tx]),SUB,{PRICE:('pro',3)},now=NOW,apply=True)
    assert result['effective_status']=='past_due'

def test_provider_customer_mismatch_fails_closed(setup):
    store, sub, tx = setup
    sub['customer_id'] = 'ctm_'+'x'*26
    with pytest.raises(ReconciliationError):
        reconcile(store,Provider(sub,[tx]),SUB,{PRICE:('pro',3)},apply=True,now=NOW)
