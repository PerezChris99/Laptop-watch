import test from 'node:test';
import assert from 'node:assert/strict';
import {generateKeyPairSync,sign as signEd,verify as verifyEd,createPublicKey} from 'node:crypto';
import {verifySignedLease,validateLicenseClaims,LicenseUnavailable} from '../src/licensing/claims.js';
const account='11111111-1111-4111-8111-111111111111';
const device='22222222-2222-4222-8222-222222222222',now=1784210000;
const kp=generateKeyPairSync('ed25519'),pub=kp.publicKey.export({type:'spki',format:'der'}).subarray(-32);
const b64=b=>Buffer.from(b).toString('base64url');
const adapters={decode:x=>Buffer.from(x,'base64url'),utf8:x=>Buffer.from(x).toString('utf8'),
  verify:(p,m,s)=>verifyEd(null,Buffer.from(m),createPublicKey({key:Buffer.concat([
    Buffer.from('302a300506032b6570032100','hex'),Buffer.from(p)]),format:'der',type:'spki'}),Buffer.from(s))};
const claims=()=>({v:1,issuer:'LaptopWatch',account_id:account,device_id:device,
 issued_at:now,expires_at:now+3590,max_devices:3,plan:'pro',subscription_id:'sub_'+'a'.repeat(26)});
function seal(v){const body=b64(Buffer.from(JSON.stringify(v)));
 return body+'.'+b64(signEd(null,Buffer.from(body),kp.privateKey));}
test('verifies the pinned issuer, device and bounded claims',()=>{
 assert.equal(verifySignedLease(seal(claims()),b64(pub),account,device,adapters,now).plan,'pro');
 for(const wrong of [{...claims(),device_id:account},{...claims(),max_devices:101},
   {...claims(),plan:'free'},{...claims(),expires_at:now+7200},
   {...claims(),issued_at:now+61},{...claims(),subscription_id:'bad'}])
  assert.throws(()=>verifySignedLease(seal(wrong),b64(pub),account,device,adapters,now),LicenseUnavailable);
 assert.throws(()=>verifySignedLease(seal(claims()),b64(Buffer.alloc(32)),account,device,adapters,now),LicenseUnavailable);
 assert.throws(()=>verifySignedLease(seal(claims())+'x',b64(pub),account,device,adapters,now),LicenseUnavailable);
 assert.throws(()=>validateLicenseClaims(claims(),account,device,now+3590),LicenseUnavailable);
});
