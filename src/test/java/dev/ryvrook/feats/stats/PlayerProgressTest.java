package dev.ryvrook.feats.stats;

import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerProgressTest {
    @Test
    void countersAddRaiseAndSet() {
        PlayerProgress p = new PlayerProgress(UUID.randomUUID(), "Counter");
        assertEquals(0, p.get("kills"));
        assertEquals(5, p.add("kills", 5));
        assertEquals(8, p.add("kills", 3));
        assertEquals(6, p.add("kills", -2));
        assertFalse(p.raiseTo("kills", 4));
        assertTrue(p.raiseTo("kills", 10));
        assertTrue(p.raiseTo("fresh", 1));
        p.set("kills", -3);
        assertEquals(0, p.get("kills"));
        assertEquals(Map.of("kills", 0L, "fresh", 1L), p.statsSnapshot());
    }

    @Test
    void revokeLowersTheFeatCount() {
        PlayerProgress p = new PlayerProgress(UUID.randomUUID(), "Revoker");
        p.unlock("first_blood", 1L);
        p.add(StatKeys.FEATS_UNLOCKED, 1);
        assertTrue(p.revoke("first_blood"));
        assertEquals(0, p.get(StatKeys.FEATS_UNLOCKED));
        assertFalse(p.revoke("first_blood"));
    }

    @Test
    void roundTripsThroughTheDocument() {
        UUID id = UUID.randomUUID();
        PlayerProgress p = new PlayerProgress(id, "Saver");
        p.add("distance", 1234);
        p.add("swim_distance", 12);
        p.unlock("first_steps", 99L);
        p.setSessionStart(123_456L);
        Document doc = Document.parse(p.snapshotForSave().toJson());
        PlayerProgress back = PlayerProgress.fromDocument(id, doc);
        assertEquals(p.statsSnapshot(), back.statsSnapshot());
        assertEquals(p.unlockedSnapshot(), back.unlockedSnapshot());
        assertEquals(123_456L, back.sessionStart());
        assertFalse(back.isDirty());
        back.setSessionStart(0);
        assertFalse(Document.parse(back.snapshotForSave().toJson()).containsKey("SessionStart"));
        back.add("distance", 1);
        assertEquals(1235, back.get("distance"));
        assertTrue(back.isDirty());
    }
}
