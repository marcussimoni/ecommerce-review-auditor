package br.com.ecommercereviewauditor.config;

import org.flywaydb.core.Flyway;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;

public class DatabaseConfig {

    private final String url;
    private final String username;
    private final String password;
    private final String flywayLocation;

    public DatabaseConfig() {
        Map<String, Object> root = loadYaml();
        Map<String, Object> ds = (Map<String, Object>) root.get("datasource");
        Map<String, Object> fw = (Map<String, Object>) root.get("flyway");

        this.url = (String) ds.get("url");
        this.username = (String) ds.get("username");
        this.password = ds.getOrDefault("password", "").toString();
        this.flywayLocation = (String) fw.getOrDefault("locations", "classpath:db/migration");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadYaml() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("application.yaml")) {
            if (in == null) {
                throw new IllegalStateException("application.yaml não encontrado em src/main/resources");
            }
            Yaml yaml = new Yaml();
            return yaml.load(in);
        } catch (Exception e) {
            throw new RuntimeException("Falha ao carregar application.yaml", e);
        }
    }

    public void migrate() {
        Flyway.configure()
                .dataSource(url, username, password)
                .locations(flywayLocation)
                .load()
                .migrate();
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }
}