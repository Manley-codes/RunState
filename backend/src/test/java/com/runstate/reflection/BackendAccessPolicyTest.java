package com.runstate.reflection;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class BackendAccessPolicyTest {
    private static final String FIRST = "A".repeat(43);
    private static final String SECOND = "b".repeat(64);
    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");
    private static final String EXPIRY = "2026-11-08T12:00:00Z";
    private static final Clock FIXED = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test void authenticatesSeparateRecipientsAndPreservesOwnerMode() {
        BackendAccessPolicy policy = configured(entry("runner-one", FIRST), entry("runner-two", SECOND));
        assertEquals("runner-one", policy.authenticate(bearer(FIRST)).orElseThrow().id());
        assertEquals("runner-two", policy.authenticate(bearer(SECOND)).orElseThrow().id());
        assertTrue(policy.authenticate(bearer("c".repeat(43))).isEmpty());
        assertEquals("owner-demo", BackendAccessPolicy.legacy(FIRST).authenticate(bearer(FIRST))
                .orElseThrow().id());
        assertTrue(BackendAccessPolicy.legacy(FIRST).authenticate(bearer(SECOND)).isEmpty());
    }

    @Test void acceptsBoundaryTokenLengthsAndRejectsMalformedAuthorization() {
        String longest = "z".repeat(128);
        BackendAccessPolicy policy = configured(entry("runner", FIRST), entry("runner", longest));
        assertTrue(policy.authenticate(bearer(longest)).isPresent());
        assertTrue(policy.authenticate(null).isEmpty());
        assertTrue(policy.authenticate(List.of()).isEmpty());
        assertTrue(policy.authenticate(Arrays.asList((String) null)).isEmpty());
        assertTrue(policy.authenticate(List.of("Bearer " + FIRST, "Bearer " + FIRST)).isEmpty());
        for (String header : List.of("bearer " + FIRST, "Basic " + FIRST, "Bearer  " + FIRST,
                " Bearer " + FIRST, "Bearer " + FIRST + " ", "Bearer " + "a".repeat(42),
                "Bearer " + "a".repeat(129), "Bearer " + FIRST + "=", "Bearer " + FIRST + "\n",
                "Bearer " + FIRST + ",Bearer " + SECOND, "Bearer " + "é".repeat(43))) {
            assertTrue(policy.authenticate(List.of(header)).isEmpty());
        }
    }

    @Test void expirationIsCheckedAgainOnEveryRequestIncludingExactBoundary() {
        AtomicReference<Instant> now = new AtomicReference<>(NOW);
        Clock clock = new Clock() {
            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        };
        JsonObject credential = entry("runner", FIRST);
        credential.addProperty("expiresAt", NOW.plusSeconds(1).toString());
        BackendAccessPolicy policy = BackendAccessPolicy.fromJson(json(credential), clock);
        assertTrue(policy.authenticate(bearer(FIRST)).isPresent());
        now.set(NOW.plusSeconds(1));
        assertTrue(policy.authenticate(bearer(FIRST)).isEmpty());
        now.set(NOW.plusSeconds(2));
        assertTrue(policy.authenticate(bearer(FIRST)).isEmpty());
    }

    @Test void disabledOrExpiredCredentialsDoNotAuthorize() {
        JsonObject disabled = entry("runner-one", FIRST);
        disabled.addProperty("enabled", false);
        JsonObject expired = entry("runner-two", SECOND);
        expired.addProperty("expiresAt", NOW.minusSeconds(1).toString());
        BackendAccessPolicy policy = configured(disabled, expired);
        assertTrue(policy.authenticate(bearer(FIRST)).isEmpty());
        assertTrue(policy.authenticate(bearer(SECOND)).isEmpty());
    }

    @Test void rotationKeepsPrincipalAndNewConfigurationRevokesOnlyOldCredential() {
        BackendAccessPolicy rotating = configured(entry("runner-one", FIRST), entry("runner-one", SECOND));
        assertEquals(rotating.authenticate(bearer(FIRST)), rotating.authenticate(bearer(SECOND)));
        BackendAccessPolicy revoked = configured(entry("runner-one", SECOND));
        assertTrue(revoked.authenticate(bearer(FIRST)).isEmpty());
        assertEquals("runner-one", revoked.authenticate(bearer(SECOND)).orElseThrow().id());
        assertTrue(rotating.authenticate(bearer(FIRST)).isPresent(), "Old immutable configuration is not live-reloaded.");
        assertTrue(BackendAccessPolicy.fromJson("[]", FIXED).authenticate(bearer(FIRST)).isEmpty());
    }

    @Test void rejectsDuplicateFingerprintEvenAcrossDifferentPrincipals() {
        invalid(json(entry("runner-one", FIRST), entry("runner-two", FIRST)));
        invalid(json(entry("runner-one", FIRST), entry("runner-one", FIRST)));
    }

    @Test void requiresExactlyFourFieldsWithoutCoercionOrDuplicates() {
        JsonObject base = entry("runner", FIRST);
        for (String field : List.of("principalId", "tokenSha256", "expiresAt", "enabled")) {
            JsonObject missing = base.deepCopy();
            missing.remove(field);
            invalid(json(missing));
            JsonObject nullValue = base.deepCopy();
            nullValue.add(field, null);
            invalid(json(nullValue));
            JsonObject wrongType = base.deepCopy();
            wrongType.addProperty(field, 1);
            invalid(json(wrongType));
            JsonObject nested = base.deepCopy();
            nested.add(field, new JsonObject());
            invalid(json(nested));
        }
        JsonObject unknown = base.deepCopy();
        unknown.addProperty("unexpected", true);
        invalid(json(unknown));
        JsonObject stringBoolean = base.deepCopy();
        stringBoolean.addProperty("enabled", "true");
        invalid(json(stringBoolean));
        String serialized = base.toString();
        invalid("[" + serialized.substring(0, serialized.length() - 1) + ",\"enabled\":true}]");
        invalid("[" + serialized.substring(0, serialized.length() - 1) + ",\"principal\\u0049d\":\"runner\"}]");
    }

    @Test void rejectsNonStrictJsonAndTrailingContent() {
        String valid = json(entry("runner", FIRST));
        for (String input : List.of("", "null", "{}", "[null]", "[true]", "[[]]", valid + " []",
                valid + " garbage", "/*comment*/" + valid, valid.replace("\"enabled\"", "enabled"),
                valid.replace("\"runner\"", "'runner'"), valid.substring(0, valid.length() - 1) + ",]")) {
            invalid(input);
        }
        invalid(null);
    }

    @Test void validatesPrincipalHashAndExpiry() {
        for (String id : List.of("", "Runner", "runner one", "a".repeat(65), "../runner", "é", "_runner")) {
            JsonObject credential = entry("runner", FIRST);
            credential.addProperty("principalId", id);
            invalid(json(credential));
        }
        assertTrue(configured(entry("a".repeat(64), FIRST)).authenticate(bearer(FIRST)).isPresent());
        assertTrue(configured(entry("a", FIRST)).authenticate(bearer(FIRST)).isPresent());
        for (String hash : List.of("", "0".repeat(63), "0".repeat(65), "A".repeat(64), "z".repeat(64))) {
            JsonObject credential = entry("runner", FIRST);
            credential.addProperty("tokenSha256", hash);
            invalid(json(credential));
        }
        for (String expiry : List.of("", "tomorrow", "2026-11-08", "2026-11-08T12:00:00")) {
            JsonObject credential = entry("runner", FIRST);
            credential.addProperty("expiresAt", expiry);
            invalid(json(credential));
        }
    }

    @Test void enforcesEntryAndConfigurationSizeBounds() {
        JsonArray entries = new JsonArray();
        for (int index = 0; index < 64; index++) entries.add(entry("runner-" + index, "a".repeat(43) + index));
        assertNotNull(BackendAccessPolicy.fromJson(entries.toString(), FIXED));
        entries.add(entry("runner-64", "a".repeat(43) + "64"));
        invalid(entries.toString());
        assertNotNull(BackendAccessPolicy.fromJson("[]" + " ".repeat(32_766), FIXED));
        invalid("[]" + " ".repeat(32_767));
    }

    @Test void diagnosticsDoNotExposeConfigurationCredentialsOrParserCauses() {
        String marker = "sensitive-configuration-marker";
        JsonObject credential = entry("private-recipient", FIRST);
        credential.addProperty("expiresAt", marker);
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> BackendAccessPolicy.fromJson(json(credential), FIXED));
        assertNull(failure.getCause());
        assertFalse(failure.toString().contains(marker));
        assertFalse(failure.toString().contains(fingerprint(FIRST)));
        assertFalse(failure.toString().contains("private-recipient"));
        BackendAccessPolicy policy = configured(entry("private-recipient", FIRST));
        assertEquals("BackendAccessPolicy[credentials=redacted]", policy.toString());
        assertEquals("Principal[id=redacted]", policy.authenticate(bearer(FIRST)).orElseThrow().toString());
        for (String invalidToken : Arrays.asList(null, marker, FIRST + "!", "a".repeat(129))) {
            IllegalArgumentException invalid = assertThrows(IllegalArgumentException.class,
                    () -> BackendAccessPolicy.legacy(invalidToken));
            assertNull(invalid.getCause());
            if (invalidToken != null) assertFalse(invalid.toString().contains(invalidToken));
        }
    }

    private static BackendAccessPolicy configured(JsonObject... entries) {
        return BackendAccessPolicy.fromJson(json(entries), FIXED);
    }

    private static JsonObject entry(String principalId, String token) {
        JsonObject entry = new JsonObject();
        entry.addProperty("principalId", principalId);
        entry.addProperty("tokenSha256", fingerprint(token));
        entry.addProperty("expiresAt", EXPIRY);
        entry.addProperty("enabled", true);
        return entry;
    }

    private static String json(JsonObject... entries) {
        JsonArray array = new JsonArray();
        for (JsonObject entry : entries) array.add(entry);
        return array.toString();
    }

    private static List<String> bearer(String token) { return List.of("Bearer " + token); }

    private static String fingerprint(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception impossible) { throw new AssertionError(impossible); }
    }

    private static void invalid(String json) {
        IllegalArgumentException invalid = assertThrows(IllegalArgumentException.class,
                () -> BackendAccessPolicy.fromJson(json, FIXED));
        assertEquals("Invalid backend access configuration.", invalid.getMessage());
        assertNull(invalid.getCause());
    }
}
