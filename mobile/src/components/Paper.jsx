import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import PropTypes from 'prop-types';
import { tokens } from '../design/tokens.js';

const { colors: c, radii: r } = tokens;
const styles = StyleSheet.create({
  card: { backgroundColor: c.surface, borderColor: c.hairline, borderWidth: 1, borderRadius: r.card, padding: 18, marginBottom: 12 },
  heading: { fontFamily: 'serif', fontSize: 22, lineHeight: 29, color: c.ink, marginBottom: 9 },
  subheading: { color: c.inkMuted, fontSize: 14, lineHeight: 21, marginBottom: 13 },
  button: { borderRadius: r.control, paddingVertical: 12, paddingHorizontal: 14, borderWidth: 1, alignItems: 'center', marginVertical: 4, minHeight: 45, justifyContent: 'center' },
  buttonText: { fontSize: 14, fontWeight: '700' },
  pill: { alignSelf: 'flex-start', borderWidth: 1, borderRadius: 999, paddingHorizontal: 11, paddingVertical: 5, marginBottom: 8 },
  pillText: { fontSize: 12, fontWeight: '700' },
  notice: { borderLeftWidth: 3, borderLeftColor: c.gold, paddingLeft: 14, paddingVertical: 7, marginVertical: 14 },
  noticeText: { color: c.inkMuted, fontSize: 13, lineHeight: 20 },
});

export function PaperCard({ children }) { return <View style={styles.card}>{children}</View>; }
PaperCard.propTypes = { children: PropTypes.node };

export function SectionHeader({ title, subtitle }) {
  return <View><Text style={styles.heading}>{title}</Text>{subtitle ? <Text style={styles.subheading}>{subtitle}</Text> : null}</View>;
}
SectionHeader.propTypes = { title: PropTypes.string.isRequired, subtitle: PropTypes.string };

export function StatusPill({ state }) {
  const tone = state === 'Armed' || state === 'Online' ? c.success :
    state === 'Offline' ? c.danger : state === 'Recording' ? c.warning : c.inkMuted;
  return <View style={[styles.pill, { borderColor: tone }]}><Text style={[styles.pillText, { color: tone }]}>{state}</Text></View>;
}
StatusPill.propTypes = { state: PropTypes.oneOf(['Armed','Disarmed','Offline','Online','Recording','Not configured']).isRequired };

function PaperButton({ title, onPress, disabled, tone = 'primary' }) {
  const backgroundColor = tone === 'primary' ? c.blue : c.surface;
  const borderColor = tone === 'destructive' ? c.danger : tone === 'primary' ? c.blue : c.hairline;
  const color = tone === 'primary' ? c.surface : tone === 'destructive' ? c.danger : c.blue;
  return <Pressable accessibilityRole="button" accessibilityState={{ disabled: Boolean(disabled) }}
    onPress={onPress} disabled={disabled} style={({ pressed }) => [
      styles.button, { backgroundColor, borderColor, opacity: disabled ? .5 : pressed ? .8 : 1 },
    ]}><Text style={[styles.buttonText, { color }]}>{title}</Text></Pressable>;
}
PaperButton.propTypes = { title: PropTypes.string.isRequired, onPress: PropTypes.func.isRequired, disabled: PropTypes.bool, tone: PropTypes.oneOf(['primary','secondary','destructive']) };

export function PrimaryButton(props) { return <PaperButton {...props} tone="primary" />; }
export function SecondaryButton(props) { return <PaperButton {...props} tone="secondary" />; }
export function DestructiveButton(props) { return <PaperButton {...props} tone="destructive" />; }

export function GoldFocusRing({ children }) { return <View style={{ borderLeftWidth: 3, borderLeftColor: c.gold, paddingLeft: 8 }}>{children}</View>; }
GoldFocusRing.propTypes = { children: PropTypes.node };

export function ConsentNotice({ children }) {
  return <View style={styles.notice}><Text style={styles.noticeText}>{children}</Text></View>;
}
ConsentNotice.propTypes = { children: PropTypes.string.isRequired };

export function Toast({ message, error = false }) {
  return message ? <Text accessibilityRole="alert" style={{ padding: 12, color: error ? c.danger : c.blueDeep, fontSize: 13 }}>{message}</Text> : null;
}
Toast.propTypes = { message: PropTypes.string, error: PropTypes.bool };

export function EmptyState({ message }) { return <Text style={{ color: c.inkMuted, paddingVertical: 15, lineHeight: 21 }}>{message}</Text>; }
EmptyState.propTypes = { message: PropTypes.string.isRequired };

export function EvidenceThumb({ kind }) { return <Text accessibilityLabel={kind + ' evidence'} style={{ color: c.blue, fontSize: 24 }}>{kind === 'video' ? '▣' : '▧'}</Text>; }
EvidenceThumb.propTypes = { kind: PropTypes.oneOf(['photo','video']).isRequired };

export function LiveBadge({ confirmed }) {
  return confirmed ? <Text style={{ color: c.success, fontWeight: '700' }}>● LIVE</Text> : null;
}
LiveBadge.propTypes = { confirmed: PropTypes.bool };

export function LastSeenBadge({ time }) {
  return <Text style={{ color: c.inkMuted, fontSize: 12 }}>LAST SEEN · {time || 'Not available'}</Text>;
}
LastSeenBadge.propTypes = { time: PropTypes.string };
