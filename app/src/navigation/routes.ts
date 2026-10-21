/** Every screen in the app, and what each one needs to know. */
export type Route =
  | { name: 'home' }
  | { name: 'cards' }
  | { name: 'activity' }
  | { name: 'profile' }
  | { name: 'card'; cardId: string }
  | { name: 'pay'; cardId: string }
  | { name: 'topUp' }
  | { name: 'issueCard' };

/** The screens you can jump to from the tab bar (phone) or sidebar (Mac). */
export const tabs = [
  { name: 'home', label: 'Wallet' },
  { name: 'cards', label: 'Cards' },
  { name: 'activity', label: 'Activity' },
  { name: 'profile', label: 'You' },
] as const;

export type TabName = (typeof tabs)[number]['name'];

/**
 * Navigation is a stack of routes: the last one is on screen. Opening a
 * screen pushes onto the stack, going back pops it, and picking a tab
 * starts a fresh stack.
 */
export type NavState = Route[];

export const push = (stack: NavState, route: Route): NavState => [
  ...stack,
  route,
];
export const pop = (stack: NavState): NavState =>
  stack.length > 1 ? stack.slice(0, -1) : stack;
export const switchTab = (tab: TabName): NavState => [{ name: tab }];
export const current = (stack: NavState): Route => stack[stack.length - 1];
