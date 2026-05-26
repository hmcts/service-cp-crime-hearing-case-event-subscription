package uk.gov.hmcts.cp.subscription.unit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Guards against modification of existing Flyway migration scripts, and ensures
 * every migration file has a registered checksum.
 * <p>
 * Flyway uses checksums to detect tampering — any change to an applied migration
 * causes startup failures in all environments. If this test fails because:
 * - a checksum mismatches: restore the original file and create a new migration instead
 * - a file is unregistered: add the SHA-256 shown in the failure to EXPECTED_CHECKSUMS below
 */
class FlywayMigrationIntegrityTest {

    private static final String MIGRATION_DIR = "/db/migration";

    private static final Map<String, String> EXPECTED_CHECKSUMS = Map.of(
            "V1.001__subscription_schema.sql", "973f11ddacfe34faa902b2250150acea1ae7992783ac452a1050b92deab4905b",
            "V1.002__subscription_schema.sql", "ab12c12995a12e4f0dd97a14fd0cf0aef76e388d811a5029aa0e4e0fd64d4808",
            "V1.003__documentmapping_schema.sql", "a23c3117fc6239aae93919b3f9cc7774c7ab286dc429449f528d003e5292c200",
            "V1.004__add_client_id_to_client_subscription.sql", "566ad97c1692c14c02fdf896b6504c11215d8d6cf17dde08fc7168c0da941137",
            "V1.005__add_event_type_table.sql", "c233451d0cabd305fbd6fd640ac67b5ce2bcce3ea7961dd078db004e9dcc06cc",
            "V1.006__add_client_table.sql", "45f62b36b2574ba96b08fff58312580b61df92caf14e20224635b0130ccc96ac",
            "V1.007__add_client_events_table.sql", "e473343cfd17fafb6a099f5ae6bd8551207f84a5caf9897e57043bd2f41ab175",
            "V1.008__forward_fix_event_type.sql", "e8532eebb667c7ed413849dde905a7fc367ab7ba43d68be3dbad602d409e53aa",
            "V1.009__restore_event_type_data.sql", "0ac839978f1efa8ae1c1dc956d561f75701102f7e2e16e2673147ac4d87dc2c9",
            "V1.010__add_client_hmac.sql", "f6f57f6e526d9dc8cf7252f99f29556df0806178114cc007bc8db0c434a115e6"
    );

    @Test
    void all_migration_files_must_have_a_registered_checksum()
            throws URISyntaxException, IOException, NoSuchAlgorithmException {
        List<String> migrationFiles = listMigrationFiles();
        assertThat(migrationFiles).isNotEmpty();

        List<String> unregistered = migrationFiles.stream()
                .filter(f -> !EXPECTED_CHECKSUMS.containsKey(f))
                .toList();

        if (!unregistered.isEmpty()) {
            StringBuilder hint = new StringBuilder(
                    "New migration(s) found without a registered checksum. "
                            + "Add the following to EXPECTED_CHECKSUMS in FlywayMigrationIntegrityTest:\n");
            for (String filename : unregistered) {
                String checksum = sha256(MIGRATION_DIR + "/" + filename);
                hint.append(String.format("  \"%s\", \"%s\"%n", filename, checksum));
            }
            fail(hint.toString());
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("registeredMigrations")
    void migration_script_must_not_be_modified(String filename, String expectedSha256)
            throws IOException, NoSuchAlgorithmException {
        String actual = sha256(MIGRATION_DIR + "/" + filename);
        assertThat(actual)
                .as("Migration '%s' has been modified. "
                        + "Editing applied Flyway migrations breaks all environments. "
                        + "Create a new migration script instead.", filename)
                .isEqualTo(expectedSha256);
    }

    static Stream<Arguments> registeredMigrations() {
        return EXPECTED_CHECKSUMS.entrySet().stream()
                .map(e -> Arguments.of(e.getKey(), e.getValue()));
    }

    private List<String> listMigrationFiles() throws URISyntaxException {
        URL dirUrl = getClass().getResource(MIGRATION_DIR);
        assertThat(dirUrl).as("Migration directory not found on classpath: %s", MIGRATION_DIR).isNotNull();
        File dir = new File(dirUrl.toURI());
        String[] files = dir.list((d, name) -> name.endsWith(".sql"));
        assertThat(files).as("No .sql files found in %s", MIGRATION_DIR).isNotNull();
        return Arrays.asList(files);
    }

    private String sha256(String classpathResource) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = getClass().getResourceAsStream(classpathResource)) {
            assertThat(in)
                    .as("Migration file not found on classpath: %s", classpathResource)
                    .isNotNull();
            try (DigestInputStream dis = new DigestInputStream(in, digest)) {
                dis.transferTo(OutputStream.nullOutputStream());
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}