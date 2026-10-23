package com.smarttrip.oraclewallet;

import org.springframework.boot.CommandLineRunner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

final class OracleConnectionCheck implements CommandLineRunner {
    private static final String QUERY = "SELECT 1 FROM DUAL";

    @Override
    public void run(String... args) {
        String walletDirectory = requiredEnvironment("ORACLE_WALLET_PATH");
        String tnsAlias = requiredEnvironment("ORACLE_TNS_ALIAS");
        String username = requiredEnvironment("ORACLE_DB_USERNAME");
        String password = requiredEnvironment("ORACLE_DB_PASSWORD");

        Path walletPath;
        try {
            walletPath = Path.of(walletDirectory).toAbsolutePath().normalize();
        } catch (RuntimeException exception) {
            throw new IllegalStateException("ORACLE_WALLET_PATH is not a valid path.");
        }
        if (!Files.isDirectory(walletPath)) {
            throw new IllegalStateException("ORACLE_WALLET_PATH does not point to an existing directory.");
        }

        // The Oracle thin driver resolves the TNS alias using this Wallet directory.
        System.setProperty("oracle.net.tns_admin", walletPath.toString());
        String jdbcUrl = "jdbc:oracle:thin:@" + tnsAlias;

        System.out.println("Testing Oracle Autonomous Database connection using the configured TNS alias...");
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(QUERY)) {
            if (resultSet.next() && resultSet.getInt(1) == 1) {
                System.out.println("SUCCESS: Connected to Oracle and SELECT 1 FROM DUAL returned 1.");
            } else {
                throw new SQLException("The validation query returned no expected result.");
            }
        } catch (SQLException exception) {
            System.err.println("FAILED: Oracle connection check did not succeed. " + describe(exception));
            throw new IllegalStateException("Oracle Wallet connection check failed.");
        }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required environment variable " + name + " is not set.");
        }
        return value.trim();
    }

    private static String describe(SQLException exception) {
        int code = exception.getErrorCode();
        return switch (code) {
            case 1017 -> "Oracle rejected the username or password (ORA-01017). Check ORACLE_DB_USERNAME and ORACLE_DB_PASSWORD.";
            case 12154 -> "Oracle could not resolve the TNS alias (ORA-12154). Check ORACLE_TNS_ALIAS and the Wallet's tnsnames.ora.";
            case 12506, 12514, 12541 -> "Oracle could not establish the requested network service (ORA-" + code + "). Check the TNS alias, network access, and database availability.";
            case 28759 -> "Oracle could not open a Wallet file (ORA-28759). Check ORACLE_WALLET_PATH and Wallet file permissions.";
            default -> code == 0
                    ? "JDBC error " + exception.getClass().getSimpleName() + ". Check the Wallet, TNS alias, network access, and database availability."
                    : "Oracle JDBC error ORA-" + code + ". Check the Wallet, TNS alias, credentials, network access, and database availability.";
        };
    }
}
