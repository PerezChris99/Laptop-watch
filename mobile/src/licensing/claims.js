// @ts-check
/** Dependency-free signed-license checks. The caller supplies trusted crypto adapters. */
const UUID=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const B64=/^[A-Za-z0-9_-]+$/;
export class LicenseUnavailable extends Error {}
export function validateLicenseClaims(v, accountId, deviceId, now=Math.floor(Date.now()/1000)) {
  if (!v || typeof v!=='object' || Array.isArray(v) || !UUID.test(accountId) || !UUID.test(deviceId) ||
      v.v!==1 || v.issuer!=='LaptopWatch' || v.account_id!==accountId ||
      v.device_id!==deviceId || !['pro','business'].includes(v.plan) ||
      typeof v.subscription_id!=='string' || !/^sub_[a-z0-9]{26}$/.test(v.subscription_id) ||
      !Number.isSafeInteger(now) || !Number.isSafeInteger(v.issued_at) ||
      !Number.isSafeInteger(v.expires_at) || !Number.isSafeInteger(v.max_devices) ||
      v.max_devices<1 || v.max_devices>100 || v.issued_at>now+60 ||
      v.expires_at<=now || v.expires_at<=v.issued_at || v.expires_at-v.issued_at>3600)
    throw new LicenseUnavailable('Paid entitlement unavailable or expired');
  return v;
}
export function verifySignedLease(token, pinnedKey, accountId, deviceId, crypto,
                                  now=Math.floor(Date.now()/1000)) {
  try {
    if (typeof token!=='string' || token.length>8192 || typeof pinnedKey!=='string' ||
        !B64.test(pinnedKey) || !crypto || typeof crypto.decode!=='function' ||
        typeof crypto.utf8!=='function' || typeof crypto.verify!=='function')
      throw new Error('Invalid issuer');
    const pieces=token.split('.');
    if (pieces.length!==2 || pieces.some(x=>!B64.test(x))) throw new Error('Malformed lease');
    const pub=crypto.decode(pinnedKey), sig=crypto.decode(pieces[1]), body=crypto.decode(pieces[0]);
    if (pub.length!==32 || sig.length!==64 || body.length>4096) throw new Error('Invalid lease length');
    const signed=Uint8Array.from(pieces[0], x=>x.charCodeAt(0));
    if (crypto.verify(pub,signed,sig)!==true) throw new Error('Invalid issuer signature');
    return validateLicenseClaims(JSON.parse(crypto.utf8(body)),accountId,deviceId,now);
  } catch (_) {
    throw new LicenseUnavailable('Paid entitlement unavailable or expired');
  }
}
