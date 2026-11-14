// @ts-check
/** Contextual, explicit permission requests. No request on application start. */
import { Platform } from 'react-native';
import { check, request, requestNotifications, PERMISSIONS, RESULTS } from 'react-native-permissions';

/** @returns {Promise<boolean>} */
export async function askQrCameraPermission() {
  const permission = Platform.OS === 'ios' ? PERMISSIONS.IOS.CAMERA : PERMISSIONS.ANDROID.CAMERA;
  const result = await check(permission);
  return result === RESULTS.GRANTED || (await request(permission, {
    title: 'Scan your laptop QR',
    message: 'Camera is only used while the scanner is open. No image is stored or sent.',
    buttonPositive: 'Allow',
    buttonNegative: 'Not now',
  })) === RESULTS.GRANTED;
}

/** @returns {Promise<boolean>} */
export async function askNotificationPermission() {
  const { status } = await requestNotifications(['alert', 'sound']);
  return status === RESULTS.GRANTED || status === RESULTS.LIMITED;
}

/** @returns {Promise<boolean>} */
export async function askMicrophonePermission() {
  const permission = Platform.OS === 'ios' ? PERMISSIONS.IOS.MICROPHONE : PERMISSIONS.ANDROID.RECORD_AUDIO;
  return (await request(permission, {
    title: 'Optional two-way intercom',
    message: 'Microphone access is off unless you explicitly enable it while viewing an armed laptop.',
    buttonPositive: 'Allow',
    buttonNegative: 'Keep microphone off',
  })) === RESULTS.GRANTED;
}
