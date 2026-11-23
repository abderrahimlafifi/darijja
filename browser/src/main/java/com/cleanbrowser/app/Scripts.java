package com.cleanbrowser.app;

import java.util.Locale;

/** JavaScript injected into web pages. Every script is safe to inject more than once. */
final class Scripts {

    private Scripts() {
    }

    /** Generic ad containers. Kept conservative so normal page content is not hidden. */
    private static final String AD_SELECTORS =
            "ins.adsbygoogle,.adsbygoogle,[id^='google_ads_'],[id^='div-gpt-ad'],[id^='gpt-ad-']," +
            "[id^='dfp-ad-'],[class^='dfp-ad'],[data-ad-slot],[data-ad-client],[data-google-query-id]," +
            "[data-ad-unit],[data-adunit],[data-ad-placement],iframe[src*='doubleclick.net']," +
            "iframe[src*='googlesyndication.com'],iframe[id^='google_ads_iframe'],iframe[src*='adnxs.com']," +
            "iframe[src*='amazon-adsystem.com'],iframe[src*='exoclick.com'],amp-ad,amp-sticky-ad," +
            "amp-embed[type='taboola'],amp-embed[type='outbrain'],.ad-slot,.ad-container,.ad-wrapper," +
            ".ad-banner,.ad-unit,.ad-placement,.ad-box,.adBanner,.adbox,.banner-ad,.ads-banner," +
            ".advertisement,.advert,.sponsored-ad,.sticky-ad,.adsense,.adslot,.adsbox,.ad-leaderboard," +
            ".leaderboard-ad,.top-ad,#ad-banner,#adBanner,#ad-container,#ad_container,#ads-container," +
            "#sticky-ad,#top-ad,.taboola,[id^='taboola-'],.trc_rbox_container,.OUTBRAIN,.ob-widget," +
            "[id^='mgid_'],.mgbox,#carbonads,.carbon-ads,[id^='ezoic-pub-ad'],.ezoic-ad,.adthrive-ad," +
            "[id^='AdThrive_'],.mv-ad-box,[class*='-ad-wrapper'],[class*='ad-sticky']," +
            "div[aria-label='advertisement'],div[aria-label='Advertisement']";

    private static final String COSMETIC = """
            (function(){
              if (document.getElementById('__cb_css')) return;
              var s = document.createElement('style');
              s.id = '__cb_css';
              s.textContent = "%SELECTORS%{display:none!important;visibility:hidden!important;}";
              (document.head || document.documentElement).appendChild(s);
            })();
            """.replace("%SELECTORS%", AD_SELECTORS.replace("\"", "'"));

    /**
     * Removes invisible full screen layers that ad scripts put over a page so that
     * the first tap anywhere opens an ad. Only layers that are on top of everything,
     * cover most of the screen and show nothing are removed.
     */
    private static final String OVERLAYS = """
            (function(){
              if (window.__cbOverlays) return;
              window.__cbOverlays = 1;
              function transparent(cs){
                var bg = cs.backgroundColor;
                return (bg === 'transparent' || bg === 'rgba(0, 0, 0, 0)') && cs.backgroundImage === 'none';
              }
              function clean(){
                try {
                  if (!document.body) return;
                  var vw = window.innerWidth, vh = window.innerHeight;
                  var els = document.querySelectorAll('body > div, body > a, body > span, body > ins, body > iframe, html > div, html > a, html > iframe');
                  for (var i = 0; i < els.length; i++) {
                    var el = els[i];
                    var cs = getComputedStyle(el);
                    if (cs.position !== 'fixed' && cs.position !== 'absolute') continue;
                    var z = parseInt(cs.zIndex, 10);
                    if (!(z >= 100000)) continue;
                    var r = el.getBoundingClientRect();
                    if (r.width < vw * 0.6 || r.height < vh * 0.6) continue;
                    if (el.querySelector('video, input, textarea, select, form, button')) continue;
                    var invisible = parseFloat(cs.opacity) < 0.1 ||
                      (el.tagName !== 'IFRAME' && transparent(cs) && !(el.innerText || '').trim() &&
                       !el.querySelector('img, svg, canvas, iframe, picture'));
                    if (invisible) el.remove();
                  }
                } catch (e) {}
              }
              clean();
              setInterval(clean, 1500);
              document.addEventListener('touchstart', clean, true);
            })();
            """;

    /**
     * YouTube serves video ads from the same servers as the videos, so they cannot be
     * blocked by domain. This mutes and jumps over them, presses "Skip" and hides ad cards.
     */
    private static final String YOUTUBE = """
            (function(){
              if (window.__cbYt) return;
              window.__cbYt = 1;
              var css = 'ytd-ad-slot-renderer,ytd-in-feed-ad-layout-renderer,ytd-promoted-sparkles-web-renderer,' +
                'ytd-display-ad-renderer,ytd-compact-promoted-video-renderer,ytd-promoted-video-renderer,' +
                'ytd-banner-promo-renderer,ytd-statement-banner-renderer,ytd-player-legacy-desktop-watch-ads-renderer,' +
                'ytd-engagement-panel-section-list-renderer[target-id="engagement-panel-ads"],' +
                '#masthead-ad,#player-ads,#panels ytd-ads-engagement-panel-content-renderer,' +
                'ytm-promoted-sparkles-web-renderer,ytm-promoted-video-renderer,ytm-companion-ad-renderer,' +
                'ytm-companion-slot,ad-slot-renderer,ytm-ad-slot-renderer,ytm-promoted-sparkles-text-search-renderer,' +
                '.ytp-ad-overlay-container,.ytp-ad-overlay-slot,.ytp-ad-image-overlay,.ytp-featured-product,' +
                '.ytd-merch-shelf-renderer,ytd-merch-shelf-renderer{display:none!important}';
              var hasCss = 'ytd-rich-item-renderer:has(ytd-ad-slot-renderer),ytm-rich-item-renderer:has(ad-slot-renderer),' +
                'ytm-item-section-renderer:has(ad-slot-renderer){display:none!important}';
              function addStyle(id, text){
                if (document.getElementById(id)) return;
                var s = document.createElement('style');
                s.id = id;
                s.textContent = text;
                (document.head || document.documentElement).appendChild(s);
              }
              var sped = false;
              function tick(){
                try {
                  addStyle('__cb_yt', css);
                  addStyle('__cb_yt2', hasCss);
                  var player = document.querySelector('.html5-video-player');
                  var ad = player && (player.classList.contains('ad-showing') || player.classList.contains('ad-interrupting'));
                  var v = player ? player.querySelector('video') : document.querySelector('video');
                  if (ad && v) {
                    if (!sped) { sped = true; }
                    v.muted = true;
                    try { v.playbackRate = 16; } catch (e) {}
                    if (isFinite(v.duration) && v.duration > 0 && v.currentTime < v.duration - 0.2) {
                      try { v.currentTime = v.duration - 0.1; } catch (e) {}
                    }
                  } else if (sped && v) {
                    sped = false;
                    try { v.playbackRate = 1; } catch (e) {}
                    v.muted = false;
                  }
                  var skip = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button,' +
                    ' .ytp-ad-skip-button-container button, .ytm-ad-skip-button, button[class*="skip-button"]');
                  if (skip) skip.click();
                  var close = document.querySelector('.ytp-ad-overlay-close-button');
                  if (close) close.click();
                } catch (e) {}
              }
              tick();
              setInterval(tick, 250);
            })();
            """;

    static String pageScript(boolean cosmetic, boolean overlays, boolean youtube) {
        StringBuilder sb = new StringBuilder();
        if (cosmetic) sb.append(COSMETIC);
        if (overlays) sb.append(OVERLAYS);
        if (youtube) sb.append(YOUTUBE);
        return sb.toString();
    }

    static boolean isYouTube(String host) {
        if (host == null) return false;
        String h = host.toLowerCase(Locale.ROOT);
        return h.equals("youtube.com") || h.endsWith(".youtube.com") || h.equals("youtube-nocookie.com")
                || h.endsWith(".youtube-nocookie.com");
    }

    /**
     * Scrolls whatever is under the pointer: an inner scrolling box if there is one,
     * otherwise the page. Coordinates are view pixels; the result is "1" if it moved.
     */
    static String scrollAt(float x, float y, int viewWidth, int dx, int dy) {
        return String.format(Locale.US, """
                (function(px, py, vw, dx, dy){
                  var vv = window.visualViewport;
                  var w = vv ? vv.width : window.innerWidth;
                  var s = w / vw;
                  var cx = px * s, cy = py * s;
                  dx = dx * s; dy = dy * s;
                  var el = document.elementFromPoint(cx, cy);
                  while (el && el !== document.body && el !== document.documentElement) {
                    var cs = getComputedStyle(el);
                    if (dy && /(auto|scroll|overlay)/.test(cs.overflowY) && el.scrollHeight > el.clientHeight + 1) {
                      var t = el.scrollTop; el.scrollTop = t + dy; if (el.scrollTop !== t) return 1;
                    }
                    if (dx && /(auto|scroll|overlay)/.test(cs.overflowX) && el.scrollWidth > el.clientWidth + 1) {
                      var l = el.scrollLeft; el.scrollLeft = l + dx; if (el.scrollLeft !== l) return 1;
                    }
                    el = el.parentElement;
                  }
                  var bx = window.scrollX, by = window.scrollY;
                  window.scrollBy(dx, dy);
                  return (window.scrollX !== bx || window.scrollY !== by) ? 1 : 0;
                })(%.1f, %.1f, %d, %d, %d)
                """, x, y, Math.max(1, viewWidth), dx, dy);
    }

    /** Play / pause the first video on the page, for the remote's play key. */
    static final String TOGGLE_VIDEO = """
            (function(){
              var v = document.querySelector('video');
              if (!v) return 0;
              if (v.paused) v.play(); else v.pause();
              return 1;
            })()
            """;

    static String seekVideo(int seconds) {
        return String.format(Locale.US, """
                (function(){
                  var v = document.querySelector('video');
                  if (v && isFinite(v.duration)) v.currentTime = Math.max(0, Math.min(v.duration, v.currentTime + %d));
                })()
                """, seconds);
    }

    /** The small page shown instead of a site that is on the block list. */
    static String blockedPage(String host, String allowUrl, String title, String body, String back, String open) {
        return "<!doctype html><html dir='rtl'><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>body{background:#0F1115;color:#E8EAF0;font-family:sans-serif;display:flex;align-items:center;"
                + "justify-content:center;min-height:90vh;margin:0;padding:24px;text-align:center}"
                + ".c{max-width:520px}.h{font-size:22px;font-weight:bold;margin:12px 0}.d{color:#9AA3B5;line-height:1.6}"
                + ".host{direction:ltr;color:#FFB4A9;margin:12px 0;word-break:break-all}"
                + "a{display:inline-block;margin:10px 6px;padding:12px 22px;border-radius:10px;text-decoration:none;"
                + "color:#fff;background:#2F6FE4;font-size:16px}a.s{background:#2A2F3A}a:focus{outline:3px solid #fff}</style>"
                + "</head><body><div class='c'><div style='font-size:48px'>&#128737;</div>"
                + "<div class='h'>" + esc(title) + "</div><div class='host'>" + esc(host) + "</div>"
                + "<div class='d'>" + esc(body) + "</div>"
                + "<a href='javascript:history.back()'>" + esc(back) + "</a>"
                + "<a class='s' href='" + esc(allowUrl) + "'>" + esc(open) + "</a>"
                + "</div></body></html>";
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "&#39;").replace("\"", "&quot;");
    }
}
