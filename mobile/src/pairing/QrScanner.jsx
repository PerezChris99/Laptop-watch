import React, { useEffect, useRef, useState } from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { Camera } from 'react-native-camera-kit';
import PropTypes from 'prop-types';
import { tokens } from '../design/tokens.js';
import { askQrCameraPermission } from '../permissions/access.js';
import { parsePairingQr } from '../crypto/protocol.js';

const styles = StyleSheet.create({
  camera: { height: 280, width: '100%', borderRadius: 10, overflow: 'hidden' },
  feedback: { marginTop: 12, color: tokens.colors.inkMuted, fontSize: 13, lineHeight: 20 },
});

export function QrScanner({ onAccepted }) {
  const [granted, setGranted] = useState(false);
  const [failure, setFailure] = useState('');
  const processed = useRef(false);

  useEffect(() => {
    let active = true;
    askQrCameraPermission().then((allowed) => {
      if (!active) return;
      setGranted(allowed);
      if (!allowed) setFailure('Camera permission denied. Enable it in system settings to scan pairing QR.');
    }).catch(() => { if (active) setFailure('QR scanner permission could not be obtained'); });
    return () => { active = false; processed.current = true; };
  }, []);

  const scanned = (event) => {
    if (processed.current) return;
    const value = event?.nativeEvent?.codeStringValue;
    if (typeof value !== 'string') return;
    try {
      parsePairingQr(value);
      processed.current = true;
      onAccepted(value);
    } catch (_) {
      // No scanned certificate, ID or signed challenge is included in UI logs.
      setFailure('Untrusted or expired pairing QR. Display a fresh code on your laptop.');
    }
  };

  return <View>
    {granted ? <Camera style={styles.camera} scanBarcode onReadCode={scanned}
      showFrame={true} frameColor={tokens.colors.gold} laserColor={tokens.colors.blue} /> : null}
    <Text style={styles.feedback}>{failure || 'Point the camera at the live pairing QR displayed on your laptop.'}</Text>
  </View>;
}
QrScanner.propTypes = { onAccepted: PropTypes.func.isRequired };
