import { formatMoney, formatSignedMoney, parseMoneyInput } from '../src/money';

describe('formatMoney', () => {
  it('turns cents into a currency amount', () => {
    expect(formatMoney(124_350, 'USD')).toBe('$1,243.50');
    expect(formatMoney(5, 'EUR')).toBe('€0.05');
  });

  it('shows a sign for transactions', () => {
    expect(formatSignedMoney(-4_250, 'USD')).toBe('−$42.50');
    expect(formatSignedMoney(1_000, 'USD')).toBe('+$10.00');
  });
});

describe('parseMoneyInput', () => {
  it.each([
    ['12', 1_200],
    ['12.5', 1_250],
    ['12.50', 1_250],
    ['12,50', 1_250],
    [' 0.01 ', 1],
  ])('reads %p as %p cents', (text, cents) => {
    expect(parseMoneyInput(text)).toBe(cents);
  });

  it.each(['', 'abc', '0', '0.00', '-5', '1.234', '12.'])(
    'rejects %p',
    text => {
      expect(parseMoneyInput(text)).toBeNull();
    },
  );
});
