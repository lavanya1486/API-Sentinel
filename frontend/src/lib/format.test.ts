import { describe, expect, it } from 'vitest';
import { formatDuration, formatLatency, formatPercent } from './format';

describe('metric formatting', () => {
  it('formats latency and percentages without inventing values', () => {
    expect(formatLatency(42)).toBe('42 ms');
    expect(formatLatency(1250)).toBe('1.25 s');
    expect(formatLatency(null)).toBe('—');
    expect(formatPercent(99.125)).toBe('99.13%');
  });

  it('formats incident durations across units', () => {
    expect(formatDuration(32)).toBe('32s');
    expect(formatDuration(3723)).toBe('1h 2m');
    expect(formatDuration(90_000)).toBe('1d 1h');
  });
});
