// @ts-check
/** Firebase push is opt-in and disabled until an authenticated relay and
 * owner-provisioned Firebase project are configured on the device.
 */
export const PUSH_ENABLED = false;

/** @param {{ownerOptedIn:boolean,relayConfigured:boolean,firebaseConfigured:boolean}} flags */
export function canEnablePush(flags) {
  return PUSH_ENABLED && flags.ownerOptedIn && flags.relayConfigured && flags.firebaseConfigured;
}

/** @returns {string} */
export function pushStatusMessage() {
  return 'Push notifications not configured. Open the app to inspect signed laptop incidents.';
}
