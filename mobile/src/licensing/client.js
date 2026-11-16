// @ts-check
/** Optional mobile entitlement checks. Local signed LAN features never require payment. */
import { bytesToUtf8, utf8ToBytes } from '@noble/hashes/utils';
import { sha256 } from '@noble/hashes/sha256';
import { toByteArray } from 'base64-js';
import { b64url, secureRandom16, publicKey, sign, verify } from '../crypto/protocol.js';
import { getPreference, setPreference } from '../storage/db.js';
import { normalizedRelayUrl } from '../net/signedRelay.js';
import { verifySignedLease, LicenseUnavailable } from './claims.js';
import { TRUSTED_ISSUER_PUBLIC_KEY_B64URL } from './trust.js';

const UUID=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const PRICE=/^pri_[a-z0-9]{26}$/;
export const normalizeBillingUrl=normalizedRelayUrl;
function decode(value) {
  if (typeof value!=='string' || value.length>6144 || !/^[A-Za-z0-9_-]+$/.test(value))
    throw new LicenseUnavailable('Invalid base64url');
  const out=toByteArray(value.replace(/-/g,'+').replace(/_/g,'/')+'='.repeat((4-value.length%4)%4));
  if (b64url(out)!==value) throw new LicenseUnavailable('Noncanonical encoding');
  return out;
}
function identity(paired,accountId) {
  if (!paired?.approved || !UUID.test(accountId) || !UUID.test(paired.deviceId) ||
      !(paired.privateSeed instanceof Uint8Array) || paired.privateSeed.length!==32)
    throw new LicenseUnavailable('Approved phone and assigned account required');
}
export function verifyMobileLease(token,accountId,deviceId,now=Math.floor(Date.now()/1000)) {
  return verifySignedLease(token,TRUSTED_ISSUER_PUBLIC_KEY_B64URL,accountId,deviceId,
    {decode,utf8:bytesToUtf8,verify},now);
}
async function post(url,path,payload) {
  if (!['/v1/accounts/enroll','/v1/licenses/lease','/v1/billing/checkout-ticket','/v1/billing/portal','/v1/accounts/close'].includes(path))
    throw new LicenseUnavailable('Unsupported billing operation');
  const base=normalizeBillingUrl(url);
  const body=JSON.stringify(payload);
  if (body.length>4096) throw new LicenseUnavailable('Billing request too large');
  const ctrl=new AbortController(),timer=setTimeout(()=>ctrl.abort(),10000);
  try {
    const result=await fetch(base+path,{method:'POST',redirect:'error',cache:'no-store',
      headers:{'Content-Type':'application/json','Accept':'application/json'},body,signal:ctrl.signal});
    if (!result.ok || result.redirected || !result.url?.startsWith(base+path))
      throw new Error('Service rejected request');
    const value=await result.text();
    if (value.length>65536) throw new Error('Response too large');
    const parsed=JSON.parse(value);
    if (!parsed || typeof parsed!=='object' || Array.isArray(parsed)) throw new Error('Invalid response');
    return parsed;
  } catch (_) { throw new LicenseUnavailable('Billing service unavailable or untrusted'); }
  finally {clearTimeout(timer);}
}
function proof(paired,accountId,operation,priceId) {
  identity(paired,accountId);
  if (!['LICENSE','PORTAL','CHECKOUT','ACCOUNT-CLOSE'].includes(operation) ||
      (operation==='CHECKOUT' && !PRICE.test(priceId)))
    throw new LicenseUnavailable('Invalid billing operation');
  const timestamp=Math.floor(Date.now()/1000),nonce=b64url(secureRandom16());
  const transcript='LW-'+operation+'-1\n'+accountId+'\n'+paired.deviceId+'\n'+
    (operation==='CHECKOUT'?priceId+'\n':'')+timestamp+'\n'+nonce+'\n';
  const data={account_id:accountId,device_id:paired.deviceId,timestamp,nonce,
    signature:b64url(sign(paired.privateSeed,utf8ToBytes(transcript)))};
  if (operation==='CHECKOUT') data.price_id=priceId;
  return data;
}
export async function enrollCommercialDevice(paired,accountId,billingUrl,invitation) {
  identity(paired,accountId);
  if (typeof invitation!=='string' || invitation.length<24 || invitation.length>2048)
    throw new LicenseUnavailable('Invalid enrollment invitation');
  const pub=b64url(publicKey(paired.privateSeed)),timestamp=Math.floor(Date.now()/1000);
  const nonce=b64url(secureRandom16());
  const digest=Array.from(sha256(utf8ToBytes(invitation)),x=>x.toString(16).padStart(2,'0')).join('');
  const transcript='LW-ENROLL-1\n'+accountId+'\n'+paired.deviceId+'\n'+pub+'\n'+
    timestamp+'\n'+nonce+'\n'+digest+'\n';
  const result=await post(billingUrl,'/v1/accounts/enroll',{account_id:accountId,
    device_id:paired.deviceId,public_key:pub,invite_code:invitation,timestamp,nonce,
    signature:b64url(sign(paired.privateSeed,utf8ToBytes(transcript)))});
  if (result.enrolled!==true) throw new LicenseUnavailable('Enrollment denied');
}
export async function cachedCommercialLease(paired,accountId) {
  if (!paired?.approved || !UUID.test(accountId) || !TRUSTED_ISSUER_PUBLIC_KEY_B64URL) return null;
  try {return verifyMobileLease(await getPreference('commercial_signed_lease'),accountId,paired.deviceId);}
  catch (_) {return null;}
}
export async function refreshCommercialLease(paired,accountId,billingUrl) {
  if (!TRUSTED_ISSUER_PUBLIC_KEY_B64URL)
    throw new LicenseUnavailable('Commercial license issuer not provisioned in this build');
  const reply=await post(billingUrl,'/v1/licenses/lease',proof(paired,accountId,'LICENSE'));
  const claims=verifyMobileLease(reply.license,accountId,paired.deviceId);
  await setPreference('commercial_signed_lease',reply.license);
  return claims;
}
/** Close a paid account using a signed, explicit account-ID confirmation.
 * The backend records closure even if Paddle is offline; it returns the
 * outstanding cancellation status rather than falsely claiming success.
 */
export async function closeCommercialAccount(paired,accountId,billingUrl,confirmation) {
  identity(paired,accountId);
  if (confirmation!==accountId) throw new LicenseUnavailable('Account closure confirmation mismatch');
  const payload={...proof(paired,accountId,'ACCOUNT-CLOSE'),confirm_account_id:accountId};
  const response=await post(billingUrl,'/v1/accounts/close',payload);
  if (!['closed','already_closed'].includes(response.result) ||
      typeof response.provider_cancellation_required!=='boolean' ||
      !Number.isSafeInteger(response.cancellation_pending_count) ||
      response.cancellation_pending_count<0)
    throw new LicenseUnavailable('Account closure not confirmed');
  await forgetCommercialLicense();
  return response;
}

export async function forgetCommercialLicense() {
  await setPreference('commercial_signed_lease','');
  await setPreference('commercial_account','');
  await setPreference('commercial_billing_host','');
}
