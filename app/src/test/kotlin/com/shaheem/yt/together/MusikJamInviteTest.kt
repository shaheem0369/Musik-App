package com.shaheem.yt.together

import org.junit.Assert.*
import org.junit.Test

class MusikJamInviteTest {
    @Test fun normalizesRoomCodesWithoutRequiringAHostToken() {
        assertEquals(MusikJamInvite("EG7NN4"), MusikJamInvite.parse(" eg7nn4 "))
        assertEquals(MusikJamInvite("123456"), MusikJamInvite.parse("123456"))
        assertNull(MusikJamInvite.parse("short"))
        assertNull(MusikJamInvite.parse("1234567"))
    }

    @Test fun inviteRoundTripsServerAndCodeFromShareText() {
        val invite = MusikJamInvite("EG7NN4", "https://music.example:8443")
        assertEquals(invite, MusikJamInvite.parse(invite.toLink()))
        assertEquals(invite, MusikJamInvite.parse("Join my Musik Jam: EG7NN4\n${invite.toLink()}"))
        assertFalse(invite.toLink().contains("token"))
        assertEquals(MusikJamInvite("EG7NN4"), MusikJamInvite.parse("musik://jam?code=EG7NN4"))
        assertEquals(invite, MusikJamInvite.parse("musik://jam?code=EG7NN4&server=https%3A%2F%2Fmusic.example%3A8443"))
        assertEquals("https://music.example:8443/?code=EG7NN4", invite.toLink())
    }

    @Test fun rejectsOtherProtocolsCredentialsAndInvalidServers() {
        for (link in listOf("https://lastwave.example/another-path?code=EG7NN4", "musik://jam?code=BAD",
            "musik://jam?code=EG7NN4&server=https%3A%2F%2Fuser%3Apass%40example.com",
            "musik://jam?code=EG7NN4&server=javascript%3Aalert(1)")) {
            assertNull(link, MusikJamInvite.parse(link))
        }
    }
}
