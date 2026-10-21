import { PermissionsAndroid, Platform } from 'react-native';
import { login } from '../src/api/auth';
import { setTokenSource } from '../src/api/client';
import { startPush, stopPush } from '../src/push/push';
import { installFakeApi } from '../testing/fakeApi';

let api: ReturnType<typeof installFakeApi>;

beforeEach(async () => {
  api = installFakeApi();
  api.seedUser('Asha');
  const { accessToken } = await login(
    'asha@example.com',
    'correct horse battery',
  );
  setTokenSource({
    accessToken: () => accessToken,
    refresh: async () => false,
  });
  api.calls.length = 0;
  jest.replaceProperty(Platform, 'OS', 'android');
  jest.spyOn(Platform, 'Version', 'get').mockReturnValue(34);
});

afterEach(() => {
  setTokenSource(null);
  jest.restoreAllMocks();
});

describe('push notifications', () => {
  it('asks first, then registers the phone, and forgets it on sign-out', async () => {
    const ask = jest
      .spyOn(PermissionsAndroid, 'request')
      .mockResolvedValue(PermissionsAndroid.RESULTS.GRANTED);

    expect(await startPush()).toBe(true);
    expect(ask).toHaveBeenCalledWith(
      PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS,
    );
    expect(api.calls).toContainEqual(
      expect.objectContaining({
        path: '/api/devices',
        body: { platform: 'ANDROID', token: 'fcm-token-1' },
      }),
    );

    await stopPush();
    expect(api.calls).toContainEqual(
      expect.objectContaining({
        path: '/api/devices/unregister',
        body: { token: 'fcm-token-1' },
      }),
    );
  });

  it('stays off when the person says no', async () => {
    jest
      .spyOn(PermissionsAndroid, 'request')
      .mockResolvedValue(PermissionsAndroid.RESULTS.DENIED);
    expect(await startPush()).toBe(false);
    expect(api.calls).toHaveLength(0);
  });

  it('stays off on the Mac', async () => {
    jest.replaceProperty(Platform, 'OS', 'macos');
    expect(await startPush()).toBe(false);
  });
});
