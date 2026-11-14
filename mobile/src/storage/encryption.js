// @ts-check
/** SQLCipher key: random high-entropy device-local secret held by OS Keychain.
 * Not the Ed25519 pairing seed and not recorded in SQLite, JSON or logs.
 */
import 'react-native-get-random-values';
import * as Keychain from 'react-native-keychain';
import { b64url, unb64url, secureRandom32 } from '../crypto/protocol.js';

const SERVICE = 'com.kc.laptopwatch.private-cache-sqlcipher-v2';
let activeKey = null;
let pending = null;

/** @returns {Promise<string>} */
export async function cacheEncryptionKey() {
  if (activeKey) return activeKey;
  if (pending) return pending;
  pending = (async () => {
    const existing = await Keychain.getGenericPassword({ service: SERVICE });
    if (existing) {
      if (existing.username !== 'sqlcipher' ||
          typeof existing.password !== 'string') {
        throw new Error('Encrypted cache key is invalid; refusing database access');
      }
      unb64url(existing.password, 32);
      activeKey = existing.password;
      return activeKey;
    }
    const random = secureRandom32();
    const encoded = b64url(random);
    random.fill(0);
    const stored = await Keychain.setGenericPassword('sqlcipher', encoded, {
      service: SERVICE,
      accessible: Keychain.ACCESSIBLE.WHEN_UNLOCKED_THIS_DEVICE_ONLY,
    });
    if (!stored) throw new Error('OS secure cache key storage unavailable');
    activeKey = encoded;
    return encoded;
  })();
  try { return await pending; }
  finally { pending = null; }
}
