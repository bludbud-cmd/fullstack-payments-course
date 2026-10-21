/**
 * A very small STOMP client. STOMP is a text protocol that runs on top of a
 * WebSocket: each message (a "frame") is a command, some "name:value" header
 * lines, a blank line, a body, and a NULL character to finish.
 *
 *   SUBSCRIBE
 *   id:sub-0
 *   destination:/topic/accounts/1234
 *
 *   ^@
 *
 * PayDeck only needs four commands: CONNECT and SUBSCRIBE going out,
 * CONNECTED and MESSAGE (or ERROR) coming back.
 */
export type Frame = {
  command: string;
  headers: Record<string, string>;
  body: string;
};

export function encodeFrame(
  command: string,
  headers: Record<string, string>,
  body = '',
): Uint8Array {
  const lines = Object.entries(headers).map(
    ([name, value]) => `${name}:${value}`,
  );
  const text = [command, ...lines, '', body].join('\n') + '\0';
  // Sent as bytes, because React Native's WebSocket drops the NULL from text
  // messages. Every frame PayDeck sends is plain ASCII: one letter, one byte.
  return Uint8Array.from(text, letter => letter.charCodeAt(0));
}

/** Reads one frame. Returns null for a heartbeat (an empty line). */
export function decodeFrame(data: string): Frame | null {
  const text = data.replace(/\0$/, '').replace(/^[\r\n]+/, '');
  if (text === '') {
    return null;
  }
  const blankLine = text.indexOf('\n\n');
  const head = blankLine < 0 ? text : text.slice(0, blankLine);
  const body = blankLine < 0 ? '' : text.slice(blankLine + 2);
  const [command, ...lines] = head.split('\n');
  const headers: Record<string, string> = {};
  for (const line of lines) {
    const colon = line.indexOf(':');
    if (colon > 0) {
      headers[line.slice(0, colon)] = line.slice(colon + 1);
    }
  }
  return { command, headers, body };
}

type Options = {
  /** For example ws://localhost:8080/ws */
  url: string;
  /** Read again on every connection, so a reconnect uses a fresh token. */
  accessToken: () => string | null;
  topics: string[];
  onMessage: (body: string) => void;
  /** The server said no, usually because the access token has expired. */
  onRefused: () => void;
};

/**
 * Connects, subscribes to `topics`, and reconnects whenever the connection
 * drops (the phone lost signal, the API restarted). Returns a function that
 * disconnects for good.
 */
export function connectStomp(options: Options): () => void {
  let socket: WebSocket | null = null;
  let stopped = false;
  let retry: ReturnType<typeof setTimeout> | null = null;
  let delay = 1000;

  const open = () => {
    const ws = new WebSocket(options.url);
    socket = ws;
    ws.onopen = () =>
      ws.send(
        encodeFrame('CONNECT', {
          'accept-version': '1.2',
          host: 'paydeck',
          'heart-beat': '0,0',
          Authorization: `Bearer ${options.accessToken() ?? ''}`,
        }),
      );
    ws.onmessage = event => {
      const frame =
        typeof event.data === 'string' ? decodeFrame(event.data) : null;
      if (frame?.command === 'CONNECTED') {
        delay = 1000;
        options.topics.forEach((destination, i) =>
          ws.send(encodeFrame('SUBSCRIBE', { id: `sub-${i}`, destination })),
        );
      } else if (frame?.command === 'MESSAGE') {
        options.onMessage(frame.body);
      } else if (frame?.command === 'ERROR') {
        options.onRefused();
      }
    };
    ws.onclose = () => {
      if (!stopped) {
        // Wait a little longer after each failure, up to a minute.
        retry = setTimeout(open, delay);
        delay = Math.min(delay * 2, 60_000);
      }
    };
  };

  open();
  return () => {
    stopped = true;
    if (retry) {
      clearTimeout(retry);
    }
    socket?.close();
  };
}
