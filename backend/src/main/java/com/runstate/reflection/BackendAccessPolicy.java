package com.runstate.reflection;

import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Immutable server-owned access list. It stores credential fingerprints, never bearer codes. */
final class BackendAccessPolicy {
    private static final int MAX_CONFIG_CHARACTERS = 32_768;
    private static final int MAX_CREDENTIALS = 64;
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{43,128}");
    private static final Pattern PRINCIPAL_ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");
    private static final Pattern FINGERPRINT = Pattern.compile("[0-9a-f]{64}");
    private static final Set<String> FIELDS = Set.of("principalId", "tokenSha256", "expiresAt", "enabled");

    private final List<Credential> credentials;
    private final Clock clock;

    private BackendAccessPolicy(List<Credential> credentials, Clock clock) {
        this.credentials = List.copyOf(credentials);
        this.clock = clock;
    }

    /** Compatibility for the existing owner-only deployment; never an implicit recipient fallback. */
    static BackendAccessPolicy legacy(String token) {
        if (token == null || !TOKEN.matcher(token).matches()) throw invalidConfiguration();
        return new BackendAccessPolicy(List.of(new Credential(new Principal("owner-demo"),
                digest(token), null, true)), Clock.systemUTC());
    }

    static BackendAccessPolicy fromJson(String json) {
        return fromJson(json, Clock.systemUTC());
    }

    static BackendAccessPolicy fromJson(String json, Clock clock) {
        if (json == null || json.length() > MAX_CONFIG_CHARACTERS || clock == null) {
            throw invalidConfiguration();
        }
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.setStrictness(Strictness.STRICT);
            requireToken(reader, JsonToken.BEGIN_ARRAY);
            reader.beginArray();
            List<Credential> credentials = new ArrayList<>();
            Set<String> fingerprints = new HashSet<>();
            while (reader.hasNext()) {
                if (credentials.size() == MAX_CREDENTIALS) throw invalidConfiguration();
                requireToken(reader, JsonToken.BEGIN_OBJECT);
                reader.beginObject();
                Set<String> seen = new HashSet<>();
                String principalId = null;
                String fingerprint = null;
                Instant expiresAt = null;
                boolean enabled = false;
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (!FIELDS.contains(name) || !seen.add(name)) throw invalidConfiguration();
                    if (name.equals("enabled")) {
                        requireToken(reader, JsonToken.BOOLEAN);
                        enabled = reader.nextBoolean();
                    } else {
                        requireToken(reader, JsonToken.STRING);
                        String value = reader.nextString();
                        switch (name) {
                            case "principalId" -> principalId = value;
                            case "tokenSha256" -> fingerprint = value;
                            case "expiresAt" -> expiresAt = Instant.parse(value);
                            default -> throw invalidConfiguration();
                        }
                    }
                }
                reader.endObject();
                if (!seen.equals(FIELDS) || !FINGERPRINT.matcher(fingerprint).matches()
                        || !fingerprints.add(fingerprint)) throw invalidConfiguration();
                credentials.add(new Credential(new Principal(principalId),
                        HexFormat.of().parseHex(fingerprint), expiresAt, enabled));
            }
            reader.endArray();
            requireToken(reader, JsonToken.END_DOCUMENT);
            return new BackendAccessPolicy(credentials, clock);
        } catch (IOException | RuntimeException invalid) {
            // Parser diagnostics may include configuration contents; never retain their cause.
            throw invalidConfiguration();
        }
    }

    Optional<Principal> authenticate(List<String> authorizationHeaders) {
        if (authorizationHeaders == null || authorizationHeaders.size() != 1) return Optional.empty();
        String authorization = authorizationHeaders.get(0);
        if (authorization == null || authorization.length() < 7 + 43 || authorization.length() > 7 + 128
                || !authorization.startsWith("Bearer ")) return Optional.empty();
        String token = authorization.substring(7);
        if (!TOKEN.matcher(token).matches()) return Optional.empty();

        byte[] supplied = digest(token);
        Instant now = clock.instant();
        Principal authorized = null;
        for (Credential credential : credentials) {
            // Compare every configured fingerprint, including disabled/expired entries.
            boolean matches = MessageDigest.isEqual(credential.fingerprint, supplied);
            boolean current = credential.expiresAt == null || now.isBefore(credential.expiresAt);
            if (matches && credential.enabled && current) authorized = credential.principal;
        }
        return Optional.ofNullable(authorized);
    }

    private static void requireToken(JsonReader reader, JsonToken token) throws IOException {
        if (reader.peek() != token) throw invalidConfiguration();
    }

    private static byte[] digest(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Required credential hashing is unavailable.");
        }
    }

    private static IllegalArgumentException invalidConfiguration() {
        return new IllegalArgumentException("Invalid backend access configuration.");
    }

    @Override public String toString() { return "BackendAccessPolicy[credentials=redacted]"; }

    record Principal(String id) {
        Principal {
            if (id == null || !PRINCIPAL_ID.matcher(id).matches()) throw invalidConfiguration();
        }

        @Override public String toString() { return "Principal[id=redacted]"; }
    }

    private static final class Credential {
        private final Principal principal;
        private final byte[] fingerprint;
        private final Instant expiresAt;
        private final boolean enabled;

        private Credential(Principal principal, byte[] fingerprint, Instant expiresAt, boolean enabled) {
            this.principal = principal;
            this.fingerprint = fingerprint.clone();
            this.expiresAt = expiresAt;
            this.enabled = enabled;
        }

        @Override public String toString() { return "Credential[redacted]"; }
    }
}
