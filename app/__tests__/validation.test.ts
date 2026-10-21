import {
  limitError,
  nicknameError,
  parseLimit,
  topUpError,
} from '../src/validation';

it('needs a card name of 1 to 50 characters', () => {
  expect(nicknameError('  ')).toBe('Give the card a name');
  expect(nicknameError('x'.repeat(51))).toBe('Use 50 characters or fewer');
  expect(nicknameError('Groceries')).toBeNull();
});

it('caps a single top-up at 100,000', () => {
  expect(topUpError('100000')).toBeNull();
  expect(topUpError('100000.01')).not.toBeNull();
  expect(topUpError('ten')).not.toBeNull();
});

it('treats an empty spending limit as no limit', () => {
  expect(limitError('')).toBeNull();
  expect(parseLimit('')).toBeNull();
  expect(parseLimit('200')).toBe(20_000);
  expect(limitError('two hundred')).not.toBeNull();
});
