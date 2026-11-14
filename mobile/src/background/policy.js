// @ts-check
/** Foreground connection policy for Android; never an auto-arming policy. */

/**
 * Require an owner opt-in, an authenticated pairing, device armed state and
 * platform notifications before a foreground connection can be requested.
 * @param {{ownerOptedIn:boolean,pairedApproved:boolean,armed:boolean,notificationsGranted:boolean}} state
 * @returns {{allowed:boolean,reason:string}}
 */
export function foregroundEligibility(state) {
  if (!state?.ownerOptedIn) return { allowed: false, reason: 'OWNER_CONSENT_REQUIRED' };
  if (!state?.pairedApproved) return { allowed: false, reason: 'PAIRING_NOT_APPROVED' };
  if (!state?.armed) return { allowed: false, reason: 'MONITORING_DISARMED' };
  if (!state?.notificationsGranted) return { allowed: false, reason: 'NOTIFICATION_PERMISSION_REQUIRED' };
  return { allowed: true, reason: 'ELIGIBLE' };
}
