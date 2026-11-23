package com.cleanbrowser.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.StringReader;
import java.io.StringWriter;
import org.junit.Test;

public class BlockListTest {

    private static BlockList parse(String text) throws Exception {
        BlockList list = new BlockList();
        list.parse(new BufferedReader(new StringReader(text)));
        return list;
    }

    @Test
    public void hostsFormat() throws Exception {
        BlockList list = parse("# comment\n127.0.0.1 localhost\n0.0.0.0 0.0.0.0\n"
                + "0.0.0.0 ads.example.com # trailing comment\n127.0.0.1\ttracker.net\n::1 ip6.example.org\n");
        assertEquals(3, list.size());
        assertTrue(list.isBlocked("ads.example.com"));
        assertTrue(list.isBlocked("tracker.net"));
        assertTrue(list.isBlocked("ip6.example.org"));
        assertFalse(list.isBlocked("localhost"));
        assertFalse(list.isBlocked("example.com"));
    }

    @Test
    public void subdomainsAreBlocked() throws Exception {
        BlockList list = parse("doubleclick.net\n");
        assertTrue(list.isBlocked("doubleclick.net"));
        assertTrue(list.isBlocked("ad.doubleclick.net"));
        assertTrue(list.isBlocked("a.b.DoubleClick.NET."));
        assertFalse(list.isBlocked("notdoubleclick.net"));
        assertFalse(list.isBlocked("net"));
    }

    @Test
    public void adblockFormatAndExceptions() throws Exception {
        BlockList list = parse("! AdGuard style\n||ads.site.com^\n||popads.net^$important\n"
                + "||example.org^\n@@||good.example.org^\n||bad.com^$client=127.0.0.1\n"
                + "||path.com/ads^\nsite.com##.banner\n/regex.*/\n@@/allow/\n[Adblock Plus 2.0]\n");
        assertTrue(list.isBlocked("ads.site.com"));
        assertTrue(list.isBlocked("x.popads.net"));
        assertTrue(list.isBlocked("www.example.org"));
        assertFalse(list.isBlocked("good.example.org"));
        assertFalse(list.isBlocked("cdn.good.example.org"));
        assertFalse(list.isBlocked("bad.com"));
        assertFalse(list.isBlocked("path.com"));
        assertFalse(list.isBlocked("site.com"));
    }

    @Test
    public void parseLineRejectsJunk() {
        boolean[] ex = new boolean[1];
        assertNull(BlockList.parseLine("", ex));
        assertNull(BlockList.parseLine("   # only comment", ex));
        assertNull(BlockList.parseLine("0.0.0.0 bad_host!", ex));
        assertNull(BlockList.parseLine("nodot", ex));
        assertNull(BlockList.parseLine("two words here", ex));
        assertEquals("wild.example.com", BlockList.parseLine("*.wild.example.com", ex));
        assertEquals("ads.x.com", BlockList.parseLine("||ads.x.com^|", ex));
        assertEquals("ok.x.com", BlockList.parseLine("@@||ok.x.com^", ex));
        assertTrue(ex[0]);
    }

    @Test
    public void writeAndReadBack() throws Exception {
        BlockList list = parse("a.com\nb.org\n@@||ok.a.com^\n");
        StringWriter out = new StringWriter();
        list.write(out);
        BlockList again = parse(out.toString());
        assertEquals(2, again.size());
        assertTrue(again.isBlocked("x.a.com"));
        assertFalse(again.isBlocked("ok.a.com"));
    }

    @Test
    public void ipHosts() throws Exception {
        BlockList list = parse("0.0.0.0 1.2.3.4\n");
        assertTrue(list.isBlocked("1.2.3.4"));
        assertFalse(list.isBlocked("5.2.3.4"));
    }

    @Test
    public void sites() {
        assertEquals("example.com", BlockList.siteOf("www.example.com"));
        assertEquals("example.com", BlockList.siteOf("a.b.example.com"));
        assertEquals("bbc.co.uk", BlockList.siteOf("news.bbc.co.uk"));
        assertEquals("hespress.com", BlockList.siteOf("m.hespress.com"));
        assertEquals("site.com.ma", BlockList.siteOf("www.site.com.ma"));
        assertEquals("xyz.de", BlockList.siteOf("abc.xyz.de"));
        assertTrue(BlockList.sameSite("m.youtube.com", "www.youtube.com"));
        assertFalse(BlockList.sameSite("youtube.com", "popads.net"));
    }
}
