// @ts-check
/** Reports leave private app storage only after an explicit document picker. */
import { NativeModules, Platform } from 'react-native';
import { incidentsCsv, incidentsText } from './incidentReport.js';

/** @param {object[]} alerts @param {'csv'|'text'} format @returns {Promise<void>} */
export async function exportIncidents(alerts, format) {
  if (!['csv', 'text'].includes(format)) throw new Error('Unsupported export format');
  const content = format === 'csv' ? incidentsCsv(alerts) : incidentsText(alerts);
  const bridge = Platform.OS === 'ios'
    ? NativeModules.LaptopWatchPinnedTransport
    : NativeModules.LaptopWatchEvidenceManagement;
  if (!bridge || typeof bridge.exportReport !== 'function') {
    throw new Error('Native document export unavailable');
  }
  await bridge.exportReport(format, content);
}
