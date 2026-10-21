import { decodeFrame, encodeFrame } from '../src/live/stomp';

const text = (bytes: Uint8Array) => String.fromCharCode(...bytes);

describe('STOMP frames', () => {
  it('writes a command, headers, a blank line and a NULL', () => {
    expect(
      text(encodeFrame('SUBSCRIBE', { id: 'sub-0', destination: '/topic/a' })),
    ).toBe('SUBSCRIBE\nid:sub-0\ndestination:/topic/a\n\n\0');
  });

  it('reads a message, with or without the NULL', () => {
    const frame =
      'MESSAGE\ndestination:/topic/a\ncontent-type:application/json\n\n{"a":1}';
    for (const data of [frame + '\0', frame]) {
      expect(decodeFrame(data)).toEqual({
        command: 'MESSAGE',
        headers: {
          destination: '/topic/a',
          'content-type': 'application/json',
        },
        body: '{"a":1}',
      });
    }
  });

  it('keeps colons in header values', () => {
    expect(decodeFrame('ERROR\nmessage:Sign in: please\n\n')?.headers).toEqual({
      message: 'Sign in: please',
    });
  });

  it('treats an empty line as a heartbeat', () => {
    expect(decodeFrame('\n')).toBeNull();
  });
});
