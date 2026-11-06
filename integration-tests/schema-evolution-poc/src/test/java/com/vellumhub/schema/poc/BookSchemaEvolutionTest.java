package com.vellumhub.schema.poc;

import com.vellumhub.kafka.contracts.book.BookChangedEvent;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.avro.AvroTypeException;
import org.apache.avro.JsonProperties;
import org.apache.avro.Schema;
import org.apache.avro.SchemaCompatibility;
import org.apache.avro.SchemaNormalization;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BookSchemaEvolutionTest {
    private final Schema v1 = BookSchemaFixtures.schema("book-v1.avsc");
    private final Schema v2 = BookSchemaFixtures.schema("book-v2.avsc");
    private final Schema v3 = BookSchemaFixtures.schema("book-v3-compatible.avsc");
    private final Schema incompatibleV3 = BookSchemaFixtures.schema("book-v3-incompatible.avsc");

    @Test void codegenSchemaIsTheVersionedContract() {
        assertEquals(SchemaNormalization.toParsingForm(v2),
                SchemaNormalization.toParsingForm(com.vellumhub.kafka.avro.book.BookChangedEvent.getClassSchema()),
                "The code generator may add avro.java.string metadata; the wire contract must be identical");
        assertEquals("com.vellumhub.kafka.avro.book.BookChangedEvent", v2.getFullName());
        // Parsing canonical form intentionally strips defaults and aliases;
        // those evolution-critical properties need independent assertions.
        assertNullDefault(v2.getField("catalogSource"));
        assertNullDefault(v2.getField("state"));
        Schema snapshot = v2.getField("state").schema().getTypes().get(1);
        for (String field : List.of("description", "coverUrl", "author", "isbn", "publisher")) {
            assertNullDefault(snapshot.getField(field));
        }
    }

    @Test void additiveEvolutionSupportsHistoricalWriterSchemas() throws IOException {
        for (Schema writer : List.of(v1, v2)) {
            assertCompatible(v3, writer);
            GenericRecord decoded = resolve(writer, v3, BookSchemaFixtures.event(writer, "CREATED", 3, true));
            assertEquals("A Book", ((GenericRecord) decoded.get("state")).get("title").toString());
            assertNull(decoded.get("language"));
        }
        GenericRecord fromV1 = resolve(v1, v2, BookSchemaFixtures.event(v1, "CREATED", 3, true));
        assertNull(fromV1.get("catalogSource"));
    }

    @Test void candidateCanPassLatestAndBreakHistoricalReplay() {
        // v2 introduced catalogSource WITH default. Removing the default from v3
        // still reads v2, but a v3 reader can no longer read v1: transitive check matters.
        assertCompatible(incompatibleV3, v2);
        assertEquals(SchemaCompatibility.SchemaCompatibilityType.INCOMPATIBLE,
                SchemaCompatibility.checkReaderWriterCompatibility(incompatibleV3, v1).getType());
        assertCompatible(v3, v1);
        assertCompatible(v3, v2);
    }

    @Test void activeEventsMustNotHaveNullState() throws IOException {
        for (String kind : List.of("BASELINE", "CREATED", "UPDATED")) {
            GenericRecord withoutState = resolve(v1, v2, BookSchemaFixtures.event(v1, kind, 4L, false));
            assertThrows(IllegalArgumentException.class, () -> toDomain(withoutState),
                    "Active kind " + kind + " must carry a complete public snapshot");
        }
    }

    @Test void structurallyCompatibleKafkaKeyChangeMustNotCorruptAProjection() throws IOException {
        // A producer could switch Kafka key from bookId to eventId while leaving
        // the Avro payload schema untouched: Registry compatibility stays green.
        // The downstream local projection must reject the semantically invalid key.
        GenericRecord event = resolve(v1, v2, BookSchemaFixtures.event(v1, "UPDATED", 2L, true));
        assertCompatible(v2, v1);
        Map<String, Long> readModel = new HashMap<>();
        readModel.put(BookSchemaFixtures.BOOK_ID, 1L);

        assertThrows(IllegalArgumentException.class,
                () -> applyBookRevision(readModel, BookSchemaFixtures.EVENT_ID, event));
        assertEquals(Map.of(BookSchemaFixtures.BOOK_ID, 1L), readModel,
                "Rejected events must not advance a local read model");
        applyBookRevision(readModel, BookSchemaFixtures.BOOK_ID, event);
        assertEquals(2L, readModel.get(BookSchemaFixtures.BOOK_ID));
    }

    @Test void renamedFieldNeedsAnAliasToReadHistoricalData() throws IOException {
        String original = v2.toString();
        String renamed = original.replace("\"name\":\"title\",\"type\":\"string\"",
                "\"name\":\"displayTitle\",\"type\":\"string\",\"aliases\":[\"title\"]");
        assertNotEquals(original, renamed, "Renamed test fixture must actually change the field");
        Schema reader = new Schema.Parser().parse(renamed);
        GenericRecord value = resolve(v2, reader, BookSchemaFixtures.event(v2, "CREATED", 4L, true));
        assertEquals("A Book", ((GenericRecord) value.get("state")).get("displayTitle").toString());
    }

    @Test void newEnumSymbolRequiresAReaderPolicy() {
        String oldJson = v2.toString();
        String extended = oldJson.replace("\"DELETED\"]", "\"DELETED\",\"RESTORED\"]");
        assertNotEquals(oldJson, extended);
        Schema newWriter = new Schema.Parser().parse(extended);
        GenericRecord event = BookSchemaFixtures.event(newWriter, "RESTORED", 7L, true);
        assertThrows(AvroTypeException.class, () -> resolve(newWriter, v2, event),
                "An old consumer cannot safely assume support for a newly introduced enum symbol");
    }

    @Test void structurallyValidDataCanStillViolateDomainSemantics() throws IOException {
        GenericRecord badRevision = resolve(v1, v2, BookSchemaFixtures.event(v1, "CREATED", -1, true));
        assertThrows(IllegalArgumentException.class, () -> toDomain(badRevision));

        GenericRecord invalidDeletion = resolve(v1, v2, BookSchemaFixtures.event(v1, "DELETED", 4, true));
        assertThrows(IllegalArgumentException.class, () -> toDomain(invalidDeletion));

        GenericRecord validDeletion = resolve(v1, v2, BookSchemaFixtures.event(v1, "DELETED", 4, false));
        assertNull(toDomain(validDeletion).state());
        assertEquals(4, toDomain(validDeletion).revision());
    }

    private static void assertNullDefault(Schema.Field field) {
        assertTrue(field.hasDefaultValue(), () -> "Missing evolution default: " + field.name());
        assertEquals(JsonProperties.NULL_VALUE, field.defaultVal(), field.name());
    }

    private static void applyBookRevision(Map<String, Long> projection,
                                          String kafkaKey, GenericRecord event) {
        BookChangedEvent domain = toDomain(event);
        if (!domain.bookId().toString().equals(kafkaKey)) {
            throw new IllegalArgumentException("Kafka key must equal bookId before projecting a Book fact");
        }
        projection.put(kafkaKey, domain.revision());
    }

    static void assertCompatible(Schema reader, Schema writer) {
        assertEquals(SchemaCompatibility.SchemaCompatibilityType.COMPATIBLE,
                SchemaCompatibility.checkReaderWriterCompatibility(reader, writer).getType(),
                () -> "Reader " + reader.getName() + " cannot read writer " + writer.getName());
    }

    static GenericRecord resolve(Schema writer, Schema reader, GenericRecord event) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        var encoder = EncoderFactory.get().binaryEncoder(bytes, null);
        new GenericDatumWriter<GenericRecord>(writer).write(event, encoder);
        encoder.flush();
        var decoder = DecoderFactory.get().binaryDecoder(bytes.toByteArray(), null);
        return new GenericDatumReader<GenericRecord>(writer, reader).read(null, decoder);
    }

    /** Reuse the current application's invariants instead of assuming Avro enforces business rules. */
    static BookChangedEvent toDomain(GenericRecord event) {
        GenericRecord snapshot = (GenericRecord) event.get("state");
        BookChangedEvent.State state = null;
        if (snapshot != null) {
            List<String> genres = ((List<?>) snapshot.get("genres")).stream()
                    .map(Object::toString).collect(Collectors.toList());
            state = new BookChangedEvent.State(
                    value(snapshot.get("title")), value(snapshot.get("description")),
                    (int) snapshot.get("releaseYear"), value(snapshot.get("coverUrl")),
                    value(snapshot.get("author")), value(snapshot.get("isbn")),
                    (int) snapshot.get("pageCount"), value(snapshot.get("publisher")), genres);
        }
        return new BookChangedEvent(
                UUID.fromString(value(event.get("eventId"))),
                Instant.parse(value(event.get("occurredAt"))),
                UUID.fromString(value(event.get("bookId"))),
                (long) event.get("revision"),
                BookChangedEvent.Kind.valueOf(event.get("kind").toString()), state);
    }

    private static String value(Object value) { return value == null ? null : value.toString(); }
}
