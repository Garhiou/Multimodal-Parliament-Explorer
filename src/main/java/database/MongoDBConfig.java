package database;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Lädt die Konfigurationsdaten aus der Datei mongodb.properties
 *
 * @author Delia Maniliuc
 */
public class MongoDBConfig extends Properties {

    public MongoDBConfig(String path) throws IOException {
        InputStream input = getClass().getClassLoader().getResourceAsStream(path);

        if (input == null) {
            throw new FileNotFoundException(" Datei nicht gefunden: " + path);
        }

        this.load(new InputStreamReader(input, StandardCharsets.UTF_8));
    }

    public String getMongoHostname() {
        return getProperty("remote_host", "127.0.0.1");
    }

    public String getMongoUsername() {
        return getProperty("remote_user", "user");
    }

    public String getMongoPassword() {
        return getProperty("remote_password", "password");
    }

    public int getMongoPort() {
        return Integer.parseInt(getProperty("remote_port", "27017"));
    }

    public String getMongoDatabase() {
        return getProperty("remote_database", "database");
    }

    public String getMongoCollection() {
        return getProperty("remote_collection", "collection");
    }
}
