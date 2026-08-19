// True while UI Verify is capturing this render. Zero dependencies, safe in a
// production bundle: in production every check is false, so components take
// their normal (random / animated) path. See https://uiverify.ai/docs/is-ui-verify
export function isUIVerify(): boolean {
    // Server-side render / build time: no window, so read an env var set only
    // for the capture run (UI_VERIFY=1). Keep it unset in production.
    if (typeof process !== 'undefined' && process.env.UI_VERIFY) return true;
    if (typeof window === 'undefined') return false;
    const ua = window.navigator?.userAgent ?? '';
    return ua.includes('UIVerify') || '__UI_VERIFY__' in window;
}
