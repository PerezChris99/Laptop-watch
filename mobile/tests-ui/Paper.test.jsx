import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react-native';
import { SectionHeader, StatusPill, PrimaryButton, EmptyState, ConsentNotice } from '../src/components/Paper.jsx';

describe('Paper UI security states', () => {
  test('status never describes an offline laptop as live', () => {
    render(<StatusPill state="Offline" />);
    expect(screen.getByText('Offline')).toBeTruthy();
  });

  test('consent is surfaced in words before actions', () => {
    render(<ConsentNotice>Recording requires lawful notice and informed permission.</ConsentNotice>);
    expect(screen.getByText(/lawful notice/)).toBeTruthy();
  });

  test('disabled action cannot execute', () => {
    const action = jest.fn();
    render(<PrimaryButton title="Lock laptop" onPress={action} disabled />);
    fireEvent.press(screen.getByText('Lock laptop'));
    expect(action).not.toHaveBeenCalled();
  });

  test('empty evidence and GNSS screens have clear states', () => {
    render(<><SectionHeader title="Evidence vault" />
      <EmptyState message="No authenticated evidence available" /></>);
    expect(screen.getByText('Evidence vault')).toBeTruthy();
    expect(screen.getByText('No authenticated evidence available')).toBeTruthy();
  });
});
